from rest_framework import serializers
from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from .models import Family
from .invite_model import InviteCode

User = get_user_model()


class UserPublicSerializer(serializers.ModelSerializer):
    """Minimal public info about a family member."""

    class Meta:
        model = User
        fields = ("id", "first_name", "last_name", "email", "role", "avatar",
                  "device_name", "is_online", "last_seen")
        read_only_fields = fields


class UserProfileSerializer(serializers.ModelSerializer):
    family_name = serializers.CharField(source="family.name", read_only=True)

    class Meta:
        model = User
        fields = ("id", "first_name", "last_name", "email", "role",
                  "avatar", "phone_number", "device_name", "device_token",
                  "family", "family_name", "is_online", "last_seen", "created_at")
        read_only_fields = ("id", "email", "role", "family", "family_name",
                            "is_online", "last_seen", "created_at")


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(
        write_only=True, required=True, validators=[validate_password]
    )
    password2 = serializers.CharField(write_only=True, required=True)

    class Meta:
        model = User
        fields = ("email", "username", "first_name", "last_name", "password",
                  "password2", "role", "phone_number", "device_name")
        extra_kwargs = {"first_name": {"required": True}, "last_name": {"required": True}}

    def validate(self, attrs):
        if attrs["password"] != attrs.pop("password2"):
            raise serializers.ValidationError({"password": "Passwords do not match."})
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

    def get_member_count(self, obj):
        return obj.members.count()


class InviteCodeSerializer(serializers.ModelSerializer):
    family_name = serializers.CharField(source="family.name", read_only=True)
    created_by_name = serializers.CharField(
        source="created_by.get_full_name", read_only=True
    )

    class Meta:
        model = InviteCode
        fields = ("code", "family_name", "created_by_name", "is_used",
                  "created_at", "expires_at")
        read_only_fields = fields


class JoinFamilySerializer(serializers.Serializer):
    code = serializers.CharField(max_length=6, min_length=6)
    device_name = serializers.CharField(max_length=100, required=False)


class DeviceTokenSerializer(serializers.Serializer):
    device_token = serializers.CharField()
