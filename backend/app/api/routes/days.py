from __future__ import annotations

import datetime as dt

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.deps import day_date
from app.api.errors import domain_http_error
from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.schemas.day import (
    DayListItem,
    DayPreviewRead,
    DayRead,
    DaySummaryRead,
    PlanningCommitCreate,
    PlanningCommitRead,
)
from app.schemas.time_block import PlannedBlockCreate, TimeBlockPatch
from app.services import day_service, activity_service

router = APIRouter(prefix="/days", tags=["days"])


def get_reporting_settings(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    return activity_service.reporting_settings(db, settings)


def _read_after_mutation(db: Session, d: dt.date, settings: Settings) -> DayRead:
    """Re-read the day a mutation just committed, so callers see its final shape."""

    day = day_service.get_day_by_date(db, d)
    assert day is not None
    return day_service.to_day_read(db, day, settings)


@router.post("/plan", response_model=PlanningCommitRead)
def commit_plan(
    body: PlanningCommitCreate,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> PlanningCommitRead:
    try:
        days = day_service.commit_planning_session(db, body.placements)
    except ValueError as exc:
        db.rollback()
        raise domain_http_error(exc) from exc
    return PlanningCommitRead(days=[day_service.to_day_read(db, day, settings) for day in days])


@router.get("", response_model=list[DayListItem])
def list_days(
    limit: int = Query(60, ge=1, le=500),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> list[DayListItem]:
    rows = day_service.list_recent_days(db, limit=limit)
    return [day_service.to_day_list_item(db, d, count, settings) for d, count in rows]


@router.get("/{date}", response_model=DayRead)
def get_day(
    d: dt.date = Depends(day_date),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> DayRead:
    day = day_service.get_or_create_day(db, d)
    return day_service.to_day_read(db, day, settings)


@router.get("/{date}/preview", response_model=DayPreviewRead)
def get_day_preview(
    d: dt.date = Depends(day_date),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> DayPreviewRead:
    day = day_service.get_day_by_date(db, d)
    return day_service.build_day_preview(db, day, d, settings)


@router.get("/{date}/summary", response_model=DaySummaryRead)
def get_day_summary(
    d: dt.date = Depends(day_date),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> DaySummaryRead:
    day = day_service.get_day_by_date(db, d)
    return day_service.build_day_summary(db, day, d, settings)


@router.post("/{date}/blocks", response_model=DayRead)
def create_block(
    body: PlannedBlockCreate,
    d: dt.date = Depends(day_date),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> DayRead:
    day = day_service.get_or_create_day(db, d)
    try:
        day_service.create_time_block(db, day, body)
    except ValueError as exc:
        raise domain_http_error(exc) from exc
    return _read_after_mutation(db, d, settings)


@router.patch("/{date}/blocks/{block_id}", response_model=DayRead)
def patch_block(
    block_id: int,
    body: TimeBlockPatch,
    d: dt.date = Depends(day_date),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> DayRead:
    day = day_service.get_or_create_day(db, d)
    try:
        day_service.patch_time_block(db, day, block_id, body)
    except ValueError as exc:
        raise domain_http_error(exc) from exc
    return _read_after_mutation(db, d, settings)


@router.delete("/{date}/blocks/{block_id}", response_model=DayRead)
def delete_block(
    block_id: int,
    d: dt.date = Depends(day_date),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
) -> DayRead:
    day = day_service.get_or_create_day(db, d)
    try:
        day_service.delete_time_block(db, day, block_id)
    except ValueError as exc:
        raise domain_http_error(exc) from exc
    return _read_after_mutation(db, d, settings)
