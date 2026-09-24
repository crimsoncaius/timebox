from __future__ import annotations

import datetime as dt
from collections import defaultdict

from sqlalchemy import or_, select
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import as_utc, get_zone, today_in_tz, utc_now
from app.models.battle_plan import Task, TaskStatus
from app.models.day import Day
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.day import ChronicleDayRead, ChronicleMonthRead
from app.services import actual_block_service


def read_month(db: Session, first: dt.date, settings: Settings) -> ChronicleMonthRead:
    """Return every date with saved activity in one reporting-zone month."""
    today = today_in_tz(settings.app_timezone)
    next_month = (first.replace(day=28) + dt.timedelta(days=4)).replace(day=1)
    last_visible = min(today, next_month - dt.timedelta(days=1))
    result = ChronicleMonthRead(month=first.strftime("%Y-%m"), today=today, days=[])
    if last_visible < first:
        return result

    zone = get_zone(settings.app_timezone)
    start_utc = dt.datetime.combine(first, dt.time.min, tzinfo=zone).astimezone(dt.UTC)
    end_utc = dt.datetime.combine(
        last_visible + dt.timedelta(days=1), dt.time.min, tzinfo=zone
    ).astimezone(dt.UTC)
    now = utc_now()

    counts: dict[dt.date, list[int]] = defaultdict(lambda: [0, 0])
    days = db.scalars(
        select(Day)
        .where(Day.date >= first, Day.date <= last_visible)
        .options(selectinload(Day.time_blocks))
    ).all()
    for day in days:
        for block in day.time_blocks:
            if block.lane == BlockLane.planned:
                counts[day.date][0] += 1
            elif block.start_at is None:  # Legacy grid Actual Block.
                counts[day.date][1] += 1

    candidate_actual_dates: set[dt.date] = set()
    intervals = db.execute(
        select(TimeBlock.start_at, TimeBlock.end_at).where(
            TimeBlock.lane == BlockLane.actual,
            TimeBlock.start_at.is_not(None),
            TimeBlock.start_at < end_utc,
            or_(TimeBlock.end_at.is_(None), TimeBlock.end_at > start_utc),
        )
    ).all()
    for start_at, end_at in intervals:
        end = min(as_utc(end_at) if end_at is not None else now, now, end_utc)
        start = max(as_utc(start_at), start_utc)
        if end <= start:
            continue
        date = start.astimezone(zone).date()
        last = (end - dt.timedelta(microseconds=1)).astimezone(zone).date()
        while date <= last:
            candidate_actual_dates.add(date)
            date += dt.timedelta(days=1)

    projections = {
        date: actual_block_service.project_actual_blocks_for_day(db, date, settings, now=now)
        for date in candidate_actual_dates
    }
    for date, actual in projections.items():
        if actual.actual_blocks:
            counts[date][1] += len(actual.actual_blocks)

    completed_dates = {
        as_utc(completed_at).astimezone(zone).date()
        for (completed_at,) in db.execute(
            select(Task.completed_at).where(
                Task.status == TaskStatus.completed,
                Task.completed_at.is_not(None),
                Task.deleted_at.is_(None),
                Task.completed_at >= start_utc,
                Task.completed_at < end_utc,
            )
        )
    }

    dates = sorted(set(counts) | completed_dates)
    result.days = [
        ChronicleDayRead(
            date=date,
            planned_count=counts[date][0],
            actual_count=counts[date][1],
            has_completion=date in completed_dates,
            actual_blocks=projections[date].actual_blocks if date in projections else [],
        )
        for date in dates
        if first <= date <= last_visible
    ]
    return result
