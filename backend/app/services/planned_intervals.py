"""Exact plan intervals, with wall-clock grid plans retained for ordinary Day planning.

An exact plan is one record even across midnight. Day positions are projections;
metadata edits never replace its instants with rounded display minutes.
"""
import datetime as dt

from sqlalchemy import select
from sqlalchemy.orm import joinedload, object_session

from app.core.time import get_zone
from app.models.activity import ActivityState
from app.models.day import Day
from app.models.time_block import BlockLane, TimeBlock


def utc(value):
    return value.replace(tzinfo=dt.UTC) if value.tzinfo is None else value.astimezone(dt.UTC)


def zone_name(db, fallback=None):
    from app.core.config import get_settings
    state = db.get(ActivityState, 1)
    return (state.reporting_timezone if state else None) or fallback or get_settings().app_timezone


def interval(row, timezone):
    if row.start_at is not None:
        return utc(row.start_at), utc(row.end_at)
    midnight = dt.datetime.combine(row.day.date, dt.time(), get_zone(timezone))
    return (midnight + dt.timedelta(minutes=row.start_minute)).astimezone(dt.UTC), (midnight + dt.timedelta(minutes=row.end_minute)).astimezone(dt.UTC)


def bounds(date, timezone):
    zone = get_zone(timezone)
    return dt.datetime.combine(date, dt.time(), zone).astimezone(dt.UTC), dt.datetime.combine(date + dt.timedelta(days=1), dt.time(), zone).astimezone(dt.UTC)


def rows(db):
    return list(db.scalars(select(TimeBlock).options(joinedload(TimeBlock.day)).where(TimeBlock.lane == BlockLane.planned).order_by(TimeBlock.id)))


def on_day(db, date, timezone):
    a, b = bounds(date, timezone)
    return sorted((p for p in rows(db) if interval(p, timezone)[0] < b and interval(p, timezone)[1] > a), key=lambda p: (interval(p, timezone)[0], p.id))


def positions(row, date, timezone):
    a, b = bounds(date, timezone)
    start, end = interval(row, timezone)
    left, right = max(a, start), min(b, end)
    zone = get_zone(timezone)
    def minute(value):
        local = value.astimezone(zone)
        return local.hour * 60 + local.minute + (local.second + local.microsecond / 1e6) / 60
    x = 0.0 if left == a else minute(left)
    y = 1440.0 if right == b else minute(right)
    # A fall-back transition can repeat wall time. Keep the elapsed duration
    # authoritative even where the ordinary wall-clock timeline cannot expand it.
    return x, max(x, y), max(0, (right - left).total_seconds() / 60)


def project(row, date, timezone):
    from app.schemas.time_block import TimeBlockRead
    x, y, minutes = positions(row, date, timezone)
    start, end = interval(row, timezone)
    return TimeBlockRead.model_validate(row).model_copy(update={
        "start_minute": int(x), "end_minute": min(1440, int(y)),
        "start_position": x, "end_position": y, "duration_minutes": (end - start).total_seconds() / 60,
        "minutes_in_day": minutes,
        "start_at": start, "end_at": end,
    })


def set_interval(db, row, start, end, timezone):
    start, end = utc(start), utc(end)
    if end <= start:
        raise ValueError("Planned Block end must be after its start")
    zone = get_zone(timezone)
    first, last = start.astimezone(zone).date(), (end - dt.timedelta(microseconds=1)).astimezone(zone).date()
    day = db.scalar(select(Day).where(Day.date == first))
    if day is None:
        day = Day(date=first)
        db.add(day)
        db.flush()
    row.day = day
    row.start_at, row.end_at = start, end
    local = start.astimezone(zone)
    row.start_minute = local.hour * 60 + local.minute
    local_end = end.astimezone(zone)
    row.end_minute = 1440 if local_end.date() != first else local_end.hour * 60 + local_end.minute
    db.add(row)
    # Ensure Chronicle can discover every date occupied by this one plan.
    date = first
    while date <= last:
        if db.scalar(select(Day.id).where(Day.date == date)) is None:
            db.add(Day(date=date))
        date += dt.timedelta(days=1)
    db.flush()


def assert_no_overlap(db, start, end, timezone, exclude_ids=()):
    for other in rows(db):
        a, b = interval(other, timezone)
        if other.id not in exclude_ids and a < end and b > start:
            raise ValueError("Block overlaps another block in the same lane")


def grid_overlap(day, start, end, exclude_ids):
    db = object_session(day)
    if db is None:
        return False
    timezone = zone_name(db)
    midnight = dt.datetime.combine(day.date, dt.time(), get_zone(timezone))
    assert_no_overlap(db, (midnight + dt.timedelta(minutes=start)).astimezone(dt.UTC),
                      (midnight + dt.timedelta(minutes=end)).astimezone(dt.UTC), timezone, exclude_ids)
    return True
