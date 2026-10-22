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
        GRANDPARENT = "grandparent", "Grandparent"  # sees the map, gets SOS, no settings
        CHILD = "child", "Child"

    class Gender(models.TextChoices):
        MALE = "male", "Male"
        FEMALE = "female", "Female"

    # Adults: the profile must be filled before the app goes on
    PROFILE_FIELDS = ("gender", "birth_date", "phone_number")

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    email = models.EmailField(unique=True)
    role = models.CharField(max_length=12, choices=Role.choices, default=Role.PARENT)
    family = models.ForeignKey(
        Family,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="members",
    )
    avatar = models.ImageField(upload_to="avatars/", null=True, blank=True)
    phone_number = models.CharField(max_length=20, blank=True)
    # Empty for accounts made before these became required
    gender = models.CharField(max_length=6, choices=Gender.choices, blank=True)
    birth_date = models.DateField(null=True, blank=True)
    # An adult may stop sharing their position for a while
    location_paused = models.BooleanField(default=False)
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

    @property
    def is_grandparent(self):
        return self.role == self.Role.GRANDPARENT

    @property
    def profile_complete(self):
        return all(getattr(self, f) for f in self.PROFILE_FIELDS)


class MemberRequest(models.Model):
    """A step that a parent of the family must approve: a new member joining
    (child or second parent) or a child device logging out."""

    class Kind(models.TextChoices):
        JOIN = "join", "Join family"
        LOGOUT = "logout", "Log out"

    class Status(models.TextChoices):
        PENDING = "pending", "Pending"
        APPROVED = "approved", "Approved"
        REJECTED = "rejected", "Rejected"
        USED = "used", "Used"

    # A request nobody answered is dropped, so an old one can't be approved by mistake
    TTL = timedelta(minutes=30)

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    family = models.ForeignKey(Family, on_delete=models.CASCADE, related_name="member_requests")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="member_requests")
    kind = models.CharField(max_length=10, choices=Kind.choices)
    role = models.CharField(max_length=12, choices=User.Role.choices, default=User.Role.CHILD)
    device_name = models.CharField(max_length=100, blank=True)
    status = models.CharField(max_length=10, choices=Status.choices, default=Status.PENDING)
    decided_by = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True, related_name="+"
    )
    created_at = models.DateTimeField(auto_now_add=True)
    decided_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.kind} {self.user} → {self.family} ({self.status})"

    @property
    def is_expired(self):
        return (
            self.status == self.Status.PENDING
            and timezone.now() - self.created_at > self.TTL
        )

    @property
    def effective_status(self):
        return "expired" if self.is_expired else self.status


class LoginChallenge(models.Model):
    """Second login step for parents: a one-time code sent by email."""

    MAX_ATTEMPTS = 5

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="login_challenges")
    code_hash = models.CharField(max_length=128)
    attempts = models.PositiveSmallIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField()


class ContactLabel(models.Model):
    """How one member renamed another in their own contact list
    (e.g. "Мама 1" → "Мама Дилноза"). Seen only by the owner."""

    owner = models.ForeignKey(User, on_delete=models.CASCADE, related_name="contact_labels")
    target = models.ForeignKey(User, on_delete=models.CASCADE, related_name="+")
    label = models.CharField(max_length=40)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["owner", "target"], name="unique_contact_label"),
        ]

    def __str__(self):
        return f"{self.owner} → {self.target}: {self.label}"
