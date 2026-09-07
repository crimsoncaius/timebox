from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.core.time import today_in_tz
from app.db.session import get_db
from app.models.battle_plan import RecurrenceStatus
from app.schemas.battle_plan import (
    RecurrencePreviewRead,
    RecurrencePreviewRequest,
    RecurringTemplateCreate,
    RecurringTemplatePatch,
    RecurringTemplateRead,
)
from app.services import day_service
from app.services import recurrence_service as service

router = APIRouter(prefix="/recurring-templates", tags=["recurring-templates"])


def _error(exc: ValueError) -> HTTPException:
    message = str(exc)
    if message == "Recurring template not found":
        return HTTPException(status_code=404, detail=message)
    if message.startswith("BACKFILL_CONFIRMATION_REQUIRED:"):
        _, cycles, tasks = message.split(":")
        return HTTPException(
            status_code=409,
            detail={"code": "backfill_confirmation_required", "past_cycles": int(cycles), "past_tasks": int(tasks)},
        )
    return HTTPException(status_code=422, detail=message)


@router.post("/preview", response_model=RecurrencePreviewRead)
def preview_rule(
    body: RecurrencePreviewRequest,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> RecurrencePreviewRead:
    app_settings = day_service.get_or_create_app_settings(db)
    return service.preview(body, today_in_tz(settings.app_timezone), app_settings.week_start)


@router.get("", response_model=list[RecurringTemplateRead])
def list_templates(
    status: RecurrenceStatus = Query(RecurrenceStatus.active),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> list[RecurringTemplateRead]:
    return service.list_templates(db, status, settings)


@router.post("", response_model=RecurringTemplateRead, status_code=201)
def create_template(
    body: RecurringTemplateCreate,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> RecurringTemplateRead:
    try:
        row = service.create_template(db, body, settings)
        return service.to_read(db, row, settings)
    except ValueError as exc:
        raise _error(exc) from exc


@router.get("/{template_id}", response_model=RecurringTemplateRead)
def get_template(
    template_id: int,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> RecurringTemplateRead:
    try:
        return service.get_template(db, template_id, settings)
    except ValueError as exc:
        raise _error(exc) from exc


@router.patch("/{template_id}", response_model=RecurringTemplateRead)
def patch_template(
    template_id: int,
    body: RecurringTemplatePatch,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> RecurringTemplateRead:
    try:
        row = service.patch_template(db, template_id, body, settings)
        return service.to_read(db, row, settings)
    except (ValueError, TypeError) as exc:
        raise _error(ValueError(str(exc))) from exc


def _lifecycle(action, template_id: int, db: Session, settings: Settings) -> RecurringTemplateRead:
    try:
        row = action(db, template_id, settings)
        return service.to_read(db, row, settings)
    except ValueError as exc:
        raise _error(exc) from exc


@router.post("/{template_id}/pause", response_model=RecurringTemplateRead)
def pause_template(template_id: int, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return _lifecycle(service.pause_template, template_id, db, settings)


@router.post("/{template_id}/resume", response_model=RecurringTemplateRead)
def resume_template(template_id: int, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return _lifecycle(service.resume_template, template_id, db, settings)


@router.post("/{template_id}/end", response_model=RecurringTemplateRead)
def end_template(template_id: int, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return _lifecycle(service.end_template, template_id, db, settings)
