from __future__ import annotations

import datetime as dt

from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.models.task_type import TaskType
from app.models.time_block import TimeBlock
from app.schemas.task_type import TaskTypeCreate, TaskTypePatch
from app.services.task_type_paths import canonicalize_task_type_path, path_prefixes


def _descendants_like(prefix: str) -> tuple[str, str]:
    """LIKE pattern for `prefix/...` children; escapes % and _ so SQLite LIKE is path-safe."""
    escaped = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"{escaped}/%", "\\"


def _utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def list_task_types(db: Session) -> list[TaskType]:
    stmt = select(TaskType).order_by(func.lower(TaskType.name), TaskType.id)
    return list(db.execute(stmt).scalars().all())


def get_task_type(db: Session, task_type_id: int) -> TaskType | None:
    return db.get(TaskType, task_type_id)


def create_task_type(db: Session, body: TaskTypeCreate) -> TaskType:
    path = canonicalize_task_type_path(body.name)
    prefixes = path_prefixes(path)
    existing_rows = list(
        db.execute(select(TaskType).where(TaskType.name.in_(prefixes))).scalars().all()
    )
    existing_by_name = {r.name: r for r in existing_rows}
    if path in existing_by_name:
        raise ValueError("A task type with this name already exists")
    for prefix in prefixes:
        if prefix in existing_by_name:
            continue
        row = TaskType(name=prefix)
        db.add(row)
        db.flush()
        existing_by_name[prefix] = row
    db.commit()
    leaf = existing_by_name[path]
    db.refresh(leaf)
    return leaf


def patch_task_type(db: Session, task_type_id: int, body: TaskTypePatch) -> TaskType:
    row = get_task_type(db, task_type_id)
    if row is None:
        raise ValueError("Task type not found")
    if body.name is None:
        return row
    old_path = row.name
    new_path = canonicalize_task_type_path(body.name)
    if new_path == old_path:
        return row

    for prefix in path_prefixes(new_path)[:-1]:
        exists = db.execute(select(TaskType.id).where(TaskType.name == prefix).limit(1)).scalar_one_or_none()
        if exists is None:
            db.add(TaskType(name=prefix))
            db.flush()

    child_pat, child_esc = _descendants_like(old_path)
    branch_rows = list(
        db.execute(
            select(TaskType)
            .where(or_(TaskType.name == old_path, TaskType.name.like(child_pat, escape=child_esc)))
            .order_by(func.length(TaskType.name), TaskType.id)
        ).scalars()
    )
    replacements: dict[int, str] = {}
    for br in branch_rows:
        if br.name == old_path:
            replacements[br.id] = new_path
        else:
            replacements[br.id] = new_path + br.name[len(old_path) :]

    collision_names = set(replacements.values())
    branch_ids = set(replacements.keys())
    conflict = db.execute(
        select(TaskType.id).where(TaskType.name.in_(collision_names), ~TaskType.id.in_(branch_ids)).limit(1)
    ).scalar_one_or_none()
    if conflict is not None:
        raise ValueError("A task type with this path already exists")

    now = _utc_now()
    for br in branch_rows:
        br.name = replacements[br.id]
        br.updated_at = now
        db.add(br)
    db.commit()
    db.refresh(row)
    return row


def delete_task_type(db: Session, task_type_id: int) -> None:
    row = get_task_type(db, task_type_id)
    if row is None:
        raise ValueError("Task type not found")
    desc_pat, desc_esc = _descendants_like(row.name)
    has_descendants = db.execute(
        select(TaskType.id).where(TaskType.name.like(desc_pat, escape=desc_esc)).limit(1)
    ).scalar_one_or_none()
    if has_descendants is not None:
        raise ValueError("TASK_TYPE_HAS_DESCENDANTS")
    in_use = db.execute(
        select(TimeBlock.id).where(TimeBlock.task_type_id == task_type_id).limit(1)
    ).scalar_one_or_none()
    if in_use is not None:
        raise ValueError("TASK_TYPE_IN_USE")
    db.delete(row)
    db.commit()
