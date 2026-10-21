from apps.sos.models import SosEvent
from apps.sos.tasks import process_sos_event
from apps.tests_base import ApiTestCase


class SosTests(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.family, self.parent, self.child = self.make_family()

    def trigger(self, press_count, **extra):
        return self.as_user(self.child).post("/api/v1/sos/trigger/", {
            "press_count": press_count, **extra,
        }, format="json")

    def test_trigger_creates_event_and_queues_task(self):
        res = self.trigger(3, latitude="41.3000000", longitude="69.2000000")
        self.assertEqual(res.status_code, 201, res.data)
        self.assertEqual(res.data["sos_type"], SosEvent.SosType.ALERT)
        event = SosEvent.objects.get(id=res.data["sos_id"])
        self.assertEqual(event.child, self.child)
        self.mock_process_sos_event.assert_called_once()
        self.assertEqual(self.mock_process_sos_event.call_args.kwargs["args"], [str(event.id)])

    def test_eight_presses_is_emergency(self):
        res = self.trigger(8)
        self.assertEqual(res.data["sos_type"], SosEvent.SosType.EMERGENCY)
        self.assertEqual(self.mock_process_sos_event.call_args.kwargs["priority"], 10)

    def test_trigger_rejects_zero_presses(self):
        self.assertEqual(self.trigger(0).status_code, 400)
        self.assertFalse(SosEvent.objects.exists())

    def test_history_is_limited_to_own_family(self):
        _, foreign_parent, foreign_child = self.make_family("Other")
        SosEvent.objects.create(child=self.child, press_count=3)
        SosEvent.objects.create(child=foreign_child, press_count=3)

        res = self.as_user(self.parent).get("/api/v1/sos/history/")
        self.assertEqual(res.status_code, 200)
        self.assertEqual([e["child"] for e in res.data["results"]], [self.child.id])

    def test_resolve_by_parent_only(self):
        event = SosEvent.objects.create(child=self.child, press_count=3)
        url = f"/api/v1/sos/{event.id}/resolve/"

        self.assertEqual(self.as_user(self.child).post(url).status_code, 403)
        res = self.as_user(self.parent).post(url)
        self.assertEqual(res.status_code, 200, res.data)
        event.refresh_from_db()
        self.assertEqual(event.status, SosEvent.SosStatus.RESOLVED)
        self.assertEqual(event.resolved_by, self.parent)

    def test_foreign_parent_cannot_resolve(self):
        _, foreign_parent, _ = self.make_family("Other")
        event = SosEvent.objects.create(child=self.child, press_count=3)
        res = self.as_user(foreign_parent).post(f"/api/v1/sos/{event.id}/resolve/")
        self.assertEqual(res.status_code, 404)

    def test_parent_without_family_cannot_resolve_familyless_sos(self):
        # family=None must not match SOS events of children who have no family
        loner_parent = self.make_user("loner@x.uz", "parent")
        loner_child = self.make_user("loner-kid@x.uz", "child")
        event = SosEvent.objects.create(child=loner_child, press_count=3)
        res = self.as_user(loner_parent).post(f"/api/v1/sos/{event.id}/resolve/")
        self.assertEqual(res.status_code, 403)
        event.refresh_from_db()
        self.assertNotEqual(event.status, SosEvent.SosStatus.RESOLVED)


class SosTaskTests(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.family, self.parent, self.child = self.make_family()

    def test_alert_notifies_parents_without_sms(self):
        event = SosEvent.objects.create(child=self.child, press_count=3,
                                        sos_type=SosEvent.SosType.ALERT)
        process_sos_event(str(event.id))

        self.mock_send_push_to_family_parents.assert_called_once()
        kwargs = self.mock_send_push_to_family_parents.call_args.kwargs
        self.assertEqual(kwargs["family_id"], str(self.family.id))
        self.assertEqual(kwargs["exclude_user_id"], str(self.child.id))
        self.mock_send_emergency_sms.assert_not_called()
        event.refresh_from_db()
        self.assertEqual(event.status, SosEvent.SosStatus.NOTIFIED)

    def test_emergency_also_sends_sms(self):
        event = SosEvent.objects.create(child=self.child, press_count=8,
                                        sos_type=SosEvent.SosType.EMERGENCY)
        process_sos_event(str(event.id))

        self.mock_send_emergency_sms.assert_called_once()
        event.refresh_from_db()
        self.assertEqual(event.status, SosEvent.SosStatus.EMERGENCY_SENT)

    def test_child_without_family_is_skipped(self):
        loner = self.make_user("loner-kid@x.uz", "child")
        event = SosEvent.objects.create(child=loner, press_count=3)
        process_sos_event(str(event.id))
        self.mock_send_push_to_family_parents.assert_not_called()
