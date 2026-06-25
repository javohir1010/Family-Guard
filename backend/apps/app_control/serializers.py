from rest_framework import serializers
from .models import AppUsage, AppRule, ScreenSchedule, PermissionAlert


class AppUsageSerializer(serializers.ModelSerializer):
    duration_minutes = serializers.ReadOnlyField()

    class Meta:
        model = AppUsage
        fields = ("id", "child", "package_name", "app_name", "duration_seconds",
                  "duration_minutes", "date", "last_used")
        read_only_fields = fields


class AppUsageBatchSerializer(serializers.Serializer):
    """Batch upload from Android UsageStatsManager."""
    usages = serializers.ListField(child=serializers.DictField(), max_length=200)

    def validate_usages(self, usages):
        validated = []
        for u in usages:
            if not u.get("package_name") or not u.get("date"):
                continue
            validated.append({
                "package_name": str(u["package_name"])[:255],
                "app_name": str(u.get("app_name", u["package_name"]))[:255],
                "duration_seconds": max(0, int(u.get("duration_seconds", 0))),
                "date": u["date"],
                "last_used": u.get("last_used"),
            })
        return validated


class AppRuleSerializer(serializers.ModelSerializer):
    class Meta:
        model = AppRule
        fields = ("id", "child", "package_name", "app_name", "rule_type",
                  "daily_limit_minutes", "is_active", "created_at")
        read_only_fields = ("id", "created_at")

    def create(self, validated_data):
        validated_data["created_by"] = self.context["request"].user
        return super().create(validated_data)

    def validate(self, attrs):
        if attrs.get("rule_type") == AppRule.RuleType.LIMIT:
            if not attrs.get("daily_limit_minutes"):
                raise serializers.ValidationError(
                    {"daily_limit_minutes": "Required for LIMIT rule type."}
                )
        return attrs


class ScreenScheduleSerializer(serializers.ModelSerializer):
    days_list = serializers.SerializerMethodField()

    class Meta:
        model = ScreenSchedule
        fields = ("id", "child", "name", "days_bitmask", "days_list",
                  "start_time", "end_time", "is_active", "created_at")
        read_only_fields = ("id", "created_at", "days_list")

    def get_days_list(self, obj):
        days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        return [days[i] for i in range(7) if obj.days_bitmask & (1 << i)]


class PermissionAlertSerializer(serializers.ModelSerializer):
    child_name = serializers.CharField(source="child.get_full_name", read_only=True)
    permission_display = serializers.CharField(source="get_permission_display", read_only=True)

    class Meta:
        model = PermissionAlert
        fields = ("id", "child", "child_name", "permission", "permission_display",
                  "was_granted", "timestamp")
        read_only_fields = fields


class PermissionAlertCreateSerializer(serializers.ModelSerializer):
    class Meta:
        model = PermissionAlert
        fields = ("permission", "was_granted")

    def create(self, validated_data):
        validated_data["child"] = self.context["request"].user
        return super().create(validated_data)
