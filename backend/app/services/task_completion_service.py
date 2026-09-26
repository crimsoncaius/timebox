from __future__ import annotations

import datetime as dt
import json
import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, joinedload

from app.core.config import Settings
from app.core.time import as_utc, get_zone
from app.models.battle_plan import Task, TaskCompletionOperation, TaskStatus
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.battle_plan import SubtaskRead
from app.services.battle_plan._shared import _load_task
from app.services.recurrence.protection import protect_task_occurrence
from app.services.task_queries import task_select


def _is_subtask(task: Task) -> bool:
    """Ordinary Subtasks exclude independently completable quota Session Tasks."""

    return task.parent_id is not None and task.recurrence_kind != "quota_session"


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
        "reminder_skipped_at": (
            task.reminder_skipped_at.isoformat()
            if task.reminder_skipped_at
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
        "name": block.name,
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
        dt.UTC
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
    db.execute(task_select(snapshot.parent_id, for_update=True)).scalar_one()
    row = db.execute(
        select(Task)
        .options(joinedload(Task.parent))
        .where(Task.id == subtask_id)
        # The parent is already locked above. PostgreSQL cannot lock the
        # nullable side of joinedload's outer join.
        .with_for_update(of=Task)
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


def _load_completable_task(db: Session, task_id: int) -> Task:
    """Lock the Task and reject the states Completion cannot act on."""

    row = db.execute(task_select(task_id, for_update=True)).scalar_one_or_none()
    if row is None:
        raise ValueError("Task not found")
    _assert_completable(row)
    if row.status == TaskStatus.completed:
        raise ValueError("Task is already completed")
    return row


def _peek_active_actual(db: Session, task_id: int) -> tuple[int | None, int | None]:
    """Read this Task's running Actual and its Planned correspondence, without locking.

    Taken before the Day and Planned locks so the caller knows which Planned
    Block to preserve; the row itself is locked afterwards, in lock order.
    """

    snapshot = db.execute(
        select(TimeBlock.id, TimeBlock.planned_block_id).where(
            TimeBlock.lane == BlockLane.actual,
            TimeBlock.task_id == task_id,
            TimeBlock.start_at.is_not(None),
            TimeBlock.end_at.is_(None),
        )
    ).one_or_none()
    if snapshot is None:
        return None, None
    return snapshot.id, snapshot.planned_block_id


def _lock_active_actual(
    db: Session, task_id: int, active_id: int | None, planned_id: int | None
) -> tuple[TimeBlock | None, int | None]:
    """Lock the Actual seen by _peek_active_actual, if it is still running."""

    if active_id is None:
        return None, planned_id
    active = db.execute(_actual_select(active_id, for_update=True)).scalar_one_or_none()
    if active is None or active.task_id != task_id or active.end_at is not None:
        return None, planned_id
    return active, active.planned_block_id


def _lock_planned_blocks(db: Session, task_id: int) -> tuple[list[TimeBlock], dict[int, Day]]:
    """Lock this Task's Planned Blocks and the Days holding them, Days first."""

    day_ids = {
        block.day_id
        for block in db.execute(_planned_for_task_select(task_id)).scalars()
        if block.day_id is not None
    }
    days = {
        day.id: day
        for day in db.execute(
            select(Day).where(Day.id.in_(day_ids)).order_by(Day.id).with_for_update()
        ).scalars()
    }
    planned = list(db.execute(_planned_for_task_select(task_id, for_update=True)).scalars())
    return planned, days


def _removable_future_blocks(
    planned: list[TimeBlock],
    days: dict[int, Day],
    *,
    keep_planned_id: int | None,
    completed_at: dt.datetime,
    settings: Settings,
) -> list[TimeBlock]:
    """Planned Blocks starting after Completion, minus the one being recorded."""

    return [
        block
        for block in planned
        if block.id != keep_planned_id
        and block.day_id in days
        and _planned_start(block, days[block.day_id], settings) > completed_at
    ]


def _lock_corresponding_actuals(db: Session, planned_ids: set[int]) -> dict[int, TimeBlock]:
    """Lock the Actual corresponding to each removable Planned Block, keyed by Planned id."""

    if not planned_ids:
        return {}
    return {
        actual.planned_block_id: actual
        for actual in db.execute(
            select(TimeBlock)
            .where(
                TimeBlock.lane == BlockLane.actual,
                TimeBlock.planned_block_id.in_(planned_ids),
            )
            .order_by(TimeBlock.id)
            .with_for_update()
        ).scalars()
    }


def _completion_operation(
    row: Task,
    removable: list[TimeBlock],
    ended_actuals: dict[int, TimeBlock],
    completed_at: dt.datetime,
) -> TaskCompletionOperation:
    """The Undo record: everything this Completion is about to remove."""

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
    return TaskCompletionOperation(
        token=uuid.uuid4().hex,
        root_task_id=row.id,
        snapshot_json=json.dumps(snapshot),
    )


def _mark_completed(db: Session, row: Task, completed_at: dt.datetime) -> None:
    row.last_non_completed_status = row.status
    protect_task_occurrence(db, row)
    row.status = TaskStatus.completed
    row.completed_at = completed_at
    row.ready_to_plan = False
    row.is_blocked = False
    row.blocking_reason = None
    row.reminder_at = None
    row.reminder_delivered_at = None
    row.reminder_skipped_at = None
    row.reminder_claim_token = None
    row.reminder_claim_until = None


def record_dated_completion(db: Session, task_id: int, completed_at: dt.datetime) -> Task:
    """Record a Task Completion for work done earlier (ADR 0015).

    Unlike :func:`complete_task`, it leaves running tracking and Planned Blocks
    alone and records no Undo operation: the completion describes the past.
    """

    row = _load_completable_task(db, task_id)
    try:
        _mark_completed(db, row, as_utc(completed_at))
        _derive_quota(db, row)
        db.commit()
    except Exception:
        db.rollback()
        raise
    return _load_task(db, task_id)


def complete_task(
    db: Session,
    task_id: int,
    captured_at: dt.datetime,
    settings: Settings,
) -> tuple[Task, str, list[int]]:
    """Apply the one global Task Completion transition atomically.

    Lock order is Task -> Day -> Planned -> Actual -> operation, the same order
    every other command touching Planned/Actual correspondence uses.
    """

    completed_at = as_utc(captured_at)
    row = _load_completable_task(db, task_id)
    active_id, active_planned_id = _peek_active_actual(db, task_id)
    planned_rows, days = _lock_planned_blocks(db, task_id)
    active, active_planned_id = _lock_active_actual(db, task_id, active_id, active_planned_id)
    removable = _removable_future_blocks(
        planned_rows,
        days,
        keep_planned_id=active_planned_id,
        completed_at=completed_at,
        settings=settings,
    )
    ended_actuals = _lock_corresponding_actuals(db, {block.id for block in removable})
    operation = _completion_operation(row, removable, ended_actuals, completed_at)
    token = operation.token

    try:
        if active is not None:
            assert active.start_at is not None
            if completed_at <= as_utc(active.start_at):
                raise ValueError("Actual Block end must be after its start")
            active.end_at = completed_at

        _mark_completed(db, row, completed_at)

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
    row = db.execute(task_select(task_id, for_update=True)).scalar_one_or_none()
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


_UNDO_CONFLICT = "Completion changed; Undo is no longer available"
_PLAN_CONFLICT = "Planned time changed; Undo is no longer available"


def _peek_completion_snapshot(db: Session, task_id: int, token: str) -> dict:
    """Read the Undo record snapshot without locking.

    Taken before the Task lock because the snapshot names the Days and Actuals
    to lock; the record itself is locked afterwards, last in lock order.
    """

    operation = db.execute(_operation_select(token)).scalar_one_or_none()
    if operation is None or operation.root_task_id != task_id:
        raise ValueError("Completion Undo not found")
    return json.loads(operation.snapshot_json)


def _load_undoable_task(db: Session, task_id: int) -> Task:
    """Lock the Task and reject the states Completion Undo cannot act on."""

    row = db.execute(task_select(task_id, for_update=True)).scalar_one_or_none()
    if row is None:
        raise ValueError("Task not found")
    _assert_completable(row)
    return row


def _lock_snapshot_days_and_plans(
    db: Session, day_ids: list[int]
) -> tuple[dict[int, Day], list[TimeBlock]]:
    """Lock the Days the snapshot restores into and their Planned Blocks, Days first."""

    if not day_ids:
        return {}, []
    days = {
        day.id: day
        for day in db.execute(
            select(Day).where(Day.id.in_(day_ids)).order_by(Day.id).with_for_update()
        ).scalars()
    }
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
    )
    return days, existing_plans


def _lock_snapshot_actuals(db: Session, actual_ids: list[int]) -> dict[int, TimeBlock]:
    """Lock the Actuals the snapshot re-links, keyed by id."""

    if not actual_ids:
        return {}
    return {
        actual.id: actual
        for actual in db.execute(
            select(TimeBlock)
            .where(TimeBlock.id.in_(actual_ids), TimeBlock.lane == BlockLane.actual)
            .order_by(TimeBlock.id)
            .with_for_update()
        ).scalars()
    }


def _lock_undo_operation(
    db: Session, task_id: int, token: str
) -> TaskCompletionOperation:
    """Lock the Undo record and reject one already spent."""

    operation = db.execute(
        _operation_select(token, for_update=True)
    ).scalar_one_or_none()
    if operation is not None and operation.undone_at is not None:
        raise ValueError("Completion has already been undone")
    if operation is None or operation.root_task_id != task_id:
        raise ValueError("Completion Undo not found")
    return operation


def _assert_completion_unchanged(
    row: Task, operation: TaskCompletionOperation, snapshot: dict
) -> None:
    """The Task must still hold the Completion this record was written for."""

    captured_at = _parse_datetime(snapshot["captured_at"])
    if (
        row.status != TaskStatus.completed
        or row.completed_at is None
        or captured_at is None
        or as_utc(row.completed_at) != as_utc(captured_at)
        or row.version != operation.completed_task_version
    ):
        raise ValueError(_UNDO_CONFLICT)


def _assert_plans_restorable(
    db: Session,
    plan_states: list[dict],
    days: dict[int, Day],
    existing_plans: list[TimeBlock],
    actuals: dict[int, TimeBlock],
) -> None:
    """Every removed Planned Block must still fit the Day it came from."""

    existing_by_id = {block.id: block for block in existing_plans}
    for state in plan_states:
        if state["id"] in existing_by_id:
            raise ValueError(_PLAN_CONFLICT)
        if state["day_id"] not in days or db.get(TaskType, state["task_type_id"]) is None:
            raise ValueError(_PLAN_CONFLICT)
        if state["task_id"] is not None and db.get(Task, state["task_id"]) is None:
            raise ValueError(_PLAN_CONFLICT)
        if any(
            block.day_id == state["day_id"]
            and _overlaps(state["start_minute"], state["end_minute"], block)
            for block in existing_plans
        ):
            raise ValueError(_PLAN_CONFLICT)
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


def _restore_planned_blocks(
    db: Session, plan_states: list[dict], days: dict[int, Day]
) -> dict[int, TimeBlock]:
    """Re-add the removed Planned Blocks under their original ids."""

    restored: dict[int, TimeBlock] = {}
    for state in plan_states:
        block = TimeBlock(
            id=state["id"],
            day_id=state["day_id"],
            lane=BlockLane.planned,
            task_type_id=state["task_type_id"],
            task_id=state["task_id"],
            name=state.get("name"),
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
        days[state["day_id"]].updated_at = dt.datetime.now(dt.UTC)
    return restored


def _restore_task_state(db: Session, row: Task, task_state: dict) -> None:
    """Put back the Task fields Completion overwrote."""

    row.status = TaskStatus(task_state["status"])
    protect_task_occurrence(db, row)
    row.completed_at = _parse_datetime(task_state["completed_at"])
    prior = task_state["last_non_completed_status"]
    row.last_non_completed_status = TaskStatus(prior) if prior else None
    row.ready_to_plan = task_state["ready_to_plan"]
    row.is_blocked = task_state["is_blocked"]
    row.blocking_reason = task_state["blocking_reason"]
    row.reminder_at = _parse_datetime(task_state["reminder_at"])
    row.reminder_delivered_at = _parse_datetime(task_state["reminder_delivered_at"])
    row.reminder_skipped_at = _parse_datetime(task_state.get("reminder_skipped_at"))
    row.reminder_claim_token = None
    row.reminder_claim_until = None


def undo_task_completion(db: Session, task_id: int, token: str) -> Task:
    """Reverse one Task Completion atomically, or refuse if anything it touched moved.

    Lock order is Task -> Day -> Planned -> Actual -> operation, the same order
    complete_task uses. The snapshot read that names those rows is unlocked and
    is therefore taken first, before the Task lock.
    """

    snapshot = _peek_completion_snapshot(db, task_id, token)
    plan_states = snapshot["removed_planned_blocks"]
    day_ids = sorted({state["day_id"] for state in plan_states})
    actual_ids = sorted(
        state["corresponding_actual_id"]
        for state in plan_states
        if state.get("corresponding_actual_id") is not None
    )

    row = _load_undoable_task(db, task_id)
    days, existing_plans = _lock_snapshot_days_and_plans(db, day_ids)
    actuals = _lock_snapshot_actuals(db, actual_ids)
    operation = _lock_undo_operation(db, task_id, token)

    _assert_completion_unchanged(row, operation, snapshot)
    _assert_plans_restorable(db, plan_states, days, existing_plans, actuals)

    try:
        restored = _restore_planned_blocks(db, plan_states, days)
        db.flush()
        for state in plan_states:
            actual_id = state.get("corresponding_actual_id")
            if actual_id is not None:
                actuals[actual_id].planned_block_id = restored[state["id"]].id

        _restore_task_state(db, row, snapshot["task"])
        operation.undone_at = dt.datetime.now(dt.UTC)
        _derive_quota(db, row)
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ValueError(_UNDO_CONFLICT) from exc
    except Exception:
        db.rollback()
        raise

    return _load_task(db, task_id)
