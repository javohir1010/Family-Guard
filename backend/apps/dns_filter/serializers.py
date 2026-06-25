from rest_framework import serializers
from .models import BlockedDomain, DnsQuery, DnsCategory


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
        return value.lower().strip().lstrip("www.").strip("/")


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
                    "domain": str(q["domain"]).lower().strip(),
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
