from django.urls import re_path
from . import consumers

websocket_urlpatterns = [
    re_path(
        r"ws/family/(?P<family_id>[0-9a-f-]+)/map/$",
        consumers.FamilyMapConsumer.as_asgi(),
    ),
]
