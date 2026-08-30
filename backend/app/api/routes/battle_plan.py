from __future__ import annotations

import datetime as dt

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.core.time import now_in_tz
from app.db.session import get_db
from app.schemas.battle_plan import (
    ProjectCreate,
    ProjectPatch,
    ProjectRead,
    ReminderRead,
    TaskCreate,
    TaskCompletionRead,
    TaskCompletionUndo,
    TaskIds,
    TaskListRead,
    TaskPatch,
    TaskRead,
    TaskReorder,
    SubtaskRead,
)
from app.services import battle_plan_service as service
from app.services import task_completion_service

router = APIRouter(tags=["battle-plan"])


def capture_utc_now() -> dt.datetime:
    """Clock seam for the single Task Completion instant."""

    return dt.datetime.now(dt.timezone.utc)


def _not_found_or_unprocessable(exc: ValueError) -> HTTPException:
    message = str(exc)
    status = 404 if message in {"Project not found", "Task not found", "Task type not found"} else 422
    return HTTPException(status_code=status, detail=message)


@router.get("/projects", response_model=list[ProjectRead])
def list_projects(db: Session = Depends(get_db)):
    return service.list_projects(db)


@router.post("/projects", response_model=ProjectRead, status_code=201)
def create_project(body: ProjectCreate, db: Session = Depends(get_db)):
    try:
        return service.create_project(db, body)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.patch("/projects/{project_id}", response_model=ProjectRead)
def patch_project(project_id: int, body: ProjectPatch, db: Session = Depends(get_db)):
    try:
        return service.patch_project(db, project_id, body)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.delete("/projects/{project_id}", status_code=204)
def delete_project(project_id: int, db: Session = Depends(get_db)) -> Response:
    try:
        service.delete_project(db, project_id)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc
    return Response(status_code=204)


@router.get("/tasks", response_model=TaskListRead)
def list_tasks(
    state: str = Query("active", pattern="^(active|archived|trash)$"),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> TaskListRead:
    now = now_in_tz(settings.app_timezone)
    try:
        items = service.list_tasks(db, state, settings)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc
    return TaskListRead(items=items, timezone=settings.app_timezone, server_now_iso=now.isoformat())


@router.post("/tasks", response_model=TaskRead, status_code=201)
def create_task(
    body: TaskCreate,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> TaskRead:
    try:
        row = service.create_task(db, body, settings)
        return service._to_read(row, settings, now_in_tz(settings.app_timezone))
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.patch("/tasks/{task_id}", response_model=TaskRead)
def patch_task(
    task_id: int,
    body: TaskPatch,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> TaskRead:
    try:
        row = service.patch_task(db, task_id, body, settings)
        return service._to_read(row, settings, now_in_tz(settings.app_timezone))
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.post("/subtasks/{subtask_id}/check", response_model=SubtaskRead)
def check_subtask(subtask_id: int, db: Session = Depends(get_db)) -> SubtaskRead:
    try:
        return task_completion_service.set_subtask_checked(db, subtask_id, checked=True)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.post("/subtasks/{subtask_id}/uncheck", response_model=SubtaskRead)
def uncheck_subtask(subtask_id: int, db: Session = Depends(get_db)) -> SubtaskRead:
    try:
        return task_completion_service.set_subtask_checked(db, subtask_id, checked=False)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.post("/tasks/{task_id}/complete", response_model=TaskCompletionRead)
def complete_task(
    task_id: int,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
    captured_at: dt.datetime = Depends(capture_utc_now),
) -> TaskCompletionRead:
    try:
        row, token, removed_ids = task_completion_service.complete_task(
            db, task_id, captured_at, settings
        )
        return TaskCompletionRead(
            task=service._to_read(row, settings, now_in_tz(settings.app_timezone)),
            undo_token=token,
            removed_planned_block_ids=removed_ids,
        )
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.post("/tasks/{task_id}/reopen", response_model=TaskRead)
def reopen_task(
    task_id: int,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> TaskRead:
    try:
        row = task_completion_service.reopen_task(db, task_id)
        return service._to_read(row, settings, now_in_tz(settings.app_timezone))
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.post("/tasks/{task_id}/undo-completion", response_model=TaskRead)
def undo_task_completion(
    task_id: int,
    body: TaskCompletionUndo,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> TaskRead:
    try:
        row = task_completion_service.undo_task_completion(db, task_id, body.undo_token)
        return service._to_read(row, settings, now_in_tz(settings.app_timezone))
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.post("/tasks/reorder", status_code=204)
def reorder_tasks(body: TaskReorder, db: Session = Depends(get_db)) -> Response:
    try:
        service.reorder_tasks(db, body.placements)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc
    return Response(status_code=204)


@router.post("/tasks/archive-completed", status_code=204)
def archive_completed(body: TaskIds, db: Session = Depends(get_db)) -> Response:
    try:
        service.archive_tasks(db, body.task_ids)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc
    return Response(status_code=204)


@router.post("/tasks/{task_id}/unarchive", status_code=204)
def unarchive_task(task_id: int, db: Session = Depends(get_db)) -> Response:
    try:
        service.unarchive_task(db, task_id)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc
    return Response(status_code=204)


@router.delete("/tasks/{task_id}", response_model=TaskRead)
def trash_task(
    task_id: int,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> TaskRead:
    try:
        row = service.trash_task(db, task_id)
        return service._to_read(row, settings, now_in_tz(settings.app_timezone))
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc


@router.post("/tasks/{task_id}/restore", status_code=204)
def restore_task(task_id: int, db: Session = Depends(get_db)) -> Response:
    try:
        service.restore_task(db, task_id)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc
    return Response(status_code=204)


@router.delete("/tasks/{task_id}/permanent", status_code=204)
def permanently_delete_task(task_id: int, db: Session = Depends(get_db)) -> Response:
    try:
        service.permanently_delete_task(db, task_id)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc
    return Response(status_code=204)


@router.get("/reminders/due", response_model=list[ReminderRead])
def due_reminders(
    db: Session = Depends(get_db), settings: Settings = Depends(get_settings)
) -> list[ReminderRead]:
    return service.due_reminders(db, settings)


@router.post("/reminders/{task_id}/delivered", status_code=204)
def delivered_reminder(task_id: int, db: Session = Depends(get_db)) -> Response:
    try:
        service.acknowledge_reminder(db, task_id)
    except ValueError as exc:
        raise _not_found_or_unprocessable(exc) from exc
    return Response(status_code=204)
