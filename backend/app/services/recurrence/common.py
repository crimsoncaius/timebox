from __future__ import annotations

import calendar
import datetime as dt
import json
from dataclasses import dataclass

from app.core.time import get_zone

LEAD_DAYS = 7
INHERITED_FIELDS = {"title", "description", "project_id", "task_type_id", "urgency", "importance"}
CUSTOMIZABLE_FIELDS = INHERITED_FIELDS | {
    "deadline_date", "deadline_at", "reminder_at", "ready_to_plan",
}
SCHEDULE_FIELDS = {
    "frequency", "interval", "weekdays", "month_day", "quota_count",
    "start_date", "end_date", "cycle_limit", "checklist_titles",
}


@dataclass(frozen=True)
class Window:
    key: str
    start: dt.date
    end: dt.date


def _utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _date_in_tz(value: dt.datetime, tz_name: str) -> dt.date:
    if value.tzinfo is None:
        value = value.replace(tzinfo=dt.timezone.utc)
    return value.astimezone(get_zone(tz_name)).date()


def _json_list(value: str) -> list:
    try:
        result = json.loads(value)
        return result if isinstance(result, list) else []
    except (TypeError, json.JSONDecodeError):
        return []


def _week_boundary(value: dt.date, week_start: str) -> dt.date:
    first = 6 if week_start == "sunday" else 0
    return value - dt.timedelta(days=(value.weekday() - first) % 7)


def _month_date(year: int, month: int, day: int) -> dt.date:
    return dt.date(year, month, min(day, calendar.monthrange(year, month)[1]))


def _rule_value(rule, name: str):
    if name == "start_date" and hasattr(rule, "generation_start_date"):
        # Persisted series keep their historical start for provenance while the
        # generation anchor advances when cadence is edited.
        return rule.generation_start_date
    if name == "weekdays":
        raw = getattr(rule, "weekdays", None)
        if raw is not None:
            return sorted(set(raw))
        return sorted(set(_json_list(rule.weekdays_json)))
    return getattr(rule, name)
