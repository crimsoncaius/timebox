from __future__ import annotations

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.models.battle_plan import RecurrenceOccurrence, Task


def protect_task_occurrence(db: Session, task: Task | int) -> None:
    """Permanently protect the scheduled occurrence containing ``task``.

    Structural protection belongs to the internal occurrence ledger rather than
    mutable Task fields.  That makes it survive reopen, Undo, block deletion, and
    other later state changes without exposing another public lifecycle concept.
    """

    row = task if isinstance(task, Task) else db.get(Task, task)
    if row is None:
        return
    if row.recurrence_kind == "checklist" and row.parent_id is not None:
        root_id = row.parent_id
    elif row.recurrence_kind == "scheduled" and row.parent_id is None:
        root_id = row.id
    else:
        # Quota Trackers and Session Tasks retain their existing derivation rules.
        return
    db.execute(
        update(RecurrenceOccurrence)
        .where(RecurrenceOccurrence.task_id == root_id)
        .values(structurally_protected=True)
    )


def occurrence_is_protected(db: Session, task_id: int) -> bool:
    return bool(
        db.execute(
            select(RecurrenceOccurrence.structurally_protected).where(
                RecurrenceOccurrence.task_id == task_id
            )
        ).scalar_one_or_none()
    )
