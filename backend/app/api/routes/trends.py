import datetime as dt
from typing import Literal
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.api.routes.days import get_reporting_settings
from app.core.config import Settings
from app.db.session import get_db
from app.schemas.trends import TrendsRead
from app.services.trends import report

router = APIRouter(prefix='/trends', tags=['trends'])


def capture_now():
    return dt.datetime.now(dt.timezone.utc)


@router.get('', response_model=TrendsRead)
def read_trends(
    period: Literal['day', 'week', 'month', 'custom'] = 'week',
    anchor: dt.date | None = None,
    start: dt.date | None = None,
    end: dt.date | None = None,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_reporting_settings),
    now: dt.datetime = Depends(capture_now),
):
    anchor = anchor or now.astimezone(ZoneInfo(settings.app_timezone)).date()
    try:
        if period == 'day':
            start = end = anchor
        elif period == 'week':
            start = anchor - dt.timedelta(days=anchor.weekday())
            end = start + dt.timedelta(days=6)
        elif period == 'month':
            start = anchor.replace(day=1)
            end = (start.replace(year=start.year + 1, month=1) if start.month == 12 else start.replace(month=start.month + 1)) - dt.timedelta(days=1)
        if start is None or end is None or start > end or end == dt.date.max:
            raise ValueError('Choose a valid inclusive date range')
        return report(db, start, end, settings.app_timezone, now)
    except (ValueError, OverflowError) as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
