from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.models.activity import ActivityState
from app.schemas.activity import ActivityCommand, ActivitySnapshot
from app.services import activity_service


def require_activity(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    state = db.get(ActivityState, 1)
    if not settings.activity_tracking_dev and not (state and state.enabled):
        raise HTTPException(404, "Activity Tracking requires database upgrade")


router = APIRouter(prefix="/activity", tags=["activity"], dependencies=[Depends(require_activity)])


@router.get("", response_model=ActivitySnapshot)
def read_activity(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    try:
        return activity_service.read(db, settings.app_timezone)
    except ValueError as exc:
        db.rollback()
        raise HTTPException(409, str(exc)) from exc


@router.post("/commands", response_model=ActivitySnapshot)
def command(body: ActivityCommand, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    try:
        return activity_service.execute(db, body, settings.app_timezone)
    except (ValueError, IntegrityError) as exc:
        db.rollback()
        detail = str(exc) if isinstance(exc, ValueError) else "Activity conflicts with recorded time"
        raise HTTPException(422, detail) from exc


from pydantic import BaseModel, Field


class ReportingTimezone(BaseModel):
    timezone: str = Field(min_length=1, max_length=100)


def save_zone(body, db, settings, initialize):
    try:
        return activity_service.set_reporting_timezone(db, body.timezone, settings.app_timezone, initialize=initialize)
    except ValueError as exc:
        db.rollback()
        raise HTTPException(422, str(exc)) from exc


@router.post("/reporting-timezone/initialize", response_model=ActivitySnapshot)
def initialize_timezone(body: ReportingTimezone, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return save_zone(body, db, settings, True)


@router.put("/reporting-timezone", response_model=ActivitySnapshot)
def update_timezone(body: ReportingTimezone, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return save_zone(body, db, settings, False)
