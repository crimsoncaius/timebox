from __future__ import annotations

import datetime as dt

from sqlalchemy import delete, func, select, update
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import now_in_tz
from app.models.battle_plan import Project, RecurrenceOccurrence, Task, TaskStatus
from app.models.task_type import TaskType
from app.schemas.battle_plan import (
    ProjectCreate,
    ProjectPatch,
    ProjectRead,
    ReminderRead,
    TaskCreate,
    TaskPatch,
    TaskPlacement,
    TaskRead,
)

TRASH_DAYS = 30


def _utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _aware(value: dt.datetime) -> dt.datetime:
    return value if value.tzinfo is not None else value.replace(tzinfo=dt.timezone.utc)


def _clean_name(value: str) -> str:
    value = value.strip()
    if not value:
        raise ValueError("Project name is required")
    return value


def _clean_title(value: str) -> str:
    value = value.strip()
    if not value:
        raise ValueError("Task title is required")
    return value


def _validate_deadline(deadline_date: dt.date | None, deadline_at: dt.datetime | None) -> None:
    if deadline_date is not None and deadline_at is not None:
        raise ValueError("Use either deadline_date or deadline_at, not both")


def _deadline_boundary(task: Task, settings: Settings) -> dt.datetime | None:
    if task.deadline_at is not None:
        return _aware(task.deadline_at)
    if task.deadline_date is not None:
        zone_now = now_in_tz(settings.app_timezone)
        return dt.datetime.combine(
            task.deadline_date + dt.timedelta(days=1), dt.time.min, tzinfo=zone_now.tzinfo
        )
    return None


def _validate_reminder(task: Task, settings: Settings) -> None:
    if task.reminder_at is None:
        return
    boundary = _deadline_boundary(task, settings)
    if boundary is None:
        raise ValueError("A reminder requires a deadline")
    if _aware(task.reminder_at) >= boundary:
        raise ValueError("Reminder must be before the deadline")


def list_projects(db: Session) -> list[Project]:
    return list(db.execute(select(Project).order_by(func.lower(Project.name), Project.id)).scalars())


def create_project(db: Session, body: ProjectCreate) -> Project:
    name = _clean_name(body.name)
    _validate_deadline(body.deadline_date, body.deadline_at)
    exists = db.execute(select(Project.id).where(func.lower(Project.name) == name.lower())).scalar_one_or_none()
    if exists is not None:
        raise ValueError("A project with this name already exists")
    row = Project(
        name=name,
        description=body.description,
        deadline_date=body.deadline_date,
        deadline_at=body.deadline_at,
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def patch_project(db: Session, project_id: int, body: ProjectPatch) -> Project:
    row = db.get(Project, project_id)
    if row is None:
        raise ValueError("Project not found")
    fields = body.model_fields_set
    if "name" in fields and body.name is not None:
        name = _clean_name(body.name)
        exists = db.execute(
            select(Project.id).where(func.lower(Project.name) == name.lower(), Project.id != project_id)
        ).scalar_one_or_none()
        if exists is not None:
            raise ValueError("A project with this name already exists")
        row.name = name
    if "description" in fields:
        row.description = body.description or ""
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
    db.commit()
    db.refresh(row)
    return row


def delete_project(db: Session, project_id: int) -> None:
    row = db.get(Project, project_id)
    if row is None:
        raise ValueError("Project not found")
    from app.services import recurrence_service
    recurrence_service.move_project_templates_to_admin(db, project_id)
    db.delete(row)
    db.commit()


def _load_task(db: Session, task_id: int) -> Task:
    row = db.execute(
        select(Task)
        .options(selectinload(Task.subtasks), selectinload(Task.project), selectinload(Task.task_type))
        .where(Task.id == task_id)
    ).scalar_one_or_none()
    if row is None:
        raise ValueError("Task not found")
    return row


def _validate_refs(db: Session, project_id: int | None, task_type_id: int | None) -> None:
    if project_id is not None and db.get(Project, project_id) is None:
        raise ValueError("Project not found")
    if task_type_id is not None and db.get(TaskType, task_type_id) is None:
        raise ValueError("Task type not found")


def _next_position(db: Session, status: TaskStatus) -> int:
    value = db.execute(
        select(func.max(Task.position)).where(Task.parent_id.is_(None), Task.status == status)
    ).scalar_one_or_none()
    return (value or 0) + 1


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


def purge_expired_trash(db: Session) -> None:
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


def _is_overdue(task: Task, settings: Settings, now: dt.datetime) -> bool:
    if task.status == TaskStatus.completed or task.archived_at is not None or task.deleted_at is not None:
        return False
    boundary = _deadline_boundary(task, settings)
    return boundary is not None and now >= boundary


def _to_read(
    task: Task,
    settings: Settings,
    now: dt.datetime,
    include_children: bool = True,
    include_deleted_children: bool = False,
) -> TaskRead:
    children = []
    if include_children:
        children = [
            _to_read(child, settings, now, False)
            for child in sorted(task.subtasks, key=lambda item: (item.position, item.id))
            if child.deleted_at is None or include_deleted_children
        ]
    return TaskRead(
        id=task.id,
        parent_id=task.parent_id,
        parent_title=task.parent.title if task.parent is not None else None,
        project_id=task.project_id,
        project=ProjectRead.model_validate(task.project) if task.project is not None else None,
        task_type_id=task.task_type_id,
        task_type=task.task_type,
        recurring_template_id=task.recurring_template_id,
        recurring_template_title=task.recurring_template.title if task.recurring_template is not None else None,
        occurrence_key=task.occurrence_key,
        recurrence_kind=task.recurrence_kind,
        quota_period_start=task.quota_period_start,
        quota_period_end=task.quota_period_end,
        expected_sessions=task.expected_sessions,
        session_index=task.session_index,
        quota_completed=(
            sum(child.deleted_at is None and child.status == TaskStatus.completed for child in task.subtasks)
            if task.recurrence_kind == "quota_parent" else None
        ),
        title=task.title,
        description=task.description,
        ready_to_plan=task.ready_to_plan,
        is_blocked=task.is_blocked,
        blocking_reason=task.blocking_reason,
        status=task.status,
        urgency=task.urgency,
        importance=task.importance,
        deadline_date=task.deadline_date,
        deadline_at=task.deadline_at,
        reminder_at=task.reminder_at,
        reminder_delivered_at=task.reminder_delivered_at,
        position=task.position,
        archived_at=task.archived_at,
        deleted_at=task.deleted_at,
        created_at=task.created_at,
        updated_at=task.updated_at,
        overdue=_is_overdue(task, settings, now),
        subtasks=children,
    )


def list_tasks(db: Session, state: str, settings: Settings) -> list[TaskRead]:
    purge_expired_trash(db)
    if state == "active":
        from app.services import recurrence_service
        recurrence_service.synchronize(db, settings)
    options = (
        selectinload(Task.project), selectinload(Task.task_type), selectinload(Task.recurring_template),
        selectinload(Task.subtasks).selectinload(Task.task_type),
        selectinload(Task.subtasks).selectinload(Task.recurring_template),
    )
    if state == "active":
        stmt = select(Task).options(*options).where(
            Task.parent_id.is_(None), Task.archived_at.is_(None), Task.deleted_at.is_(None)
        )
    elif state == "archived":
        stmt = select(Task).options(*options).where(
            Task.parent_id.is_(None), Task.archived_at.is_not(None), Task.deleted_at.is_(None)
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


def due_reminders(db: Session, settings: Settings) -> list[ReminderRead]:
    now = now_in_tz(settings.app_timezone)
    rows = list(
        db.execute(
            select(Task).where(
                Task.reminder_at.is_not(None),
                Task.reminder_delivered_at.is_(None),
                Task.reminder_at <= now,
                Task.status != TaskStatus.completed,
                Task.archived_at.is_(None),
                Task.deleted_at.is_(None),
            )
        ).scalars()
    )
    return [
        ReminderRead(
            id=row.id,
            title=row.title,
            deadline_date=row.deadline_date,
            deadline_at=row.deadline_at,
            reminder_at=row.reminder_at,
        )
        for row in rows
        if not _is_overdue(row, settings, now)
    ]


def acknowledge_reminder(db: Session, task_id: int) -> None:
    row = _load_task(db, task_id)
    if row.reminder_at is None:
        raise ValueError("Task has no reminder")
    row.reminder_delivered_at = _utc_now()
    db.commit()


def task_type_counts(db: Session) -> dict[int, int]:
    stmt = select(Task.task_type_id, func.count(Task.id)).where(Task.task_type_id.is_not(None)).group_by(Task.task_type_id)
    return {type_id: count for type_id, count in db.execute(stmt).all()}


def clear_task_type_references(db: Session, task_type_id: int) -> None:
    db.execute(
        Task.__table__.update().where(Task.task_type_id == task_type_id).values(task_type_id=None)
    )
