from rest_framework import generics, status
from rest_framework.decorators import api_view
from rest_framework.response import Response
from drf_spectacular.utils import extend_schema
from .models import Notification
from rest_framework import serializers


class NotificationSerializer(serializers.ModelSerializer):
    class Meta:
        model = Notification
        fields = ("id", "notification_type", "title", "body", "data",
                  "is_read", "sent_at")
        read_only_fields = fields


class NotificationListView(generics.ListAPIView):
    """GET /api/v1/notifications/ — list user's notifications."""
    serializer_class = NotificationSerializer

    @extend_schema(tags=["notifications"])
    def get_queryset(self):
        return Notification.objects.filter(recipient=self.request.user)


@extend_schema(tags=["notifications"])
@api_view(["POST"])
def mark_all_read(request):
    """Mark all notifications as read."""
    Notification.objects.filter(recipient=request.user, is_read=False).update(is_read=True)
    return Response({"message": "All notifications marked as read."})


@extend_schema(tags=["notifications"])
@api_view(["POST"])
def mark_read(request, notification_id):
    """Mark a single notification as read."""
    Notification.objects.filter(id=notification_id, recipient=request.user).update(is_read=True)
    return Response({"message": "Marked as read."})
