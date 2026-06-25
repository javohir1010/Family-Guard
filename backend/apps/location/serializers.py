from rest_framework import serializers
from .models import LocationRecord, GeoZone, GeoZoneEvent


class LocationRecordSerializer(serializers.ModelSerializer):
    user_name = serializers.CharField(source="user.get_full_name", read_only=True)

    class Meta:
        model = LocationRecord
        fields = (
            "id", "user", "user_name", "latitude", "longitude",
            "accuracy", "altitude", "speed", "battery_level",
            "timestamp", "recorded_at",
        )
        read_only_fields = ("id", "user", "user_name", "timestamp")


class LocationCreateSerializer(serializers.ModelSerializer):
    class Meta:
        model = LocationRecord
        fields = ("latitude", "longitude", "accuracy", "altitude",
                  "speed", "battery_level", "recorded_at")

    def create(self, validated_data):
        validated_data["user"] = self.context["request"].user
        return super().create(validated_data)


class GeoZoneSerializer(serializers.ModelSerializer):
    class Meta:
        model = GeoZone
        fields = (
            "id", "name", "center_latitude", "center_longitude",
            "radius_meters", "notify_on_enter", "notify_on_exit",
            "is_active", "created_at",
        )
        read_only_fields = ("id", "created_at")

    def create(self, validated_data):
        validated_data["family"] = self.context["request"].user.family
        return super().create(validated_data)


class GeoZoneEventSerializer(serializers.ModelSerializer):
    zone_name = serializers.CharField(source="zone.name", read_only=True)
    user_name = serializers.CharField(source="user.get_full_name", read_only=True)

    class Meta:
        model = GeoZoneEvent
        fields = ("id", "zone", "zone_name", "user", "user_name",
                  "event_type", "latitude", "longitude", "timestamp")
        read_only_fields = fields
