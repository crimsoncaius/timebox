from __future__ import annotations

from datetime import timedelta
from uuid import uuid4

from sqlalchemy import or_, select, update
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.time import as_utc, utc_now
from app.models.battle_plan import Task, TaskStatus
from app.schemas.battle_plan import ReminderRead

DELIVERY_WINDOW = timedelta(minutes=30)
CLAIM_DURATION = timedelta(minutes=2)


def _active(row: Task) -> bool:
    return (
        row.status != TaskStatus.completed
        and row.archived_at is None
        and row.deleted_at is None
    )


def _same_reminder(row: Task | None, reminder_at) -> bool:
    return row is not None and row.reminder_at is not None and as_utc(row.reminder_at) == as_utc(reminder_at)


def due_reminders(db: Session, settings: Settings) -> list[ReminderRead]:
    del settings  # The user-chosen instant, rather than the reporting time zone, owns expiry.
    now = utc_now()
    rows = list(db.scalars(select(Task).where(
        Task.reminder_at.is_not(None),
        Task.reminder_delivered_at.is_(None),
        Task.reminder_skipped_at.is_(None),
        Task.status != TaskStatus.completed,
        Task.archived_at.is_(None),
        Task.deleted_at.is_(None),
    )))
    due = []
    expired = False
    for row in rows:
        scheduled = as_utc(row.reminder_at)
        if scheduled > now:
            continue
        if scheduled + DELIVERY_WINDOW < now:
            # A live claim may already have handed off just before the cutoff.
            if row.reminder_claim_until is None or as_utc(row.reminder_claim_until) <= now:
                row.reminder_skipped_at = now
                row.reminder_claim_token = None
                row.reminder_claim_until = None
                expired = True
            continue
        if row.reminder_claim_until is not None and as_utc(row.reminder_claim_until) > now:
            continue
        due.append(ReminderRead(
            id=row.id,
            title=row.title,
            deadline_date=row.deadline_date,
            deadline_at=row.deadline_at,
            reminder_at=row.reminder_at,
        ))
    if expired:
        db.commit()
    return due


def claim_reminder(db: Session, task_id: int, reminder_at) -> str:
    now = utc_now()
    row = db.get(Task, task_id)
    if (
        not _same_reminder(row, reminder_at)
        or not _active(row)
        or row.reminder_delivered_at is not None
        or row.reminder_skipped_at is not None
        or not as_utc(row.reminder_at) <= now <= as_utc(row.reminder_at) + DELIVERY_WINDOW
    ):
        raise ValueError("Reminder is no longer eligible")
    token = str(uuid4())
    result = db.execute(update(Task).where(
        Task.id == task_id,
        Task.reminder_at == row.reminder_at,
        Task.reminder_delivered_at.is_(None),
        Task.reminder_skipped_at.is_(None),
        Task.status != TaskStatus.completed,
        Task.archived_at.is_(None),
        Task.deleted_at.is_(None),
        or_(Task.reminder_claim_until.is_(None), Task.reminder_claim_until <= now),
    ).values(reminder_claim_token=token, reminder_claim_until=now + CLAIM_DURATION).execution_options(synchronize_session=False))
    if result.rowcount != 1:
        db.rollback()
        raise ValueError("Reminder is already claimed")
    db.commit()
    return token


def acknowledge_reminder(db: Session, task_id: int, reminder_at, token: str) -> None:
    row = db.get(Task, task_id)
    if not _same_reminder(row, reminder_at):
        raise ValueError("Reminder changed before delivery was acknowledged")
    result = db.execute(update(Task).where(
        Task.id == task_id,
        Task.reminder_at == row.reminder_at,
        Task.reminder_claim_token == token,
        Task.reminder_delivered_at.is_(None),
        Task.reminder_skipped_at.is_(None),
        Task.status != TaskStatus.completed,
        Task.archived_at.is_(None),
        Task.deleted_at.is_(None),
    ).values(
        reminder_delivered_at=utc_now(),
        reminder_claim_token=None,
        reminder_claim_until=None,
    ).execution_options(synchronize_session=False))
    if result.rowcount != 1:
        db.rollback()
        raise ValueError("Reminder claim is no longer valid")
    db.commit()


def release_reminder(db: Session, task_id: int, reminder_at, token: str) -> None:
    row = db.get(Task, task_id)
    if not _same_reminder(row, reminder_at):
        return
    db.execute(update(Task).where(
        Task.id == task_id,
        Task.reminder_at == row.reminder_at,
        Task.reminder_claim_token == token,
    ).values(reminder_claim_token=None, reminder_claim_until=None).execution_options(synchronize_session=False))
    db.commit()
