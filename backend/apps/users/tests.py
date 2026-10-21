from datetime import timedelta
from unittest import mock

from django.utils import timezone

from apps.tests_base import PASSWORD, ApiTestCase
from apps.users.invite_model import InviteCode
from apps.users.models import User


class AuthAndFamilyFlowTests(ApiTestCase):
    def test_register_login_create_family_invite_join(self):
        res = self.client.post("/api/v1/auth/register/", {
            "email": "p@x.uz", "username": "p", "first_name": "P", "last_name": "A",
            "password": PASSWORD, "password2": PASSWORD, "role": "parent",
        })
        self.assertEqual(res.status_code, 201, res.data)

        res = self.client.post("/api/v1/auth/login/", {"email": "p@x.uz", "password": PASSWORD})
        self.assertEqual(res.status_code, 200)
        self.assertIn("refresh", res.data)

        parent = User.objects.get(email="p@x.uz")
        self.as_user(parent)
        self.assertEqual(self.client.post("/api/v1/family/create/", {"name": "Fam"}).status_code, 201)
        code = self.client.post("/api/v1/family/invite/generate/").data["code"]

        child = self.make_user("c@x.uz", role="child")
        res = self.as_user(child).post("/api/v1/family/invite/join/", {"code": code})
        self.assertEqual(res.status_code, 200, res.data)
        child.refresh_from_db()
        self.assertEqual(child.family_id, parent.family_id)

        # Code is single-use
        other = self.make_user("c2@x.uz", role="child")
        res = self.as_user(other).post("/api/v1/family/invite/join/", {"code": code})
        self.assertEqual(res.status_code, 400)

    def test_invite_code_never_reuses_a_used_code(self):
        family, parent, _ = self.make_family()
        InviteCode.objects.create(
            code="111111", family=family, created_by=parent, is_used=True,
            expires_at=timezone.now() + timedelta(days=1),
        )
        with mock.patch.object(InviteCode, "generate_code", side_effect=["111111", "222222"]):
            res = self.as_user(parent).post("/api/v1/family/invite/generate/")
        self.assertEqual(res.status_code, 201)
        self.assertEqual(res.data["code"], "222222")

    def test_invite_code_is_six_digits(self):
        for _ in range(50):
            code = InviteCode.generate_code()
            self.assertRegex(code, r"^\d{6}$")

    def test_join_family_is_rate_limited(self):
        family, parent, _ = self.make_family()
        InviteCode.objects.create(
            code="123456", family=family, created_by=parent,
            expires_at=timezone.now() + timedelta(days=1),
        )
        guesser = self.make_user("guesser@x.uz", "child")
        client = self.as_user(guesser)
        for code in ("000001", "000002", "000003", "000004", "000005"):
            res = client.post("/api/v1/family/invite/join/", {"code": code})
            self.assertEqual(res.status_code, 400)
        # Sixth try is refused even with the right code
        res = client.post("/api/v1/family/invite/join/", {"code": "123456"})
        self.assertEqual(res.status_code, 429)
        guesser.refresh_from_db()
        self.assertIsNone(guesser.family)

    def test_join_family_is_rate_limited_per_ip(self):
        family, parent, _ = self.make_family()
        for i in range(30):
            user = self.make_user(f"bot{i}@x.uz", "child")
            res = self.as_user(user).post("/api/v1/family/invite/join/", {"code": "000000"})
            self.assertEqual(res.status_code, 400)
        user = self.make_user("bot-last@x.uz", "child")
        res = self.as_user(user).post("/api/v1/family/invite/join/", {"code": "000000"})
        self.assertEqual(res.status_code, 429)

    def test_child_cannot_rename_family(self):
        family, _, child = self.make_family()
        res = self.as_user(child).patch("/api/v1/family/detail/", {"name": "Hacked"})
        self.assertEqual(res.status_code, 403)
        family.refresh_from_db()
        self.assertEqual(family.name, "Fam")

    def test_remove_member_requires_family(self):
        lonely_parent = self.make_user("lonely@x.uz")
        lonely_child = self.make_user("lonelychild@x.uz", role="child")
        res = self.as_user(lonely_parent).delete(f"/api/v1/family/members/{lonely_child.id}/remove/")
        self.assertEqual(res.status_code, 403)

    def test_online_status_expires(self):
        _, parent, child = self.make_family()
        User.objects.filter(id=child.id).update(
            is_online=True, last_seen=timezone.now() - timedelta(hours=2)
        )
        members = self.as_user(parent).get("/api/v1/family/members/").data
        child_data = next(m for m in members if m["role"] == "child")
        self.assertFalse(child_data["is_online"])

        User.objects.filter(id=child.id).update(last_seen=timezone.now())
        members = self.client.get("/api/v1/family/members/").data
        child_data = next(m for m in members if m["role"] == "child")
        self.assertTrue(child_data["is_online"])
