from rest_framework import generics, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from django.contrib.auth import get_user_model
from django.utils import timezone
from drf_spectacular.utils import extend_schema, OpenApiParameter
from .models import AppUsage, AppRule, ScreenSchedule, PermissionAlert
from .serializers import (
    AppUsageSerializer,
    AppUsageBatchSerializer,
    AppRuleSerializer,
    ScreenScheduleSerializer,
    PermissionAlertSerializer,
    PermissionAlertCreateSerializer,
)
from apps.users.permissions import IsParent, IsChild, IsFamilyMember
import datetime

User = get_user_model()


@extend_schema(tags=["apps"], request=AppUsageBatchSerializer)
@api_view(["POST"])
def upload_app_usage(request):
    """POST /api/v1/apps/usage/batch/ — batch upload app usage from Android."""
    serializer = AppUsageBatchSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    child = request.user
    created = updated = 0

    for u in serializer.validated_data["usages"]:
        date = u["date"]
        if isinstance(date, str):
            try:
                date = datetime.date.fromisoformat(date)
            except ValueError:
                date = timezone.now().date()

        obj, is_created = AppUsage.objects.update_or_create(
            child=child,
            package_name=u["package_name"],
            date=date,
            defaults={
                "app_name": u["app_name"],
                "duration_seconds": u["duration_seconds"],
                "last_used": u.get("last_used"),
            },
        )
        if is_created:
            created += 1
        else:
            updated += 1

    return Response(
        {"created": created, "updated": updated}, status=status.HTTP_200_OK
    )


class AppUsageListView(generics.ListAPIView):
    """GET /api/v1/apps/usage/<child_id>/ — app usage for parent dashboard."""
    serializer_class = AppUsageSerializer
    permission_classes = [IsParent]

    @extend_schema(
        tags=["apps"],
        parameters=[
            OpenApiParameter("date", str, description="YYYY-MM-DD"),
            OpenApiParameter("package_name", str),
        ],
    )
    def get_queryset(self):
        child_id = self.kwargs["child_id"]
        qs = AppUsage.objects.filter(
            child__id=child_id,
            child__family=self.request.user.family,
        )
        date_str = self.request.query_params.get("date")
        if date_str:
            qs = qs.filter(date=date_str)
        pkg = self.request.query_params.get("package_name")
        if pkg:
            qs = qs.filter(package_name=pkg)
        return qs.order_by("-duration_seconds")


class AppRuleListView(generics.ListCreateAPIView):
    """List app rules / create a new rule. Parents only."""
    serializer_class = AppRuleSerializer
    permission_classes = [IsParent]

    @extend_schema(tags=["apps"])
    def get_queryset(self):
        child_id = self.kwargs.get("child_id")
        qs = AppRule.objects.filter(child__family=self.request.user.family)
        if child_id:
            qs = qs.filter(child__id=child_id)
        return qs


class AppRuleDetailView(generics.RetrieveUpdateDestroyAPIView):
    """Manage a single rule. Parents only."""
    serializer_class = AppRuleSerializer
    permission_classes = [IsParent]

    @extend_schema(tags=["apps"])
    def get_queryset(self):
        return AppRule.objects.filter(child__family=self.request.user.family)


@extend_schema(tags=["apps"])
@api_view(["GET"])
def get_active_rules_for_device(request):
    """
    GET /api/v1/apps/rules/device/
    Child device fetches its own active rules.
    """
    rules = AppRule.objects.filter(child=request.user, is_active=True)
    return Response(AppRuleSerializer(rules, many=True).data)


class ScreenScheduleListView(generics.ListCreateAPIView):
    """List/create screen schedules. Parents only."""
    serializer_class = ScreenScheduleSerializer
    permission_classes = [IsParent]

    @extend_schema(tags=["apps"])
    def get_queryset(self):
        child_id = self.kwargs.get("child_id")
        qs = ScreenSchedule.objects.filter(child__family=self.request.user.family)
        if child_id:
            qs = qs.filter(child__id=child_id)
        return qs


class ScreenScheduleDetailView(generics.RetrieveUpdateDestroyAPIView):
    """Manage schedule. Parents only."""
    serializer_class = ScreenScheduleSerializer
    permission_classes = [IsParent]

    @extend_schema(tags=["apps"])
    def get_queryset(self):
        return ScreenSchedule.objects.filter(child__family=self.request.user.family)


@extend_schema(tags=["apps"])
@api_view(["GET"])
def get_schedule_for_device(request):
    """Child device fetches its own active screen schedules."""
    schedules = ScreenSchedule.objects.filter(child=request.user, is_active=True)
    return Response(ScreenScheduleSerializer(schedules, many=True).data)


class PermissionAlertListView(generics.ListAPIView):
    """List permission alerts for parent."""
    serializer_class = PermissionAlertSerializer
    permission_classes = [IsParent]

    @extend_schema(tags=["apps"])
    def get_queryset(self):
        return PermissionAlert.objects.filter(
            child__family=self.request.user.family
        ).select_related("child")


class PermissionAlertCreateView(generics.CreateAPIView):
    """Child device reports permission change."""
    serializer_class = PermissionAlertCreateSerializer

    @extend_schema(tags=["apps"])
    def perform_create(self, serializer):
        alert = serializer.save()
        # Notify parents
        if not alert.was_granted and alert.child.family:
            from apps.notifications.tasks import send_push_to_family_parents
            send_push_to_family_parents.delay(
                family_id=str(alert.child.family.id),
                exclude_user_id=str(alert.child.id),
                title="⚠️ Разрешение отозвано",
                body=(
                    f"{alert.child.get_full_name() or alert.child.email} отозвал разрешение: "
                    f"{alert.get_permission_display()}"
                ),
                data={"type": "permission_revoked", "permission": alert.permission},
            )
