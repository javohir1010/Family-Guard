from django.db import models
from django.contrib.auth import get_user_model
import uuid

User = get_user_model()


class SosEvent(models.Model):
    class SosType(models.TextChoices):
        ALERT = "alert", "Alert (1-7 presses)"
        EMERGENCY = "emergency", "Emergency (8+ presses)"

    class SosStatus(models.TextChoices):
        PENDING = "pending", "Pending"
        NOTIFIED = "notified", "Family Notified"
        EMERGENCY_SENT = "emergency_sent", "Emergency Services Notified"
        RESOLVED = "resolved", "Resolved"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name="sos_events",
    )
    sos_type = models.CharField(max_length=20, choices=SosType.choices)
    status = models.CharField(
        max_length=20, choices=SosStatus.choices, default=SosStatus.PENDING
    )
    press_count = models.PositiveIntegerField(default=1)
    latitude = models.DecimalField(
        max_digits=10, decimal_places=7, null=True, blank=True
    )
    longitude = models.DecimalField(
        max_digits=10, decimal_places=7, null=True, blank=True
    )
    message = models.TextField(blank=True)
    resolved_by = models.ForeignKey(
        User,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="resolved_sos",
    )
    resolved_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "SOS Event"
        verbose_name_plural = "SOS Events"
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["child", "-created_at"]),
            models.Index(fields=["status"]),
        ]

    def __str__(self):
        return f"SOS [{self.sos_type}] from {self.child} at {self.created_at}"
