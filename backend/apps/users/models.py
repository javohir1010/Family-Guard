from django.contrib.auth.models import AbstractUser
from django.db import models
import uuid
from datetime import timedelta
from django.utils import timezone

# Child devices report location every ~10 min; allow two missed reports
ONLINE_WINDOW = timedelta(minutes=25)


class Family(models.Model):
    name = models.CharField(max_length=100)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "Family"
        verbose_name_plural = "Families"

    def __str__(self):
        return self.name


class User(AbstractUser):
    class Role(models.TextChoices):
        PARENT = "parent", "Parent"
        CHILD = "child", "Child"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    email = models.EmailField(unique=True)
    role = models.CharField(max_length=10, choices=Role.choices, default=Role.PARENT)
    family = models.ForeignKey(
        Family,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="members",
    )
    avatar = models.ImageField(upload_to="avatars/", null=True, blank=True)
    phone_number = models.CharField(max_length=20, blank=True)
    device_token = models.TextField(blank=True, help_text="FCM device token")
    device_name = models.CharField(max_length=100, blank=True)
    is_online = models.BooleanField(default=False)
    last_seen = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = ["username"]

    class Meta:
        verbose_name = "User"
        verbose_name_plural = "Users"

    def __str__(self):
        return f"{self.get_full_name() or self.email} ({self.role})"

    @property
    def is_active_now(self):
        """Online = reported recently (the is_online flag alone never resets)."""
        return bool(
            self.is_online
            and self.last_seen
            and timezone.now() - self.last_seen <= ONLINE_WINDOW
        )

    @property
    def is_parent(self):
        return self.role == self.Role.PARENT

    @property
    def is_child(self):
        return self.role == self.Role.CHILD
