from __future__ import annotations

import datetime as dt

from sqlalchemy import func, select, update
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import get_zone, isoformat_z, now_in_tz, today_in_tz
from app.models.app_settings import AppSettings
from app.models.day import Day
from app.models.time_block import BlockLane, TimeBlock
from app.services import task_type_service
from app.schemas.day import DayListItem, DayMeta, DayRead, DaySummaryRead, DaySummaryRow
from app.schemas.settings import SettingsPatch
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
    stmt = (
        select(Day)
        .where(Day.date == d)
        .options(
            selectinload(Day.time_blocks).selectinload(TimeBlock.task_type),
        )
    )
    return db.execute(stmt).scalar_one_or_none()


def get_or_create_app_settings(db: Session) -> AppSettings:
    stmt = select(AppSettings).where(AppSettings.id == 1)
    row = db.execute(stmt).scalar_one_or_none()
    if row is None:
        row = AppSettings(id=1, start_hour=8, end_hour=20, show_full_day=False)
        db.add(row)
        db.flush()
        db.refresh(row)
    return row


def create_day(db: Session, d: dt.date) -> Day:
    s = get_or_create_app_settings(db)
    day = Day(
        date=d,
        start_hour=s.start_hour,
        end_hour=s.end_hour,
        show_full_day=s.show_full_day,
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
    meta = _day_meta(settings)
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


def _day_meta(settings: Settings) -> DayMeta:
    tz_name = settings.app_timezone
    return DayMeta(
        timezone=tz_name,
        today=today_in_tz(tz_name),
        server_now_iso=isoformat_z(now_in_tz(tz_name)),
    )


def build_day_summary(day: Day | None, d: dt.date, settings: Settings) -> DaySummaryRead:
    """Aggregate a day's blocks into planned/actual totals and per-task-type rows.

    `day` may be None for a date that was never opened; that yields an empty summary
    rather than creating the day, so viewing the review does not add it to the archive.
    """
    planned_by_type: dict[int, int] = {}
    actual_by_type: dict[int, int] = {}
    names: dict[int, str] = {}

    for block in day.time_blocks if day is not None else []:
        minutes = block.end_minute - block.start_minute
        names[block.task_type_id] = block.task_type.name
        bucket = planned_by_type if block.lane == BlockLane.planned else actual_by_type
        bucket[block.task_type_id] = bucket.get(block.task_type_id, 0) + minutes

    rows = [
        DaySummaryRow(
            task_type_id=type_id,
            task_type_name=names[type_id],
            planned_minutes=planned_by_type.get(type_id, 0),
            actual_minutes=actual_by_type.get(type_id, 0),
        )
        for type_id in names
    ]
    # Busiest first, so the review screen leads with where the day actually went.
    rows.sort(
        key=lambda r: (-(r.actual_minutes + r.planned_minutes), r.task_type_name.lower())
    )

    return DaySummaryRead(
        date=d,
        planned_minutes=sum(planned_by_type.values()),
        actual_minutes=sum(actual_by_type.values()),
        rows=rows,
        meta=_day_meta(settings),
    )


def to_day_list_item(day: Day, block_count: int) -> DayListItem:
    return DayListItem(
        id=day.id,
        date=day.date,
        start_hour=day.start_hour,
        end_hour=day.end_hour,
        show_full_day=day.show_full_day,
        updated_at=day.updated_at,
        block_count=block_count,
    )


def patch_app_settings(db: Session, body: SettingsPatch) -> AppSettings:
    s = get_or_create_app_settings(db)
    data = body.model_dump(exclude_unset=True)
    if not data:
        return s
    if body.start_hour is not None:
        s.start_hour = body.start_hour
    if body.end_hour is not None:
        s.end_hour = body.end_hour
    if body.show_full_day is not None:
        s.show_full_day = body.show_full_day
    if s.start_hour >= s.end_hour or s.end_hour > 24 or s.start_hour < 0:
        raise ValueError("Invalid day window: require 0 <= start_hour < end_hour <= 24")
    s.updated_at = _utc_now()
    db.add(s)
    db.flush()
    now = _utc_now()
    db.execute(
        update(Day).values(
            start_hour=s.start_hour,
            end_hour=s.end_hour,
            show_full_day=s.show_full_day,
            updated_at=now,
        )
    )
    db.commit()
    db.refresh(s)
    return s


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
    tt = task_type_service.get_task_type(db, body.task_type_id)
    if tt is None:
        raise ValueError("Task type not found")
    note_val = (body.note or "").strip() or None
    block = TimeBlock(
        day_id=day.id,
        lane=body.lane,
        task_type_id=body.task_type_id,
        note=note_val,
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
    if "task_type_id" in data:
        tid = data["task_type_id"]
        if task_type_service.get_task_type(db, tid) is None:
            raise ValueError("Task type not found")
        block.task_type_id = tid
    if "note" in data:
        block.note = str(data["note"] or "").strip() or None
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


def complete_planned_as_actual(db: Session, day: Day, planned_block_id: int) -> TimeBlock:
    """Create (or refresh) an Actual block that mirrors a Planned block, linked by planned_block_id."""
    drow = get_day_by_date(db, day.date)
    assert drow is not None
    planned = get_block(db, drow, planned_block_id)
    if planned is None:
        raise ValueError("Block not found")
    if planned.lane != BlockLane.planned:
        raise ValueError("Only planned blocks can be completed as planned")

    existing = next(
        (
            b
            for b in drow.time_blocks
            if b.lane == BlockLane.actual and b.planned_block_id == planned_block_id
        ),
        None,
    )
    start = planned.start_minute
    end = planned.end_minute
    _validate_minutes(start, end)
    if existing is not None:
        _assert_no_overlap(drow, BlockLane.actual, start, end, exclude_id=existing.id)
        tt = task_type_service.get_task_type(db, planned.task_type_id)
        if tt is None:
            raise ValueError("Task type not found")
        existing.task_type_id = planned.task_type_id
        existing.note = (planned.note or "").strip() or None
        existing.start_minute = start
        existing.end_minute = end
        _touch_day(drow)
        db.commit()
        db.refresh(existing)
        db.refresh(drow)
        return existing

    _assert_no_overlap(drow, BlockLane.actual, start, end)
    tt = task_type_service.get_task_type(db, planned.task_type_id)
    if tt is None:
        raise ValueError("Task type not found")
    note_val = (planned.note or "").strip() or None
    block = TimeBlock(
        day_id=drow.id,
        lane=BlockLane.actual,
        task_type_id=planned.task_type_id,
        note=note_val,
        start_minute=start,
        end_minute=end,
        planned_block_id=planned.id,
    )
    db.add(block)
    _touch_day(drow)
    db.commit()
    db.refresh(block)
    db.refresh(drow)
    return block


def list_recent_days(db: Session, limit: int = 60) -> list[tuple[Day, int]]:
    """Recent days, newest first, each paired with how many blocks it holds.

    Merely opening a date creates the day, so the archive is full of empty rows; the
    count rides along as a subquery to tell those apart without loading every block.
    """
    block_count = (
        select(func.count(TimeBlock.id))
        .where(TimeBlock.day_id == Day.id)
        .correlate(Day)
        .scalar_subquery()
    )
    stmt = select(Day, block_count).order_by(Day.date.desc()).limit(limit)
    return [(day, count) for day, count in db.execute(stmt).all()]


def validate_timezone(tz_name: str) -> None:
    get_zone(tz_name)
