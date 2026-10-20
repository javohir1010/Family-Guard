import datetime
from rest_framework import serializers
from django.utils.dateparse import parse_datetime
from .models import AppUsage, AppRule, ScreenSchedule, PermissionAlert


def validate_family_child(serializer, child):
    """Parents may only target children of their own family."""
    user = serializer.context["request"].user
    if child.role != "child" or not user.family_id or child.family_id != user.family_id:
        raise serializers.ValidationError("Child not found in your family.")
    return child


class AppUsageSerializer(serializers.ModelSerializer):
    duration_minutes = serializers.FloatField(read_only=True)

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
            try:
                date = datetime.date.fromisoformat(str(u["date"]))
                duration = max(0, int(u.get("duration_seconds") or 0))
            except (TypeError, ValueError):
                continue  # skip malformed entries instead of failing the batch
            last_used = None
            if u.get("last_used"):
                try:
                    last_used = parse_datetime(str(u["last_used"]))
                except ValueError:
                    pass
            validated.append({
                "package_name": str(u["package_name"])[:255],
                "app_name": str(u.get("app_name") or u["package_name"])[:255],
                "duration_seconds": duration,
                "date": date,
                "last_used": last_used,
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

    def validate_child(self, child):
        return validate_family_child(self, child)

    def validate(self, attrs):
        # Fall back to the stored values on PATCH so partial updates are checked too
        rule_type = attrs.get("rule_type", getattr(self.instance, "rule_type", None))
        limit = attrs.get(
            "daily_limit_minutes", getattr(self.instance, "daily_limit_minutes", None)
        )
        if rule_type == AppRule.RuleType.LIMIT and not limit:
            raise serializers.ValidationError(
                {"daily_limit_minutes": "Required for LIMIT rule type."}
            )
        if rule_type == AppRule.RuleType.BLOCK:
            attrs["daily_limit_minutes"] = None
        return attrs


class ScreenScheduleSerializer(serializers.ModelSerializer):
    days_list = serializers.SerializerMethodField()

    class Meta:
        model = ScreenSchedule
        fields = ("id", "child", "name", "days_bitmask", "days_list",
                  "start_time", "end_time", "is_active", "created_at")
        read_only_fields = ("id", "created_at", "days_list")

    def validate_child(self, child):
        return validate_family_child(self, child)

    def validate_days_bitmask(self, value):
        if not 1 <= value <= 127:
            raise serializers.ValidationError("Must be between 1 and 127 (Mon=1 … Sun=64).")
        return value

    def validate(self, attrs):
        start = attrs.get("start_time", getattr(self.instance, "start_time", None))
        end = attrs.get("end_time", getattr(self.instance, "end_time", None))
        if start is not None and start == end:
            raise serializers.ValidationError({"end_time": "Must differ from start_time."})
        return attrs

    def get_days_list(self, obj) -> list[str]:
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
