from __future__ import annotations

import datetime as dt

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.task_type import TaskType
from app.models.time_block import TimeBlock
from app.schemas.task_type import TaskTypeCreate, TaskTypePatch
from app.services.task_type_paths import canonicalize_task_type_path


def _utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def list_task_types(db: Session) -> list[TaskType]:
    stmt = select(TaskType).order_by(func.lower(TaskType.name), TaskType.id)
    return list(db.execute(stmt).scalars().all())


def get_task_type(db: Session, task_type_id: int) -> TaskType | None:
    return db.get(TaskType, task_type_id)


def _name_taken(db: Session, name: str, exclude_id: int | None = None) -> bool:
    stmt = select(TaskType.id).where(TaskType.name == name)
    if exclude_id is not None:
        stmt = stmt.where(TaskType.id != exclude_id)
    return db.execute(stmt).scalar_one_or_none() is not None


def create_task_type(db: Session, body: TaskTypeCreate) -> TaskType:
    name = canonicalize_task_type_path(body.name)
    if _name_taken(db, name):
        raise ValueError("A task type with this name already exists")
    row = TaskType(name=name)
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def patch_task_type(db: Session, task_type_id: int, body: TaskTypePatch) -> TaskType:
    row = get_task_type(db, task_type_id)
    if row is None:
        raise ValueError("Task type not found")
    if body.name is None:
        return row
    name = canonicalize_task_type_path(body.name)
    if _name_taken(db, name, exclude_id=task_type_id):
        raise ValueError("A task type with this name already exists")
    row.name = name
    row.updated_at = _utc_now()
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def delete_task_type(db: Session, task_type_id: int) -> None:
    row = get_task_type(db, task_type_id)
    if row is None:
        raise ValueError("Task type not found")
    in_use = db.execute(
        select(TimeBlock.id).where(TimeBlock.task_type_id == task_type_id).limit(1)
    ).scalar_one_or_none()
    if in_use is not None:
        raise ValueError("TASK_TYPE_IN_USE")
    db.delete(row)
    db.commit()
