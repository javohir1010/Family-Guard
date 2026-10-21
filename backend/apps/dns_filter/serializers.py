import re
from urllib.parse import urlsplit
from rest_framework import serializers
from .models import BlockedDomain, DnsQuery, DnsCategory

DOMAIN_RE = re.compile(r"^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9-]{2,63}$")


def normalize_domain(value):
    """'https://WWW.Example.com/path' -> 'example.com'."""
    value = str(value).strip().lower()
    if "://" in value:
        value = urlsplit(value).hostname or ""
    value = value.split("/")[0].split(":")[0].rstrip(".")
    if value.startswith("www."):
        value = value[len("www."):]
    return value


class BlockedDomainSerializer(serializers.ModelSerializer):
    added_by_name = serializers.CharField(source="added_by.get_full_name", read_only=True)

    class Meta:
        model = BlockedDomain
        fields = ("id", "domain", "reason", "added_by_name", "created_at")
        read_only_fields = ("id", "added_by_name", "created_at")

    def create(self, validated_data):
        validated_data["family"] = self.context["request"].user.family
        validated_data["added_by"] = self.context["request"].user
        return super().create(validated_data)

    def validate_domain(self, value):
        domain = normalize_domain(value)
        if not DOMAIN_RE.match(domain):
            raise serializers.ValidationError("Enter a valid domain, e.g. example.com.")
        family = self.context["request"].user.family
        qs = BlockedDomain.objects.filter(family=family, domain=domain)
        if self.instance:
            qs = qs.exclude(pk=self.instance.pk)
        if qs.exists():
            raise serializers.ValidationError("This domain is already blocked.")
        return domain


class DnsQuerySerializer(serializers.ModelSerializer):
    child_name = serializers.CharField(source="child.get_full_name", read_only=True)

    class Meta:
        model = DnsQuery
        fields = ("id", "child", "child_name", "domain", "query_type",
                  "was_blocked", "timestamp")
        read_only_fields = fields


class DnsQueryBatchSerializer(serializers.Serializer):
    """Batch DNS query upload from Android device."""
    queries = serializers.ListField(
        child=serializers.DictField(),
        max_length=500,
    )

    def validate_queries(self, queries):
        validated = []
        for q in queries:
            if not q.get("domain"):
                continue
            validated.append(
                {
                    "domain": str(q["domain"]).lower().strip().rstrip(".")[:255],
                    "query_type": str(q.get("query_type", "A"))[:10],
                    "was_blocked": bool(q.get("was_blocked", False)),
                    "timestamp": q.get("timestamp"),
                }
            )
        return validated


class DnsCategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = DnsCategory
        fields = ("id", "name", "description", "is_enabled")
        read_only_fields = ("id",)
