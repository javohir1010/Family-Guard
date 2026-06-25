from django.urls import path
from . import views

urlpatterns = [
    path("blocklist/", views.get_blocklist, name="dns-blocklist"),
    path("blocked/", views.BlockedDomainListView.as_view(), name="dns-blocked-list"),
    path("blocked/<uuid:pk>/", views.BlockedDomainDetailView.as_view(), name="dns-blocked-detail"),
    path("queries/", views.DnsQueryListView.as_view(), name="dns-query-list"),
    path("queries/batch/", views.upload_dns_queries, name="dns-query-batch"),
    path("categories/", views.DnsCategoryView.as_view(), name="dns-categories"),
    path("categories/<int:category_id>/toggle/", views.toggle_category, name="dns-category-toggle"),
]
