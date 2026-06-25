from django.db import models
from django.contrib.auth import get_user_model
import uuid

User = get_user_model()


class DnsCategory(models.Model):
    """Predefined categories for bulk domain blocking."""
    name = models.CharField(max_length=50, unique=True)
    description = models.TextField(blank=True)
    is_enabled = models.BooleanField(default=False)
    family = models.ForeignKey(
        "users.Family",
        on_delete=models.CASCADE,
        related_name="dns_categories",
    )

    class Meta:
        verbose_name_plural = "DNS Categories"

    def __str__(self):
        return self.name


class BlockedDomain(models.Model):
    """Manually blocked domains by parent."""
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    domain = models.CharField(max_length=255)
    family = models.ForeignKey(
        "users.Family",
        on_delete=models.CASCADE,
        related_name="blocked_domains",
    )
    added_by = models.ForeignKey(
        User,
        on_delete=models.SET_NULL,
        null=True,
        related_name="added_blocks",
    )
    reason = models.CharField(max_length=200, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ("domain", "family")
        ordering = ["-created_at"]
        indexes = [models.Index(fields=["family", "domain"])]

    def __str__(self):
        return f"{self.domain} (blocked)"


class DnsQuery(models.Model):
    """Log of DNS queries made by child devices."""
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name="dns_queries",
    )
    domain = models.CharField(max_length=255, db_index=True)
    query_type = models.CharField(max_length=10, default="A")
    was_blocked = models.BooleanField(default=False, db_index=True)
    timestamp = models.DateTimeField(db_index=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "DNS Query"
        verbose_name_plural = "DNS Queries"
        ordering = ["-timestamp"]
        indexes = [
            models.Index(fields=["child", "-timestamp"]),
            models.Index(fields=["child", "was_blocked"]),
        ]

    def __str__(self):
        status = "BLOCKED" if self.was_blocked else "allowed"
        return f"{self.domain} [{status}] by {self.child}"
