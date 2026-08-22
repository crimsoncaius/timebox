from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.time import now_in_tz
from app.models.battle_plan import Task, TaskStatus
from app.schemas.battle_plan import ReminderRead
from app.services.battle_plan._shared import _is_overdue, _load_task, _utc_now


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
