from __future__ import annotations

import json

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.battle_plan import Task, TaskStatus
from app.services.recurrence.common import CUSTOMIZABLE_FIELDS, _json_list


def record_task_overrides(task: Task, fields: set[str]) -> None:
    if task.recurring_template_id is None:
        return
    current = set(_json_list(task.recurrence_overrides_json))
    current.update(fields & CUSTOMIZABLE_FIELDS)
    task.recurrence_overrides_json = json.dumps(sorted(current))


def quota_progress(db: Session, parent_id: int) -> int:
    return db.execute(select(func.count(Task.id)).where(
        Task.parent_id == parent_id,
        Task.deleted_at.is_(None),
        Task.status == TaskStatus.completed,
    )).scalar_one()
