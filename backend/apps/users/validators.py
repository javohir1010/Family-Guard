import re

from django.core.exceptions import ValidationError


class LettersAndDigitsValidator:
    """Password must mix letters and digits (Django's built-ins don't check this)."""

    def validate(self, password, user=None):
        if not re.search(r"[A-Za-zА-Яа-яЁё]", password) or not re.search(r"\d", password):
            raise ValidationError(
                "Пароль должен содержать хотя бы одну букву и одну цифру.",
                code="password_no_letters_digits",
            )

    def get_help_text(self):
        return "Пароль должен содержать буквы и цифры."
