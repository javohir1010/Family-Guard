from django.urls import path
from . import views

urlpatterns = [
    path("trigger/", views.SosTriggerView.as_view(), name="sos-trigger"),
    path("history/", views.SosHistoryView.as_view(), name="sos-history"),
    path("<uuid:sos_id>/resolve/", views.SosResolveView.as_view(), name="sos-resolve"),
]
