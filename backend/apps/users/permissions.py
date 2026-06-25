from rest_framework.permissions import BasePermission


class IsParent(BasePermission):
    """Allow access only to users with role='parent'."""
    message = "Only parents can perform this action."

    def has_permission(self, request, view):
        return bool(
            request.user
            and request.user.is_authenticated
            and request.user.role == "parent"
        )


class IsChild(BasePermission):
    """Allow access only to users with role='child'."""
    message = "Only child devices can perform this action."

    def has_permission(self, request, view):
        return bool(
            request.user
            and request.user.is_authenticated
            and request.user.role == "child"
        )


class IsParentOrReadOnly(BasePermission):
    """Parents can write, all family members can read."""
    message = "Only parents can modify this resource."

    def has_permission(self, request, view):
        if not request.user or not request.user.is_authenticated:
            return False
        if request.method in ("GET", "HEAD", "OPTIONS"):
            return True
        return request.user.role == "parent"


class IsFamilyMember(BasePermission):
    """Allow access only if user belongs to a family."""
    message = "You must belong to a family to access this resource."

    def has_permission(self, request, view):
        return bool(
            request.user
            and request.user.is_authenticated
            and request.user.family is not None
        )


class IsParentOfChild(BasePermission):
    """Allow only if the requesting parent and the target child share the same family."""
    message = "You can only manage your own family members."

    def has_object_permission(self, request, view, obj):
        if not request.user.is_authenticated:
            return False
        # obj is expected to have a .child or .user FK
        child = getattr(obj, "child", None) or getattr(obj, "user", None)
        if not child:
            return True
        return (
            request.user.role == "parent"
            and child.family_id == request.user.family_id
        )
