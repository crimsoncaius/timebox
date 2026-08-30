from __future__ import annotations

import datetime as dt
import json
import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, joinedload

from app.core.config import Settings
from app.core.time import get_zone
from app.models.battle_plan import Task, TaskCompletionOperation, TaskStatus
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.battle_plan import SubtaskRead
from app.services.battle_plan._shared import _load_task
from app.services.recurrence.protection import protect_task_occurrence


def _is_subtask(task: Task) -> bool:
    """Ordinary Subtasks exclude independently completable quota Session Tasks."""

    return task.parent_id is not None and task.recurrence_kind != "quota_session"


def _utc(value: dt.datetime) -> dt.datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=dt.timezone.utc)
    return value.astimezone(dt.timezone.utc)


def _task_select(task_id: int, *, for_update: bool = False):
    statement = select(Task).where(Task.id == task_id)
    return statement.with_for_update() if for_update else statement


def _planned_for_task_select(task_id: int, *, for_update: bool = False):
    statement = (
        select(TimeBlock)
        .where(
            TimeBlock.task_id == task_id,
            TimeBlock.lane == BlockLane.planned,
        )
        .order_by(TimeBlock.id)
    )
    return (
        statement.execution_options(populate_existing=True).with_for_update()
        if for_update
        else statement
    )


def _actual_select(actual_block_id: int, *, for_update: bool = False):
    statement = select(TimeBlock).where(
        TimeBlock.id == actual_block_id,
        TimeBlock.lane == BlockLane.actual,
        TimeBlock.start_at.is_not(None),
    )
    return statement.with_for_update() if for_update else statement


def _operation_select(token: str, *, for_update: bool = False):
    statement = select(TaskCompletionOperation).where(
        TaskCompletionOperation.token == token
    )
    return statement.with_for_update() if for_update else statement


def _task_snapshot(task: Task) -> dict[str, object]:
    return {
        "status": task.status.value,
        "completed_at": task.completed_at.isoformat() if task.completed_at else None,
        "last_non_completed_status": (
            task.last_non_completed_status.value
            if task.last_non_completed_status
            else None
        ),
        "ready_to_plan": task.ready_to_plan,
        "is_blocked": task.is_blocked,
        "blocking_reason": task.blocking_reason,
        "reminder_at": task.reminder_at.isoformat() if task.reminder_at else None,
        "reminder_delivered_at": (
            task.reminder_delivered_at.isoformat()
            if task.reminder_delivered_at
            else None
        ),
    }


def _block_snapshot(
    block: TimeBlock, *, corresponding_actual_id: int | None
) -> dict[str, object]:
    return {
        "id": block.id,
        "day_id": block.day_id,
        "task_type_id": block.task_type_id,
        "task_id": block.task_id,
        "note": block.note,
        "start_minute": block.start_minute,
        "end_minute": block.end_minute,
        "created_at": block.created_at.isoformat(),
        "updated_at": block.updated_at.isoformat(),
        "corresponding_actual_id": corresponding_actual_id,
    }


def _parse_datetime(value: str | None) -> dt.datetime | None:
    return dt.datetime.fromisoformat(value) if value else None


def _assert_completable(task: Task) -> None:
    if task.archived_at is not None or task.deleted_at is not None:
        raise ValueError("Inactive tasks are read-only")
    if _is_subtask(task):
        raise ValueError("Subtasks use check and uncheck actions")
    if task.recurrence_kind == "quota_parent":
        raise ValueError("Quota Tracker completion is derived from Session Tasks")


def _planned_start(
    block: TimeBlock, day: Day, settings: Settings
) -> dt.datetime:
    assert block.start_minute is not None
    local_midnight = dt.datetime.combine(
        day.date, dt.time.min, tzinfo=get_zone(settings.app_timezone)
    )
    return (local_midnight + dt.timedelta(minutes=block.start_minute)).astimezone(
        dt.timezone.utc
    )


def _derive_quota(db: Session, task: Task) -> None:
    if task.recurrence_kind == "quota_session":
        from app.services import recurrence_service

        recurrence_service._derive_quota_parents(db)


def _subtask_read(task: Task) -> SubtaskRead:
    assert task.parent is not None
    return SubtaskRead(
        id=task.id,
        parent_task_id=task.parent.id,
        title=task.title,
        checked=task.checked,
        effectively_resolved=(
            task.parent.status == TaskStatus.completed or task.checked
        ),
        position=task.position,
        created_at=task.created_at,
        updated_at=task.updated_at,
    )


def set_subtask_checked(
    db: Session, subtask_id: int, *, checked: bool
) -> SubtaskRead:
    snapshot = db.execute(
        select(Task).where(Task.id == subtask_id)
    ).scalar_one_or_none()
    if snapshot is None:
        raise ValueError("Subtask not found")
    if not _is_subtask(snapshot):
        raise ValueError("Only a Subtask can be checked or unchecked")
    assert snapshot.parent_id is not None
    db.execute(_task_select(snapshot.parent_id, for_update=True)).scalar_one()
    row = db.execute(
        select(Task)
        .options(joinedload(Task.parent))
        .where(Task.id == subtask_id)
        .with_for_update()
    ).scalar_one_or_none()
    if row is None:
        raise ValueError("Subtask not found")
    if not _is_subtask(row):
        raise ValueError("Only a Subtask can be checked or unchecked")
    assert row.parent is not None
    if (
        row.archived_at is not None
        or row.deleted_at is not None
        or row.parent.archived_at is not None
        or row.parent.deleted_at is not None
    ):
        raise ValueError("Inactive Subtasks are read-only")
    if row.parent.status == TaskStatus.completed:
        raise ValueError("Completed Tasks and their Subtasks are read-only until reopen")

    row.checked = checked
    protect_task_occurrence(db, row)
    db.commit()
    return _subtask_read(row)


def complete_task(
    db: Session,
    task_id: int,
    captured_at: dt.datetime,
    settings: Settings,
) -> tuple[Task, str, list[int]]:
    """Apply the one global Task Completion transition atomically."""

    completed_at = _utc(captured_at)
    row = db.execute(_task_select(task_id, for_update=True)).scalar_one_or_none()
    if row is None:
        raise ValueError("Task not found")
    _assert_completable(row)
    if row.status == TaskStatus.completed:
        raise ValueError("Task is already completed")

    active_snapshot = db.execute(
        select(TimeBlock.id, TimeBlock.planned_block_id).where(
            TimeBlock.lane == BlockLane.actual,
            TimeBlock.task_id == task_id,
            TimeBlock.start_at.is_not(None),
            TimeBlock.end_at.is_(None),
        )
    ).one_or_none()
    active_id = active_snapshot.id if active_snapshot is not None else None
    active_planned_id = (
        active_snapshot.planned_block_id if active_snapshot is not None else None
    )

    planned_snapshot = list(
        db.execute(_planned_for_task_select(task_id)).scalars()
    )
    day_ids = {
        block.day_id for block in planned_snapshot if block.day_id is not None
    }
    days = {
        day.id: day
        for day in db.execute(
            select(Day).where(Day.id.in_(day_ids)).order_by(Day.id).with_for_update()
        ).scalars()
    }
    planned_rows = list(
        db.execute(_planned_for_task_select(task_id, for_update=True)).scalars()
    )

    active: TimeBlock | None = None
    if active_id is not None:
        active = db.execute(
            _actual_select(active_id, for_update=True)
        ).scalar_one_or_none()
        if active is not None and (
            active.task_id != task_id or active.end_at is not None
        ):
            active = None
        elif active is not None:
            active_planned_id = active.planned_block_id

    removable = [
        block
        for block in planned_rows
        if block.id != active_planned_id
        and block.day_id in days
        and _planned_start(block, days[block.day_id], settings) > completed_at
    ]
    removable_ids = {block.id for block in removable}
    ended_actuals = {
        actual.planned_block_id: actual
        for actual in db.execute(
            select(TimeBlock)
            .where(
                TimeBlock.lane == BlockLane.actual,
                TimeBlock.planned_block_id.in_(removable_ids),
            )
            .order_by(TimeBlock.id)
            .with_for_update()
        ).scalars()
    } if removable_ids else {}

    token = uuid.uuid4().hex
    snapshot = {
        "captured_at": completed_at.isoformat(),
        "task": _task_snapshot(row),
        "removed_planned_blocks": [
            _block_snapshot(
                block,
                corresponding_actual_id=(
                    ended_actuals[block.id].id if block.id in ended_actuals else None
                ),
            )
            for block in removable
        ],
    }
    operation = TaskCompletionOperation(
        token=token,
        root_task_id=row.id,
        snapshot_json=json.dumps(snapshot),
    )

    try:
        if active is not None:
            assert active.start_at is not None
            if completed_at <= _utc(active.start_at):
                raise ValueError("Actual Block end must be after its start")
            active.end_at = completed_at

        row.last_non_completed_status = row.status
        protect_task_occurrence(db, row)
        row.status = TaskStatus.completed
        row.completed_at = completed_at
        row.ready_to_plan = False
        row.is_blocked = False
        row.blocking_reason = None
        row.reminder_at = None
        row.reminder_delivered_at = None

        for block in removable:
            linked_actual = ended_actuals.get(block.id)
            if linked_actual is not None:
                linked_actual.planned_block_id = None
            days[block.day_id].updated_at = completed_at
            db.delete(block)

        db.add(operation)
        _derive_quota(db, row)
        db.flush()
        operation.completed_task_version = row.version
        db.commit()
    except Exception:
        db.rollback()
        raise

    return _load_task(db, task_id), token, [block.id for block in removable]


def reopen_task(db: Session, task_id: int) -> Task:
    row = db.execute(_task_select(task_id, for_update=True)).scalar_one_or_none()
    if row is None:
        raise ValueError("Task not found")
    _assert_completable(row)
    if row.status != TaskStatus.completed:
        raise ValueError("Only completed tasks can be reopened")

    row.status = TaskStatus.open
    protect_task_occurrence(db, row)
    row.completed_at = None
    _derive_quota(db, row)
    db.commit()
    return _load_task(db, task_id)


def _overlaps(start: int, end: int, other: TimeBlock) -> bool:
    assert other.start_minute is not None and other.end_minute is not None
    return not (end <= other.start_minute or other.end_minute <= start)


def undo_task_completion(db: Session, task_id: int, token: str) -> Task:
    operation_snapshot = db.execute(_operation_select(token)).scalar_one_or_none()
    if operation_snapshot is None or operation_snapshot.root_task_id != task_id:
        raise ValueError("Completion Undo not found")
    snapshot = json.loads(operation_snapshot.snapshot_json)
    plan_states = snapshot["removed_planned_blocks"]

    row = db.execute(_task_select(task_id, for_update=True)).scalar_one_or_none()
    if row is None:
        raise ValueError("Task not found")
    _assert_completable(row)

    day_ids = sorted({state["day_id"] for state in plan_states})
    days = {
        day.id: day
        for day in db.execute(
            select(Day).where(Day.id.in_(day_ids)).order_by(Day.id).with_for_update()
        ).scalars()
    } if day_ids else {}
    existing_plans = list(
        db.execute(
            select(TimeBlock)
            .where(
                TimeBlock.lane == BlockLane.planned,
                TimeBlock.day_id.in_(day_ids),
            )
            .order_by(TimeBlock.id)
            .with_for_update()
        ).scalars()
    ) if day_ids else []
    actual_ids = sorted(
        state["corresponding_actual_id"]
        for state in plan_states
        if state.get("corresponding_actual_id") is not None
    )
    actuals = {
        actual.id: actual
        for actual in db.execute(
            select(TimeBlock)
            .where(TimeBlock.id.in_(actual_ids), TimeBlock.lane == BlockLane.actual)
            .order_by(TimeBlock.id)
            .with_for_update()
        ).scalars()
    } if actual_ids else {}
    operation = db.execute(
        _operation_select(token, for_update=True)
    ).scalar_one_or_none()

    conflict = "Completion changed; Undo is no longer available"
    captured_at = _parse_datetime(snapshot["captured_at"])
    if (
        operation is None
        or operation.root_task_id != task_id
        or operation.undone_at is not None
    ):
        if operation is not None and operation.undone_at is not None:
            raise ValueError("Completion has already been undone")
        raise ValueError("Completion Undo not found")
    if (
        row.status != TaskStatus.completed
        or row.completed_at is None
        or captured_at is None
        or _utc(row.completed_at) != _utc(captured_at)
        or row.version != operation.completed_task_version
    ):
        raise ValueError(conflict)

    existing_by_id = {block.id: block for block in existing_plans}
    for state in plan_states:
        if state["id"] in existing_by_id:
            raise ValueError("Planned time changed; Undo is no longer available")
        if state["day_id"] not in days or db.get(TaskType, state["task_type_id"]) is None:
            raise ValueError("Planned time changed; Undo is no longer available")
        if state["task_id"] is not None and db.get(Task, state["task_id"]) is None:
            raise ValueError("Planned time changed; Undo is no longer available")
        if any(
            block.day_id == state["day_id"]
            and _overlaps(state["start_minute"], state["end_minute"], block)
            for block in existing_plans
        ):
            raise ValueError("Planned time changed; Undo is no longer available")
        actual_id = state.get("corresponding_actual_id")
        if actual_id is not None:
            actual = actuals.get(actual_id)
            if (
                actual is None
                or actual.planned_block_id is not None
                or (actual.task_type_id, actual.task_id)
                != (state["task_type_id"], state["task_id"])
            ):
                raise ValueError("Actual correspondence changed; Undo is no longer available")

    task_state = snapshot["task"]
    restored: dict[int, TimeBlock] = {}
    try:
        for state in plan_states:
            block = TimeBlock(
                id=state["id"],
                day_id=state["day_id"],
                lane=BlockLane.planned,
                task_type_id=state["task_type_id"],
                task_id=state["task_id"],
                note=state["note"],
                start_minute=state["start_minute"],
                end_minute=state["end_minute"],
                start_at=None,
                end_at=None,
                planned_block_id=None,
                created_at=_parse_datetime(state["created_at"]),
                updated_at=_parse_datetime(state["updated_at"]),
            )
            db.add(block)
            restored[state["id"]] = block
            days[state["day_id"]].updated_at = dt.datetime.now(dt.timezone.utc)
        db.flush()
        for state in plan_states:
            actual_id = state.get("corresponding_actual_id")
            if actual_id is not None:
                actuals[actual_id].planned_block_id = restored[state["id"]].id

        row.status = TaskStatus(task_state["status"])
        protect_task_occurrence(db, row)
        row.completed_at = _parse_datetime(task_state["completed_at"])
        prior = task_state["last_non_completed_status"]
        row.last_non_completed_status = TaskStatus(prior) if prior else None
        row.ready_to_plan = task_state["ready_to_plan"]
        row.is_blocked = task_state["is_blocked"]
        row.blocking_reason = task_state["blocking_reason"]
        row.reminder_at = _parse_datetime(task_state["reminder_at"])
        row.reminder_delivered_at = _parse_datetime(
            task_state["reminder_delivered_at"]
        )
        operation.undone_at = dt.datetime.now(dt.timezone.utc)
        _derive_quota(db, row)
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(conflict) from exc
    except Exception:
        db.rollback()
        raise

    return _load_task(db, task_id)
