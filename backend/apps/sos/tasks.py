from celery import shared_task
from django.conf import settings
import logging

logger = logging.getLogger(__name__)


@shared_task(bind=True, queue="sos", max_retries=3, default_retry_delay=30)
def process_sos_event(self, sos_event_id: str):
    """Process SOS event: send push notifications and optionally SMS."""
    from .models import SosEvent
    from apps.notifications.tasks import send_push_to_family_parents, send_emergency_sms

    try:
        event = SosEvent.objects.select_related("child__family").get(id=sos_event_id)
    except SosEvent.DoesNotExist:
        logger.error(f"SOS event {sos_event_id} not found")
        return

    child = event.child
    family = child.family
    if not family:
        logger.warning(f"SOS event {sos_event_id}: child has no family")
        return

    child_name = child.get_full_name() or child.email
    location_text = ""
    if event.latitude and event.longitude:
        location_text = f"\nКоординаты: {event.latitude}, {event.longitude}"
        location_text += f"\nhttps://maps.google.com/?q={event.latitude},{event.longitude}"

    if event.sos_type == SosEvent.SosType.ALERT:
        title = f"⚠️ Сигнал от {child_name}"
        body = f"{child_name} нажал кнопку SOS ({event.press_count} раз).{location_text}"
    else:
        title = f"🚨 ЭКСТРЕННЫЙ СИГНАЛ от {child_name}"
        body = (
            f"ВНИМАНИЕ! {child_name} нажал кнопку SOS {event.press_count} раз!\n"
            f"Немедленно свяжитесь с ребёнком!{location_text}"
        )

    # Send FCM push to all family parents
    send_push_to_family_parents.delay(
        family_id=str(family.id),
        exclude_user_id=str(child.id),
        title=title,
        body=body,
        data={
            "type": "sos",
            "sos_type": event.sos_type,
            "sos_id": str(event.id),
            "latitude": str(event.latitude) if event.latitude else "",
            "longitude": str(event.longitude) if event.longitude else "",
        },
    )

    # Emergency: send SMS via Twilio
    if event.sos_type == SosEvent.SosType.EMERGENCY:
        sms_body = (
            f"ЭКСТРЕННЫЙ СИГНАЛ FamilyGuard!\n"
            f"Ребёнок: {child_name}\n"
            f"Нажатий кнопки SOS: {event.press_count}\n"
        )
        if event.latitude and event.longitude:
            sms_body += f"Местоположение: https://maps.google.com/?q={event.latitude},{event.longitude}"

        send_emergency_sms.delay(message=sms_body, sos_event_id=str(event.id))

    # Update status
    from django.utils import timezone
    status_value = (
        SosEvent.SosStatus.EMERGENCY_SENT
        if event.sos_type == SosEvent.SosType.EMERGENCY
        else SosEvent.SosStatus.NOTIFIED
    )
    SosEvent.objects.filter(id=sos_event_id).update(status=status_value)

    # Broadcast via WebSocket
    from channels.layers import get_channel_layer
    from asgiref.sync import async_to_sync
    channel_layer = get_channel_layer()
    async_to_sync(channel_layer.group_send)(
        f"family_map_{family.id}",
        {
            "type": "sos_alert",
            "data": {
                "sos_id": str(event.id),
                "sos_type": event.sos_type,
                "child_id": str(child.id),
                "child_name": child_name,
                "latitude": float(event.latitude) if event.latitude else None,
                "longitude": float(event.longitude) if event.longitude else None,
                "press_count": event.press_count,
                "created_at": event.created_at.isoformat(),
            },
        },
    )

    logger.info(f"SOS event {sos_event_id} processed successfully")
