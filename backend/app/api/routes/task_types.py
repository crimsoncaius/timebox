from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.schemas.task_type import TaskTypeCreate, TaskTypePatch, TaskTypeRead
from app.services import task_type_service

router = APIRouter(prefix="/task-types", tags=["task-types"])


@router.get("", response_model=list[TaskTypeRead])
def list_task_types(db: Session = Depends(get_db)) -> list[TaskTypeRead]:
    rows = task_type_service.list_task_types(db)
    return [TaskTypeRead.model_validate(r) for r in rows]


@router.post("", response_model=TaskTypeRead)
def create_task_type(
    body: TaskTypeCreate,
    db: Session = Depends(get_db),
) -> TaskTypeRead:
    try:
        row = task_type_service.create_task_type(db, body)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return TaskTypeRead.model_validate(row)


@router.patch("/{task_type_id}", response_model=TaskTypeRead)
def patch_task_type(
    task_type_id: int,
    body: TaskTypePatch,
    db: Session = Depends(get_db),
) -> TaskTypeRead:
    try:
        row = task_type_service.patch_task_type(db, task_type_id, body)
    except ValueError as e:
        msg = str(e)
        if msg == "Task type not found":
            raise HTTPException(status_code=404, detail=msg) from e
        raise HTTPException(status_code=422, detail=msg) from e
    return TaskTypeRead.model_validate(row)


@router.delete("/{task_type_id}", status_code=204)
def delete_task_type(
    task_type_id: int,
    cascade_blocks: bool = Query(False),
    migrate_blocks_to: int | None = Query(None),
    db: Session = Depends(get_db),
) -> None:
    if cascade_blocks and migrate_blocks_to is not None:
        raise HTTPException(
            status_code=422,
            detail="Cannot use cascade_blocks and migrate_blocks_to together",
        )
    try:
        task_type_service.delete_task_type(
            db,
            task_type_id,
            cascade_blocks=cascade_blocks,
            migrate_blocks_to=migrate_blocks_to,
        )
    except ValueError as e:
        msg = str(e)
        if msg == "TASK_TYPE_IN_USE":
            raise HTTPException(
                status_code=409,
                detail="Task type is still used by existing blocks",
            ) from e
        if msg == "TASK_TYPE_HAS_DESCENDANTS":
            raise HTTPException(
                status_code=409,
                detail="Task type still has saved subpaths",
            ) from e
        if msg == "Task type not found":
            raise HTTPException(status_code=404, detail=msg) from e
        raise HTTPException(status_code=422, detail=msg) from e
