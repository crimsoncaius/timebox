from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.core.time import parse_iso_date
from app.db.session import get_db
from app.schemas.day import DayListItem, DayPatch, DayRead
from app.schemas.time_block import TimeBlockCreate, TimeBlockPatch
from app.services import day_service

router = APIRouter(prefix="/days", tags=["days"])


@router.get("", response_model=list[DayListItem])
def list_days(
    limit: int = Query(60, ge=1, le=500),
    db: Session = Depends(get_db),
) -> list[DayListItem]:
    days = day_service.list_recent_days(db, limit=limit)
    return [day_service.to_day_list_item(d) for d in days]


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
    return day_service.to_day_read(day, settings)


@router.patch("/{date}", response_model=DayRead)
def patch_day(
    date: str,
    body: DayPatch,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> DayRead:
    try:
        d = parse_iso_date(date)
    except ValueError as e:
        raise HTTPException(status_code=422, detail="Invalid date, use YYYY-MM-DD") from e
    day = day_service.get_or_create_day(db, d)
    try:
        day_service.patch_day(db, day, body)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    db_day = day_service.get_day_by_date(db, d)
    assert db_day is not None
    return day_service.to_day_read(db_day, settings)


@router.post("/{date}/blocks", response_model=DayRead)
def create_block(
    date: str,
    body: TimeBlockCreate,
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
    return day_service.to_day_read(db_day, settings)


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
    return day_service.to_day_read(db_day, settings)


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
    return day_service.to_day_read(db_day, settings)
