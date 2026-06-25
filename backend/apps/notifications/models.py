from django.db import models
from django.contrib.auth import get_user_model
import uuid

User = get_user_model()


class Notification(models.Model):
    class NotificationType(models.TextChoices):
        SOS = "sos", "SOS Alert"
        LOCATION = "location", "Location Update"
        GEOFENCE = "geofence", "Geofence Event"
        PERMISSION = "permission", "Permission Changed"
        DNS = "dns", "DNS Block"
        SYSTEM = "system", "System"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    recipient = models.ForeignKey(
        User, on_delete=models.CASCADE, related_name="notifications"
    )
    notification_type = models.CharField(max_length=20, choices=NotificationType.choices)
    title = models.CharField(max_length=200)
    body = models.TextField()
    data = models.JSONField(default=dict)
    is_read = models.BooleanField(default=False)
    sent_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-sent_at"]
        indexes = [
            models.Index(fields=["recipient", "is_read"]),
            models.Index(fields=["recipient", "-sent_at"]),
        ]

    def __str__(self):
        return f"[{self.notification_type}] {self.title} → {self.recipient}"
