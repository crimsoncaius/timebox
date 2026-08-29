from __future__ import annotations

import datetime as dt
import uuid

from sqlalchemy import or_, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, joinedload

from app.core.config import Settings
from app.core.time import get_zone
from app.models.battle_plan import Task
from app.models.time_block import ActualBlockRecordOperation, BlockLane, TimeBlock
from app.models.task_type import TaskType
from app.schemas.time_block import (
    ActualBlockCreate,
    ActualBlockStart,
    ActualBlockDayProjectionRead,
    ActualBlockDayRead,
    ActualBlockRead,
    ActualBlockPatch,
)


def _as_utc(value: dt.datetime) -> dt.datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=dt.timezone.utc)
    return value.astimezone(dt.timezone.utc)


def _minute_floor(value: dt.datetime) -> dt.datetime:
    return value.replace(second=0, microsecond=0)


def _read(row: TimeBlock) -> ActualBlockRead:
    return ActualBlockRead.model_validate(row)


def _actual_select(actual_block_id: int, *, for_update: bool = False):
    statement = (
        select(TimeBlock)
        .options(joinedload(TimeBlock.task_type), joinedload(TimeBlock.task))
        .where(
            TimeBlock.id == actual_block_id,
            TimeBlock.lane == BlockLane.actual,
            TimeBlock.start_at.is_not(None),
        )
    )
    return statement.with_for_update() if for_update else statement


def _planned_select(planned_block_id: int, *, for_update: bool = False):
    statement = select(TimeBlock).where(
        TimeBlock.id == planned_block_id,
        TimeBlock.lane == BlockLane.planned,
    )
    return statement.with_for_update() if for_update else statement


def _record_operation_select(token: str, *, for_update: bool = False):
    statement = select(ActualBlockRecordOperation).where(
        ActualBlockRecordOperation.token == token
    )
    return statement.with_for_update() if for_update else statement


def _record_operation_for_actual_select(
    actual_block_id: int, *, for_update: bool = False
):
    statement = select(ActualBlockRecordOperation).where(
        ActualBlockRecordOperation.actual_block_id == actual_block_id,
        ActualBlockRecordOperation.undone_at.is_(None),
        ActualBlockRecordOperation.invalidated_at.is_(None),
    )
    return statement.with_for_update() if for_update else statement


def _load_actual(
    db: Session, actual_block_id: int, *, for_update: bool = False
) -> TimeBlock:
    row = db.execute(
        _actual_select(actual_block_id, for_update=for_update)
    ).scalar_one_or_none()
    if row is None:
        raise ValueError("Actual Block not found")
    return row


def _validate_item(db: Session, task_type_id: int, task_id: int | None) -> None:
    if db.get(TaskType, task_type_id) is None:
        raise ValueError("Task type not found")
    if task_id is None:
        return
    task = db.get(Task, task_id)
    if task is None:
        raise ValueError("Task not found")
    if task.archived_at is not None or task.deleted_at is not None:
        raise ValueError("Only active tasks can record Actual time")


def _planned_row(
    db: Session, planned_block_id: int, *, for_update: bool = False
) -> TimeBlock:
    planned = db.execute(
        _planned_select(planned_block_id, for_update=for_update)
    ).scalar_one_or_none()
    if planned is None:
        raise ValueError("Planned Block not found")
    return planned


def _resolve_origin_item(
    db: Session,
    *,
    task_type_id: int | None,
    task_id: int | None,
    planned_block_id: int | None,
) -> tuple[int, int | None]:
    if planned_block_id is None:
        if task_type_id is None:
            raise ValueError("task_type_id is required for standalone Actual")
        _validate_item(db, task_type_id, task_id)
        return task_type_id, task_id

    planned = _planned_row(db, planned_block_id, for_update=True)
    if task_type_id is not None and task_type_id != planned.task_type_id:
        raise ValueError("Linked Actual must use the Planned Block primary item")
    if task_id is not None and task_id != planned.task_id:
        raise ValueError("Linked Actual must use the Planned Block primary item")
    _validate_item(db, planned.task_type_id, planned.task_id)
    return planned.task_type_id, planned.task_id


def _integrity_message(exc: IntegrityError) -> str:
    message = str(exc.orig).lower()
    if "overlap" in message:
        return "Actual Blocks cannot overlap"
    if "active" in message or "uq_time_blocks_one_active_actual" in message:
        return "An Actual Block is already active"
    return "Actual Block conflicts with current recorded time"


def invalidate_record_actual_undo(db: Session, actual_block_id: int) -> None:
    """Invalidate shortcut Undo when newer intent mutates its created Actual."""

    operation = db.execute(
        _record_operation_for_actual_select(actual_block_id, for_update=True)
    ).scalar_one_or_none()
    if operation is not None:
        operation.invalidated_at = dt.datetime.now(dt.timezone.utc)


def start_actual_block(
    db: Session, body: ActualBlockStart, captured_at: dt.datetime
) -> ActualBlockRead:
    """Atomically create the sole active Actual Block."""

    task_type_id, task_id = _resolve_origin_item(
        db,
        task_type_id=body.task_type_id,
        task_id=body.task_id,
        planned_block_id=body.planned_block_id,
    )
    if get_active_actual_block(db) is not None:
        raise ValueError("An Actual Block is already active")
    row = TimeBlock(
        lane=BlockLane.actual,
        task_type_id=task_type_id,
        task_id=task_id,
        note=(body.note or "").strip() or None,
        day_id=None,
        start_minute=None,
        end_minute=None,
        start_at=_as_utc(captured_at),
        end_at=None,
        planned_block_id=body.planned_block_id,
    )
    try:
        db.add(row)
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(_integrity_message(exc)) from exc
    return _read(_load_actual(db, row.id))


def finish_actual_block(
    db: Session, actual_block_id: int, captured_at: dt.datetime
) -> ActualBlockRead:
    """Atomically finish one active Actual Block at the captured instant."""

    row = _load_actual(db, actual_block_id, for_update=True)
    if row.end_at is not None:
        raise ValueError("Actual Block is already finished")
    finished_at = _as_utc(captured_at)
    assert row.start_at is not None
    if finished_at <= _as_utc(row.start_at):
        raise ValueError("Actual Block end must be after its start")
    try:
        result = db.execute(
            update(TimeBlock)
            .where(TimeBlock.id == row.id, TimeBlock.end_at.is_(None))
            .values(end_at=finished_at)
        )
        if result.rowcount != 1:
            db.rollback()
            raise ValueError("Actual Block is already finished")
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(_integrity_message(exc)) from exc
    return _read(_load_actual(db, actual_block_id))


def create_actual_block(db: Session, body: ActualBlockCreate) -> ActualBlockRead:
    """Create a finished retrospective Actual Block in one transaction."""

    task_type_id, task_id = _resolve_origin_item(
        db,
        task_type_id=body.task_type_id,
        task_id=body.task_id,
        planned_block_id=body.planned_block_id,
    )
    row = TimeBlock(
        lane=BlockLane.actual,
        task_type_id=task_type_id,
        task_id=task_id,
        note=(body.note or "").strip() or None,
        planned_block_id=body.planned_block_id,
        day_id=None,
        start_minute=None,
        end_minute=None,
        start_at=_as_utc(body.start_at),
        end_at=_as_utc(body.end_at),
    )
    try:
        db.add(row)
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(_integrity_message(exc)) from exc
    return _read(_load_actual(db, row.id))


def patch_actual_block(
    db: Session, actual_block_id: int, body: ActualBlockPatch
) -> ActualBlockRead:
    """Correct Actual facts without rewriting its corresponding Planned Block."""

    row = _load_actual(db, actual_block_id, for_update=True)
    data = body.model_dump(exclude_unset=True)
    start_at = data.get("start_at", row.start_at)
    end_at = data.get("end_at", row.end_at)
    if start_at is None:
        raise ValueError("Actual Block start is required")
    start_at = _as_utc(start_at)
    end_at = _as_utc(end_at) if end_at is not None else None
    if end_at is not None and end_at <= start_at:
        raise ValueError("Actual Block end must be after its start")

    task_type_id = data.get("task_type_id", row.task_type_id)
    task_id = data.get("task_id", row.task_id)
    if task_type_id is None:
        raise ValueError("task_type_id is required for Actual")
    _validate_item(db, task_type_id, task_id)
    item_changed = task_type_id != row.task_type_id or task_id != row.task_id

    if data:
        invalidate_record_actual_undo(db, row.id)

    row.task_type_id = task_type_id
    row.task_id = task_id
    if "note" in data:
        row.note = str(data["note"] or "").strip() or None
    row.start_at = start_at
    row.end_at = end_at
    if item_changed:
        row.planned_block_id = None
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(_integrity_message(exc)) from exc
    return _read(_load_actual(db, actual_block_id))


def detach_actual_block(db: Session, actual_block_id: int) -> ActualBlockRead:
    row = _load_actual(db, actual_block_id, for_update=True)
    if row.planned_block_id is None:
        raise ValueError("Actual Block is not linked to a Planned Block")
    invalidate_record_actual_undo(db, row.id)
    row.planned_block_id = None
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(_integrity_message(exc)) from exc
    return _read(_load_actual(db, actual_block_id))


def relink_actual_block(
    db: Session, actual_block_id: int, planned_block_id: int
) -> ActualBlockRead:
    # All multi-row correspondence commands lock Planned before Actual. Planned
    # patch/delete use the same order, avoiding an Actual->Planned deadlock.
    planned = _planned_row(db, planned_block_id, for_update=True)
    row = _load_actual(db, actual_block_id, for_update=True)
    if row.planned_block_id is not None:
        raise ValueError("Detach Actual from its current Planned Block before relinking")
    existing = db.execute(
        select(TimeBlock).where(TimeBlock.planned_block_id == planned.id)
    ).scalar_one_or_none()
    if existing is not None:
        raise ValueError("Planned Block already has corresponding Actual")
    if (row.task_type_id, row.task_id) != (planned.task_type_id, planned.task_id):
        raise ValueError("Actual and Planned Blocks must have the same primary item")
    invalidate_record_actual_undo(db, row.id)
    row.planned_block_id = planned.id
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        message = str(exc.orig).lower()
        if "unique" in message or "correspondence" in message:
            raise ValueError("Planned Block already has corresponding Actual") from exc
        raise ValueError(_integrity_message(exc)) from exc
    return _read(_load_actual(db, actual_block_id))


def record_actual_as_planned(
    db: Session,
    planned_block_id: int,
    settings: Settings,
) -> tuple[ActualBlockRead, str]:
    """Create the Planned Block's exact current interval as authoritative Actual."""

    planned = _planned_row(db, planned_block_id, for_update=True)
    if db.execute(
        select(TimeBlock.id).where(TimeBlock.planned_block_id == planned.id)
    ).scalar_one_or_none() is not None:
        raise ValueError("Planned Block already has corresponding Actual")
    assert planned.day is not None
    assert planned.start_minute is not None and planned.end_minute is not None
    zone = get_zone(settings.app_timezone)
    local_midnight = dt.datetime.combine(planned.day.date, dt.time.min, tzinfo=zone)
    start_at = (local_midnight + dt.timedelta(minutes=planned.start_minute)).astimezone(
        dt.timezone.utc
    )
    end_at = (local_midnight + dt.timedelta(minutes=planned.end_minute)).astimezone(
        dt.timezone.utc
    )
    token = uuid.uuid4().hex
    actual = TimeBlock(
        lane=BlockLane.actual,
        task_type_id=planned.task_type_id,
        task_id=planned.task_id,
        note=planned.note,
        planned_block_id=planned.id,
        day_id=None,
        start_minute=None,
        end_minute=None,
        start_at=start_at,
        end_at=end_at,
    )
    try:
        db.add(actual)
        db.flush()
        db.add(
            ActualBlockRecordOperation(
                token=token,
                actual_block_id=actual.id,
                planned_block_id=planned.id,
            )
        )
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        message = str(exc.orig).lower()
        if "unique" in message or "correspondence" in message:
            raise ValueError("Planned Block already has corresponding Actual") from exc
        raise ValueError(_integrity_message(exc)) from exc
    return _read(_load_actual(db, actual.id)), token


def undo_record_actual_as_planned(
    db: Session, planned_block_id: int, token: str
) -> None:
    # Planned -> Actual -> operation is the global correspondence lock order.
    # Locking Planned first also makes concurrent Undo replays serialize.
    _planned_row(db, planned_block_id, for_update=True)
    operation_snapshot = db.execute(_record_operation_select(token)).scalar_one_or_none()
    if operation_snapshot is None or operation_snapshot.planned_block_id != planned_block_id:
        raise ValueError("Record Actual Undo not found")
    if operation_snapshot.undone_at is not None:
        raise ValueError("Record Actual Undo has already been used")
    if operation_snapshot.invalidated_at is not None:
        raise ValueError("Actual Block changed; Record Actual Undo is no longer available")
    if operation_snapshot.actual_block_id is None:
        raise ValueError("Actual Block changed; Record Actual Undo is no longer available")
    actual = db.execute(
        _actual_select(operation_snapshot.actual_block_id, for_update=True)
    ).scalar_one_or_none()
    operation = db.execute(
        _record_operation_select(token, for_update=True)
    ).scalar_one_or_none()
    if operation is None or operation.planned_block_id != planned_block_id:
        raise ValueError("Record Actual Undo not found")
    if operation.undone_at is not None:
        raise ValueError("Record Actual Undo has already been used")
    if operation.invalidated_at is not None:
        raise ValueError("Actual Block changed; Record Actual Undo is no longer available")
    if (
        actual is None
        or operation.actual_block_id != actual.id
        or actual.planned_block_id != planned_block_id
    ):
        raise ValueError("Actual Block changed; Record Actual Undo is no longer available")
    try:
        operation.actual_block_id = None
        operation.undone_at = dt.datetime.now(dt.timezone.utc)
        db.delete(actual)
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(_integrity_message(exc)) from exc


def delete_actual_block(db: Session, actual_block_id: int) -> None:
    row = _load_actual(db, actual_block_id, for_update=True)
    invalidate_record_actual_undo(db, row.id)
    try:
        db.delete(row)
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(_integrity_message(exc)) from exc


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


def get_actual_block(db: Session, actual_block_id: int) -> ActualBlockRead:
    return _read(_load_actual(db, actual_block_id))


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
