from django.urls import path
from . import family_views

urlpatterns = [
    path("create/", family_views.CreateFamilyView.as_view(), name="family-create"),
    path("detail/", family_views.FamilyDetailView.as_view(), name="family-detail"),
    path("members/", family_views.family_members, name="family-members"),
    path("members/<uuid:user_id>/remove/", family_views.remove_member, name="family-remove-member"),
    path("invite/generate/", family_views.generate_invite_code, name="family-invite-generate"),
    path("invite/join/", family_views.join_family, name="family-invite-join"),
    path("requests/", family_views.pending_requests, name="family-requests"),
    path("requests/logout/", family_views.request_logout, name="family-request-logout"),
    path("requests/<uuid:request_id>/", family_views.request_status, name="family-request-status"),
    path("requests/<uuid:request_id>/approve/", family_views.decide_request,
         {"decision": "approve"}, name="family-request-approve"),
    path("requests/<uuid:request_id>/reject/", family_views.decide_request,
         {"decision": "reject"}, name="family-request-reject"),
]
