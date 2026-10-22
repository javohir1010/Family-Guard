from apps.app_control.models import AppRule, AppUsage, PermissionAlert
from apps.tests_base import ApiTestCase


class AppControlTests(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.family, self.parent, self.child = self.make_family()

    def test_parent_cannot_create_rule_for_other_familys_child(self):
        _, _, foreign_child = self.make_family("Other")
        res = self.as_user(self.parent).post("/api/v1/apps/rules/", {
            "child": foreign_child.id, "package_name": "com.x", "rule_type": "block",
        })
        self.assertEqual(res.status_code, 400)
        self.assertFalse(AppRule.objects.exists())

    def test_rule_crud_and_device_fetch(self):
        client = self.as_user(self.parent)
        res = client.post("/api/v1/apps/rules/", {
            "child": self.child.id, "package_name": "com.x", "rule_type": "limit",
            "daily_limit_minutes": 30,
        }, format="json")
        self.assertEqual(res.status_code, 201, res.data)
        rule_id = res.data["id"]

        # Switching to block clears the limit; switching back requires one
        res = client.patch(f"/api/v1/apps/rules/{rule_id}/detail/", {"rule_type": "block"})
        self.assertEqual(res.status_code, 200)
        self.assertIsNone(res.data["daily_limit_minutes"])
        res = client.patch(f"/api/v1/apps/rules/{rule_id}/detail/", {"rule_type": "limit"})
        self.assertEqual(res.status_code, 400)

        rules = self.as_user(self.child).get("/api/v1/apps/rules/device/").data
        self.assertEqual([r["package_name"] for r in rules], ["com.x"])

    def test_schedule_validation(self):
        client = self.as_user(self.parent)
        base = {"child": self.child.id, "start_time": "21:00", "end_time": "07:00"}
        self.assertEqual(client.post("/api/v1/apps/schedule/", base).status_code, 201)
        self.assertEqual(
            client.post("/api/v1/apps/schedule/", {**base, "days_bitmask": 0}).status_code, 400
        )
        self.assertEqual(
            client.post("/api/v1/apps/schedule/", {**base, "end_time": "21:00"}).status_code, 400
        )

    def test_usage_batch_skips_malformed_entries(self):
        res = self.as_user(self.child).post("/api/v1/apps/usage/batch/", {"usages": [
            {"package_name": "com.ok", "app_name": "OK", "duration_seconds": 600,
             "date": "2026-10-07", "last_used": "2026-10-07T10:00:00Z"},
            {"package_name": "com.bad", "duration_seconds": "lots", "date": "2026-10-07"},
            {"package_name": "com.bad2", "duration_seconds": 5, "date": "yesterday"},
        ]}, format="json")
        self.assertEqual(res.status_code, 200, res.data)
        self.assertEqual(list(AppUsage.objects.values_list("package_name", flat=True)), ["com.ok"])

    def test_usage_list_filters_by_date(self):
        AppUsage.objects.create(child=self.child, package_name="a", app_name="A",
                                duration_seconds=60, date="2026-10-06")
        AppUsage.objects.create(child=self.child, package_name="b", app_name="B",
                                duration_seconds=60, date="2026-10-07")
        client = self.as_user(self.parent)
        res = client.get(f"/api/v1/apps/usage/{self.child.id}/", {"date": "2026-10-07"})
        self.assertEqual([u["package_name"] for u in res.data["results"]], ["b"])
        res = client.get(f"/api/v1/apps/usage/{self.child.id}/", {"date": "07.10.2026"})
        self.assertEqual(res.status_code, 400)

    def test_private_dns_bypass_is_reported_to_parents(self):
        res = self.as_user(self.child).post("/api/v1/apps/permissions/", {
            "permission": "PRIVATE_DNS", "was_granted": False,
        }, format="json")
        self.assertEqual(res.status_code, 201, res.data)
        self.assertEqual(PermissionAlert.objects.get().permission, "PRIVATE_DNS")
        self.mock_send_push_to_family_parents.assert_called_once()

    def test_rule_changes_are_pushed_to_child_devices(self):
        client = self.as_user(self.parent)
        res = client.post("/api/v1/apps/rules/", {
            "child": self.child.id, "package_name": "com.google.android.youtube",
            "rule_type": "block",
        }, format="json")
        self.assertEqual(res.status_code, 201, res.data)
        self.mock_send_data_push_to_family_children.assert_called_once_with(
            family_id=str(self.family.id), data={"type": "rules_updated"}
        )
        client.patch(f"/api/v1/apps/rules/{res.data['id']}/detail/", {"is_active": False})
        client.delete(f"/api/v1/apps/rules/{res.data['id']}/detail/")
        self.assertEqual(self.mock_send_data_push_to_family_children.call_count, 3)

    def test_failed_rule_create_sends_no_push(self):
        _, _, foreign_child = self.make_family("Other")
        self.as_user(self.parent).post("/api/v1/apps/rules/", {
            "child": foreign_child.id, "package_name": "com.x", "rule_type": "block",
        })
        self.mock_send_data_push_to_family_children.assert_not_called()
