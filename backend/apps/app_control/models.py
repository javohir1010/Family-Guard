from django.db import models
from django.contrib.auth import get_user_model
import uuid

User = get_user_model()


class AppUsage(models.Model):
    """App usage statistics collected from child device."""
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(
        User, on_delete=models.CASCADE, related_name="app_usages"
    )
    package_name = models.CharField(max_length=255)
    app_name = models.CharField(max_length=255)
    duration_seconds = models.PositiveIntegerField(default=0)
    date = models.DateField(db_index=True)
    last_used = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ("child", "package_name", "date")
        ordering = ["-date", "-duration_seconds"]
        indexes = [
            models.Index(fields=["child", "-date"]),
            models.Index(fields=["package_name"]),
        ]

    def __str__(self):
        return f"{self.app_name} — {self.duration_seconds}s ({self.date})"

    @property
    def duration_minutes(self):
        return round(self.duration_seconds / 60, 1)


class AppRule(models.Model):
    """Rules set by parent for an app on a child's device."""
    class RuleType(models.TextChoices):
        BLOCK = "block", "Fully Blocked"
        LIMIT = "limit", "Daily Time Limit"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(
        User, on_delete=models.CASCADE, related_name="app_rules"
    )
    package_name = models.CharField(max_length=255)
    app_name = models.CharField(max_length=255, blank=True)
    rule_type = models.CharField(max_length=10, choices=RuleType.choices)
    daily_limit_minutes = models.PositiveIntegerField(
        null=True, blank=True, help_text="Only for LIMIT type"
    )
    is_active = models.BooleanField(default=True)
    created_by = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, related_name="created_rules"
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        unique_together = ("child", "package_name")
        ordering = ["app_name"]

    def __str__(self):
        return f"{self.app_name or self.package_name} → {self.rule_type}"


class ScreenSchedule(models.Model):
    """Time-based device lockdown schedule."""
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(
        User, on_delete=models.CASCADE, related_name="screen_schedules"
    )
    name = models.CharField(max_length=100, default="Расписание")
    # Bitmask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64
    days_bitmask = models.PositiveIntegerField(default=127, help_text="Bitmask 1=Mon...64=Sun")
    start_time = models.TimeField()
    end_time = models.TimeField()
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["start_time"]

    def __str__(self):
        return f"{self.name}: {self.start_time}–{self.end_time}"


class PermissionAlert(models.Model):
    """Alert when child revokes a critical permission."""
    class PermissionType(models.TextChoices):
        LOCATION = "ACCESS_FINE_LOCATION", "GPS Location"
        CAMERA = "CAMERA", "Camera"
        CONTACTS = "READ_CONTACTS", "Contacts"
        PHONE = "READ_PHONE_STATE", "Phone State"
        ACCESSIBILITY = "BIND_ACCESSIBILITY_SERVICE", "Accessibility Service"
        VPN = "BIND_VPN_SERVICE", "VPN Service"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(
        User, on_delete=models.CASCADE, related_name="permission_alerts"
    )
    permission = models.CharField(max_length=50, choices=PermissionType.choices)
    was_granted = models.BooleanField()
    timestamp = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-timestamp"]

    def __str__(self):
        status = "granted" if self.was_granted else "revoked"
        return f"{self.permission} {status} by {self.child}"
