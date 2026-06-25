from django.urls import path
from . import views

urlpatterns = [
    path("", views.LocationCreateView.as_view(), name="location-create"),
    path("current/", views.current_positions, name="location-current"),
    path("history/<uuid:child_id>/", views.LocationHistoryView.as_view(), name="location-history"),
    path("zones/", views.GeoZoneViewSet.as_view(), name="geozone-list"),
    path("zones/<uuid:pk>/", views.GeoZoneDetailView.as_view(), name="geozone-detail"),
]
