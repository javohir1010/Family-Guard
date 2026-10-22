from rest_framework import serializers
from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from django.utils import timezone
import re
from .models import Family, MemberRequest
from .invite_model import InviteCode

User = get_user_model()


def clean_phone(value):
    """"+998 90 123-45-67" → "+998901234567"; 9 to 15 digits."""
    phone = re.sub(r"[\s\-()]", "", value or "")
    if not re.fullmatch(r"\+?\d{9,15}", phone):
        raise serializers.ValidationError("Введите номер телефона, например +998901234567.")
    return phone


def check_birth_date(value):
    today = timezone.localdate()
    if value > today or value.year < today.year - 120:
        raise serializers.ValidationError("Проверьте дату рождения.")
    return value


class PersonFieldsMixin:
    """Validation shared by every form that sets a member's personal data."""

    def validate_phone_number(self, value):
        return clean_phone(value)

    def validate_birth_date(self, value):
        return check_birth_date(value)


class UserPublicSerializer(serializers.ModelSerializer):
    """Minimal public info about a family member."""
    is_online = serializers.BooleanField(source="is_active_now", read_only=True)

    class Meta:
        model = User
        fields = ("id", "first_name", "last_name", "email", "role", "avatar",
                  "gender", "birth_date", "phone_number", "location_paused",
                  "device_name", "is_online", "last_seen")
        read_only_fields = fields


class UserProfileSerializer(PersonFieldsMixin, serializers.ModelSerializer):
    family_name = serializers.CharField(source="family.name", read_only=True)
    is_online = serializers.BooleanField(source="is_active_now", read_only=True)
    profile_complete = serializers.BooleanField(read_only=True)

    # A child sets up only these; the rest is filled in by a parent
    CHILD_WRITABLE = {"avatar", "device_name", "device_token"}

    class Meta:
        model = User
        fields = ("id", "username", "first_name", "last_name", "email", "role",
                  "avatar", "gender", "birth_date", "phone_number", "location_paused",
                  "device_name", "device_token", "family", "family_name",
                  "is_online", "last_seen", "created_at", "profile_complete")
        read_only_fields = ("id", "username", "email", "role", "family", "family_name",
                            "is_online", "last_seen", "created_at")

    def validate(self, attrs):
        if self.instance is not None and self.instance.is_child:
            locked = sorted(set(attrs) - self.CHILD_WRITABLE)
            if locked:
                raise serializers.ValidationError(
                    {name: "Эти данные меняет родитель." for name in locked})
        return attrs


class MemberEditSerializer(PersonFieldsMixin, serializers.ModelSerializer):
    """A parent fills in the data of a child or grandparent."""

    class Meta:
        model = User
        fields = ("id", "first_name", "last_name", "gender", "birth_date", "phone_number")
        read_only_fields = ("id",)


class ContactLabelSerializer(serializers.Serializer):
    label = serializers.CharField(max_length=40, allow_blank=True, trim_whitespace=True)


class RegisterSerializer(PersonFieldsMixin, serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, required=True)
    password2 = serializers.CharField(write_only=True, required=True)

    class Meta:
        model = User
        fields = ("email", "username", "first_name", "last_name", "password",
                  "password2", "role", "gender", "birth_date", "phone_number", "device_name")
        # gender/birth_date/phone become required once the app and dashboard
        # forms send them; until then an incomplete profile is asked for after login
        extra_kwargs = {"first_name": {"required": True}, "last_name": {"required": True}}

    def validate(self, attrs):
        if attrs["password"] != attrs.pop("password2"):
            raise serializers.ValidationError({"password": "Пароли не совпадают."})
        # With a draft user so the "too similar to email/name" check works too
        draft = User(**{k: v for k, v in attrs.items() if k != "password"})
        try:
            validate_password(attrs["password"], user=draft)
        except DjangoValidationError as e:
            raise serializers.ValidationError({"password": list(e.messages)})
        return attrs

    def create(self, validated_data):
        user = User.objects.create_user(**validated_data)
        return user


class FamilySerializer(serializers.ModelSerializer):
    members = UserPublicSerializer(many=True, read_only=True)
    member_count = serializers.SerializerMethodField()

    class Meta:
        model = Family
        fields = ("id", "name", "created_at", "members", "member_count")
        read_only_fields = ("id", "created_at")

    def get_member_count(self, obj) -> int:
        return obj.members.count()


class InviteCodeSerializer(serializers.ModelSerializer):
    family_name = serializers.CharField(source="family.name", read_only=True)
    created_by_name = serializers.CharField(
        source="created_by.get_full_name", read_only=True
    )

    class Meta:
        model = InviteCode
        fields = ("code", "role", "family_name", "created_by_name", "is_used",
                  "created_at", "expires_at")
        read_only_fields = fields


class JoinFamilySerializer(serializers.Serializer):
    code = serializers.CharField(max_length=6, min_length=6)
    device_name = serializers.CharField(max_length=100, required=False)


class GenerateInviteSerializer(serializers.Serializer):
    role = serializers.ChoiceField(choices=User.Role.choices, default=User.Role.CHILD)


class MemberRequestSerializer(serializers.ModelSerializer):
    status = serializers.CharField(source="effective_status", read_only=True)
    user = UserPublicSerializer(read_only=True)

    class Meta:
        model = MemberRequest
        fields = ("id", "kind", "role", "status", "user", "device_name",
                  "created_at", "decided_at")
        read_only_fields = fields


class LoginSerializer(serializers.Serializer):
    email = serializers.EmailField()
    password = serializers.CharField(trim_whitespace=False)


class LoginVerifySerializer(serializers.Serializer):
    challenge_id = serializers.UUIDField()
    code = serializers.RegexField(r"^\d{6}$")


class LogoutSerializer(serializers.Serializer):
    refresh = serializers.CharField()


class DeviceTokenSerializer(serializers.Serializer):
    device_token = serializers.CharField()
