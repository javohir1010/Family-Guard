"""Shared helpers for API tests."""
from unittest import mock

from django.test import override_settings
from rest_framework.test import APITestCase

from apps.users.models import Family, User

PASSWORD = "Str0ngPass!234"


@override_settings(
    CHANNEL_LAYERS={"default": {"BACKEND": "channels.layers.InMemoryChannelLayer"}},
    CACHES={"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}},
    REST_FRAMEWORK={
        "DEFAULT_AUTHENTICATION_CLASSES": [
            "rest_framework_simplejwt.authentication.JWTAuthentication",
        ],
        "DEFAULT_PERMISSION_CLASSES": ["rest_framework.permissions.IsAuthenticated"],
        "DEFAULT_FILTER_BACKENDS": [
            "django_filters.rest_framework.DjangoFilterBackend",
            "rest_framework.filters.SearchFilter",
            "rest_framework.filters.OrderingFilter",
        ],
        "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
        "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.PageNumberPagination",
        "PAGE_SIZE": 50,
    },
)
class ApiTestCase(APITestCase):
    """No Redis/Celery needed: in-memory channels/cache, Celery calls mocked."""

    def setUp(self):
        super().setUp()
        for target in (
            "apps.notifications.tasks.send_push_to_family_parents.delay",
            "apps.notifications.tasks.send_push_notification.delay",
            "apps.notifications.tasks.send_emergency_sms.delay",
            "apps.sos.tasks.process_sos_event.apply_async",
        ):
            patcher = mock.patch(target)
            setattr(self, "mock_" + target.split(".")[-2], patcher.start())
            self.addCleanup(patcher.stop)

    def make_user(self, email, role="parent", family=None):
        return User.objects.create_user(
            email=email, username=email.split("@")[0], password=PASSWORD,
            first_name=email[0].upper(), last_name="Test", role=role, family=family,
        )

    def make_family(self, name="Fam"):
        family = Family.objects.create(name=name)
        parent = self.make_user(f"parent-{name.lower()}@x.uz", "parent", family)
        child = self.make_user(f"child-{name.lower()}@x.uz", "child", family)
        return family, parent, child

    def as_user(self, user):
        self.client.force_authenticate(user)
        return self.client
