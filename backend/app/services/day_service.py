from __future__ import annotations

import datetime as dt

from sqlalchemy import func, select, update
from sqlalchemy.dialects.postgresql import insert as postgresql_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import get_zone, isoformat_z, now_in_tz, today_in_tz
from app.models.app_settings import AppSettings
from app.models.day import Day
from app.models.battle_plan import Task
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.services import actual_block_service, task_type_service
from app.services.recurrence.protection import protect_task_occurrence
from app.schemas.day import (
    DayListItem,
    DayMeta,
    DayPreviewRead,
    DayRead,
    DaySummaryRead,
    DaySummaryRow,
    PlanningPlacementCreate,
)
from app.schemas.settings import SettingsPatch
from app.schemas.time_block import PlannedBlockCreate, PlannedBlockRead, TimeBlockPatch, TimeBlockRead

MIN_PLANNED_BLOCK_MINUTES = 30
DAY_END = 24 * 60  # 1440
UNSPECIFIED_TASK_TYPE = "unspecified"


def _task_select(task_id: int, *, for_update: bool = False):
    statement = select(Task).where(Task.id == task_id)
    return statement.with_for_update() if for_update else statement


def _assert_schedulable_task(task: Task, *, allow_completed: bool) -> None:
    if task.archived_at is not None or task.deleted_at is not None:
        raise ValueError("Only active tasks can be scheduled")
    if task.parent_id is not None and task.recurrence_kind != "quota_session":
        raise ValueError("Subtasks cannot be scheduled")
    if task.recurrence_kind == "quota_parent":
        raise ValueError("Schedule quota sessions individually")
    if task.status.value == "completed" and not allow_completed:
        raise ValueError("Completed Tasks cannot receive new Planned Blocks")


def _utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _touch_day(day: Day) -> None:
    day.updated_at = _utc_now()


def _validate_minutes(start: int, end: int) -> None:
    if not (0 <= start < end <= DAY_END):
        raise ValueError("Invalid range: require 0 <= start < end <= 1440")
    if end - start < MIN_PLANNED_BLOCK_MINUTES:
        raise ValueError(
            f"Planned Blocks must be at least {MIN_PLANNED_BLOCK_MINUTES} minutes"
        )


def _active_task(
    db: Session,
    task_id: int | None,
    *,
    for_update: bool = False,
    allow_completed: bool = False,
) -> Task | None:
    if task_id is None:
        return None
    task = db.execute(
        _task_select(task_id, for_update=for_update)
    ).scalar_one_or_none()
    if task is None:
        raise ValueError("Task not found")
    _assert_schedulable_task(task, allow_completed=allow_completed)
    return task


def _get_or_create_unspecified_task_type(db: Session) -> TaskType:
    """Materialize the linked-task fallback without committing its caller's work."""

    values = {"name": UNSPECIFIED_TASK_TYPE}
    dialect = db.get_bind().dialect.name
    if dialect == "postgresql":
        statement = postgresql_insert(TaskType).values(**values).on_conflict_do_nothing(
            index_elements=[TaskType.name]
        )
    elif dialect == "sqlite":
        statement = sqlite_insert(TaskType).values(**values).on_conflict_do_nothing(
            index_elements=[TaskType.name]
        )
    else:  # The application supports PostgreSQL and SQLite; keep test adapters usable.
        existing = db.execute(
            select(TaskType).where(TaskType.name == UNSPECIFIED_TASK_TYPE)
        ).scalar_one_or_none()
        if existing is not None:
            return existing
        row = TaskType(name=UNSPECIFIED_TASK_TYPE)
        db.add(row)
        db.flush()
        return row

    db.execute(statement)
    row = db.execute(
        select(TaskType).where(TaskType.name == UNSPECIFIED_TASK_TYPE)
    ).scalar_one_or_none()
    assert row is not None
    return row


def _resolve_planned_block_task_type(
    db: Session,
    *,
    task: Task | None,
    requested_task_type_id: int | None,
) -> TaskType:
    """Resolve the Task Type behind the Planned Block creation interface.

    Explicit values remain valid for older callers and intentional overrides. New
    linked-task callers can omit the value: the Battle Plan Task's Task Type wins,
    with one canonical fallback for untyped tasks.
    """

    if requested_task_type_id is not None:
        task_type = task_type_service.get_task_type(db, requested_task_type_id)
        if task_type is None:
            raise ValueError("Task type not found")
        return task_type
    if task is None:
        raise ValueError("Task Type is required when no Battle Plan Task is linked")
    if task.task_type_id is not None:
        task_type = task_type_service.get_task_type(db, task.task_type_id)
        if task_type is None:
            raise ValueError("Task type not found")
        return task_type
    return _get_or_create_unspecified_task_type(db)


def _validate_recurrence_schedule(task: Task | None, day: Day) -> None:
    if task is None:
        return
    if task.recurrence_kind == "quota_parent":
        raise ValueError("Schedule quota sessions individually")
    if task.recurrence_kind == "quota_session" and task.quota_period_start is not None:
        if day.date < task.quota_period_start:
            raise ValueError("Quota sessions cannot be scheduled before their period starts")
        if task.quota_period_end is not None and day.date > task.quota_period_end:
            raise ValueError("Quota sessions cannot be scheduled after their period ends")


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
            selectinload(Day.time_blocks).selectinload(TimeBlock.task),
            selectinload(Day.time_blocks).selectinload(TimeBlock.completion_actual),
        )
    )
    return db.execute(stmt).scalar_one_or_none()


def get_or_create_app_settings(db: Session) -> AppSettings:
    row = get_app_settings(db)
    if row is None:
        row = AppSettings(id=1, start_hour=8, end_hour=20, show_full_day=False, week_start="monday")
        db.add(row)
        db.flush()
        db.refresh(row)
    return row


def get_app_settings(db: Session) -> AppSettings | None:
    """Read the singleton window settings without creating them."""

    stmt = select(AppSettings).where(AppSettings.id == 1)
    return db.execute(stmt).scalar_one_or_none()


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


def to_day_read(db: Session, day: Day, settings: Settings) -> DayRead:
    blocks = sorted(day.time_blocks, key=lambda b: (b.lane.value, b.start_minute, b.id))
    planned = [block for block in blocks if block.lane == BlockLane.planned]
    actual = actual_block_service.project_actual_blocks_for_day(db, day.date, settings)
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
        planned_blocks=[PlannedBlockRead.model_validate(block) for block in planned],
        actual_blocks=actual.actual_blocks,
        planned_minutes=sum(
            (block.end_minute or 0) - (block.start_minute or 0) for block in planned
        ),
        actual_minutes=actual.actual_minutes,
        meta=meta,
    )


def build_day_preview(
    db: Session,
    day: Day | None,
    d: dt.date,
    settings: Settings,
) -> DayPreviewRead:
    """Return a renderable day without materialising a missing date."""

    if day is None:
        window = get_app_settings(db) or AppSettings(
            id=1,
            start_hour=8,
            end_hour=20,
            show_full_day=False,
        )
        start_hour = window.start_hour
        end_hour = window.end_hour
        show_full_day = window.show_full_day
        blocks: list[TimeBlock] = []
    else:
        start_hour = day.start_hour
        end_hour = day.end_hour
        show_full_day = day.show_full_day
        blocks = sorted(day.time_blocks, key=lambda b: (b.lane.value, b.start_minute, b.id))

    planned = [block for block in blocks if block.lane == BlockLane.planned]
    actual = actual_block_service.project_actual_blocks_for_day(db, d, settings)

    return DayPreviewRead(
        date=d,
        start_hour=start_hour,
        end_hour=end_hour,
        show_full_day=show_full_day,
        time_blocks=[TimeBlockRead.model_validate(block) for block in blocks],
        planned_blocks=[PlannedBlockRead.model_validate(block) for block in planned],
        actual_blocks=actual.actual_blocks,
        planned_minutes=sum(
            (block.end_minute or 0) - (block.start_minute or 0) for block in planned
        ),
        actual_minutes=actual.actual_minutes,
        meta=_day_meta(settings),
    )


def _day_meta(settings: Settings) -> DayMeta:
    tz_name = settings.app_timezone
    return DayMeta(
        timezone=tz_name,
        today=today_in_tz(tz_name),
        server_now_iso=isoformat_z(now_in_tz(tz_name)),
    )


def build_day_summary(
    db: Session, day: Day | None, d: dt.date, settings: Settings
) -> DaySummaryRead:
    """Aggregate a day's blocks into planned/actual totals and per-task-type rows.

    `day` may be None for a date that was never opened; that yields an empty summary
    rather than creating the day, so viewing the review does not add it to the archive.
    """
    planned_by_type: dict[int, int] = {}
    actual_by_type: dict[int, int] = {}
    names: dict[int, str] = {}

    for block in day.time_blocks if day is not None else []:
        if block.start_minute is None or block.end_minute is None:
            continue
        minutes = block.end_minute - block.start_minute
        names[block.task_type_id] = block.task_type.name
        bucket = planned_by_type if block.lane == BlockLane.planned else actual_by_type
        bucket[block.task_type_id] = bucket.get(block.task_type_id, 0) + minutes

    definitive_actual = actual_block_service.project_actual_blocks_for_day(db, d, settings)
    for projection in definitive_actual.actual_blocks:
        block = projection.actual_block
        names[block.task_type_id] = block.task_type.name
        actual_by_type[block.task_type_id] = (
            actual_by_type.get(block.task_type_id, 0) + projection.duration_minutes
        )

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
    if body.week_start is not None:
        s.week_start = body.week_start
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


def _day_block_select(day_id: int, block_id: int, *, for_update: bool = False):
    stmt = (
        select(TimeBlock)
        .where(TimeBlock.id == block_id, TimeBlock.day_id == day_id)
        .limit(1)
    )
    return stmt.with_for_update() if for_update else stmt


def get_block(
    db: Session,
    day: Day,
    block_id: int,
    *,
    for_update: bool = False,
) -> TimeBlock | None:
    return db.execute(
        _day_block_select(day.id, block_id, for_update=for_update)
    ).scalar_one_or_none()


def create_time_block(db: Session, day: Day, body: PlannedBlockCreate) -> TimeBlock:
    _validate_minutes(body.start_minute, body.end_minute)
    task = _active_task(db, body.task_id, for_update=True)
    day = db.execute(
        select(Day).where(Day.id == day.id).with_for_update()
    ).scalar_one()
    _assert_no_overlap(day, body.lane, body.start_minute, body.end_minute)
    _validate_recurrence_schedule(task, day)
    task_type = _resolve_planned_block_task_type(
        db,
        task=task,
        requested_task_type_id=body.task_type_id,
    )
    note_val = (body.note or "").strip() or None
    block = TimeBlock(
        day_id=day.id,
        lane=body.lane,
        task_type_id=task_type.id,
        task_id=body.task_id,
        note=note_val,
        start_minute=body.start_minute,
        end_minute=body.end_minute,
    )
    db.add(block)
    if task is not None and body.lane == BlockLane.planned:
        task.ready_to_plan = False
        protect_task_occurrence(db, task)
    _touch_day(day)
    db.commit()
    db.refresh(block)
    db.refresh(day)
    return block


def commit_planning_session(
    db: Session,
    placements: list[PlanningPlacementCreate],
) -> list[Day]:
    """Atomically turn one Plan-mode session into linked Planned blocks."""

    task_ids = [placement.task_id for placement in placements]
    if len(task_ids) != len(set(task_ids)):
        raise ValueError("Each task can only be planned once per session")

    locked_tasks = {
        task.id: task
        for task in db.execute(
            select(Task)
            .where(Task.id.in_(sorted(task_ids)))
            .order_by(Task.id)
            .with_for_update()
        ).scalars()
    }
    if len(locked_tasks) != len(task_ids):
        raise ValueError("Task not found")
    for task in locked_tasks.values():
        _assert_schedulable_task(task, allow_completed=False)

    days: dict[dt.date, Day] = {}
    staged_intervals: dict[dt.date, list[tuple[int, int]]] = {}
    resolved: list[tuple[PlanningPlacementCreate, Day, Task, TaskType]] = []

    for date in sorted({placement.date for placement in placements}):
        day = db.execute(
            select(Day).where(Day.date == date).with_for_update()
        ).scalar_one_or_none()
        days[date] = day or create_day(db, date)

    for placement in placements:
        _validate_minutes(placement.start_minute, placement.end_minute)
        day = days[placement.date]
        task = locked_tasks[placement.task_id]
        if not task.ready_to_plan:
            raise ValueError(f"Task {task.id} is no longer ready to plan")
        _validate_recurrence_schedule(task, day)

        task_type = _resolve_planned_block_task_type(
            db,
            task=task,
            requested_task_type_id=placement.task_type_id,
        )
        _assert_no_overlap(
            day,
            BlockLane.planned,
            placement.start_minute,
            placement.end_minute,
        )
        for other_start, other_end in staged_intervals.setdefault(placement.date, []):
            if _intervals_overlap(
                placement.start_minute,
                placement.end_minute,
                other_start,
                other_end,
            ):
                raise ValueError("Planned tasks overlap each other")
        staged_intervals[placement.date].append((placement.start_minute, placement.end_minute))
        resolved.append((placement, day, task, task_type))

    try:
        for placement, day, task, task_type in resolved:
            db.add(
                TimeBlock(
                    day_id=day.id,
                    lane=BlockLane.planned,
                    task_type_id=task_type.id,
                    task_id=task.id,
                    note=None,
                    start_minute=placement.start_minute,
                    end_minute=placement.end_minute,
                )
            )
            task.ready_to_plan = False
            protect_task_occurrence(db, task)
            _touch_day(day)
        db.commit()
    except Exception:
        db.rollback()
        raise

    committed: list[Day] = []
    for date in sorted(days):
        day = get_day_by_date(db, date)
        assert day is not None
        committed.append(day)
    return committed


def patch_time_block(db: Session, day: Day, block_id: int, patch: TimeBlockPatch) -> TimeBlock:
    data = patch.model_dump(exclude_unset=True)
    target_task = None
    if "task_id" in data:
        target_task = _active_task(
            db, data["task_id"], for_update=True, allow_completed=True
        )
    # Task eligibility is locked before Planned; correspondence mutations then
    # retain the Planned -> Actual order.
    block = get_block(db, day, block_id, for_update=True)
    if block is None:
        raise ValueError("Block not found")
    original_item = (block.task_type_id, block.task_id)
    if block.lane == BlockLane.planned and block.task_id is not None:
        protect_task_occurrence(db, block.task_id)
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
    if "task_id" in data:
        _validate_recurrence_schedule(target_task, day)
        block.task_id = data["task_id"]
        if target_task is not None and block.lane == BlockLane.planned:
            target_task.ready_to_plan = False
            protect_task_occurrence(db, target_task)
    if block.lane == BlockLane.planned and block.task_id is not None:
        protect_task_occurrence(db, block.task_id)
    if "note" in data:
        block.note = str(data["note"] or "").strip() or None
    if "start_minute" in data:
        block.start_minute = data["start_minute"]
    if "end_minute" in data:
        block.end_minute = data["end_minute"]
    if block.lane == BlockLane.planned and original_item != (
        block.task_type_id,
        block.task_id,
    ):
        linked_actual = db.execute(
            select(TimeBlock)
            .where(TimeBlock.planned_block_id == block.id)
            .with_for_update()
        ).scalar_one_or_none()
        if linked_actual is not None:
            actual_block_service.invalidate_record_actual_undo(db, linked_actual.id)
            linked_actual.planned_block_id = None
    _touch_day(day)
    try:
        db.commit()
    except Exception:
        db.rollback()
        raise
    db.refresh(block)
    db.refresh(day)
    return block


def delete_time_block(db: Session, day: Day, block_id: int) -> None:
    # Same Planned -> Actual lock order as relink and Undo.
    block = get_block(db, day, block_id, for_update=True)
    if block is None:
        raise ValueError("Block not found")
    if block.lane == BlockLane.planned:
        if block.task_id is not None:
            protect_task_occurrence(db, block.task_id)
        linked_actual = db.execute(
            select(TimeBlock)
            .where(TimeBlock.planned_block_id == block.id)
            .with_for_update()
        ).scalar_one_or_none()
        if linked_actual is not None:
            actual_block_service.invalidate_record_actual_undo(db, linked_actual.id)
            linked_actual.planned_block_id = None
    db.delete(block)
    _touch_day(day)
    db.commit()
    db.refresh(day)


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
