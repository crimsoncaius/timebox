from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.schemas.settings import SettingsPatch, SettingsRead
from app.services import day_service

router = APIRouter(prefix="/settings", tags=["settings"])


@router.get("", response_model=SettingsRead)
def get_settings(db: Session = Depends(get_db)) -> SettingsRead:
    s = day_service.get_or_create_app_settings(db)
    return SettingsRead.model_validate(s)


@router.patch("", response_model=SettingsRead)
def patch_settings(
    body: SettingsPatch,
    db: Session = Depends(get_db),
) -> SettingsRead:
    try:
        s = day_service.patch_app_settings(db, body)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return SettingsRead.model_validate(s)
