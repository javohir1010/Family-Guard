import json
from channels.generic.websocket import AsyncWebsocketConsumer
from channels.db import database_sync_to_async
from django.contrib.auth.models import AnonymousUser
from django.utils import timezone


class FamilyMapConsumer(AsyncWebsocketConsumer):
    """
    WebSocket consumer for real-time family member locations.
    Connect: ws://<host>/ws/family/<family_id>/map/?token=<jwt>
    """

    async def connect(self):
        user = self.scope.get("user")
        if not user or isinstance(user, AnonymousUser):
            await self.close(code=4001)
            return

        family = await self.get_family(user)
        if not family:
            await self.close(code=4003)
            return

        family_id = str(family.id)
        if str(self.scope["url_route"]["kwargs"]["family_id"]) != family_id:
            await self.close(code=4004)
            return

        self.group_name = f"family_map_{family_id}"
        self.user = user

        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.accept()

        # Send current positions of all family members on connect
        positions = await self.get_current_positions(family)
        await self.send(text_data=json.dumps({"type": "initial_positions", "data": positions}))

    async def disconnect(self, close_code):
        if hasattr(self, "group_name"):
            await self.channel_layer.group_discard(self.group_name, self.channel_name)
            # Mark user offline
            if hasattr(self, "user"):
                await self.set_user_offline(self.user)

    async def receive(self, text_data):
        """Receive ping from client to keep connection alive."""
        try:
            data = json.loads(text_data)
            if data.get("type") == "ping":
                await self.send(text_data=json.dumps({"type": "pong"}))
        except json.JSONDecodeError:
            pass

    async def location_update(self, event):
        """Broadcast location update to all connected clients in the group."""
        await self.send(
            text_data=json.dumps(
                {
                    "type": "location_update",
                    "data": event["data"],
                }
            )
        )

    async def sos_alert(self, event):
        """Broadcast SOS alert to all connected clients."""
        await self.send(
            text_data=json.dumps(
                {
                    "type": "sos_alert",
                    "data": event["data"],
                }
            )
        )

    @database_sync_to_async
    def get_family(self, user):
        try:
            return user.family
        except Exception:
            return None

    @database_sync_to_async
    def get_current_positions(self, family):
        from .models import LocationRecord
        from django.contrib.auth import get_user_model

        User = get_user_model()
        members = User.objects.filter(family=family)
        positions = []
        for member in members:
            last = (
                LocationRecord.objects.filter(user=member)
                .order_by("-recorded_at")
                .first()
            )
            if last:
                positions.append(
                    {
                        "user_id": str(member.id),
                        "name": member.get_full_name() or member.email,
                        "role": member.role,
                        "latitude": float(last.latitude),
                        "longitude": float(last.longitude),
                        "accuracy": last.accuracy,
                        "battery_level": last.battery_level,
                        "recorded_at": last.recorded_at.isoformat(),
                        "is_online": member.is_online,
                    }
                )
        return positions

    @database_sync_to_async
    def set_user_offline(self, user):
        from django.contrib.auth import get_user_model

        User = get_user_model()
        User.objects.filter(id=user.id).update(
            is_online=False, last_seen=timezone.now()
        )
