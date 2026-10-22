"""Contact names as one family member sees the others: "Мама", "Старший брат"…

Computed from role, gender and birth date relative to the viewer. When two
members get the same name they are numbered ("Мама 1", "Мама 2") in the
order they registered. A member's own rename (ContactLabel) wins.
"""
from .models import ContactLabel, User

M, F = User.Gender.MALE, User.Gender.FEMALE


def _base_label(viewer, member):
    """The automatic name, or None when it can't be told (no gender yet)."""
    g = member.gender
    if not g:
        return None
    if viewer.is_child:
        if member.is_parent:
            return {M: "Папа", F: "Мама"}[g]
        if member.is_grandparent:
            return {M: "Дедушка", F: "Бабушка"}[g]
        word = {M: "брат", F: "сестра"}[g]
        if viewer.birth_date and member.birth_date and viewer.birth_date != member.birth_date:
            older = member.birth_date < viewer.birth_date
            prefix = {M: "Старший", F: "Старшая"} if older else {M: "Младший", F: "Младшая"}
            return f"{prefix[g]} {word}"
        return word.capitalize()
    if member.is_child:
        if viewer.is_grandparent:
            return {M: "Внук", F: "Внучка"}[g]
        return {M: "Сын", F: "Дочь"}[g]
    # Adults among themselves: the in-law and spouse cases can't be told apart
    return None


def contact_labels(viewer, members):
    """{member.id: label} for every member except the viewer."""
    others = sorted((m for m in members if m.id != viewer.id), key=lambda m: m.date_joined)
    base = {m.id: _base_label(viewer, m) for m in others}

    labels, counters = {}, {}
    for m in others:
        label = base[m.id]
        if label is None:
            labels[m.id] = m.get_full_name() or m.username
            continue
        if list(base.values()).count(label) > 1:
            counters[label] = counters.get(label, 0) + 1
            label = f"{label} {counters[label]}"
        labels[m.id] = label

    custom = ContactLabel.objects.filter(owner=viewer, target__in=others)
    labels.update({c.target_id: c.label for c in custom})
    return labels
