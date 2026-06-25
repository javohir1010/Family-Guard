from django.db import models
from django.contrib.auth import get_user_model
import random
import string

User = get_user_model()


class InviteCode(models.Model):
    code = models.CharField(max_length=6, unique=True)
    family = models.ForeignKey(
        "users.Family",
        on_delete=models.CASCADE,
        related_name="invite_codes",
    )
    created_by = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name="created_invites",
    )
    is_used = models.BooleanField(default=False)
    used_by = models.ForeignKey(
        User,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="used_invites",
    )
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField()

    class Meta:
        verbose_name = "Invite Code"
        verbose_name_plural = "Invite Codes"
        ordering = ["-created_at"]

    def __str__(self):
        return f"Code {self.code} for family {self.family.name}"

    @staticmethod
    def generate_code():
        return "".join(random.choices(string.digits, k=6))
