from rest_framework import generics, status, permissions
from rest_framework.decorators import api_view, permission_classes, throttle_classes
from rest_framework.throttling import SimpleRateThrottle, UserRateThrottle
from rest_framework.response import Response
from django.contrib.auth import get_user_model
from django.utils import timezone
from django.conf import settings
from django.core.cache import cache
from drf_spectacular.utils import extend_schema
from .labels import contact_labels
from .models import ContactLabel, Family, MemberRequest
from .permissions import IsFamilyMember, IsParent, IsParentOrReadOnly
from .invite_model import InviteCode
from .serializers import (
    FamilySerializer,
    ContactLabelSerializer,
    GenerateInviteSerializer,
    MemberEditSerializer,
    InviteCodeSerializer,
    MemberRequestSerializer,
    JoinFamilySerializer,
    UserPublicSerializer,
)

User = get_user_model()


# A 6-digit code is guessable by brute force, so joining is rate limited
# per account and per IP (several accounts from one address).
class JoinFamilyUserThrottle(UserRateThrottle):
    scope = "join_family_user"
    rate = "5/minute"


class JoinFamilyIpThrottle(SimpleRateThrottle):
    scope = "join_family_ip"
    rate = "30/hour"

    def get_cache_key(self, request, view):
        return self.cache_format % {"scope": self.scope, "ident": self.get_ident(request)}


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
    """Get current family info. Only parents can rename it."""
    serializer_class = FamilySerializer
    permission_classes = [IsParentOrReadOnly]

    @extend_schema(tags=["family"])
    def get_object(self):
        if not self.request.user.family:
            from rest_framework.exceptions import NotFound
            raise NotFound("You are not a member of any family.")
        return self.request.user.family


@extend_schema(tags=["family"], request=GenerateInviteSerializer, responses=InviteCodeSerializer)
@api_view(["POST"])
def generate_invite_code(request):
    """Generate a 6-digit invite code for a child device or a second parent."""
    if not request.user.family:
        return Response(
            {"error": "Create a family first."}, status=status.HTTP_400_BAD_REQUEST
        )
    if not request.user.is_parent:
        return Response(
            {"error": "Only parents can generate invite codes."},
            status=status.HTTP_403_FORBIDDEN,
        )

    params = GenerateInviteSerializer(data=request.data)
    params.is_valid(raise_exception=True)
    code = InviteCode.generate_code()
    # Ensure uniqueness
    # code is unique across all rows (used ones included)
    while InviteCode.objects.filter(code=code).exists():
        code = InviteCode.generate_code()

    ttl = settings.INVITE_CODE_TTL
    invite = InviteCode.objects.create(
        code=code,
        role=params.validated_data["role"],
        family=request.user.family,
        created_by=request.user,
        expires_at=timezone.now() + timezone.timedelta(seconds=ttl),
    )
    # Cache for fast lookup
    cache.set(f"invite:{code}", invite.family_id, timeout=ttl)
    return Response(InviteCodeSerializer(invite).data, status=status.HTTP_201_CREATED)


@extend_schema(tags=["family"], request=JoinFamilySerializer)
@api_view(["POST"])
@throttle_classes([JoinFamilyUserThrottle, JoinFamilyIpThrottle])
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

    # The code alone is not enough: someone who saw it could join a stranger's
    # device, so a parent of the family approves every new member.
    invite.is_used = True
    invite.used_by = request.user
    invite.save(update_fields=["is_used", "used_by"])
    cache.delete(f"invite:{code}")

    MemberRequest.objects.filter(
        user=request.user, kind=MemberRequest.Kind.JOIN, status=MemberRequest.Status.PENDING
    ).delete()
    join_request = MemberRequest.objects.create(
        family=invite.family,
        user=request.user,
        kind=MemberRequest.Kind.JOIN,
        role=invite.role,
        device_name=serializer.validated_data.get("device_name", ""),
    )
    who = {User.Role.PARENT: "Родитель", User.Role.GRANDPARENT: "Бабушка/дедушка"}.get(
        invite.role, "Устройство")
    _notify_parents(
        join_request,
        "Заявка на вступление в семью",
        f"{who} {_display_name(request.user, join_request.device_name)} "
        f"просит доступ. Подтвердите в разделе «Семья».",
    )
    return Response(
        {
            "message": "Request sent. Waiting for a parent to approve it.",
            "family_name": invite.family.name,
            "request": MemberRequestSerializer(join_request).data,
        },
        status=status.HTTP_202_ACCEPTED,
    )


def _display_name(user, device_name=""):
    name = user.get_full_name() or user.email
    return f"{name} ({device_name})" if device_name else name


def _notify_parents(member_request, title, body):
    from apps.notifications.tasks import send_push_to_family_parents
    send_push_to_family_parents.delay(
        str(member_request.family_id), title, body,
        exclude_user_id=str(member_request.user_id),
        data={"type": "member_request", "request_id": str(member_request.id)},
    )


@extend_schema(tags=["family"], responses=MemberRequestSerializer(many=True))
@api_view(["GET"])
@permission_classes([IsParent, IsFamilyMember])
def pending_requests(request):
    """Join requests waiting for a parent's decision."""
    cutoff = timezone.now() - MemberRequest.TTL
    requests_qs = MemberRequest.objects.select_related("user").filter(
        family=request.user.family,
        status=MemberRequest.Status.PENDING,
        created_at__gt=cutoff,
    )
    return Response(MemberRequestSerializer(requests_qs, many=True).data)


@extend_schema(tags=["family"], responses=MemberRequestSerializer)
@api_view(["GET"])
def request_status(request, request_id):
    """The requesting device polls this until a parent decides."""
    member_request = MemberRequest.objects.select_related("user").filter(id=request_id).first()
    allowed = member_request and (
        member_request.user_id == request.user.id
        or (request.user.is_parent and member_request.family_id == request.user.family_id)
    )
    if not allowed:
        return Response({"error": "Request not found."}, status=status.HTTP_404_NOT_FOUND)
    return Response(MemberRequestSerializer(member_request).data)


@extend_schema(tags=["family"], request=None, responses=MemberRequestSerializer)
@api_view(["POST"])
@permission_classes([IsParent, IsFamilyMember])
def decide_request(request, request_id, decision):
    """Approve or reject a pending request of the parent's own family."""
    member_request = MemberRequest.objects.select_related("user").filter(
        id=request_id, family=request.user.family, status=MemberRequest.Status.PENDING,
    ).first()
    if member_request is None or member_request.is_expired:
        return Response(
            {"error": "Request not found or expired."}, status=status.HTTP_404_NOT_FOUND
        )

    approve = decision == "approve"
    if approve and member_request.kind == MemberRequest.Kind.JOIN:
        member = member_request.user
        if member.family_id and member.family_id != member_request.family_id:
            return Response(
                {"error": "This user already belongs to another family."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        member.family = member_request.family
        member.role = member_request.role
        fields = ["family", "role"]
        if member_request.device_name:
            member.device_name = member_request.device_name
            fields.append("device_name")
        member.save(update_fields=fields)

    member_request.status = (
        MemberRequest.Status.APPROVED if approve else MemberRequest.Status.REJECTED
    )
    member_request.decided_by = request.user
    member_request.decided_at = timezone.now()
    member_request.save(update_fields=["status", "decided_by", "decided_at"])
    return Response(MemberRequestSerializer(member_request).data)


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
    if not request.user.is_parent or not request.user.family:
        return Response(status=status.HTTP_403_FORBIDDEN)
    try:
        member = User.objects.get(id=user_id, family=request.user.family, role=User.Role.CHILD)
    except User.DoesNotExist:
        return Response({"error": "Member not found."}, status=status.HTTP_404_NOT_FOUND)
    member.family = None
    member.save(update_fields=["family"])
    return Response({"message": "Member removed."})


@extend_schema(tags=["family"], request=None)
@api_view(["POST"])
@permission_classes([IsParent, IsFamilyMember])
def unlink_device(request, user_id):
    """Log a child's device out (the child itself cannot). The child stays
    in the family and can log in again on any phone."""
    from rest_framework_simplejwt.token_blacklist.models import BlacklistedToken, OutstandingToken
    from apps.notifications.tasks import send_data_push_to_token

    member = User.objects.filter(
        id=user_id, family=request.user.family, role=User.Role.CHILD
    ).first()
    if member is None:
        return Response({"error": "Member not found."}, status=status.HTTP_404_NOT_FOUND)

    # Every refresh token dies, so the app is logged out on its next refresh
    # even when the push below does not arrive
    for token in OutstandingToken.objects.filter(user=member, expires_at__gt=timezone.now()):
        BlacklistedToken.objects.get_or_create(token=token)
    if member.device_token:
        send_data_push_to_token.delay(member.device_token, {"type": "unlinked"})
    member.device_token = ""
    member.device_name = ""
    member.save(update_fields=["device_token", "device_name"])
    return Response({"message": "Device unlinked."})


@extend_schema(tags=["family"], request=MemberEditSerializer, responses=MemberEditSerializer)
@api_view(["PATCH"])
@permission_classes([IsParent, IsFamilyMember])
def edit_member(request, user_id):
    """A parent fills in a child's or grandparent's name, gender, birth date
    and phone (a child cannot change them itself)."""
    member = User.objects.filter(id=user_id, family=request.user.family).exclude(
        role=User.Role.PARENT).first()
    if member is None:
        return Response({"error": "Member not found."}, status=status.HTTP_404_NOT_FOUND)
    serializer = MemberEditSerializer(member, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(serializer.data)


@extend_schema(tags=["family"])
@api_view(["GET"])
@permission_classes([IsFamilyMember])
def contacts(request):
    """The other members as the viewer names them ("Мама", "Старший брат"),
    with phone numbers for quick dial."""
    members = list(User.objects.filter(family=request.user.family))
    labels = contact_labels(request.user, members)
    data = UserPublicSerializer(
        [m for m in members if m.id != request.user.id], many=True,
        context={"request": request},
    ).data
    labels = {str(member_id): label for member_id, label in labels.items()}
    for row in data:
        row["label"] = labels[row["id"]]
    order = {User.Role.PARENT: 0, User.Role.GRANDPARENT: 1, User.Role.CHILD: 2}
    data = sorted(data, key=lambda r: (order.get(r["role"], 3), r["label"]))
    return Response(data)


@extend_schema(tags=["family"], request=ContactLabelSerializer)
@api_view(["PUT"])
@permission_classes([IsFamilyMember])
def rename_contact(request, user_id):
    """Rename a member in one's own contact list; an empty label restores the
    automatic one."""
    target = User.objects.filter(id=user_id, family=request.user.family).exclude(
        id=request.user.id).first()
    if target is None:
        return Response({"error": "Member not found."}, status=status.HTTP_404_NOT_FOUND)
    serializer = ContactLabelSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    label = serializer.validated_data["label"]
    if label:
        ContactLabel.objects.update_or_create(
            owner=request.user, target=target, defaults={"label": label})
    else:
        ContactLabel.objects.filter(owner=request.user, target=target).delete()
    members = list(User.objects.filter(family=request.user.family))
    return Response({"id": str(target.id), "label": contact_labels(request.user, members)[target.id]})
