import os
from django.core.asgi import get_asgi_application
from channels.routing import ProtocolTypeRouter, URLRouter
from channels.security.websocket import AllowedHostsOriginValidator

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

django_asgi_app = get_asgi_application()

from config.middleware import JwtAuthMiddlewareStack
from apps.location import routing as location_routing
from apps.sos import routing as sos_routing

application = ProtocolTypeRouter(
    {
        "http": django_asgi_app,
        "websocket": AllowedHostsOriginValidator(
            JwtAuthMiddlewareStack(
                URLRouter(
                    location_routing.websocket_urlpatterns
                    + sos_routing.websocket_urlpatterns
                )
            )
        ),
    }
)
