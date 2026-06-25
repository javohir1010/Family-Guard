from rest_framework import generics, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from django.contrib.auth import get_user_model
from django.utils import timezone
from drf_spectacular.utils import extend_schema, OpenApiParameter
from .models import BlockedDomain, DnsQuery, DnsCategory
from .serializers import (
    BlockedDomainSerializer,
    DnsQuerySerializer,
    DnsQueryBatchSerializer,
    DnsCategorySerializer,
)
from apps.users.permissions import IsParent, IsChild, IsFamilyMember
import datetime

User = get_user_model()

# Built-in blocklist categories (simplified; in production use Pi-hole lists)
BUILTIN_CATEGORIES = {
    "adult": ["pornhub.com", "xvideos.com", "xhamster.com", "onlyfans.com"],
    "gambling": ["bet365.com", "pokerstars.com", "888casino.com", "1xbet.com"],
    "violence": ["bestgore.com", "liveleak.com"],
    "social_media": ["tiktok.com", "instagram.com", "facebook.com", "twitter.com"],
}


class BlockedDomainListView(generics.ListCreateAPIView):
    """List blocked domains / add a new one. Parents only."""
    serializer_class = BlockedDomainSerializer
    permission_classes = [IsParent]

    @extend_schema(tags=["dns"])
    def get_queryset(self):
        return BlockedDomain.objects.filter(family=self.request.user.family)


class BlockedDomainDetailView(generics.RetrieveDestroyAPIView):
    """Get or delete a blocked domain. Parents only."""
    serializer_class = BlockedDomainSerializer
    permission_classes = [IsParent]

    @extend_schema(tags=["dns"])
    def get_queryset(self):
        return BlockedDomain.objects.filter(family=self.request.user.family)


@extend_schema(tags=["dns"])
@api_view(["GET"])
def get_blocklist(request):
    """
    GET /api/v1/dns/blocklist/
    Returns compact domain list for Android VPN service.
    Includes manually blocked + enabled categories.
    """
    if not request.user.family:
        return Response({"domains": []})

    domains = set(
        BlockedDomain.objects.filter(family=request.user.family)
        .values_list("domain", flat=True)
    )

    # Add enabled category domains
    enabled_categories = DnsCategory.objects.filter(
        family=request.user.family, is_enabled=True
    ).values_list("name", flat=True)

    for cat in enabled_categories:
        domains.update(BUILTIN_CATEGORIES.get(cat, []))

    return Response(
        {
            "domains": sorted(domains),
            "count": len(domains),
            "updated_at": timezone.now().isoformat(),
        }
    )


@extend_schema(tags=["dns"], request=DnsQueryBatchSerializer)
@api_view(["POST"])
def upload_dns_queries(request):
    """
    POST /api/v1/dns/queries/batch/
    Child device uploads batched DNS query logs.
    """
    serializer = DnsQueryBatchSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    queries_data = serializer.validated_data["queries"]
    child = request.user

    bulk_objects = []
    for q in queries_data:
        ts = q.get("timestamp")
        if isinstance(ts, str):
            try:
                ts = datetime.datetime.fromisoformat(ts)
            except ValueError:
                ts = timezone.now()
        elif not ts:
            ts = timezone.now()

        bulk_objects.append(
            DnsQuery(
                child=child,
                domain=q["domain"],
                query_type=q["query_type"],
                was_blocked=q["was_blocked"],
                timestamp=ts,
            )
        )

    DnsQuery.objects.bulk_create(bulk_objects, ignore_conflicts=True)
    return Response({"created": len(bulk_objects)}, status=status.HTTP_201_CREATED)


class DnsQueryListView(generics.ListAPIView):
    """GET /api/v1/dns/queries/ — DNS query log. Parents only."""
    serializer_class = DnsQuerySerializer
    filterset_fields = ["was_blocked"]
    search_fields = ["domain"]
    ordering_fields = ["timestamp", "domain"]
    permission_classes = [IsParent]

    @extend_schema(
        tags=["dns"],
        parameters=[
            OpenApiParameter("child_id", str),
            OpenApiParameter("was_blocked", bool),
            OpenApiParameter("search", str, description="Search domain"),
        ],
    )
    def get_queryset(self):
        user = self.request.user
        qs = DnsQuery.objects.filter(child__family=user.family).select_related("child")
        child_id = self.request.query_params.get("child_id")
        if child_id:
            qs = qs.filter(child__id=child_id)
        return qs.order_by("-timestamp")


class DnsCategoryView(generics.ListAPIView):
    """List DNS filter categories. Parents only."""
    serializer_class = DnsCategorySerializer
    permission_classes = [IsParent]

    @extend_schema(tags=["dns"])
    def get_queryset(self):
        family = self.request.user.family
        # Auto-create categories for family if they don't exist
        for cat_name in BUILTIN_CATEGORIES:
            DnsCategory.objects.get_or_create(
                family=family,
                name=cat_name,
                defaults={"description": f"Block {cat_name} content"},
            )
        return DnsCategory.objects.filter(family=family)


@extend_schema(tags=["dns"])
@api_view(["PATCH"])
def toggle_category(request, category_id):
    """Toggle DNS category on/off."""
    if not request.user.is_parent:
        return Response(status=status.HTTP_403_FORBIDDEN)
    try:
        cat = DnsCategory.objects.get(id=category_id, family=request.user.family)
    except DnsCategory.DoesNotExist:
        return Response(status=status.HTTP_404_NOT_FOUND)
    cat.is_enabled = not cat.is_enabled
    cat.save(update_fields=["is_enabled"])
    return Response(DnsCategorySerializer(cat).data)
