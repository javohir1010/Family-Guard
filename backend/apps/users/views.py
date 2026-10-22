from rest_framework import generics, status, permissions
from rest_framework.decorators import api_view, permission_classes, throttle_classes
from rest_framework.response import Response
from rest_framework.throttling import AnonRateThrottle
from rest_framework_simplejwt.tokens import RefreshToken
import logging
import secrets
from datetime import timedelta

from django.conf import settings
from django.contrib.auth import authenticate, get_user_model
from django.contrib.auth.hashers import check_password, make_password
from django.contrib.auth.models import update_last_login
from django.core.mail import send_mail
from django.utils import timezone
from drf_spectacular.utils import extend_schema, OpenApiResponse
from .serializers import (
    RegisterSerializer,
    UserProfileSerializer,
    DeviceTokenSerializer,
    LogoutSerializer,
    LoginSerializer,
    LoginVerifySerializer,
)
from .models import LoginChallenge, MemberRequest

User = get_user_model()
logger = logging.getLogger(__name__)


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


def _token_response(user):
    refresh = RefreshToken.for_user(user)
    update_last_login(None, user)
    return Response({"access": str(refresh.access_token), "refresh": str(refresh)})


def _mask_email(email):
    name, _, domain = email.partition("@")
    return f"{name[:2]}***@{domain}"


class LoginView(generics.GenericAPIView):
    """Email + password. Parents get a code by email instead of tokens and
    finish with /auth/login/verify/; children get tokens right away."""
    serializer_class = LoginSerializer
    permission_classes = [permissions.AllowAny]
    authentication_classes = []
    throttle_classes = [LoginRateThrottle]

    @extend_schema(tags=["auth"])
    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = authenticate(
            request,
            email=serializer.validated_data["email"],
            password=serializer.validated_data["password"],
        )
        if user is None:
            return Response(
                {"detail": "Неверный email или пароль."},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        if not (settings.LOGIN_EMAIL_CODE and user.is_parent):
            return _token_response(user)

        # One live code per account: a new login replaces the previous code
        LoginChallenge.objects.filter(user=user).delete()
        code = f"{secrets.randbelow(10**6):06d}"
        challenge = LoginChallenge.objects.create(
            user=user,
            code_hash=make_password(code),
            expires_at=timezone.now() + timedelta(seconds=settings.LOGIN_CODE_TTL),
        )
        to = settings.LOGIN_CODE_EMAIL_OVERRIDE or user.email
        minutes = settings.LOGIN_CODE_TTL // 60
        try:
            send_mail(
                "Код входа FamilyGuard",
                f"Ваш код для входа в FamilyGuard: {code}\n\n"
                f"Код действует {minutes} мин. Если вы не входили в аккаунт, "
                f"смените пароль.",
                None,
                [to],
            )
        except Exception:
            logger.exception("Login code email to %s failed", to)
            challenge.delete()
            return Response(
                {"detail": "Не удалось отправить код на почту. Попробуйте позже."},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
        return Response({
            "otp_required": True,
            "challenge_id": str(challenge.id),
            "email_hint": _mask_email(to),
            "expires_in": settings.LOGIN_CODE_TTL,
        })


class LoginVerifyRateThrottle(AnonRateThrottle):
    rate = "10/minute"
    scope = "login_verify"


class LoginVerifyView(generics.GenericAPIView):
    """Second login step: the code from the email → JWT tokens."""
    serializer_class = LoginVerifySerializer
    permission_classes = [permissions.AllowAny]
    authentication_classes = []
    throttle_classes = [LoginVerifyRateThrottle]

    @extend_schema(tags=["auth"])
    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        challenge = LoginChallenge.objects.select_related("user").filter(
            id=serializer.validated_data["challenge_id"],
            expires_at__gt=timezone.now(),
            attempts__lt=LoginChallenge.MAX_ATTEMPTS,
        ).first()
        if challenge is None:
            return Response(
                {"detail": "Код истёк. Войдите заново, чтобы получить новый."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if not check_password(serializer.validated_data["code"], challenge.code_hash):
            challenge.attempts += 1
            challenge.save(update_fields=["attempts"])
            left = LoginChallenge.MAX_ATTEMPTS - challenge.attempts
            return Response(
                {"detail": f"Неверный код. Осталось попыток: {left}."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        user = challenge.user
        challenge.delete()
        return _token_response(user)


class LogoutView(generics.GenericAPIView):
    """Blacklist refresh token on logout."""
    serializer_class = LogoutSerializer

    @extend_schema(tags=["auth"])
    def post(self, request):
        # A child device leaves only after a parent approved it in the dashboard
        user = request.user
        if user.is_child and user.family_id:
            approved = MemberRequest.objects.filter(
                user=user, family_id=user.family_id, kind=MemberRequest.Kind.LOGOUT,
                status=MemberRequest.Status.APPROVED,
                decided_at__gt=timezone.now() - MemberRequest.TTL,
            ).first()
            if approved is None:
                return Response(
                    {"error": "Выход должен подтвердить родитель."},
                    status=status.HTTP_403_FORBIDDEN,
                )
            approved.status = MemberRequest.Status.USED
            approved.save(update_fields=["status"])
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
