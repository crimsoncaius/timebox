from __future__ import annotations

import datetime as dt
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import now_in_tz, today_in_tz
from app.models.battle_plan import (
    RecurrenceOccurrence,
    RecurringTemplate,
    Task,
    TaskStatus,
)
from app.models.time_block import TimeBlock
from app.schemas.battle_plan import TaskCreate, TaskPatch, TaskPlacement, TaskRead
from app.services.recurrence.protection import protect_task_occurrence
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


def _purge_expired_trash(db: Session) -> None:
    cutoff = _utc_now() - dt.timedelta(days=TRASH_DAYS)
    expired = list(
        db.execute(select(Task).where(Task.deleted_at.is_not(None), Task.deleted_at < cutoff)).scalars()
    )
    expired_ids = {row.id for row in expired}
    for row in expired:
        if row.parent_id not in expired_ids:
            db.execute(
                update(RecurrenceOccurrence)
                .where(RecurrenceOccurrence.task_id == row.id)
                .values(task_id=None, suppressed=True, structurally_protected=True)
            )
            db.delete(row)
    if expired:
        db.commit()


def _load_task_for_mutation(db: Session, task_id: int) -> Task:
    """Lock Parent before ordinary Subtask; otherwise lock the Task itself."""

    snapshot = db.execute(
        select(Task.id, Task.parent_id, Task.recurrence_kind).where(Task.id == task_id)
    ).one_or_none()
    if snapshot is None:
        raise ValueError("Task not found")
    if snapshot.parent_id is not None and snapshot.recurrence_kind != "quota_session":
        _load_task(db, snapshot.parent_id, for_update=True)
    return _load_task(db, task_id, for_update=True)


def create_task(db: Session, body: TaskCreate, settings: Settings) -> Task:
    title = _clean_title(body.title)
    _validate_deadline(body.deadline_date, body.deadline_at)
    status = TaskStatus.open if body.status == TaskStatus.blocked else body.status
    if status == TaskStatus.completed:
        raise ValueError("Use the Task completion command to complete a Task")
    is_blocked = body.is_blocked or body.status == TaskStatus.blocked
    if status == TaskStatus.completed:
        is_blocked = False
    parent = None
    project_id = body.project_id
    if body.parent_id is not None:
        parent = _load_task(db, body.parent_id, for_update=True)
        if parent.parent_id is not None:
            raise ValueError("Subtasks cannot contain subtasks")
        if parent.deleted_at is not None or parent.archived_at is not None:
            raise ValueError("Cannot add a subtask to an inactive task")
        if parent.status == TaskStatus.completed:
            raise ValueError("Completed Tasks and their Subtasks are read-only until reopen")
        if (
            body.ready_to_plan
            or body.is_blocked
            or body.blocking_reason is not None
            or status != TaskStatus.open
            or body.deadline_date is not None
            or body.deadline_at is not None
            or body.reminder_at is not None
            or body.project_id is not None
            or body.task_type_id is not None
            or body.urgency is not None
            or body.importance is not None
        ):
            raise ValueError("Subtasks do not have a Task lifecycle")
        project_id = parent.project_id
    _validate_refs(db, project_id, body.task_type_id)
    row = Task(
        title=title,
        description=body.description,
        ready_to_plan=body.ready_to_plan,
        is_blocked=is_blocked,
        blocking_reason=(body.blocking_reason.strip() or None) if is_blocked and body.blocking_reason else None,
        status=status,
        checked=False,
        completed_at=None,
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
    if parent is not None:
        protect_task_occurrence(db, parent)
    db.commit()
    return _load_task(db, row.id)


def patch_task(db: Session, task_id: int, body: TaskPatch, settings: Settings) -> Task:
    row = _load_task_for_mutation(db, task_id)
    fields = body.model_fields_set
    if row.archived_at is not None or row.deleted_at is not None:
        raise ValueError("Inactive tasks are read-only")
    if row.status == TaskStatus.completed or (
        row.parent is not None and row.parent.status == TaskStatus.completed
    ):
        raise ValueError("Completed Tasks and their Subtasks are read-only until reopen")
    is_subtask = row.parent_id is not None and row.recurrence_kind != "quota_session"
    if is_subtask and not fields <= {"title", "description"}:
        raise ValueError("Subtasks do not have a Task lifecycle")
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
    if fields:
        protect_task_occurrence(db, row)
    db.commit()
    if row.parent_id is not None and row.recurrence_kind == "quota_session":
        recurrence_service._derive_quota_parents(db)
        db.commit()
    return _load_task(db, task_id)


def _visible_active_rows(
    rows: list[Task],
    today: dt.date,
    planning_date: dt.date | None,
) -> list[tuple[Task, int]]:
    visible: list[tuple[Task, int]] = []
    recurring: dict[int, list[Task]] = {}
    for row in rows:
        if row.recurring_template_id is None or row.occurrence is None:
            visible.append((row, 1))
        else:
            recurring.setdefault(row.recurring_template_id, []).append(row)

    for tasks in recurring.values():
        actionable = [
            task for task in tasks
            if not task.occurrence.skipped
            and task.status != TaskStatus.completed
            and task.occurrence.cycle_start <= today
        ]
        requested = [
            task for task in tasks
            if planning_date is not None
            and not task.occurrence.skipped
            and task.status != TaskStatus.completed
            and task.occurrence.cycle_start <= planning_date <= task.occurrence.cycle_end
        ]
        candidates = requested or actionable
        if not candidates:
            continue
        representative = min(
            candidates,
            key=lambda task: (task.occurrence.cycle_start, task.occurrence.id),
        )
        outstanding_ids = {task.id for task in actionable}
        outstanding_ids.update(task.id for task in requested)
        visible.append((representative, max(1, len(outstanding_ids))))
    return sorted(visible, key=lambda item: (item[0].position, item[0].id))


def list_tasks(
    db: Session,
    state: str,
    settings: Settings,
    planning_date: dt.date | None = None,
) -> list[TaskRead]:
    from app.services import recurrence_service

    _purge_expired_trash(db)
    if state == "active":
        recurrence_service.synchronize(db, settings, planning_date=planning_date)
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
    visible_rows = (
        _visible_active_rows(rows, today_in_tz(settings.app_timezone), planning_date)
        if state == "active"
        else [(row, 1) for row in rows]
    )
    if state == "trash":
        deleted_ids = {row.id for row in rows}
        visible_rows = [(row, count) for row, count in visible_rows if row.parent_id not in deleted_ids]
    now = now_in_tz(settings.app_timezone)
    return [
        _to_read(
            row,
            settings,
            now,
            include_children=True,
            include_deleted_children=state == "trash",
            outstanding_occurrence_count=count,
        )
        for row, count in visible_rows
    ]


def reorder_tasks(db: Session, placements: list[TaskPlacement]) -> None:
    ids = [item.task_id for item in placements]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate task in reorder request")
    rows = {
        row.id: row
        for row in db.execute(
            select(Task)
            .where(Task.id.in_(ids))
            .order_by(Task.id)
            .with_for_update()
        ).scalars()
    }
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
        if row.status == TaskStatus.completed:
            raise ValueError("Completed Tasks are read-only until reopen")
        if item.status == TaskStatus.blocked:
            row.status = TaskStatus.open
            row.is_blocked = True
        else:
            row.status = item.status
        if row.status == TaskStatus.completed:
            row.is_blocked = False
            row.blocking_reason = None
        row.position = item.position
        if row.recurring_template_id is not None:
            template = db.get(RecurringTemplate, row.recurring_template_id)
            if template is not None:
                template.position = item.position
            db.execute(
                update(Task)
                .where(Task.recurring_template_id == row.recurring_template_id)
                .values(position=item.position)
            )
        protect_task_occurrence(db, row)
    db.commit()


def archive_tasks(db: Session, task_ids: list[int]) -> None:
    now = _utc_now()
    rows = list(
        db.execute(
            select(Task)
            .where(Task.id.in_(task_ids))
            .order_by(Task.id)
            .with_for_update()
        ).scalars()
    )
    if len(rows) != len(set(task_ids)):
        raise ValueError("Task not found")
    for row in rows:
        if row.parent_id is not None or row.status != TaskStatus.completed or row.deleted_at is not None:
            raise ValueError("Only completed parent tasks can be archived")
        row.archived_at = now
        protect_task_occurrence(db, row)
        for child in row.subtasks:
            child.archived_at = now
    db.commit()


def unarchive_task(db: Session, task_id: int) -> None:
    row = _load_task_for_mutation(db, task_id)
    if row.parent_id is not None:
        raise ValueError("Subtasks are restored with their parent")
    row.archived_at = None
    protect_task_occurrence(db, row)
    for child in row.subtasks:
        child.archived_at = None
    db.commit()


def trash_task(db: Session, task_id: int) -> Task:
    row = _load_task_for_mutation(db, task_id)
    if row.parent is not None and row.parent.status == TaskStatus.completed:
        raise ValueError("Completed Tasks and their Subtasks are read-only until reopen")
    now = _utc_now()
    row.deleted_at = now
    protect_task_occurrence(db, row)
    row.reminder_delivered_at = None
    if row.parent_id is None:
        for child in row.subtasks:
            child.deleted_at = now
            child.reminder_delivered_at = None
    db.commit()
    return _load_task(db, task_id)


def restore_task(db: Session, task_id: int) -> None:
    row = _load_task_for_mutation(db, task_id)
    if row.parent is not None and row.parent.status == TaskStatus.completed:
        raise ValueError("Completed Tasks and their Subtasks are read-only until reopen")
    row.deleted_at = None
    protect_task_occurrence(db, row)
    if row.parent_id is None:
        for child in row.subtasks:
            child.deleted_at = None
    elif row.parent is not None and row.parent.deleted_at is not None:
        raise ValueError("Restore the parent task first")
    db.commit()


def permanently_delete_task(db: Session, task_id: int) -> None:
    row = _load_task_for_mutation(db, task_id)
    if row.parent is not None and row.parent.status == TaskStatus.completed:
        raise ValueError("Completed Tasks and their Subtasks are read-only until reopen")
    if row.deleted_at is None:
        raise ValueError("Only trashed tasks can be permanently deleted")
    protect_task_occurrence(db, row)
    db.execute(
        update(RecurrenceOccurrence)
        .where(RecurrenceOccurrence.task_id == row.id)
        .values(task_id=None, suppressed=True, structurally_protected=True)
    )
    db.delete(row)
    db.commit()


def task_type_counts(db: Session) -> dict[int, int]:
    stmt = select(Task.task_type_id, func.count(Task.id)).where(Task.task_type_id.is_not(None)).group_by(Task.task_type_id)
    return {type_id: count for type_id, count in db.execute(stmt).all()}


def clear_task_type_references(db: Session, task_type_id: int) -> None:
    db.execute(Task.__table__.update().where(Task.task_type_id == task_type_id).values(task_type_id=None))
