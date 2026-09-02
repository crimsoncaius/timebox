from __future__ import annotations

import datetime as dt

from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import now_in_tz
from app.models.battle_plan import Project, Task, TaskStatus
from app.models.time_block import BlockLane, TimeBlock
from app.models.task_type import TaskType
from app.schemas.battle_plan import ProjectRead, SubtaskRead, TaskOccurrenceIdentityRead, TaskRead

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
            task.deadline_date + dt.timedelta(days=1),
            dt.time.min,
            tzinfo=zone_now.tzinfo,
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


def _task_select(task_id: int, *, for_update: bool = False):
    statement = (
        select(Task)
        .options(
            selectinload(Task.subtasks),
            selectinload(Task.subtasks).selectinload(Task.occurrence),
            selectinload(Task.subtasks).selectinload(Task.time_blocks).selectinload(TimeBlock.day),
            selectinload(Task.subtasks)
            .selectinload(Task.time_blocks)
            .selectinload(TimeBlock.completion_actual),
            selectinload(Task.project),
            selectinload(Task.occurrence),
            selectinload(Task.task_type),
            selectinload(Task.time_blocks).selectinload(TimeBlock.day),
            selectinload(Task.time_blocks)
            .selectinload(TimeBlock.completion_actual),
        )
        .where(Task.id == task_id)
    )
    return (
        statement.execution_options(populate_existing=True).with_for_update()
        if for_update
        else statement
    )


def _load_task(db: Session, task_id: int, *, for_update: bool = False) -> Task:
    row = db.execute(
        _task_select(task_id, for_update=for_update)
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
    outstanding_occurrence_count: int = 1,
) -> TaskRead:
    visible_children = [
        child
        for child in sorted(task.subtasks, key=lambda item: (item.position, item.id))
        if child.deleted_at is None or include_deleted_children
    ] if include_children else []
    is_quota_tracker = task.recurrence_kind == "quota_parent"
    subtasks = (
        [
            SubtaskRead(
                id=child.id,
                parent_task_id=task.id,
                title=child.title,
                checked=child.checked,
                effectively_resolved=(task.status == TaskStatus.completed or child.checked),
                position=child.position,
                created_at=child.created_at,
                updated_at=child.updated_at,
            )
            for child in visible_children
        ]
        if not is_quota_tracker
        else []
    )
    session_tasks = (
        [_to_read(child, settings, now, False) for child in visible_children]
        if is_quota_tracker
        else []
    )
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
        completed_at=task.completed_at,
        version=task.version,
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
        planned_dates=sorted(
            {
                block.day.date
                for block in task.time_blocks
                if block.lane == BlockLane.planned
            }
        ),
        occurrence=(
            TaskOccurrenceIdentityRead(
                id=task.occurrence.id,
                recurring_task_series_id=task.occurrence.template_id,
                occurrence_key=task.occurrence.occurrence_key,
            )
            if task.occurrence is not None
            else None
        ),
        subtasks=subtasks,
        session_tasks=session_tasks,
        outstanding_occurrence_count=outstanding_occurrence_count,
    )
