from __future__ import annotations

import datetime as dt

from app.models.battle_plan import RecurrenceMode
from app.schemas.battle_plan import RecurrencePreviewRead, RecurrencePreviewRequest, RecurrenceWindow
from app.services.recurrence.windows import _windows_for_preview


def preview(rule: RecurrencePreviewRequest, today: dt.date, week_start: str) -> RecurrencePreviewRead:
    past, upcoming = _windows_for_preview(rule, today, week_start)
    tasks_per_cycle = rule.quota_count if rule.mode == RecurrenceMode.quota else 1
    return RecurrencePreviewRead(
        upcoming=[RecurrenceWindow(key=w.key, start=w.start, end=w.end) for w in upcoming],
        past_cycles=len(past),
        past_tasks=len(past) * (tasks_per_cycle or 1),
    )

