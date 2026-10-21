from django.test import override_settings

from apps.notifications.models import Notification
from apps.notifications.tasks import send_push_notification
from apps.tests_base import ApiTestCase


@override_settings(FIREBASE_CREDENTIALS_FILE="")
class NotificationTests(ApiTestCase):
    def test_notification_is_stored_without_fcm_or_device_token(self):
        _, parent, _ = self.make_family()
        send_push_notification.run(str(parent.id), "Title", "Body", {"type": "geofence"})
        notification = Notification.objects.get(recipient=parent)
        self.assertEqual(notification.notification_type, "geofence")

        res = self.as_user(parent).get("/api/v1/notifications/")
        self.assertEqual(res.data["count"], 1)
        self.client.post("/api/v1/notifications/mark-all-read/")
        notification.refresh_from_db()
        self.assertTrue(notification.is_read)

    def test_unknown_type_falls_back_to_system(self):
        _, parent, _ = self.make_family()
        send_push_notification.run(str(parent.id), "T", "B", {"type": "weekly_report"})
        self.assertEqual(Notification.objects.get().notification_type, "system")


class SosFlowTests(ApiTestCase):
    def test_trigger_and_resolve(self):
        _, parent, child = self.make_family()
        res = self.as_user(child).post("/api/v1/sos/trigger/", {"press_count": 8})
        self.assertEqual(res.status_code, 201)
        self.assertEqual(res.data["sos_type"], "emergency")
        self.mock_process_sos_event.assert_called_once()

        sos_id = res.data["sos_id"]
        self.assertEqual(self.client.post(f"/api/v1/sos/{sos_id}/resolve/").status_code, 403)
        res = self.as_user(parent).post(f"/api/v1/sos/{sos_id}/resolve/")
        self.assertEqual(res.data["status"], "resolved")
