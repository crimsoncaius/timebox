from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.schemas.activity import ActivityCommand, ActivitySnapshot
from app.services import activity_service


def require_development(settings: Settings = Depends(get_settings)):
    if not settings.activity_tracking_dev:
        raise HTTPException(404, "Activity development protocol is disabled")


router = APIRouter(prefix="/activity", tags=["activity"], dependencies=[Depends(require_development)])


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
