from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.models.activity import ActivityState
from app.schemas.activity import ActivityCommand, ActivitySnapshot, PlanNowRequest, ReportingTimezone
from app.services import activity_service, plan_now


def require_activity(
    db: Session = Depends(get_db), settings: Settings = Depends(get_settings)
) -> None:
    state = db.get(ActivityState, 1)
    if not settings.activity_tracking_dev and not (state and state.enabled):
        raise HTTPException(404, "Activity Tracking requires database upgrade")


router = APIRouter(prefix="/activity", tags=["activity"], dependencies=[Depends(require_activity)])


@router.post("/plan-now", response_model=ActivitySnapshot)
def plan_and_track(body: PlanNowRequest, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    try:
        return plan_now.execute(db, body, settings.app_timezone)
    except (ValueError, IntegrityError) as exc:
        db.rollback()
        raise HTTPException(409, str(exc) if isinstance(exc, ValueError) else "The plan changed. Refresh and review it again.") from exc


@router.post("/plan-now/{operation_id}/undo", response_model=ActivitySnapshot)
def undo_plan_and_track(operation_id: UUID, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    try:
        return plan_now.undo(db, operation_id, settings.app_timezone)
    except (ValueError, IntegrityError) as exc:
        db.rollback()
        raise HTTPException(409, str(exc) if isinstance(exc, ValueError) else "The plan changed. Undo is no longer available.") from exc


@router.get("", response_model=ActivitySnapshot)
def read_activity(
    db: Session = Depends(get_db), settings: Settings = Depends(get_settings)
) -> ActivitySnapshot:
    try:
        return activity_service.read(db, settings.app_timezone)
    except ValueError as exc:
        db.rollback()
        raise HTTPException(409, str(exc)) from exc


@router.post("/commands", response_model=ActivitySnapshot)
def command(
    body: ActivityCommand,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> ActivitySnapshot:
    try:
        return activity_service.execute(db, body, settings.app_timezone)
    except (ValueError, IntegrityError) as exc:
        db.rollback()
        detail = str(exc) if isinstance(exc, ValueError) else "Activity conflicts with recorded time"
        raise HTTPException(422, detail) from exc


def _save_zone(
    body: ReportingTimezone, db: Session, settings: Settings, initialize: bool
) -> ActivitySnapshot:
    try:
        return activity_service.set_reporting_timezone(
            db, body.timezone, settings.app_timezone, initialize=initialize
        )
    except ValueError as exc:
        db.rollback()
        raise HTTPException(422, str(exc)) from exc


@router.post("/reporting-timezone/initialize", response_model=ActivitySnapshot)
def initialize_timezone(
    body: ReportingTimezone,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> ActivitySnapshot:
    return _save_zone(body, db, settings, initialize=True)


@router.put("/reporting-timezone", response_model=ActivitySnapshot)
def update_timezone(
    body: ReportingTimezone,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> ActivitySnapshot:
    return _save_zone(body, db, settings, initialize=False)
