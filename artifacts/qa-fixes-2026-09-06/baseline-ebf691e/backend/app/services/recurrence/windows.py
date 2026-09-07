from __future__ import annotations

import datetime as dt

from dateutil.relativedelta import relativedelta

from app.models.battle_plan import RecurrenceFrequency, RecurrenceMode
from app.services.recurrence.common import Window, _month_date, _rule_value, _week_boundary


def iter_windows(rule, through: dt.date, week_start: str = "monday") -> list[Window]:
    """Return all cycles from the rule start through `through`, with inclusive endings."""
    start = _rule_value(rule, "start_date")
    end_date = _rule_value(rule, "end_date")
    limit = _rule_value(rule, "cycle_limit")
    frequency = _rule_value(rule, "frequency")
    mode = _rule_value(rule, "mode")
    interval = _rule_value(rule, "interval")
    frequency = RecurrenceFrequency(frequency)
    mode = RecurrenceMode(mode)
    windows: list[Window] = []

    def add(value: Window) -> bool:
        if value.start > through or (end_date is not None and value.start > end_date):
            return False
        if limit is not None and len(windows) >= limit:
            return False
        windows.append(value)
        return True

    if mode == RecurrenceMode.scheduled:
        if frequency == RecurrenceFrequency.daily:
            value = start
            while value <= through:
                if not add(Window(f"scheduled:{value.isoformat()}", value, value)):
                    break
                value += dt.timedelta(days=interval)
        elif frequency == RecurrenceFrequency.weekly:
            weekdays = _rule_value(rule, "weekdays")
            anchor = start - dt.timedelta(days=start.weekday())
            week = anchor
            while week <= through:
                for weekday in weekdays:
                    value = week + dt.timedelta(days=weekday)
                    if value < start:
                        continue
                    if value > through:
                        return windows
                    if not add(Window(f"scheduled:{value.isoformat()}", value, value)):
                        return windows
                week += dt.timedelta(weeks=interval)
        else:
            month_day = _rule_value(rule, "month_day")
            cursor = dt.date(start.year, start.month, 1)
            while cursor <= through:
                value = _month_date(cursor.year, cursor.month, month_day)
                if value >= start:
                    if value > through or not add(Window(f"scheduled:{value.isoformat()}", value, value)):
                        break
                cursor += relativedelta(months=interval)
        return windows

    if frequency == RecurrenceFrequency.daily:
        cursor = start
        while cursor <= through:
            if not add(Window(f"quota:{cursor.isoformat()}", cursor, cursor)):
                break
            cursor += dt.timedelta(days=1)
    elif frequency == RecurrenceFrequency.weekly:
        cursor = _week_boundary(start, week_start)
        while cursor <= through:
            period_end = cursor + dt.timedelta(days=6)
            effective_start = max(cursor, start)
            if not add(Window(f"quota:{cursor.isoformat()}", effective_start, period_end)):
                break
            cursor += dt.timedelta(days=7)
    else:
        cursor = dt.date(start.year, start.month, 1)
        while cursor <= through:
            period_end = _month_date(cursor.year, cursor.month, 31)
            effective_start = max(cursor, start)
            if not add(Window(f"quota:{cursor.isoformat()}", effective_start, period_end)):
                break
            cursor += relativedelta(months=1)
    return windows


def _windows_for_preview(rule, today: dt.date, week_start: str) -> tuple[list[Window], list[Window]]:
    horizon = today + relativedelta(years=20)
    all_windows = iter_windows(rule, horizon, week_start)
    past = [window for window in all_windows if window.end < today]
    upcoming = [window for window in all_windows if window.end >= today][:5]
    return past, upcoming

