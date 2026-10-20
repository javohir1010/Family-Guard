from apps.location.models import GeoZone, GeoZoneEvent, LocationRecord
from apps.tests_base import ApiTestCase

SCHOOL = (41.3000, 69.2000)
FAR_AWAY = (41.4000, 69.2000)  # ~11 km north


class LocationAndGeofenceTests(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.family, self.parent, self.child = self.make_family()

    def post_location(self, lat, lon):
        return self.as_user(self.child).post("/api/v1/location/", {
            "latitude": lat, "longitude": lon, "accuracy": 5,
            "recorded_at": "2026-10-07T10:00:00Z",
        })

    def make_zone(self, **kwargs):
        return GeoZone.objects.create(
            family=self.family, name="School",
            center_latitude=SCHOOL[0], center_longitude=SCHOOL[1], radius_meters=500,
            **kwargs,
        )

    def test_post_location_with_geozone_records_enter_and_exit(self):
        self.make_zone()
        self.assertEqual(self.post_location(*SCHOOL).status_code, 201)
        self.assertEqual(self.post_location(*FAR_AWAY).status_code, 201)
        events = list(GeoZoneEvent.objects.order_by("timestamp").values_list("event_type", flat=True))
        self.assertEqual(events, ["enter", "exit"])
        self.assertEqual(self.mock_send_push_to_family_parents.call_count, 2)

    def test_exit_detected_even_when_enter_notifications_disabled(self):
        self.make_zone(notify_on_enter=False)
        self.post_location(*SCHOOL)
        self.post_location(*FAR_AWAY)
        events = list(GeoZoneEvent.objects.order_by("timestamp").values_list("event_type", flat=True))
        self.assertEqual(events, ["enter", "exit"])
        # Only the exit is notified
        self.assertEqual(self.mock_send_push_to_family_parents.call_count, 1)

    def test_history_does_not_leak_to_users_without_family(self):
        stranger_child = self.make_user("stranger@x.uz", role="child")
        LocationRecord.objects.create(
            user=stranger_child, latitude=1, longitude=1, recorded_at="2026-10-07T10:00:00Z"
        )
        lonely_parent = self.make_user("lonely@x.uz")
        res = self.as_user(lonely_parent).get(f"/api/v1/location/history/{stranger_child.id}/")
        self.assertEqual(res.status_code, 403)

    def test_history_rejects_bad_dates(self):
        res = self.as_user(self.parent).get(
            f"/api/v1/location/history/{self.child.id}/", {"from_date": "not-a-date"}
        )
        self.assertEqual(res.status_code, 400)
        res = self.client.get(
            f"/api/v1/location/history/{self.child.id}/", {"from_date": "2026-10-01"}
        )
        self.assertEqual(res.status_code, 200)

    def test_child_cannot_create_zones(self):
        res = self.as_user(self.child).post("/api/v1/location/zones/", {
            "name": "Mine", "center_latitude": 1, "center_longitude": 1,
        })
        self.assertEqual(res.status_code, 403)
        res = self.client.get("/api/v1/location/zones/")
        self.assertEqual(res.status_code, 200)

    def test_parent_without_family_cannot_create_zone(self):
        lonely_parent = self.make_user("lonely@x.uz")
        res = self.as_user(lonely_parent).post("/api/v1/location/zones/", {
            "name": "Z", "center_latitude": 1, "center_longitude": 1,
        })
        self.assertEqual(res.status_code, 403)
