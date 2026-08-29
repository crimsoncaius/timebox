from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.core.time import parse_iso_date
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
from app.services import day_service

router = APIRouter(prefix="/days", tags=["days"])


@router.post("/plan", response_model=PlanningCommitRead)
def commit_plan(
    body: PlanningCommitCreate,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> PlanningCommitRead:
    try:
        days = day_service.commit_planning_session(db, body.placements)
    except ValueError as e:
        db.rollback()
        raise HTTPException(status_code=422, detail=str(e)) from e
    return PlanningCommitRead(days=[day_service.to_day_read(db, day, settings) for day in days])


@router.get("", response_model=list[DayListItem])
def list_days(
    limit: int = Query(60, ge=1, le=500),
    db: Session = Depends(get_db),
) -> list[DayListItem]:
    rows = day_service.list_recent_days(db, limit=limit)
    return [day_service.to_day_list_item(d, count) for d, count in rows]


@router.get("/{date}", response_model=DayRead)
def get_day(
    date: str,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> DayRead:
    try:
        d = parse_iso_date(date)
    except ValueError as e:
        raise HTTPException(status_code=422, detail="Invalid date, use YYYY-MM-DD") from e
    day = day_service.get_or_create_day(db, d)
    return day_service.to_day_read(db, day, settings)


@router.get("/{date}/preview", response_model=DayPreviewRead)
def get_day_preview(
    date: str,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> DayPreviewRead:
    try:
        d = parse_iso_date(date)
    except ValueError as e:
        raise HTTPException(status_code=422, detail="Invalid date, use YYYY-MM-DD") from e
    day = day_service.get_day_by_date(db, d)
    return day_service.build_day_preview(db, day, d, settings)


@router.get("/{date}/summary", response_model=DaySummaryRead)
def get_day_summary(
    date: str,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> DaySummaryRead:
    try:
        d = parse_iso_date(date)
    except ValueError as e:
        raise HTTPException(status_code=422, detail="Invalid date, use YYYY-MM-DD") from e
    day = day_service.get_day_by_date(db, d)
    return day_service.build_day_summary(db, day, d, settings)


@router.post("/{date}/blocks", response_model=DayRead)
def create_block(
    date: str,
    body: PlannedBlockCreate,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> DayRead:
    try:
        d = parse_iso_date(date)
    except ValueError as e:
        raise HTTPException(status_code=422, detail="Invalid date, use YYYY-MM-DD") from e
    day = day_service.get_or_create_day(db, d)
    try:
        day_service.create_time_block(db, day, body)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    db_day = day_service.get_day_by_date(db, d)
    assert db_day is not None
    return day_service.to_day_read(db, db_day, settings)

@router.patch("/{date}/blocks/{block_id}", response_model=DayRead)
def patch_block(
    date: str,
    block_id: int,
    body: TimeBlockPatch,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> DayRead:
    try:
        d = parse_iso_date(date)
    except ValueError as e:
        raise HTTPException(status_code=422, detail="Invalid date, use YYYY-MM-DD") from e
    day = day_service.get_or_create_day(db, d)
    try:
        day_service.patch_time_block(db, day, block_id, body)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    db_day = day_service.get_day_by_date(db, d)
    assert db_day is not None
    return day_service.to_day_read(db, db_day, settings)
@router.delete("/{date}/blocks/{block_id}", response_model=DayRead)
def delete_block(
    date: str,
    block_id: int,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> DayRead:
    try:
        d = parse_iso_date(date)
    except ValueError as e:
        raise HTTPException(status_code=422, detail="Invalid date, use YYYY-MM-DD") from e
    day = day_service.get_or_create_day(db, d)
    try:
        day_service.delete_time_block(db, day, block_id)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    db_day = day_service.get_day_by_date(db, d)
    assert db_day is not None
    return day_service.to_day_read(db, db_day, settings)
