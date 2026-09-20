from __future__ import annotations

import datetime as dt

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError

from app.api.deps import utc_clock_seam
from app.api.errors import domain_http_error
from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.schemas.time_block import (
    ActualBlockCreate,
    ActualBlockPatch,
    ActualBlockRead,
    ActualBlockRelink,
    ActualBlockStart,
    RecordActualAsPlannedUndo,
    RecordPlannedRequest,
)
from app.services import actual_block_service
from app.services import planned_recording

router = APIRouter(prefix="/actual-blocks", tags=["actual-blocks"])
planned_router = APIRouter(prefix="/planned-blocks", tags=["planned-blocks"])


#: Clock boundary for commands that capture one authoritative instant.
capture_utc_now = utc_clock_seam()


@router.post("", response_model=ActualBlockRead, status_code=201)
def create_actual_block(
    body: ActualBlockCreate,
    db: Session = Depends(get_db),
) -> ActualBlockRead:
    try:
        return actual_block_service.create_actual_block(db, body)
    except ValueError as exc:
        raise domain_http_error(exc) from exc


@router.post("/start", response_model=ActualBlockRead, status_code=201)
def start_actual_block(
    body: ActualBlockStart,
    db: Session = Depends(get_db),
    captured_at: dt.datetime = Depends(capture_utc_now),
) -> ActualBlockRead:
    try:
        return actual_block_service.start_actual_block(db, body, captured_at)
    except ValueError as exc:
        raise domain_http_error(exc) from exc


@router.post("/{actual_block_id}/finish", response_model=ActualBlockRead)
def finish_actual_block(
    actual_block_id: int,
    db: Session = Depends(get_db),
    captured_at: dt.datetime = Depends(capture_utc_now),
) -> ActualBlockRead:
    try:
        return actual_block_service.finish_actual_block(db, actual_block_id, captured_at)
    except ValueError as exc:
        raise domain_http_error(exc) from exc


@router.patch("/{actual_block_id}", response_model=ActualBlockRead)
def patch_actual_block(
    actual_block_id: int,
    body: ActualBlockPatch,
    db: Session = Depends(get_db),
) -> ActualBlockRead:
    try:
        return actual_block_service.patch_actual_block(db, actual_block_id, body)
    except ValueError as exc:
        raise domain_http_error(exc) from exc


@router.delete("/{actual_block_id}", status_code=204)
def delete_actual_block(
    actual_block_id: int,
    db: Session = Depends(get_db),
) -> Response:
    try:
        actual_block_service.delete_actual_block(db, actual_block_id)
        return Response(status_code=204)
    except ValueError as exc:
        raise domain_http_error(exc) from exc


@router.post("/{actual_block_id}/detach", response_model=ActualBlockRead)
def detach_actual_block(
    actual_block_id: int,
    db: Session = Depends(get_db),
) -> ActualBlockRead:
    try:
        return actual_block_service.detach_actual_block(db, actual_block_id)
    except ValueError as exc:
        raise domain_http_error(exc) from exc


@router.post("/{actual_block_id}/relink", response_model=ActualBlockRead)
def relink_actual_block(
    actual_block_id: int,
    body: ActualBlockRelink,
    db: Session = Depends(get_db),
) -> ActualBlockRead:
    try:
        return actual_block_service.relink_actual_block(
            db, actual_block_id, body.planned_block_id
        )
    except ValueError as exc:
        raise domain_http_error(exc) from exc


@planned_router.post(
    "/{planned_block_id}/record-actual-as-planned",
    status_code=201,
)
def record_actual_as_planned(
    planned_block_id: int,
    body: RecordPlannedRequest = RecordPlannedRequest(),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
    captured_at: dt.datetime = Depends(capture_utc_now),
) -> dict:
    try:
        return planned_recording.record(db, planned_block_id, settings, captured_at, body)
    except ValueError as exc:
        raise domain_http_error(exc) from exc
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(409, "Recorded activity changed. Review the recording again.") from exc


@planned_router.post(
    "/{planned_block_id}/undo-record-actual-as-planned",
    status_code=204,
)
def undo_record_actual_as_planned(
    planned_block_id: int,
    body: RecordActualAsPlannedUndo,
    db: Session = Depends(get_db),
) -> Response:
    try:
        if not planned_recording.undo(db, planned_block_id, body.undo_token, capture_utc_now()):
            actual_block_service.undo_record_actual_as_planned(db, planned_block_id, body.undo_token)
        return Response(status_code=204)
    except ValueError as exc:
        raise domain_http_error(exc) from exc
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(409, "Recorded activity changed; Undo is no longer available.") from exc


@router.get("/active", response_model=ActualBlockRead | None)
def get_active_actual_block(db: Session = Depends(get_db)) -> ActualBlockRead | None:
    return actual_block_service.get_active_actual_block(db)


@router.get("/{actual_block_id}", response_model=ActualBlockRead)
def get_actual_block(
    actual_block_id: int, db: Session = Depends(get_db)
) -> ActualBlockRead:
    try:
        return actual_block_service.get_actual_block(db, actual_block_id)
    except ValueError as exc:
        raise domain_http_error(exc) from exc
