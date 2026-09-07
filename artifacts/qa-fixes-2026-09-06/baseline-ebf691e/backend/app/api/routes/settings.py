from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings as get_config
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
    config: Settings = Depends(get_config),
) -> SettingsRead:
    try:
        previous = day_service.get_or_create_app_settings(db).week_start
        s = day_service.patch_app_settings(db, body)
        if body.week_start is not None and body.week_start != previous:
            from app.services import recurrence_service
            recurrence_service.recalculate_weekly_quotas(db, config)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return SettingsRead.model_validate(s)
