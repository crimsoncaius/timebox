from __future__ import annotations

import datetime as dt
from calendar import monthrange

from dateutil.relativedelta import relativedelta
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.time import today_in_tz
from app.models.battle_plan import (
    RecurrenceMode,
    RecurrenceOccurrence,
    RecurrenceStatus,
    Task,
    TaskStatus,
)
from app.schemas.battle_plan import RoutineCalendarCompletion, RoutineCalendarRead
from app.services.recurrence.common import _date_in_tz, _json_list, _month_date
from app.services.recurrence.helpers import _load_template
from app.services.recurrence.windows import iter_windows


def _last_cycle_date(row) -> dt.date:
    """Bound count-limited schedules without enumerating a potentially huge span."""
    count = row.cycle_limit
    start = row.start_date
    try:
        if row.frequency.value == "daily":
            return start + dt.timedelta(days=(count - 1) * row.interval)
        if row.frequency.value == "weekly":
            weekdays = sorted(_json_list(row.weekdays_json))
            anchor = start - dt.timedelta(days=start.weekday())
            first_week = [day for day in weekdays if day >= start.weekday()]
            if count <= len(first_week):
                return anchor + dt.timedelta(days=first_week[count - 1])
            remaining = count - len(first_week) - 1
            weeks, index = divmod(remaining, len(weekdays))
            return anchor + dt.timedelta(
                weeks=(weeks + 1) * row.interval, days=weekdays[index]
            )
        month = start.replace(day=1)
        if _month_date(month.year, month.month, row.month_day) < start:
            month += relativedelta(months=row.interval)
        month += relativedelta(months=(count - 1) * row.interval)
        return _month_date(month.year, month.month, row.month_day)
    except (OverflowError, ValueError):
        # The schedule extends beyond the representable calendar.
        return dt.date.max


def read_calendar(
    db: Session, template_id: int, month: dt.date | None, settings: Settings
) -> RoutineCalendarRead:
    """Read completion facts and projected dates without materializing future work."""
    row = _load_template(db, template_id)
    today = today_in_tz(settings.app_timezone)
    tasks = db.scalars(
        select(Task).where(
            Task.recurring_template_id == row.id,
            Task.recurrence_kind.in_(["scheduled", "quota_session"]),
            Task.status == TaskStatus.completed,
            Task.completed_at.is_not(None),
            Task.deleted_at.is_(None),
        )
    ).all()
    completed = [
        RoutineCalendarCompletion(
            id=t.id,
            title=t.title,
            date=_date_in_tz(t.completed_at, settings.app_timezone),
        )
        for t in tasks
    ]
    dates = [t.date for t in completed]
    show_upcoming = (
        row.mode == RecurrenceMode.scheduled and row.status == RecurrenceStatus.active
    )
    last = row.end_date
    if show_upcoming and row.cycle_limit:
        last = _last_cycle_date(row)
    first_date = (
        min([row.start_date, *dates]) if show_upcoming else min(dates, default=None)
    )
    last_date = (
        (max([last, *dates]) if last else None)
        if show_upcoming
        else max(dates, default=None)
    )
    minimum = first_date.replace(day=1) if first_date else None
    maximum = last_date.replace(day=1) if last_date else None
    chosen = (month or today).replace(day=1)
    if minimum:
        chosen = max(chosen, minimum)
    if maximum:
        chosen = min(chosen, maximum)
    end = chosen.replace(day=monthrange(chosen.year, chosen.month)[1])
    upcoming = []
    if show_upcoming:
        excluded = set(
            db.scalars(
                select(RecurrenceOccurrence.occurrence_key).where(
                    RecurrenceOccurrence.template_id == row.id,
                    RecurrenceOccurrence.suppressed.is_(True),
                )
            )
        )
        completed_keys = set(
            db.scalars(
                select(RecurrenceOccurrence.occurrence_key)
                .join(Task, Task.id == RecurrenceOccurrence.task_id)
                .where(
                    RecurrenceOccurrence.template_id == row.id,
                    Task.status == TaskStatus.completed,
                )
            )
        )
        upcoming = [
            w.start
            for w in iter_windows(row, end)
            if chosen <= w.start <= end
            and w.start >= today
            and w.start >= row.generation_start_date
            and w.key not in excluded
            and w.key not in completed_keys
        ]
    return RoutineCalendarRead(
        today=today,
        month=chosen,
        first_month=minimum,
        last_month=maximum,
        has_dates=first_date is not None,
        upcoming=upcoming,
        completed=[t for t in completed if chosen <= t.date <= end],
    )
