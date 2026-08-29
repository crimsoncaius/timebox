from __future__ import annotations

import datetime as dt

from sqlalchemy import or_, select
from sqlalchemy.orm import Session, joinedload

from app.core.config import Settings
from app.core.time import get_zone
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.time_block import (
    ActualBlockDayProjectionRead,
    ActualBlockDayRead,
    ActualBlockRead,
)


def _as_utc(value: dt.datetime) -> dt.datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=dt.timezone.utc)
    return value.astimezone(dt.timezone.utc)


def _minute_floor(value: dt.datetime) -> dt.datetime:
    return value.replace(second=0, microsecond=0)


def _read(row: TimeBlock) -> ActualBlockRead:
    return ActualBlockRead.model_validate(row)


def get_active_actual_block(db: Session) -> ActualBlockRead | None:
    """Return the single globally active definitive Actual Block, if any."""

    row = db.execute(
        select(TimeBlock)
        .options(joinedload(TimeBlock.task_type), joinedload(TimeBlock.task))
        .where(
            TimeBlock.lane == BlockLane.actual,
            TimeBlock.start_at.is_not(None),
            TimeBlock.end_at.is_(None),
        )
    ).scalar_one_or_none()
    return _read(row) if row is not None else None


def project_actual_blocks_for_day(
    db: Session,
    date: dt.date,
    settings: Settings,
    *,
    now: dt.datetime | None = None,
) -> ActualBlockDayRead:
    """Project authoritative Actual intervals onto one local Day.

    Projection never writes or splits a TimeBlock row. Active Actual is projected
    only through the captured current minute, rather than pretending future time
    has already occurred.
    """

    zone = get_zone(settings.app_timezone)
    local_start = dt.datetime.combine(date, dt.time.min, tzinfo=zone)
    local_end = dt.datetime.combine(date + dt.timedelta(days=1), dt.time.min, tzinfo=zone)
    day_start = local_start.astimezone(dt.timezone.utc)
    day_end = local_end.astimezone(dt.timezone.utc)
    captured_now = _minute_floor(_as_utc(now or dt.datetime.now(dt.timezone.utc)))

    rows = list(
        db.execute(
            select(TimeBlock)
            .options(joinedload(TimeBlock.task_type), joinedload(TimeBlock.task))
            .where(
                TimeBlock.lane == BlockLane.actual,
                TimeBlock.start_at.is_not(None),
                TimeBlock.start_at < day_end,
                or_(TimeBlock.end_at.is_(None), TimeBlock.end_at > day_start),
            )
            .order_by(TimeBlock.start_at, TimeBlock.id)
        ).scalars()
    )

    projections: list[ActualBlockDayProjectionRead] = []
    for row in rows:
        assert row.start_at is not None
        actual_start = _as_utc(row.start_at)
        actual_end = _as_utc(row.end_at) if row.end_at is not None else captured_now
        intersection_start = max(actual_start, day_start)
        intersection_end = min(actual_end, day_end)
        if intersection_end <= intersection_start:
            continue

        start_local = intersection_start.astimezone(zone)
        end_local = intersection_end.astimezone(zone)
        start_minute = (
            0
            if intersection_start == day_start
            else start_local.hour * 60 + start_local.minute
        )
        end_minute = (
            1440
            if intersection_end == day_end
            else end_local.hour * 60 + end_local.minute
        )
        duration = int((intersection_end - intersection_start).total_seconds() // 60)
        projections.append(
            ActualBlockDayProjectionRead(
                actual_block=_read(row),
                date=date,
                start_minute=start_minute,
                end_minute=end_minute,
                duration_minutes=duration,
            )
        )

    return ActualBlockDayRead(
        date=date,
        actual_blocks=projections,
        actual_minutes=sum(item.duration_minutes for item in projections),
    )
