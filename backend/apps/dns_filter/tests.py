from apps.dns_filter.models import DnsCategory, DnsQuery
from apps.dns_filter.serializers import normalize_domain
from apps.tests_base import ApiTestCase


class DnsFilterTests(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.family, self.parent, self.child = self.make_family()

    def test_categories_work_for_more_than_one_family(self):
        _, other_parent, _ = self.make_family("Other")
        self.assertEqual(self.as_user(self.parent).get("/api/v1/dns/categories/").status_code, 200)
        self.assertEqual(self.as_user(other_parent).get("/api/v1/dns/categories/").status_code, 200)
        self.assertEqual(DnsCategory.objects.filter(name="adult").count(), 2)

    def test_toggle_category_feeds_blocklist(self):
        cats = self.as_user(self.parent).get("/api/v1/dns/categories/").data
        cats = cats["results"] if isinstance(cats, dict) else cats
        gambling = next(c for c in cats if c["name"] == "gambling")
        res = self.client.patch(f"/api/v1/dns/categories/{gambling['id']}/toggle/")
        self.assertTrue(res.data["is_enabled"])
        domains = self.as_user(self.child).get("/api/v1/dns/blocklist/").data["domains"]
        self.assertIn("bet365.com", domains)

    def test_normalize_domain(self):
        self.assertEqual(normalize_domain("wikipedia.org"), "wikipedia.org")
        self.assertEqual(normalize_domain("web.com"), "web.com")
        self.assertEqual(normalize_domain("https://WWW.Example.com/path?q=1"), "example.com")
        self.assertEqual(normalize_domain("www.example.com:443/"), "example.com")

    def test_add_blocked_domain_keeps_letters_and_rejects_duplicates(self):
        client = self.as_user(self.parent)
        res = client.post("/api/v1/dns/blocked/", {"domain": "wikipedia.org"})
        self.assertEqual(res.status_code, 201, res.data)
        self.assertEqual(res.data["domain"], "wikipedia.org")
        res = client.post("/api/v1/dns/blocked/", {"domain": "https://www.wikipedia.org/"})
        self.assertEqual(res.status_code, 400)
        res = client.post("/api/v1/dns/blocked/", {"domain": "not a domain"})
        self.assertEqual(res.status_code, 400)

    def test_parent_without_family_gets_403_not_500(self):
        lonely = self.make_user("lonely@x.uz")
        client = self.as_user(lonely)
        self.assertEqual(client.post("/api/v1/dns/blocked/", {"domain": "a.com"}).status_code, 403)
        self.assertEqual(client.get("/api/v1/dns/categories/").status_code, 403)

    def test_query_batch_upload(self):
        res = self.as_user(self.child).post("/api/v1/dns/queries/batch/", {"queries": [
            {"domain": "A.com.", "query_type": "A", "was_blocked": False,
             "timestamp": "2026-10-07T10:00:00Z"},
            {"domain": "b.com", "was_blocked": True, "timestamp": "garbage"},
            {"query_type": "A"},  # no domain -> skipped
        ]}, format="json")
        self.assertEqual(res.status_code, 201)
        self.assertEqual(res.data["created"], 2)
        self.assertEqual(
            sorted(DnsQuery.objects.values_list("domain", flat=True)), ["a.com", "b.com"]
        )
