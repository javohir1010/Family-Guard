import datetime
import math
from rest_framework import generics, status
from rest_framework.exceptions import ValidationError
from rest_framework.decorators import api_view
from rest_framework.response import Response
from django.contrib.auth import get_user_model
from django.utils import timezone
from django.utils.dateparse import parse_date, parse_datetime
from channels.layers import get_channel_layer
from asgiref.sync import async_to_sync
from drf_spectacular.utils import extend_schema, OpenApiParameter
from .models import LocationRecord, GeoZone, GeoZoneEvent
from apps.users.permissions import IsFamilyMember, IsParentOrReadOnly
from .serializers import (
    LocationCreateSerializer,
    LocationRecordSerializer,
    GeoZoneSerializer,
    GeoZoneEventSerializer,
)

User = get_user_model()


class LocationCreateView(generics.CreateAPIView):
    """
    POST /api/v1/location/
    Child device sends current GPS coordinates.
    Automatically broadcasts to WebSocket group.
    """
    serializer_class = LocationCreateSerializer

    @extend_schema(tags=["location"])
    def perform_create(self, serializer):
        record = serializer.save()
        user = self.request.user

        # Update user online status
        User.objects.filter(id=user.id).update(is_online=True, last_seen=timezone.now())

        # Broadcast to WebSocket family map group
        if user.family:
            channel_layer = get_channel_layer()
            async_to_sync(channel_layer.group_send)(
                f"family_map_{user.family.id}",
                {
                    "type": "location_update",
                    "data": {
                        "user_id": str(user.id),
                        "name": user.get_full_name() or user.email,
                        "role": user.role,
                        "latitude": float(record.latitude),
                        "longitude": float(record.longitude),
                        "accuracy": record.accuracy,
                        "battery_level": record.battery_level,
                        "recorded_at": record.recorded_at.isoformat(),
                        "is_online": True,
                    },
                },
            )

        # Check geofencing
        self._check_geofences(user, record)

    def _check_geofences(self, user, record):
        """Check if user entered/exited any active geofence zones."""
        if not user.family:
            return

        zones = GeoZone.objects.filter(family=user.family, is_active=True)
        for zone in zones:
            distance = self._haversine(
                float(record.latitude), float(record.longitude),
                float(zone.center_latitude), float(zone.center_longitude),
            )
            inside = distance <= zone.radius_meters

            # Get last event for this zone/user
            last_event = (
                GeoZoneEvent.objects.filter(zone=zone, user=user)
                .order_by("-timestamp")
                .first()
            )
            was_inside = bool(
                last_event and last_event.event_type == GeoZoneEvent.EventType.ENTER
            )
            if inside == was_inside:
                continue

            # Always record the transition so enter/exit state stays consistent,
            # even when notifications for this direction are turned off.
            event_type = (
                GeoZoneEvent.EventType.ENTER if inside else GeoZoneEvent.EventType.EXIT
            )
            GeoZoneEvent.objects.create(
                zone=zone, user=user,
                event_type=event_type,
                latitude=record.latitude, longitude=record.longitude,
            )
            if (inside and zone.notify_on_enter) or (not inside and zone.notify_on_exit):
                self._notify_geofence(user, zone, "enter" if inside else "exit")

    def _haversine(self, lat1, lon1, lat2, lon2):
        """Calculate distance between two coordinates in meters."""
        R = 6371000
        phi1, phi2 = math.radians(lat1), math.radians(lat2)
        dphi = math.radians(lat2 - lat1)
        dlambda = math.radians(lon2 - lon1)
        a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
        return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    def _notify_geofence(self, user, zone, event_type):
        from apps.notifications.tasks import send_push_to_family_parents
        action = "вошёл в зону" if event_type == "enter" else "вышел из зоны"
        send_push_to_family_parents.delay(
            family_id=str(user.family.id),
            exclude_user_id=str(user.id),
            title=f"Геозона: {zone.name}",
            body=f"{user.get_full_name() or user.email} {action} '{zone.name}'",
            data={"type": "geofence", "event": event_type, "zone_id": str(zone.id)},
        )


class LocationHistoryView(generics.ListAPIView):
    """GET /api/v1/location/history/<child_id>/ — location history for a child."""
    serializer_class = LocationRecordSerializer
    permission_classes = [IsFamilyMember]

    @extend_schema(
        tags=["location"],
        parameters=[
            OpenApiParameter("from_date", str, description="ISO datetime"),
            OpenApiParameter("to_date", str, description="ISO datetime"),
        ],
    )
    def get_queryset(self):
        child_id = self.kwargs["child_id"]
        user = self.request.user
        qs = LocationRecord.objects.filter(
            user__id=child_id, user__family=user.family
        ).order_by("-recorded_at")

        for param, lookup in (("from_date", "recorded_at__gte"), ("to_date", "recorded_at__lte")):
            value = self.request.query_params.get(param)
            if not value:
                continue
            try:
                parsed = parse_datetime(value)
                parsed_date = None if parsed else parse_date(value)
            except ValueError:
                parsed = parsed_date = None
            if parsed is None:
                if parsed_date is None:
                    raise ValidationError({param: "Expected ISO date or datetime."})
                parsed = datetime.datetime.combine(
                    parsed_date,
                    datetime.time.max if param == "to_date" else datetime.time.min,
                )
            if timezone.is_naive(parsed):
                parsed = timezone.make_aware(parsed)
            qs = qs.filter(**{lookup: parsed})
        return qs


class GeoZoneViewSet(generics.ListCreateAPIView):
    """List and create geofence zones (parents only can modify)."""
    serializer_class = GeoZoneSerializer
    permission_classes = [IsFamilyMember, IsParentOrReadOnly]

    @extend_schema(tags=["location"])
    def get_queryset(self):
        return GeoZone.objects.filter(family=self.request.user.family)


class GeoZoneDetailView(generics.RetrieveUpdateDestroyAPIView):
    serializer_class = GeoZoneSerializer
    permission_classes = [IsFamilyMember, IsParentOrReadOnly]

    @extend_schema(tags=["location"])
    def get_queryset(self):
        return GeoZone.objects.filter(family=self.request.user.family)


@extend_schema(tags=["location"])
@api_view(["GET"])
def current_positions(request):
    """Get latest position for all family members."""
    if not request.user.family:
        return Response([], status=status.HTTP_200_OK)

    members = User.objects.filter(family=request.user.family)
    result = []
    for member in members:
        last = (
            LocationRecord.objects.filter(user=member)
            .order_by("-recorded_at")
            .first()
        )
        if last:
            result.append(
                {
                    "user_id": str(member.id),
                    "name": member.get_full_name() or member.email,
                    "role": member.role,
                    "latitude": float(last.latitude),
                    "longitude": float(last.longitude),
                    "accuracy": last.accuracy,
                    "battery_level": last.battery_level,
                    "recorded_at": last.recorded_at.isoformat(),
                    "is_online": member.is_active_now,
                    "last_seen": member.last_seen.isoformat() if member.last_seen else None,
                    # An adult stopped sharing: the point is the last known place
                    "paused": member.location_paused,
                }
            )
    return Response(result)
