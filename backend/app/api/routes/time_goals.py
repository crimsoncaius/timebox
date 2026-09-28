from __future__ import annotations

import datetime as dt
from contextlib import contextmanager
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.api.routes.days import get_reporting_settings
from app.api.routes.trends import capture_now
from app.core.config import Settings
from app.db.session import get_db
from app.schemas.time_goal import (
    TimeGoalCreate,
    TimeGoalRead,
    TimeGoalReplace,
    TimeGoalsWeek,
    TimeGoalTargetChange,
)
from app.services import time_goals

router = APIRouter(prefix="/time-goals", tags=["time-goals"])


@contextmanager
def errors(db: Session):
    try:
        yield
    except (ValueError, OverflowError) as error:
        db.rollback()
        code = 404 if str(error) == "Time Goal not found" else 422
        raise HTTPException(status_code=code, detail=str(error)) from error


@router.get("", response_model=TimeGoalsWeek)
def read_week(week: dt.date | None = None, db: Session = Depends(get_db),
              settings: Settings = Depends(get_reporting_settings), now: dt.datetime = Depends(capture_now)):
    with errors(db):
        return time_goals.read_week(db, week, settings.app_timezone, now)


@router.get("/{goal_id}/period", response_model=TimeGoalRead)
def read_period(goal_id: int, anchor: dt.date, week: dt.date, db: Session = Depends(get_db),
                settings: Settings = Depends(get_reporting_settings), now: dt.datetime = Depends(capture_now)):
    with errors(db):
        return time_goals.read_goal(db, time_goals.get(db, goal_id), anchor, time_goals.monday(week),
                                    settings.app_timezone, now)


@router.post("", response_model=TimeGoalRead, status_code=201)
def create(body: TimeGoalCreate, db: Session = Depends(get_db),
           settings: Settings = Depends(get_reporting_settings), now: dt.datetime = Depends(capture_now)):
    with errors(db):
        goal = time_goals.create(db, body)
        today = now.astimezone(ZoneInfo(settings.app_timezone)).date()
        result = time_goals.read_goal(db, goal, today, time_goals.monday(today), settings.app_timezone, now)
        db.commit()
        return result


@router.patch("/{goal_id}/target", status_code=204)
def target(goal_id: int, body: TimeGoalTargetChange, db: Session = Depends(get_db),
           settings: Settings = Depends(get_reporting_settings), now: dt.datetime = Depends(capture_now)):
    with errors(db):
        time_goals.change_target(db, time_goals.get(db, goal_id), body.target_minutes,
                                 now.astimezone(ZoneInfo(settings.app_timezone)).date())
        db.commit()


@router.post("/{goal_id}/end", status_code=204)
def end(goal_id: int, db: Session = Depends(get_db), settings: Settings = Depends(get_reporting_settings),
        now: dt.datetime = Depends(capture_now)):
    with errors(db):
        time_goals.end_goal(time_goals.get(db, goal_id), now.astimezone(ZoneInfo(settings.app_timezone)).date())
        db.commit()


@router.post("/{goal_id}/replace", response_model=TimeGoalRead)
def replace(goal_id: int, body: TimeGoalReplace, db: Session = Depends(get_db),
            settings: Settings = Depends(get_reporting_settings), now: dt.datetime = Depends(capture_now)):
    with errors(db):
        today = now.astimezone(ZoneInfo(settings.app_timezone)).date()
        time_goals.end_goal(time_goals.get(db, goal_id), today)
        goal = time_goals.create(db, TimeGoalCreate(**body.model_dump(), start_date=today))
        result = time_goals.read_goal(db, goal, today, time_goals.monday(today), settings.app_timezone, now)
        db.commit()
        return result


@router.delete("/{goal_id}", status_code=204)
def delete(goal_id: int, db: Session = Depends(get_db)):
    with errors(db):
        db.delete(time_goals.get(db, goal_id))
        db.commit()
