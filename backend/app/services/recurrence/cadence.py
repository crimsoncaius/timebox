from __future__ import annotations

from app.models.battle_plan import RecurrenceFrequency, RecurrenceMode, RecurringTemplate
from app.services.recurrence.common import _json_list


def _cadence(row: RecurringTemplate) -> str:
    frequency = row.frequency.value if hasattr(row.frequency, "value") else str(row.frequency)
    if row.mode == RecurrenceMode.quota:
        period = {"daily": "day", "weekly": "week", "monthly": "month"}[frequency]
        return f"{row.quota_count} times per {period}"
    if row.frequency == RecurrenceFrequency.weekly:
        names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        days = ", ".join(names[index] for index in _json_list(row.weekdays_json))
        return f"Every {row.interval} week{'s' if row.interval != 1 else ''} · {days}"
    if row.frequency == RecurrenceFrequency.monthly:
        return f"Every {row.interval} month{'s' if row.interval != 1 else ''} · day {row.month_day}"
    return "Daily" if row.interval == 1 else f"Every {row.interval} days"

