from __future__ import annotations

import datetime as dt

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import get_zone, isoformat_z, now_in_tz, today_in_tz
from app.models.day import Day
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.day import DayListItem, DayMeta, DayPatch, DayRead
from app.schemas.time_block import TimeBlockCreate, TimeBlockPatch, TimeBlockRead

SLOT_MINUTES = 30
DAY_END = 24 * 60  # 1440


def _utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _touch_day(day: Day) -> None:
    day.updated_at = _utc_now()


def _validate_minutes(start: int, end: int) -> None:
    if start % SLOT_MINUTES != 0 or end % SLOT_MINUTES != 0:
        raise ValueError(f"Times must snap to {SLOT_MINUTES}-minute boundaries")
    if not (0 <= start < end <= DAY_END):
        raise ValueError("Invalid range: require 0 <= start < end <= 1440")


def _intervals_overlap(a0: int, a1: int, b0: int, b1: int) -> bool:
    """[a0,a1) and [b0,b1) overlap if not disjoint."""
    return not (a1 <= b0 or b1 <= a0)


def _lane_blocks(
    day: Day,
    lane: BlockLane,
    exclude_id: int | None = None,
) -> list[TimeBlock]:
    return [
        b
        for b in day.time_blocks
        if b.lane == lane and (exclude_id is None or b.id != exclude_id)
    ]


def _assert_no_overlap(
    day: Day,
    lane: BlockLane,
    start: int,
    end: int,
    exclude_id: int | None = None,
) -> None:
    for other in _lane_blocks(day, lane, exclude_id=exclude_id):
        if _intervals_overlap(start, end, other.start_minute, other.end_minute):
            raise ValueError("Block overlaps another block in the same lane")


def get_day_by_date(db: Session, d: dt.date) -> Day | None:
    stmt = select(Day).where(Day.date == d).options(selectinload(Day.time_blocks))
    return db.execute(stmt).scalar_one_or_none()


def create_day(db: Session, d: dt.date) -> Day:
    day = Day(
        date=d,
        start_hour=8,
        end_hour=20,
        show_full_day=False,
    )
    db.add(day)
    db.flush()
    db.refresh(day)
    return day


def get_or_create_day(db: Session, d: dt.date) -> Day:
    day = get_day_by_date(db, d)
    if day is None:
        day = create_day(db, d)
        db.commit()
        db.refresh(day)
        day = get_day_by_date(db, d)
        assert day is not None
    return day


def to_day_read(day: Day, settings: Settings) -> DayRead:
    blocks = sorted(day.time_blocks, key=lambda b: (b.lane.value, b.start_minute, b.id))
    tz_name = settings.app_timezone
    now = now_in_tz(tz_name)
    today = today_in_tz(tz_name)
    meta = DayMeta(
        timezone=tz_name,
        today=today,
        server_now_iso=isoformat_z(now),
    )
    return DayRead(
        id=day.id,
        date=day.date,
        start_hour=day.start_hour,
        end_hour=day.end_hour,
        show_full_day=day.show_full_day,
        created_at=day.created_at,
        updated_at=day.updated_at,
        time_blocks=[TimeBlockRead.model_validate(b) for b in blocks],
        meta=meta,
    )


def to_day_list_item(day: Day) -> DayListItem:
    return DayListItem(
        id=day.id,
        date=day.date,
        start_hour=day.start_hour,
        end_hour=day.end_hour,
        show_full_day=day.show_full_day,
        updated_at=day.updated_at,
    )


def patch_day(db: Session, day: Day, body: DayPatch) -> Day:
    data = body.model_dump(exclude_unset=True)
    if not data:
        return day
    if body.start_hour is not None:
        day.start_hour = body.start_hour
    if body.end_hour is not None:
        day.end_hour = body.end_hour
    if body.show_full_day is not None:
        day.show_full_day = body.show_full_day
    if day.start_hour >= day.end_hour or day.end_hour > 24 or day.start_hour < 0:
        raise ValueError("Invalid day window: require 0 <= start_hour < end_hour <= 24")
    _touch_day(day)
    db.commit()
    db.refresh(day)
    return day


def get_block(db: Session, day: Day, block_id: int) -> TimeBlock | None:
    stmt = (
        select(TimeBlock)
        .where(TimeBlock.id == block_id, TimeBlock.day_id == day.id)
        .limit(1)
    )
    return db.execute(stmt).scalar_one_or_none()


def create_time_block(db: Session, day: Day, body: TimeBlockCreate) -> TimeBlock:
    _validate_minutes(body.start_minute, body.end_minute)
    _assert_no_overlap(day, body.lane, body.start_minute, body.end_minute)
    title = (body.title or "").strip()
    block = TimeBlock(
        day_id=day.id,
        lane=body.lane,
        title=title,
        start_minute=body.start_minute,
        end_minute=body.end_minute,
    )
    db.add(block)
    _touch_day(day)
    db.commit()
    db.refresh(block)
    db.refresh(day)
    return block


def patch_time_block(db: Session, day: Day, block_id: int, patch: TimeBlockPatch) -> TimeBlock:
    block = get_block(db, day, block_id)
    if block is None:
        raise ValueError("Block not found")
    data = patch.model_dump(exclude_unset=True)
    start = data.get("start_minute", block.start_minute)
    end = data.get("end_minute", block.end_minute)
    if "start_minute" in data or "end_minute" in data:
        _validate_minutes(start, end)
        _assert_no_overlap(day, block.lane, start, end, exclude_id=block.id)
    if "title" in data:
        block.title = str(data["title"] or "").strip()
    if "start_minute" in data:
        block.start_minute = data["start_minute"]
    if "end_minute" in data:
        block.end_minute = data["end_minute"]
    _touch_day(day)
    db.commit()
    db.refresh(block)
    db.refresh(day)
    return block


def delete_time_block(db: Session, day: Day, block_id: int) -> None:
    block = get_block(db, day, block_id)
    if block is None:
        raise ValueError("Block not found")
    db.delete(block)
    _touch_day(day)
    db.commit()
    db.refresh(day)


def list_recent_days(db: Session, limit: int = 60) -> list[Day]:
    stmt = select(Day).order_by(Day.date.desc()).limit(limit)
    return list(db.execute(stmt).scalars().all())


def validate_timezone(tz_name: str) -> None:
    get_zone(tz_name)
