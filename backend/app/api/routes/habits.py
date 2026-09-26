from __future__ import annotations

import datetime as dt

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.api.routes.days import get_reporting_settings
from app.core.config import Settings
from app.db.session import get_db
from app.schemas.battle_plan import HabitsWeekRead
from app.services.recurrence import habits

router = APIRouter(prefix="/habits", tags=["habits"])


def _error(exc: ValueError) -> HTTPException:
    message = str(exc)
    status = 404 if message == "Recurring template not found" else 422
    return HTTPException(status_code=status, detail=message)


@router.get("", response_model=HabitsWeekRead)
def read_week(
    week: dt.date | None = Query(None, description="Any date in the Calendar Week to read"),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> HabitsWeekRead:
    return habits.read_week(db, week, settings)


@router.post("/{template_id}/days/{day}", response_model=HabitsWeekRead)
def tick_day(
    template_id: int,
    day: dt.date,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> HabitsWeekRead:
    try:
        habits.tick(db, template_id, day, settings)
    except ValueError as exc:
        raise _error(exc) from exc
    return habits.read_week(db, day, settings)


@router.delete("/{template_id}/days/{day}", response_model=HabitsWeekRead)
def untick_day(
    template_id: int,
    day: dt.date,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> HabitsWeekRead:
    try:
        habits.untick(db, template_id, day, settings)
    except ValueError as exc:
        raise _error(exc) from exc
    return habits.read_week(db, day, settings)
