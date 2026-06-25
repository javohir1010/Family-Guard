from django.db import models
from django.contrib.auth import get_user_model
import uuid

User = get_user_model()


class LocationRecord(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name="location_records",
    )
    latitude = models.DecimalField(max_digits=10, decimal_places=7)
    longitude = models.DecimalField(max_digits=10, decimal_places=7)
    accuracy = models.FloatField(null=True, blank=True, help_text="Accuracy in meters")
    altitude = models.FloatField(null=True, blank=True)
    speed = models.FloatField(null=True, blank=True, help_text="Speed m/s")
    battery_level = models.IntegerField(
        null=True, blank=True, help_text="Battery percentage 0-100"
    )
    timestamp = models.DateTimeField(auto_now_add=True)
    recorded_at = models.DateTimeField(
        help_text="Device-side timestamp (may differ from server time)"
    )

    class Meta:
        verbose_name = "Location Record"
        verbose_name_plural = "Location Records"
        ordering = ["-recorded_at"]
        indexes = [
            models.Index(fields=["user", "-recorded_at"]),
            models.Index(fields=["user", "timestamp"]),
        ]

    def __str__(self):
        return f"{self.user} @ ({self.latitude}, {self.longitude})"


class GeoZone(models.Model):
    """Geofencing zones set by parents."""
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    name = models.CharField(max_length=100)
    family = models.ForeignKey(
        "users.Family",
        on_delete=models.CASCADE,
        related_name="geo_zones",
    )
    center_latitude = models.DecimalField(max_digits=10, decimal_places=7)
    center_longitude = models.DecimalField(max_digits=10, decimal_places=7)
    radius_meters = models.PositiveIntegerField(default=200)
    notify_on_enter = models.BooleanField(default=True)
    notify_on_exit = models.BooleanField(default=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["name"]

    def __str__(self):
        return f"{self.name} (r={self.radius_meters}m)"


class GeoZoneEvent(models.Model):
    class EventType(models.TextChoices):
        ENTER = "enter", "Entered Zone"
        EXIT = "exit", "Exited Zone"

    zone = models.ForeignKey(GeoZone, on_delete=models.CASCADE, related_name="events")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="zone_events")
    event_type = models.CharField(max_length=10, choices=EventType.choices)
    latitude = models.DecimalField(max_digits=10, decimal_places=7)
    longitude = models.DecimalField(max_digits=10, decimal_places=7)
    timestamp = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-timestamp"]
