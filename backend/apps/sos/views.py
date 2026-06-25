from rest_framework import generics, status
from rest_framework.decorators import api_view
from rest_framework.response import Response
from django.utils import timezone
from drf_spectacular.utils import extend_schema
from .models import SosEvent
from .serializers import SosTriggerSerializer, SosEventSerializer, SosResolveSerializer
from .tasks import process_sos_event


class SosTriggerView(generics.GenericAPIView):
    """
    POST /api/v1/sos/trigger/
    Called by the child's Android app when SOS button is pressed.
    """
    serializer_class = SosTriggerSerializer

    @extend_schema(tags=["sos"])
    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        press_count = data["press_count"]
        sos_type = (
            SosEvent.SosType.EMERGENCY if press_count >= 8 else SosEvent.SosType.ALERT
        )

        event = SosEvent.objects.create(
            child=request.user,
            sos_type=sos_type,
            press_count=press_count,
            latitude=data.get("latitude"),
            longitude=data.get("longitude"),
            message=data.get("message", ""),
        )

        # Process asynchronously via Celery
        process_sos_event.apply_async(
            args=[str(event.id)],
            queue="sos",
            priority=10 if sos_type == SosEvent.SosType.EMERGENCY else 5,
        )

        return Response(
            {
                "message": "SOS signal sent.",
                "sos_id": str(event.id),
                "sos_type": sos_type,
            },
            status=status.HTTP_201_CREATED,
        )


class SosHistoryView(generics.ListAPIView):
    """GET /api/v1/sos/history/ — SOS history for the family."""
    serializer_class = SosEventSerializer

    @extend_schema(tags=["sos"])
    def get_queryset(self):
        user = self.request.user
        if not user.family:
            return SosEvent.objects.none()
        return SosEvent.objects.filter(
            child__family=user.family
        ).select_related("child", "resolved_by").order_by("-created_at")


class SosResolveView(generics.GenericAPIView):
    """POST /api/v1/sos/<id>/resolve/ — parent marks SOS as resolved."""
    serializer_class = SosResolveSerializer

    @extend_schema(tags=["sos"])
    def post(self, request, sos_id):
        if not request.user.is_parent:
            return Response(status=status.HTTP_403_FORBIDDEN)
        try:
            event = SosEvent.objects.get(
                id=sos_id, child__family=request.user.family
            )
        except SosEvent.DoesNotExist:
            return Response({"error": "SOS event not found."}, status=status.HTTP_404_NOT_FOUND)

        event.status = SosEvent.SosStatus.RESOLVED
        event.resolved_by = request.user
        event.resolved_at = timezone.now()
        event.save(update_fields=["status", "resolved_by", "resolved_at"])
        return Response(SosEventSerializer(event).data)
