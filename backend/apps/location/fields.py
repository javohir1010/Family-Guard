from decimal import ROUND_HALF_UP, Decimal, InvalidOperation

from rest_framework import serializers

SEVEN_PLACES = Decimal("0.0000001")


class CoordinateField(serializers.DecimalField):
    """
    Latitude/longitude stored as Decimal(10, 7). Phones send doubles with
    10–15 decimals, which plain DecimalField rejects; round to 7 (~1 cm) first.
    """

    def __init__(self, **kwargs):
        super().__init__(max_digits=10, decimal_places=7, **kwargs)

    def to_internal_value(self, data):
        try:
            data = Decimal(str(data).strip()).quantize(SEVEN_PLACES, rounding=ROUND_HALF_UP)
        except (InvalidOperation, ValueError, TypeError):
            pass  # let DecimalField report the error
        return super().to_internal_value(data)
