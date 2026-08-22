from __future__ import annotations

from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session, selectinload

from app.models.battle_plan import Project, RecurringChecklistItem, RecurringTemplate, RecurrenceMode, Task, TaskStatus
from app.models.task_type import TaskType


def _load_template(db: Session, template_id: int) -> RecurringTemplate:
    row = db.execute(
        select(RecurringTemplate)
        .where(RecurringTemplate.id == template_id)
        .options(
            selectinload(RecurringTemplate.project),
            selectinload(RecurringTemplate.task_type),
            selectinload(RecurringTemplate.checklist_items),
        )
    ).scalar_one_or_none()
    if row is None:
        raise ValueError("Recurring template not found")
    return row


def _validate_refs(db: Session, project_id: int | None, task_type_id: int | None) -> None:
    if project_id is not None and db.get(Project, project_id) is None:
        raise ValueError("Project not found")
    if task_type_id is not None and db.get(TaskType, task_type_id) is None:
        raise ValueError("Task type not found")


def _replace_checklist(db: Session, row: RecurringTemplate, titles: list[str]) -> None:
    cleaned = [title.strip() for title in titles if title.strip()]
    db.execute(delete(RecurringChecklistItem).where(RecurringChecklistItem.template_id == row.id))
    for position, title in enumerate(cleaned):
        db.add(RecurringChecklistItem(template_id=row.id, title=title, position=position))


def _next_position(db: Session, status: TaskStatus = TaskStatus.open) -> int:
    maximum = db.execute(
        select(func.max(Task.position)).where(Task.parent_id.is_(None), Task.status == status)
    ).scalar_one_or_none()
    return (maximum or 0) + 1


def _task_kwargs(template: RecurringTemplate, window) -> dict:
    return {
        "project_id": template.project_id,
        "task_type_id": template.task_type_id,
        "title": template.title,
        "description": template.description,
        "status": TaskStatus.open,
        "urgency": template.urgency,
        "importance": template.importance,
        "deadline_date": window.end,
        "recurring_template_id": template.id,
        "occurrence_key": window.key,
        "quota_period_start": window.start if template.mode == RecurrenceMode.quota else None,
        "quota_period_end": window.end if template.mode == RecurrenceMode.quota else None,
        "recurrence_overrides_json": "[]",
    }
