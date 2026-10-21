import json
from unittest import mock

from asgiref.sync import async_to_sync, sync_to_async
from channels.layers import get_channel_layer
from channels.testing import WebsocketCommunicator
from django.utils import timezone
from rest_framework_simplejwt.tokens import AccessToken

from apps.location.models import LocationRecord
from apps.tests_base import ApiTestCase
from config.asgi import application


class FamilyMapWebSocketTests(ApiTestCase):
    """Full ASGI stack: origin check → JWT middleware → FamilyMapConsumer."""

    def setUp(self):
        super().setUp()
        self.family, self.parent, self.child = self.make_family()
        # database_sync_to_async closes "old" connections, which would close
        # the test transaction's connection
        patcher = mock.patch("channels.db.close_old_connections")
        patcher.start()
        self.addCleanup(patcher.stop)

    def connect(self, family_id, token=None):
        path = f"/ws/family/{family_id}/map/"
        if token is not None:
            path += f"?token={token}"
        return WebsocketCommunicator(
            application, path, headers=[(b"origin", b"http://localhost")]
        )

    def token(self, user):
        return str(AccessToken.for_user(user))

    def assert_rejected(self, family_id, token, code):
        async def run():
            communicator = self.connect(family_id, token)
            connected, close_code = await communicator.connect()
            self.assertFalse(connected)
            self.assertEqual(close_code, code)
        async_to_sync(run)()

    def test_missing_or_bad_token_is_rejected(self):
        self.assert_rejected(self.family.id, None, 4001)
        self.assert_rejected(self.family.id, "not-a-jwt", 4001)

    def test_user_without_family_is_rejected(self):
        loner = self.make_user("loner@x.uz", "parent")
        self.assert_rejected(self.family.id, self.token(loner), 4003)

    def test_other_familys_map_is_rejected(self):
        _, other_parent, _ = self.make_family("Other")
        self.assert_rejected(self.family.id, self.token(other_parent), 4004)

    def test_initial_positions_ping_and_broadcast(self):
        LocationRecord.objects.create(user=self.child, latitude="41.3", longitude="69.2",
                                      recorded_at=timezone.now())

        async def run():
            communicator = self.connect(self.family.id, self.token(self.parent))
            connected, _ = await communicator.connect()
            self.assertTrue(connected)

            initial = await communicator.receive_json_from()
            self.assertEqual(initial["type"], "initial_positions")
            self.assertEqual([p["user_id"] for p in initial["data"]], [str(self.child.id)])

            await communicator.send_to(text_data=json.dumps({"type": "ping"}))
            self.assertEqual(await communicator.receive_json_from(), {"type": "pong"})

            await get_channel_layer().group_send(
                f"family_map_{self.family.id}",
                {"type": "sos_alert", "data": {"sos_id": "x"}},
            )
            self.assertEqual(
                await communicator.receive_json_from(),
                {"type": "sos_alert", "data": {"sos_id": "x"}},
            )
            await communicator.disconnect()

        async_to_sync(run)()

    def test_location_post_reaches_connected_parent(self):
        async def run():
            communicator = self.connect(self.family.id, self.token(self.parent))
            connected, _ = await communicator.connect()
            self.assertTrue(connected)
            await communicator.receive_json_from()  # initial_positions

            res = await sync_to_async(
                lambda: self.as_user(self.child).post("/api/v1/location/", {
                    "latitude": "41.3", "longitude": "69.2",
                    "recorded_at": timezone.now().isoformat(),
                }, format="json")
            )()
            self.assertEqual(res.status_code, 201, res.data)

            update = await communicator.receive_json_from()
            self.assertEqual(update["type"], "location_update")
            self.assertEqual(update["data"]["user_id"], str(self.child.id))
            await communicator.disconnect()

        async_to_sync(run)()
