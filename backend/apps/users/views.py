from rest_framework import generics, status, permissions
from rest_framework.decorators import api_view, permission_classes, throttle_classes
from rest_framework.response import Response
from rest_framework.throttling import AnonRateThrottle
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView
from rest_framework_simplejwt.tokens import RefreshToken
from django.contrib.auth import get_user_model
from django.utils import timezone
from drf_spectacular.utils import extend_schema, OpenApiResponse
from .serializers import (
    RegisterSerializer,
    UserProfileSerializer,
    DeviceTokenSerializer,
)

User = get_user_model()


class LoginRateThrottle(AnonRateThrottle):
    rate = "5/minute"
    scope = "login"


class RegisterView(generics.CreateAPIView):
    """Register a new user (parent or child)."""
    serializer_class = RegisterSerializer
    permission_classes = [permissions.AllowAny]

    @extend_schema(tags=["auth"])
    def post(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        refresh = RefreshToken.for_user(user)
        return Response(
            {
                "message": "Registration successful.",
                "access": str(refresh.access_token),
                "refresh": str(refresh),
                "user": UserProfileSerializer(user).data,
            },
            status=status.HTTP_201_CREATED,
        )


class LoginView(TokenObtainPairView):
    throttle_classes = [LoginRateThrottle]

    @extend_schema(tags=["auth"])
    def post(self, request, *args, **kwargs):
        return super().post(request, *args, **kwargs)


class LogoutView(generics.GenericAPIView):
    """Blacklist refresh token on logout."""

    @extend_schema(tags=["auth"])
    def post(self, request):
        try:
            refresh_token = request.data.get("refresh")
            token = RefreshToken(refresh_token)
            token.blacklist()
            return Response({"message": "Logged out successfully."})
        except Exception:
            return Response(
                {"error": "Invalid token."}, status=status.HTTP_400_BAD_REQUEST
            )


class ProfileView(generics.RetrieveUpdateAPIView):
    serializer_class = UserProfileSerializer

    @extend_schema(tags=["auth"])
    def get_object(self):
        return self.request.user


@extend_schema(tags=["auth"], request=DeviceTokenSerializer)
@api_view(["POST"])
def update_device_token(request):
    """Update FCM device token for push notifications."""
    serializer = DeviceTokenSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    request.user.device_token = serializer.validated_data["device_token"]
    request.user.save(update_fields=["device_token"])
    return Response({"message": "Device token updated."})
