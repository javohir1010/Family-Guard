from django.urls import path
from . import views

urlpatterns = [
    # Child → Server
    path("usage/batch/", views.upload_app_usage, name="app-usage-batch"),
    path("rules/device/", views.get_active_rules_for_device, name="app-rules-device"),
    path("schedule/device/", views.get_schedule_for_device, name="app-schedule-device"),
    path("permissions/", views.PermissionAlertCreateView.as_view(), name="app-permission-create"),
    # Parent dashboard
    path("usage/<uuid:child_id>/", views.AppUsageListView.as_view(), name="app-usage-list"),
    path("rules/", views.AppRuleListView.as_view(), name="app-rules-list"),
    path("rules/<uuid:child_id>/", views.AppRuleListView.as_view(), name="app-rules-child"),
    path("rules/<uuid:pk>/detail/", views.AppRuleDetailView.as_view(), name="app-rule-detail"),
    path("schedule/", views.ScreenScheduleListView.as_view(), name="app-schedule-list"),
    path("schedule/<uuid:child_id>/", views.ScreenScheduleListView.as_view(), name="app-schedule-child"),
    path("schedule/<uuid:pk>/detail/", views.ScreenScheduleDetailView.as_view(), name="app-schedule-detail"),
    path("permission-alerts/", views.PermissionAlertListView.as_view(), name="app-permission-alerts"),
]
