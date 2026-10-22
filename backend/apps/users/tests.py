from datetime import date, timedelta
from unittest import mock

import re

from django.core import mail
from django.test import override_settings
from django.utils import timezone
from rest_framework_simplejwt.tokens import RefreshToken

from apps.tests_base import PASSWORD, ApiTestCase
from apps.users.invite_model import InviteCode
from apps.users.models import MemberRequest, User


class AuthAndFamilyFlowTests(ApiTestCase):
    def test_register_login_create_family_invite_join(self):
        res = self.client.post("/api/v1/auth/register/", {
            "email": "p@x.uz", "username": "p", "first_name": "P", "last_name": "A",
            "password": PASSWORD, "password2": PASSWORD, "role": "parent",
            "gender": "female", "birth_date": "1990-05-01", "phone_number": "+998 90 123-45-67",
        })
        self.assertEqual(res.status_code, 201, res.data)

        parent = User.objects.get(email="p@x.uz")
        self.as_user(parent)
        self.assertEqual(self.client.post("/api/v1/family/create/", {"name": "Fam"}).status_code, 201)
        code = self.client.post("/api/v1/family/invite/generate/").data["code"]

        child = self.make_user("c@x.uz", role="child")
        res = self.as_user(child).post("/api/v1/family/invite/join/", {"code": code})
        self.assertEqual(res.status_code, 202, res.data)
        request_id = res.data["request"]["id"]
        child.refresh_from_db()
        self.assertIsNone(child.family_id)  # not before a parent approves

        res = self.as_user(parent).post(f"/api/v1/family/requests/{request_id}/approve/")
        self.assertEqual(res.status_code, 200, res.data)
        child.refresh_from_db()
        self.assertEqual(child.family_id, parent.family_id)
        res = self.as_user(child).get(f"/api/v1/family/requests/{request_id}/")
        self.assertEqual(res.data["status"], "approved")

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


def login_code_from_outbox():
    return re.search(r"\b(\d{6})\b", mail.outbox[-1].body).group(1)


class LoginCodeTests(ApiTestCase):
    def login(self, email):
        return self.client.post("/api/v1/auth/login/", {"email": email, "password": PASSWORD})

    def test_parent_login_needs_code_from_email(self):
        parent = self.make_user("p@x.uz")
        res = self.login("p@x.uz")
        self.assertEqual(res.status_code, 200)
        self.assertTrue(res.data["otp_required"])
        self.assertNotIn("access", res.data)
        self.assertEqual(mail.outbox[-1].to, ["p@x.uz"])

        res = self.client.post("/api/v1/auth/login/verify/", {
            "challenge_id": res.data["challenge_id"], "code": login_code_from_outbox(),
        })
        self.assertEqual(res.status_code, 200, res.data)
        self.assertIn("refresh", res.data)
        parent.refresh_from_db()
        self.assertIsNotNone(parent.last_login)

    def test_code_is_single_use_and_attempts_are_limited(self):
        self.make_user("p@x.uz")
        challenge_id = self.login("p@x.uz").data["challenge_id"]
        code = login_code_from_outbox()
        wrong = "000000" if code != "000000" else "111111"
        for _ in range(5):
            res = self.client.post("/api/v1/auth/login/verify/",
                                   {"challenge_id": challenge_id, "code": wrong})
            self.assertEqual(res.status_code, 400)
        # Out of attempts: even the right code no longer works
        res = self.client.post("/api/v1/auth/login/verify/",
                               {"challenge_id": challenge_id, "code": code})
        self.assertEqual(res.status_code, 400)

        challenge_id = self.login("p@x.uz").data["challenge_id"]
        code = login_code_from_outbox()
        payload = {"challenge_id": challenge_id, "code": code}
        self.assertEqual(self.client.post("/api/v1/auth/login/verify/", payload).status_code, 200)
        self.assertEqual(self.client.post("/api/v1/auth/login/verify/", payload).status_code, 400)

    def test_wrong_password_and_child_login(self):
        self.make_user("c@x.uz", role="child")
        res = self.client.post("/api/v1/auth/login/", {"email": "c@x.uz", "password": "nope"})
        self.assertEqual(res.status_code, 401)
        res = self.login("c@x.uz")
        self.assertIn("access", res.data)  # children get no email step
        self.assertEqual(len(mail.outbox), 0)

    @override_settings(LOGIN_CODE_EMAIL_OVERRIDE="inbox@x.uz")
    def test_dev_override_sends_all_codes_to_one_inbox(self):
        self.make_user("fake@x.uz")
        self.login("fake@x.uz")
        self.assertEqual(mail.outbox[-1].to, ["inbox@x.uz"])


class MemberRequestTests(ApiTestCase):
    def invite(self, parent, role="child"):
        return self.as_user(parent).post("/api/v1/family/invite/generate/", {"role": role}).data["code"]

    def test_second_parent_joins_after_approval(self):
        family, parent, _ = self.make_family()
        code = self.invite(parent, role="parent")
        second = self.make_user("mom@x.uz")
        res = self.as_user(second).post("/api/v1/family/invite/join/", {"code": code})
        self.assertEqual(res.status_code, 202)
        self.mock_send_push_to_family_parents.assert_called_once()

        pending = self.as_user(parent).get("/api/v1/family/requests/").data
        self.assertEqual([r["user"]["email"] for r in pending], ["mom@x.uz"])
        self.client.post(f"/api/v1/family/requests/{pending[0]['id']}/approve/")
        second.refresh_from_db()
        self.assertEqual(second.family_id, family.id)
        self.assertTrue(second.is_parent)
        # The new parent can invite too
        self.assertEqual(self.as_user(second).post("/api/v1/family/invite/generate/").status_code, 201)

    def test_rejected_or_foreign_requests_do_not_join(self):
        family, parent, _ = self.make_family()
        _, other_parent, _ = self.make_family("Other")
        stranger = self.make_user("stranger@x.uz", role="child")
        code = self.invite(parent)
        res = self.as_user(stranger).post("/api/v1/family/invite/join/", {"code": code})
        request_id = res.data["request"]["id"]

        # A parent of another family can neither see nor approve it
        self.assertEqual(self.as_user(other_parent).get("/api/v1/family/requests/").data, [])
        res = self.client.post(f"/api/v1/family/requests/{request_id}/approve/")
        self.assertEqual(res.status_code, 404)
        self.assertEqual(self.client.get(f"/api/v1/family/requests/{request_id}/").status_code, 404)
        # The requester cannot approve their own request
        res = self.as_user(stranger).post(f"/api/v1/family/requests/{request_id}/approve/")
        self.assertEqual(res.status_code, 403)

        self.as_user(parent).post(f"/api/v1/family/requests/{request_id}/reject/")
        stranger.refresh_from_db()
        self.assertIsNone(stranger.family_id)
        res = self.as_user(stranger).get(f"/api/v1/family/requests/{request_id}/")
        self.assertEqual(res.data["status"], "rejected")

    def test_old_request_expires(self):
        _, parent, _ = self.make_family()
        child = self.make_user("late@x.uz", role="child")
        code = self.invite(parent)
        request_id = self.as_user(child).post(
            "/api/v1/family/invite/join/", {"code": code}).data["request"]["id"]
        MemberRequest.objects.filter(id=request_id).update(
            created_at=timezone.now() - MemberRequest.TTL - timedelta(minutes=1))
        res = self.as_user(parent).post(f"/api/v1/family/requests/{request_id}/approve/")
        self.assertEqual(res.status_code, 404)
        self.assertEqual(self.as_user(child).get(f"/api/v1/family/requests/{request_id}/").data["status"], "expired")

    def test_child_cannot_log_out(self):
        _, _, child = self.make_family()
        refresh = str(RefreshToken.for_user(child))
        res = self.as_user(child).post("/api/v1/auth/logout/", {"refresh": refresh})
        self.assertEqual(res.status_code, 403)

    def test_parent_unlinks_child_device(self):
        _, parent, child = self.make_family()
        child.device_token = "fcm-token"
        child.device_name = "Pixel"
        child.save()
        refresh = str(RefreshToken.for_user(child))

        res = self.as_user(parent).post(f"/api/v1/family/members/{child.id}/unlink/")
        self.assertEqual(res.status_code, 200)
        self.mock_send_data_push_to_token.assert_called_once_with("fcm-token", {"type": "unlinked"})
        child.refresh_from_db()
        self.assertEqual((child.device_token, child.device_name), ("", ""))
        # The device can no longer refresh its session
        self.client.force_authenticate(None)
        res = self.client.post("/api/v1/auth/refresh/", {"refresh": refresh})
        self.assertEqual(res.status_code, 401)

    def test_unlink_only_own_family_child(self):
        _, parent, child = self.make_family()
        _, other_parent, other_child = self.make_family("Other")
        self.assertEqual(
            self.as_user(parent).post(f"/api/v1/family/members/{other_child.id}/unlink/").status_code, 404)
        # Parents are not unlinked this way, and children cannot unlink anyone
        self.assertEqual(
            self.as_user(parent).post(f"/api/v1/family/members/{other_parent.id}/unlink/").status_code, 404)
        self.assertEqual(
            self.as_user(child).post(f"/api/v1/family/members/{child.id}/unlink/").status_code, 403)

    def test_parent_logs_out_freely(self):
        _, parent, _ = self.make_family()
        refresh = str(RefreshToken.for_user(parent))
        res = self.as_user(parent).post("/api/v1/auth/logout/", {"refresh": refresh})
        self.assertEqual(res.status_code, 200)


class PasswordRulesTests(ApiTestCase):
    def register(self, password):
        return self.client.post("/api/v1/auth/register/", {
            "email": "kid@x.uz", "username": "kid", "first_name": "Ali", "last_name": "V",
            "password": password, "password2": password, "role": "child",
            "gender": "male", "birth_date": "2014-03-02", "phone_number": "+998901112233",
        })

    def test_weak_passwords_are_rejected(self):
        for weak in ("Ab1", "12345678", "abcdefgh", "password1", "kid@x.uz1"):
            res = self.register(weak)
            self.assertEqual(res.status_code, 400, weak)
            self.assertIn("password", res.data)
        self.assertFalse(User.objects.filter(email="kid@x.uz").exists())

    def test_strong_password_is_accepted(self):
        self.assertEqual(self.register("Sunny7Garden").status_code, 201)


class ProfileAndContactsTests(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.family, self.mom, self.kid = self.make_family()
        self.dad = self.make_user("dad@x.uz", "parent", self.family)
        self.gran = self.make_user("gran@x.uz", "grandparent", self.family)
        self.sis = self.make_user("sis@x.uz", "child", self.family)
        people = [
            (self.mom, "female", "1985-01-01"), (self.dad, "male", "1983-01-01"),
            (self.gran, "female", "1960-01-01"), (self.kid, "male", "2012-01-01"),
            (self.sis, "female", "2015-06-01"),
        ]
        for user, gender, born in people:
            user.gender, user.phone_number = gender, "+998900000000"
            user.birth_date = date.fromisoformat(born)
            user.save()

    def labels(self, user):
        res = self.as_user(user).get("/api/v1/family/contacts/")
        self.assertEqual(res.status_code, 200)
        return {row["id"]: row["label"] for row in res.data}

    def test_register_checks_profile_fields(self):
        res = self.client.post("/api/v1/auth/register/", {
            "email": "n@x.uz", "username": "n", "first_name": "N", "last_name": "A",
            "password": PASSWORD, "password2": PASSWORD,
            "gender": "x", "birth_date": "2999-01-01", "phone_number": "12",
        })
        self.assertEqual(res.status_code, 400)
        for field in ("gender", "birth_date", "phone_number"):
            self.assertIn(field, res.data)

    def test_child_sees_family_names(self):
        labels = self.labels(self.kid)
        self.assertEqual(labels[str(self.mom.id)], "Мама")
        self.assertEqual(labels[str(self.dad.id)], "Папа")
        self.assertEqual(labels[str(self.gran.id)], "Бабушка")
        self.assertEqual(labels[str(self.sis.id)], "Младшая сестра")
        self.assertNotIn(str(self.kid.id), labels)
        self.assertEqual(self.labels(self.sis)[str(self.kid.id)], "Старший брат")
        self.assertEqual(self.labels(self.mom)[str(self.kid.id)], "Сын")
        self.assertEqual(self.labels(self.gran)[str(self.sis.id)], "Внучка")

    def test_same_names_are_numbered_and_renamed(self):
        mom2 = self.make_user("mom2@x.uz", "parent", self.family)
        mom2.gender = "female"
        mom2.save()
        labels = self.labels(self.kid)
        self.assertEqual(labels[str(self.mom.id)], "Мама 1")
        self.assertEqual(labels[str(mom2.id)], "Мама 2")

        url = f"/api/v1/family/contacts/{mom2.id}/label/"
        self.assertEqual(self.as_user(self.kid).put(url, {"label": "Мама Дилноза"}).status_code, 200)
        self.assertEqual(self.labels(self.kid)[str(mom2.id)], "Мама Дилноза")
        # Only the child who renamed sees it
        self.assertEqual(self.labels(self.sis)[str(mom2.id)], "Мама 2")
        # An empty label brings the automatic one back
        self.as_user(self.kid).put(url, {"label": ""})
        self.assertEqual(self.labels(self.kid)[str(mom2.id)], "Мама 2")

    def test_no_gender_falls_back_to_name(self):
        self.dad.gender = ""
        self.dad.save()
        self.assertEqual(self.labels(self.kid)[str(self.dad.id)], self.dad.get_full_name())

    def test_cannot_rename_stranger(self):
        _, stranger, _ = self.make_family("Other")
        res = self.as_user(self.kid).put(f"/api/v1/family/contacts/{stranger.id}/label/", {"label": "x"})
        self.assertEqual(res.status_code, 404)

    def test_child_changes_only_avatar_and_device(self):
        client = self.as_user(self.kid)
        res = client.patch("/api/v1/auth/profile/", {"birth_date": "2010-01-01"})
        self.assertEqual(res.status_code, 400)
        res = client.patch("/api/v1/auth/profile/", {"location_paused": True})
        self.assertEqual(res.status_code, 400)
        self.assertEqual(client.patch("/api/v1/auth/profile/", {"device_name": "Pixel"}).status_code, 200)

    def test_adult_pauses_location(self):
        res = self.as_user(self.dad).patch("/api/v1/auth/profile/", {"location_paused": True})
        self.assertEqual(res.status_code, 200)
        self.assertTrue(res.data["location_paused"])
        self.assertTrue(res.data["profile_complete"])

    def test_old_account_profile_incomplete(self):
        self.dad.birth_date = None
        self.dad.save()
        self.assertFalse(self.as_user(self.dad).get("/api/v1/auth/profile/").data["profile_complete"])

    def test_parent_edits_child(self):
        url = f"/api/v1/family/members/{self.kid.id}/"
        res = self.as_user(self.mom).patch(url, {"birth_date": "2012-02-02", "phone_number": "90 111 22 33"})
        self.assertEqual(res.status_code, 200, res.data)
        self.kid.refresh_from_db()
        self.assertEqual(str(self.kid.birth_date), "2012-02-02")
        self.assertEqual(self.kid.phone_number, "901112233")
        # Not another parent, not as a child, not someone else's family
        self.assertEqual(self.as_user(self.mom).patch(f"/api/v1/family/members/{self.dad.id}/", {}).status_code, 404)
        self.assertEqual(self.as_user(self.kid).patch(url, {"gender": "female"}).status_code, 403)
        _, other_parent, _ = self.make_family("Other")
        self.assertEqual(self.as_user(other_parent).patch(url, {"gender": "female"}).status_code, 404)

    def test_bad_phone_and_future_birth_date(self):
        url = f"/api/v1/family/members/{self.kid.id}/"
        res = self.as_user(self.mom).patch(url, {"phone_number": "abc", "birth_date": "2999-01-01"})
        self.assertEqual(res.status_code, 400)
        self.assertIn("phone_number", res.data)
        self.assertIn("birth_date", res.data)

    def test_grandparent_can_log_out_but_not_manage(self):
        refresh = str(RefreshToken.for_user(self.gran))
        self.assertEqual(
            self.as_user(self.gran).post("/api/v1/auth/logout/", {"refresh": refresh}).status_code, 200)
        self.assertEqual(
            self.as_user(self.gran).post(f"/api/v1/family/members/{self.kid.id}/unlink/").status_code, 403)

    def test_positions_show_paused(self):
        from apps.location.models import LocationRecord
        LocationRecord.objects.create(
            user=self.dad, latitude=41.3, longitude=69.2, recorded_at=timezone.now())
        self.dad.location_paused = True
        self.dad.save()
        res = self.as_user(self.kid).get("/api/v1/location/current/")
        self.assertEqual(res.status_code, 200)
        self.assertTrue(res.data[0]["paused"])
