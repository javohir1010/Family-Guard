from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as BaseUserAdmin
from .models import User, Family
from .invite_model import InviteCode


@admin.register(Family)
class FamilyAdmin(admin.ModelAdmin):
    list_display = ("id", "name", "created_at")
    search_fields = ("name",)


@admin.register(User)
class UserAdmin(BaseUserAdmin):
    fieldsets = BaseUserAdmin.fieldsets + (
        (
            "FamilyGuard",
            {
                "fields": (
                    "role",
                    "family",
                    "phone_number",
                    "device_token",
                    "device_name",
                    "is_online",
                    "last_seen",
                )
            },
        ),
    )
    list_display = ("email", "get_full_name", "role", "family", "is_online", "created_at")
    list_filter = ("role", "is_online", "family")
    search_fields = ("email", "first_name", "last_name")
    ordering = ("-created_at",)


@admin.register(InviteCode)
class InviteCodeAdmin(admin.ModelAdmin):
    list_display = ("code", "family", "created_by", "is_used", "expires_at")
    list_filter = ("is_used",)
    readonly_fields = ("code", "created_at")
