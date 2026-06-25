from django.urls import path
from . import family_views

urlpatterns = [
    path("create/", family_views.CreateFamilyView.as_view(), name="family-create"),
    path("detail/", family_views.FamilyDetailView.as_view(), name="family-detail"),
    path("members/", family_views.family_members, name="family-members"),
    path("members/<uuid:user_id>/remove/", family_views.remove_member, name="family-remove-member"),
    path("invite/generate/", family_views.generate_invite_code, name="family-invite-generate"),
    path("invite/join/", family_views.join_family, name="family-invite-join"),
]
