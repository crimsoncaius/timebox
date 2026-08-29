from __future__ import annotations

import datetime as dt
import json
import uuid

from sqlalchemy import func, select, update
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import now_in_tz, today_in_tz
from app.models.day import Day
from app.models.battle_plan import (
    RecurrenceOccurrence,
    Task,
    TaskCompletionOperation,
    TaskStatus,
)
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.battle_plan import TaskCreate, TaskPatch, TaskPlacement, TaskRead
from app.services.battle_plan._shared import (
    TRASH_DAYS,
    _clean_title,
    _is_overdue,
    _load_task,
    _next_position,
    _to_read,
    _validate_deadline,
    _validate_refs,
    _validate_reminder,
    _utc_now,
)


def _completion_task_snapshot(task: Task) -> dict[str, object]:
    return {
        "id": task.id,
        "status": task.status.value,
        "completed_at": task.completed_at.isoformat() if task.completed_at else None,
        "version": task.version,
        "last_non_completed_status": (
            task.last_non_completed_status.value if task.last_non_completed_status else None
        ),
        "ready_to_plan": task.ready_to_plan,
        "is_blocked": task.is_blocked,
        "blocking_reason": task.blocking_reason,
        "reminder_at": task.reminder_at.isoformat() if task.reminder_at else None,
        "reminder_delivered_at": (
            task.reminder_delivered_at.isoformat() if task.reminder_delivered_at else None
        ),
    }


def _completion_block_snapshot(block: TimeBlock) -> dict[str, object]:
    return {
        "id": block.id,
        "day_id": block.day_id,
        "lane": block.lane.value,
        "task_type_id": block.task_type_id,
        "task_id": block.task_id,
        "note": block.note,
        "planned_block_id": block.planned_block_id,
        "start_minute": block.start_minute,
        "end_minute": block.end_minute,
        "created_at": block.created_at.isoformat(),
        "updated_at": block.updated_at.isoformat(),
    }


def _parse_snapshot_datetime(value: str | None) -> dt.datetime | None:
    return dt.datetime.fromisoformat(value) if value else None


def _assert_completion_editable(task: Task) -> None:
    if task.archived_at is not None or task.deleted_at is not None:
        raise ValueError("Inactive tasks are read-only")
    if task.recurrence_kind == "quota_parent":
        raise ValueError("Quota Tracker completion is derived from Session Tasks")


def complete_task(
    db: Session,
    task_id: int,
    planned_time: str,
    settings: Settings,
) -> tuple[Task, str, list[int]]:
    row = _load_task(db, task_id)
    _assert_completion_editable(row)
    if row.status == TaskStatus.completed:
        raise ValueError("Task is already completed")

    affected = [row]
    if row.parent_id is None:
        affected.extend(child for child in row.subtasks if child.deleted_at is None)

    today = today_in_tz(settings.app_timezone)
    removed_blocks = [
        block
        for task in affected
        for block in task.time_blocks
        if (
            planned_time == "remove"
            and block.lane == BlockLane.planned
            and block.day.date >= today
            and block.completion_actual is None
        )
    ]
    token = uuid.uuid4().hex
    snapshot = {
        "tasks": [_completion_task_snapshot(task) for task in affected],
        "removed_planned_blocks": [_completion_block_snapshot(block) for block in removed_blocks],
    }
    operation = TaskCompletionOperation(
        token=token,
        root_task_id=row.id,
        snapshot_json=json.dumps(snapshot),
    )
    completed_at = _utc_now()
    try:
        for task in affected:
            if task.status != TaskStatus.completed:
                task.last_non_completed_status = task.status
            task.status = TaskStatus.completed
            if task.parent_id is None or task.recurrence_kind == "quota_session":
                task.completed_at = completed_at
            task.ready_to_plan = False
            task.is_blocked = False
            task.blocking_reason = None
            task.reminder_at = None
            task.reminder_delivered_at = None
        for block in removed_blocks:
            block.day.updated_at = _utc_now()
            db.delete(block)
        db.add(operation)
        if row.recurrence_kind == "quota_session":
            from app.services import recurrence_service

            recurrence_service._derive_quota_parents(db)
        db.flush()
        operation.completed_task_version = row.version
        db.commit()
    except Exception:
        db.rollback()
        raise

    return _load_task(db, task_id), token, [block.id for block in removed_blocks]


def undo_task_completion(db: Session, task_id: int, token: str) -> Task:
    operation = db.get(TaskCompletionOperation, token)
    if operation is None or operation.root_task_id != task_id:
        raise ValueError("Completion Undo not found")
    if operation.undone_at is not None:
        raise ValueError("Completion has already been undone")

    row = _load_task(db, task_id)
    _assert_completion_editable(row)
    snapshot = json.loads(operation.snapshot_json)
    try:
        for task_state in snapshot["tasks"]:
            task = db.get(Task, task_state["id"])
            if task is None:
                raise ValueError("A Task changed after completion; Undo is no longer available")
            task.status = TaskStatus(task_state["status"])
            task.completed_at = _parse_snapshot_datetime(task_state.get("completed_at"))
            prior = task_state["last_non_completed_status"]
            task.last_non_completed_status = TaskStatus(prior) if prior else None
            task.ready_to_plan = task_state["ready_to_plan"]
            task.is_blocked = task_state["is_blocked"]
            task.blocking_reason = task_state["blocking_reason"]
            task.reminder_at = _parse_snapshot_datetime(task_state["reminder_at"])
            task.reminder_delivered_at = _parse_snapshot_datetime(
                task_state["reminder_delivered_at"]
            )

        for block_state in snapshot["removed_planned_blocks"]:
            if db.get(TimeBlock, block_state["id"]) is not None:
                raise ValueError("Planned time changed after completion; Undo is no longer available")
            day = db.get(Day, block_state["day_id"])
            if day is None:
                raise ValueError("Planned time changed after completion; Undo is no longer available")
            db.add(
                TimeBlock(
                    id=block_state["id"],
                    day_id=block_state["day_id"],
                    lane=BlockLane(block_state["lane"]),
                    task_type_id=block_state["task_type_id"],
                    task_id=block_state["task_id"],
                    note=block_state["note"],
                    planned_block_id=block_state["planned_block_id"],
                    start_minute=block_state["start_minute"],
                    end_minute=block_state["end_minute"],
                    created_at=_parse_snapshot_datetime(block_state["created_at"]),
                    updated_at=_parse_snapshot_datetime(block_state["updated_at"]),
                )
            )
            day.updated_at = _utc_now()
        operation.undone_at = _utc_now()
        if row.recurrence_kind == "quota_session":
            from app.services import recurrence_service

            recurrence_service._derive_quota_parents(db)
        db.commit()
    except Exception:
        db.rollback()
        raise

    return _load_task(db, task_id)


def reopen_task(db: Session, task_id: int) -> Task:
    row = _load_task(db, task_id)
    _assert_completion_editable(row)
    if row.status != TaskStatus.completed:
        raise ValueError("Only completed tasks can be reopened")
    prior = row.last_non_completed_status
    row.status = prior if prior in {TaskStatus.open, TaskStatus.in_progress} else TaskStatus.open
    row.completed_at = None
    if row.recurrence_kind == "quota_session":
        from app.services import recurrence_service

        recurrence_service._derive_quota_parents(db)
    db.commit()
    return _load_task(db, task_id)


def _purge_expired_trash(db: Session) -> None:
    cutoff = _utc_now() - dt.timedelta(days=TRASH_DAYS)
    expired = list(
        db.execute(select(Task).where(Task.deleted_at.is_not(None), Task.deleted_at < cutoff)).scalars()
    )
    expired_ids = {row.id for row in expired}
    for row in expired:
        if row.parent_id not in expired_ids:
            db.delete(row)
    if expired:
        db.commit()


def create_task(db: Session, body: TaskCreate, settings: Settings) -> Task:
    title = _clean_title(body.title)
    _validate_deadline(body.deadline_date, body.deadline_at)
    status = TaskStatus.open if body.status == TaskStatus.blocked else body.status
    is_blocked = body.is_blocked or body.status == TaskStatus.blocked
    if status == TaskStatus.completed:
        is_blocked = False
    parent = None
    project_id = body.project_id
    if body.parent_id is not None:
        parent = _load_task(db, body.parent_id)
        if parent.parent_id is not None:
            raise ValueError("Subtasks cannot contain subtasks")
        if parent.deleted_at is not None or parent.archived_at is not None:
            raise ValueError("Cannot add a subtask to an inactive task")
        project_id = parent.project_id
    _validate_refs(db, project_id, body.task_type_id)
    row = Task(
        title=title,
        description=body.description,
        ready_to_plan=body.ready_to_plan,
        is_blocked=is_blocked,
        blocking_reason=(body.blocking_reason.strip() or None) if is_blocked and body.blocking_reason else None,
        status=status,
        checked=(parent is not None and status == TaskStatus.completed),
        completed_at=(
            _utc_now() if parent is None and status == TaskStatus.completed else None
        ),
        project_id=project_id,
        parent_id=body.parent_id,
        task_type_id=body.task_type_id,
        urgency=body.urgency,
        importance=body.importance,
        deadline_date=body.deadline_date,
        deadline_at=body.deadline_at,
        reminder_at=body.reminder_at,
        position=(len(parent.subtasks) if parent is not None else _next_position(db, status)),
    )
    _validate_reminder(row, settings)
    db.add(row)
    db.commit()
    return _load_task(db, row.id)


def patch_task(db: Session, task_id: int, body: TaskPatch, settings: Settings) -> Task:
    row = _load_task(db, task_id)
    fields = body.model_fields_set
    if row.recurrence_kind == "quota_parent" and "status" in fields:
        raise ValueError("Quota parent status is derived from its sessions")
    old_deadline = (row.deadline_date, row.deadline_at, row.reminder_at)
    if "title" in fields and body.title is not None:
        row.title = _clean_title(body.title)
    if "description" in fields:
        row.description = body.description or ""
    if "ready_to_plan" in fields and body.ready_to_plan is not None:
        row.ready_to_plan = body.ready_to_plan
    if "status" in fields and body.status is not None:
        if body.status != row.status and TaskStatus.completed in {body.status, row.status}:
            raise ValueError("Use the Task completion or reopen command to change completion")
        if body.status == TaskStatus.blocked:
            row.status = TaskStatus.open
            row.is_blocked = True
        else:
            row.status = body.status
        if row.status == TaskStatus.completed:
            row.is_blocked = False
            row.blocking_reason = None
    if "is_blocked" in fields and body.is_blocked is not None:
        row.is_blocked = body.is_blocked and row.status != TaskStatus.completed
        if not row.is_blocked:
            row.blocking_reason = None
    if "blocking_reason" in fields:
        row.blocking_reason = (body.blocking_reason or "").strip() or None
        if row.blocking_reason is not None and row.status != TaskStatus.completed:
            row.is_blocked = True
    if "project_id" in fields:
        if row.parent_id is not None:
            raise ValueError("A subtask inherits its parent's project")
        _validate_refs(db, body.project_id, None)
        row.project_id = body.project_id
        for child in row.subtasks:
            child.project_id = body.project_id
    if "task_type_id" in fields:
        _validate_refs(db, None, body.task_type_id)
        row.task_type_id = body.task_type_id
    if "urgency" in fields:
        row.urgency = body.urgency
    if "importance" in fields:
        row.importance = body.importance
    if "deadline_date" in fields or "deadline_at" in fields:
        date_value = body.deadline_date if "deadline_date" in fields else row.deadline_date
        at_value = body.deadline_at if "deadline_at" in fields else row.deadline_at
        if "deadline_date" in fields and body.deadline_date is not None:
            at_value = None
        if "deadline_at" in fields and body.deadline_at is not None:
            date_value = None
        _validate_deadline(date_value, at_value)
        row.deadline_date = date_value
        row.deadline_at = at_value
        if date_value is None and at_value is None:
            row.reminder_at = None
    if "reminder_at" in fields:
        row.reminder_at = body.reminder_at
    if old_deadline != (row.deadline_date, row.deadline_at, row.reminder_at):
        row.reminder_delivered_at = None
    _validate_reminder(row, settings)
    from app.services import recurrence_service

    recurrence_service.record_task_overrides(row, fields)
    db.commit()
    if row.parent_id is not None and row.recurrence_kind == "quota_session":
        recurrence_service._derive_quota_parents(db)
        db.commit()
    return _load_task(db, task_id)


def list_tasks(db: Session, state: str, settings: Settings) -> list[TaskRead]:
    from app.services import recurrence_service

    _purge_expired_trash(db)
    if state == "active":
        recurrence_service.synchronize(db, settings)
    options = (
        selectinload(Task.project),
        selectinload(Task.task_type),
        selectinload(Task.recurring_template),
        selectinload(Task.occurrence),
        selectinload(Task.time_blocks).selectinload(TimeBlock.day),
        selectinload(Task.time_blocks)
        .selectinload(TimeBlock.completion_actual),
        selectinload(Task.subtasks).selectinload(Task.task_type),
        selectinload(Task.subtasks).selectinload(Task.recurring_template),
        selectinload(Task.subtasks).selectinload(Task.occurrence),
        selectinload(Task.subtasks).selectinload(Task.time_blocks).selectinload(TimeBlock.day),
        selectinload(Task.subtasks)
        .selectinload(Task.time_blocks)
        .selectinload(TimeBlock.completion_actual),
    )
    if state == "active":
        stmt = select(Task).options(*options).where(
            Task.parent_id.is_(None),
            Task.archived_at.is_(None),
            Task.deleted_at.is_(None),
        )
    elif state == "archived":
        stmt = select(Task).options(*options).where(
            Task.parent_id.is_(None),
            Task.archived_at.is_not(None),
            Task.deleted_at.is_(None),
        )
    elif state == "trash":
        stmt = select(Task).options(*options).where(Task.deleted_at.is_not(None))
    else:
        raise ValueError("state must be active, archived, or trash")
    rows = list(db.execute(stmt.order_by(Task.position, Task.id)).scalars().unique())
    if state == "trash":
        deleted_ids = {row.id for row in rows}
        rows = [row for row in rows if row.parent_id not in deleted_ids]
    now = now_in_tz(settings.app_timezone)
    return [
        _to_read(
            row,
            settings,
            now,
            include_children=True,
            include_deleted_children=state == "trash",
        )
        for row in rows
    ]


def reorder_tasks(db: Session, placements: list[TaskPlacement]) -> None:
    ids = [item.task_id for item in placements]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate task in reorder request")
    rows = {row.id: row for row in db.execute(select(Task).where(Task.id.in_(ids))).scalars()}
    if len(rows) != len(ids):
        raise ValueError("Task not found")
    if any(
        item.status != rows[item.task_id].status
        and TaskStatus.completed in {item.status, rows[item.task_id].status}
        for item in placements
    ):
        raise ValueError("Use the Task completion or reopen command to change completion")
    for item in placements:
        row = rows[item.task_id]
        if row.parent_id is not None or row.archived_at is not None or row.deleted_at is not None:
            raise ValueError("Only active parent tasks can be reordered")
        if item.status == TaskStatus.blocked:
            row.status = TaskStatus.open
            row.is_blocked = True
        else:
            row.status = item.status
        if row.status == TaskStatus.completed:
            row.is_blocked = False
            row.blocking_reason = None
        row.position = item.position
    db.commit()


def archive_tasks(db: Session, task_ids: list[int]) -> None:
    now = _utc_now()
    rows = list(db.execute(select(Task).where(Task.id.in_(task_ids))).scalars())
    if len(rows) != len(set(task_ids)):
        raise ValueError("Task not found")
    for row in rows:
        if row.parent_id is not None or row.status != TaskStatus.completed or row.deleted_at is not None:
            raise ValueError("Only completed parent tasks can be archived")
        row.archived_at = now
        for child in row.subtasks:
            child.archived_at = now
    db.commit()


def unarchive_task(db: Session, task_id: int) -> None:
    row = _load_task(db, task_id)
    if row.parent_id is not None:
        raise ValueError("Subtasks are restored with their parent")
    row.archived_at = None
    for child in row.subtasks:
        child.archived_at = None
    db.commit()


def trash_task(db: Session, task_id: int) -> Task:
    row = _load_task(db, task_id)
    now = _utc_now()
    row.deleted_at = now
    row.reminder_delivered_at = None
    if row.parent_id is None:
        for child in row.subtasks:
            child.deleted_at = now
            child.reminder_delivered_at = None
    db.commit()
    return _load_task(db, task_id)


def restore_task(db: Session, task_id: int) -> None:
    row = _load_task(db, task_id)
    row.deleted_at = None
    if row.parent_id is None:
        for child in row.subtasks:
            child.deleted_at = None
    elif row.parent is not None and row.parent.deleted_at is not None:
        raise ValueError("Restore the parent task first")
    db.commit()


def permanently_delete_task(db: Session, task_id: int) -> None:
    row = _load_task(db, task_id)
    if row.deleted_at is None:
        raise ValueError("Only trashed tasks can be permanently deleted")
    db.execute(update(RecurrenceOccurrence).where(RecurrenceOccurrence.task_id == row.id).values(task_id=None))
    db.delete(row)
    db.commit()


def task_type_counts(db: Session) -> dict[int, int]:
    stmt = select(Task.task_type_id, func.count(Task.id)).where(Task.task_type_id.is_not(None)).group_by(Task.task_type_id)
    return {type_id: count for type_id, count in db.execute(stmt).all()}


def clear_task_type_references(db: Session, task_type_id: int) -> None:
    db.execute(Task.__table__.update().where(Task.task_type_id == task_type_id).values(task_type_id=None))
