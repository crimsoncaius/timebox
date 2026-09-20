from __future__ import annotations

from sqlalchemy import Select, select

from app.models.battle_plan import Task


def task_select(task_id: int, *, for_update: bool = False) -> Select[tuple[Task]]:
    """Select one Task by id, optionally taking the row lock.

    Commands that touch Task eligibility acquire this lock first; the rest of
    the codebase keeps the Task -> Planned -> Actual order from there.
    """
    statement = select(Task).where(Task.id == task_id)
    return statement.with_for_update() if for_update else statement
