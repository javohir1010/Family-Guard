from celery import shared_task
from django.conf import settings
from django.contrib.auth import get_user_model
import logging
import os

logger = logging.getLogger(__name__)
User = get_user_model()


# Maps the "type" sent in push data to Notification.NotificationType
NOTIFICATION_TYPE_MAP = {
    "sos": "sos",
    "geofence": "geofence",
    "permission_revoked": "permission",
    "weekly_report": "system",
}


def get_fcm_app():
    """Initialize Firebase Admin app (lazy, singleton). None if not configured."""
    cred_file = settings.FIREBASE_CREDENTIALS_FILE
    if not cred_file or not os.path.isfile(cred_file):
        return None
    try:
        import firebase_admin
        from firebase_admin import credentials
        try:
            return firebase_admin.get_app()
        except ValueError:
            return firebase_admin.initialize_app(credentials.Certificate(cred_file))
    except Exception as e:
        logger.error(f"Firebase init error: {e}")
        return None


@shared_task(bind=True, queue="notifications", max_retries=3, default_retry_delay=60)
def send_push_notification(self, user_id: str, title: str, body: str, data: dict = None,
                           notification_id: str = None):
    """Store a notification for the user and deliver it via FCM if possible."""
    from .models import Notification

    data = data or {}
    try:
        user = User.objects.get(id=user_id)
    except User.DoesNotExist:
        logger.warning(f"Push skipped: user {user_id} not found")
        return

    # Store first so the web dashboard shows it even without FCM.
    # On retry the same row is reused instead of creating a duplicate.
    if notification_id is None:
        notification_type = NOTIFICATION_TYPE_MAP.get(data.get("type"), "system")
        notification_id = str(Notification.objects.create(
            recipient=user,
            notification_type=notification_type,
            title=title,
            body=body,
            data=data,
        ).id)

    if not user.device_token:
        logger.info(f"User {user_id} has no device token, skipping push.")
        return

    app = get_fcm_app()
    if not app:
        logger.info("FCM not configured, skipping push.")
        return

    try:
        from firebase_admin import messaging

        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            data={k: str(v) for k, v in data.items()},
            token=user.device_token,
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    channel_id="fg_alerts_channel",  # PushMessagingService.CHANNEL_ID
                    sound="default",
                    priority="max",
                ),
            ),
        )
        response = messaging.send(message, app=app)
        logger.info(f"FCM sent to {user.email}: {response}")
    except Exception as exc:
        logger.error(f"FCM send error for user {user_id}: {exc}")
        raise self.retry(exc=exc, kwargs={
            "user_id": user_id, "title": title, "body": body,
            "data": data, "notification_id": notification_id,
        })


@shared_task(bind=True, queue="notifications", max_retries=2)
def send_push_to_family_parents(
    self, family_id: str, title: str, body: str,
    exclude_user_id: str = None, data: dict = None
):
    """Send FCM push to all parents in a family."""
    parents = User.objects.filter(
        family__id=family_id,
        role="parent",
    ).exclude(id=exclude_user_id or "")

    for parent in parents:
        send_push_notification.apply_async(
            args=[str(parent.id), title, body, data or {}],
            queue="notifications",
        )

    logger.info(f"Queued push for {parents.count()} parents in family {family_id}")


@shared_task(queue="notifications")
def send_data_push_to_family_children(family_id: str, data: dict):
    """
    Silent FCM data message to every child device of the family, e.g.
    {"type": "blocklist_updated"} so the DNS filter reloads its list now.
    Not stored as a Notification. No-op without FCM credentials.
    """
    app = get_fcm_app()
    if not app:
        return
    from firebase_admin import messaging

    tokens = User.objects.filter(
        family__id=family_id, role="child"
    ).exclude(device_token="").values_list("device_token", flat=True)
    for token in tokens:
        try:
            messaging.send(messaging.Message(
                data={k: str(v) for k, v in data.items()},
                token=token,
                android=messaging.AndroidConfig(priority="high"),
            ), app=app)
        except Exception as exc:
            logger.warning(f"Data push to a child device failed: {exc}")


@shared_task(queue="notifications")
def send_data_push_to_token(token: str, data: dict):
    """Silent FCM data message to one device, e.g. {"type": "unlinked"}.
    Takes the token itself: the caller may already have cleared it on the user."""
    app = get_fcm_app()
    if not app:
        return
    from firebase_admin import messaging

    try:
        messaging.send(messaging.Message(
            data={k: str(v) for k, v in data.items()},
            token=token,
            android=messaging.AndroidConfig(priority="high"),
        ), app=app)
    except Exception as exc:
        logger.warning(f"Data push to a device failed: {exc}")


@shared_task(bind=True, queue="sos", max_retries=3, default_retry_delay=10)
def send_emergency_sms(self, message: str, sos_event_id: str = None):
    """Send emergency SMS via Twilio."""
    try:
        from twilio.rest import Client

        account_sid = settings.TWILIO_ACCOUNT_SID
        auth_token = settings.TWILIO_AUTH_TOKEN
        from_number = settings.TWILIO_FROM_NUMBER
        to_number = settings.EMERGENCY_PHONE_NUMBER

        if not all([account_sid, auth_token, from_number, to_number]):
            logger.warning("Twilio not configured, skipping SMS.")
            return

        client = Client(account_sid, auth_token)
        msg = client.messages.create(
            body=message, from_=from_number, to=to_number
        )
        logger.info(f"Emergency SMS sent: {msg.sid}")

        if sos_event_id:
            from apps.sos.models import SosEvent
            SosEvent.objects.filter(id=sos_event_id).update(
                status=SosEvent.SosStatus.EMERGENCY_SENT
            )

    except Exception as exc:
        logger.error(f"Twilio SMS error: {exc}")
        raise self.retry(exc=exc)


@shared_task(queue="notifications")
def send_weekly_report():
    """Generate and send weekly activity report to all parents."""
    from django.utils import timezone
    from apps.app_control.models import AppUsage
    from apps.dns_filter.models import DnsQuery

    week_ago = timezone.now() - timezone.timedelta(days=7)

    families = User.objects.filter(role="parent", family__isnull=False).values_list(
        "family", flat=True
    ).distinct()

    for family_id in families:
        children = User.objects.filter(family_id=family_id, role="child")
        if not children.exists():
            continue

        report_lines = ["📊 Еженедельный отчёт FamilyGuard\n"]
        for child in children:
            report_lines.append(f"\n👶 {child.get_full_name() or child.email}:")
            # Top apps
            top_apps = (
                AppUsage.objects.filter(child=child, date__gte=week_ago.date())
                .order_by("-duration_seconds")[:3]
            )
            for app in top_apps:
                report_lines.append(f"  📱 {app.app_name}: {app.duration_minutes} мин")
            # Blocked DNS
            blocked_count = DnsQuery.objects.filter(
                child=child, was_blocked=True, timestamp__gte=week_ago
            ).count()
            report_lines.append(f"  🚫 Заблокировано запросов: {blocked_count}")

        report_body = "\n".join(report_lines)

        parents = User.objects.filter(family_id=family_id, role="parent")
        for parent in parents:
            send_push_notification.delay(
                str(parent.id),
                "Еженедельный отчёт",
                report_body[:500],
                {"type": "weekly_report"},
            )
