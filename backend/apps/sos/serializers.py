from rest_framework import serializers
from .models import SosEvent


class SosTriggerSerializer(serializers.Serializer):
    press_count = serializers.IntegerField(min_value=1)
    latitude = serializers.DecimalField(
        max_digits=10, decimal_places=7, required=False, allow_null=True
    )
    longitude = serializers.DecimalField(
        max_digits=10, decimal_places=7, required=False, allow_null=True
    )
    message = serializers.CharField(required=False, allow_blank=True, default="")


class SosEventSerializer(serializers.ModelSerializer):
    child_name = serializers.CharField(source="child.get_full_name", read_only=True)
    child_device = serializers.CharField(source="child.device_name", read_only=True)
    resolved_by_name = serializers.CharField(
        source="resolved_by.get_full_name", read_only=True
    )

    class Meta:
        model = SosEvent
        fields = (
            "id", "child", "child_name", "child_device", "sos_type",
            "status", "press_count", "latitude", "longitude", "message",
            "resolved_by", "resolved_by_name", "resolved_at", "created_at",
        )
        read_only_fields = fields


class SosResolveSerializer(serializers.Serializer):
    message = serializers.CharField(required=False, allow_blank=True, default="")
