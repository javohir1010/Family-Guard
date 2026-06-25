from rest_framework import generics, status, permissions
from rest_framework.decorators import api_view
from rest_framework.response import Response
from django.contrib.auth import get_user_model
from django.utils import timezone
from django.conf import settings
from django.core.cache import cache
from drf_spectacular.utils import extend_schema
from .models import Family
from .invite_model import InviteCode
from .serializers import (
    FamilySerializer,
    InviteCodeSerializer,
    JoinFamilySerializer,
    UserPublicSerializer,
)

User = get_user_model()


class CreateFamilyView(generics.CreateAPIView):
    """Create a new family group (parent only)."""
    serializer_class = FamilySerializer

    @extend_schema(tags=["family"])
    def post(self, request, *args, **kwargs):
        if request.user.family:
            return Response(
                {"error": "You already belong to a family."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        family = serializer.save()
        request.user.family = family
        request.user.role = User.Role.PARENT
        request.user.save(update_fields=["family", "role"])
        return Response(
            {"message": "Family created.", "family": FamilySerializer(family).data},
            status=status.HTTP_201_CREATED,
        )


class FamilyDetailView(generics.RetrieveUpdateAPIView):
    """Get current family info."""
    serializer_class = FamilySerializer

    @extend_schema(tags=["family"])
    def get_object(self):
        if not self.request.user.family:
            from rest_framework.exceptions import NotFound
            raise NotFound("You are not a member of any family.")
        return self.request.user.family


@extend_schema(tags=["family"], responses=InviteCodeSerializer)
@api_view(["POST"])
def generate_invite_code(request):
    """Generate a 6-digit invite code for joining the family."""
    if not request.user.family:
        return Response(
            {"error": "Create a family first."}, status=status.HTTP_400_BAD_REQUEST
        )
    if not request.user.is_parent:
        return Response(
            {"error": "Only parents can generate invite codes."},
            status=status.HTTP_403_FORBIDDEN,
        )

    code = InviteCode.generate_code()
    # Ensure uniqueness
    while InviteCode.objects.filter(code=code, is_used=False).exists():
        code = InviteCode.generate_code()

    ttl = settings.INVITE_CODE_TTL
    invite = InviteCode.objects.create(
        code=code,
        family=request.user.family,
        created_by=request.user,
        expires_at=timezone.now() + timezone.timedelta(seconds=ttl),
    )
    # Cache for fast lookup
    cache.set(f"invite:{code}", invite.family_id, timeout=ttl)
    return Response(InviteCodeSerializer(invite).data, status=status.HTTP_201_CREATED)


@extend_schema(tags=["family"], request=JoinFamilySerializer)
@api_view(["POST"])
def join_family(request):
    """Join a family using an invite code."""
    if request.user.family:
        return Response(
            {"error": "You already belong to a family."},
            status=status.HTTP_400_BAD_REQUEST,
        )
    serializer = JoinFamilySerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    code = serializer.validated_data["code"]

    try:
        invite = InviteCode.objects.select_related("family").get(
            code=code,
            is_used=False,
            expires_at__gt=timezone.now(),
        )
    except InviteCode.DoesNotExist:
        return Response(
            {"error": "Invalid or expired invite code."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    request.user.family = invite.family
    request.user.role = User.Role.CHILD
    if serializer.validated_data.get("device_name"):
        request.user.device_name = serializer.validated_data["device_name"]
    request.user.save(update_fields=["family", "role", "device_name"])

    invite.is_used = True
    invite.used_by = request.user
    invite.save(update_fields=["is_used", "used_by"])
    cache.delete(f"invite:{code}")

    return Response(
        {
            "message": f"Joined family '{invite.family.name}' successfully.",
            "family": FamilySerializer(invite.family).data,
        }
    )


@extend_schema(tags=["family"])
@api_view(["GET"])
def family_members(request):
    """List all members of the current family."""
    if not request.user.family:
        return Response(
            {"error": "You are not a member of any family."},
            status=status.HTTP_404_NOT_FOUND,
        )
    members = User.objects.filter(family=request.user.family).order_by("role", "first_name")
    return Response(UserPublicSerializer(members, many=True).data)


@extend_schema(tags=["family"])
@api_view(["DELETE"])
def remove_member(request, user_id):
    """Remove a child from the family (parent only)."""
    if not request.user.is_parent:
        return Response(status=status.HTTP_403_FORBIDDEN)
    try:
        member = User.objects.get(id=user_id, family=request.user.family, role=User.Role.CHILD)
    except User.DoesNotExist:
        return Response({"error": "Member not found."}, status=status.HTTP_404_NOT_FOUND)
    member.family = None
    member.save(update_fields=["family"])
    return Response({"message": "Member removed."})
