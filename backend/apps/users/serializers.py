from rest_framework import serializers
from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from .models import Family, MemberRequest
from .invite_model import InviteCode

User = get_user_model()


class UserPublicSerializer(serializers.ModelSerializer):
    """Minimal public info about a family member."""
    is_online = serializers.BooleanField(source="is_active_now", read_only=True)

    class Meta:
        model = User
        fields = ("id", "first_name", "last_name", "email", "role", "avatar",
                  "device_name", "is_online", "last_seen")
        read_only_fields = fields


class UserProfileSerializer(serializers.ModelSerializer):
    family_name = serializers.CharField(source="family.name", read_only=True)
    is_online = serializers.BooleanField(source="is_active_now", read_only=True)

    class Meta:
        model = User
        fields = ("id", "first_name", "last_name", "email", "role",
                  "avatar", "phone_number", "device_name", "device_token",
                  "family", "family_name", "is_online", "last_seen", "created_at")
        read_only_fields = ("id", "email", "role", "family", "family_name",
                            "is_online", "last_seen", "created_at")


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, required=True)
    password2 = serializers.CharField(write_only=True, required=True)

    class Meta:
        model = User
        fields = ("email", "username", "first_name", "last_name", "password",
                  "password2", "role", "phone_number", "device_name")
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
