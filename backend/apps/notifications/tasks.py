from celery import shared_task
from django.conf import settings
from django.contrib.auth import get_user_model
import logging

logger = logging.getLogger(__name__)
User = get_user_model()


def get_fcm_app():
    """Initialize Firebase Admin app (lazy, singleton)."""
    try:
        import firebase_admin
        from firebase_admin import credentials
        try:
            return firebase_admin.get_app()
        except ValueError:
            cred_file = settings.FIREBASE_CREDENTIALS_FILE
            if cred_file:
                cred = credentials.Certificate(cred_file)
            else:
                cred = credentials.ApplicationDefault()
            return firebase_admin.initialize_app(cred)
    except Exception as e:
        logger.error(f"Firebase init error: {e}")
        return None


@shared_task(bind=True, queue="notifications", max_retries=3, default_retry_delay=60)
def send_push_notification(self, user_id: str, title: str, body: str, data: dict = None):
    """Send FCM push notification to a single user."""
    try:
        from firebase_admin import messaging

        user = User.objects.get(id=user_id)
        if not user.device_token:
            logger.info(f"User {user_id} has no device token, skipping push.")
            return

        app = get_fcm_app()
        if not app:
            return

        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            data={k: str(v) for k, v in (data or {}).items()},
            token=user.device_token,
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    sound="default",
                    priority="max",
                ),
            ),
        )
        response = messaging.send(message, app=app)
        logger.info(f"FCM sent to {user.email}: {response}")

        # Save to DB
        from .models import Notification
        Notification.objects.create(
            recipient=user,
            notification_type=data.get("type", "system") if data else "system",
            title=title,
            body=body,
            data=data or {},
        )

    except Exception as exc:
        logger.error(f"FCM send error for user {user_id}: {exc}")
        raise self.retry(exc=exc)


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
