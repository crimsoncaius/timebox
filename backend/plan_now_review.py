"""Issue 297 experiment: real app API, disposable SQLite data, local-only launcher.

Run with .venv/Scripts/python plan_now_review.py. Never imported by production.
Plan creation and tracking are separate requests in this experiment; atomic retry,
undo, recurrence and multi-device reconciliation remain production work.
"""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "artifacts" / "plan-now-297.sqlite"
DATA.parent.mkdir(exist_ok=True)
os.environ["DATABASE_URL"] = "sqlite:///" + DATA.as_posix()
os.environ["APP_TIMEZONE"] = "Asia/Singapore"
os.environ["ACTIVITY_TRACKING_DEV"] = "true"
os.environ["API_KEY"] = ""

import datetime as dt
from zoneinfo import ZoneInfo
from fastapi import Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from app.main import app
from app.db.base import Base
from app.db.session import get_engine, get_db
from app.models.activity import ActivityState
from app.models.day import Day
from app.models.time_block import TimeBlock, BlockLane
from app.models.task_type import TaskType
from app.services import activity_service, activity_selection

ZONE = ZoneInfo("Asia/Singapore")

def seed():
    Base.metadata.create_all(get_engine())
    with Session(get_engine()) as db:
        if db.scalar(select(TaskType.id).limit(1)):
            return
        now = dt.datetime.now(dt.UTC)
        local = now.astimezone(ZONE)
        minute = local.hour * 60 + local.minute
        writing, email, meeting = [TaskType(name=name) for name in ("Writing", "Email", "Meeting")]
        db.add_all([writing, email, meeting]); db.flush()
        day = Day(date=local.date(), start_hour=max(0, local.hour - 1), end_hour=min(24, local.hour + 4))
        db.add(day); db.flush()
        db.add(ActivityState(id=1, enabled=True, cursor=0, reporting_timezone="Asia/Singapore"))
        db.add(TimeBlock(lane=BlockLane.actual, task_type_id=writing.id, name="Draft the proposal", start_at=now-dt.timedelta(minutes=20), activity_source="review-seed"))
        db.add(TimeBlock(day_id=day.id, lane=BlockLane.planned, task_type_id=meeting.id, name="Team catch-up", start_minute=min(minute+60, 1400), end_minute=min(minute+90, 1440)))
        db.commit()

class PlanRequest(BaseModel):
    minutes: int = Field(ge=1, le=90)
    task_type_id: int
    name: str = Field(max_length=500)
    current_id: int

@app.post("/prototype/plan-now")
def plan_now(body: PlanRequest, db: Session = Depends(get_db)):
    state = activity_service._lock(db)
    current = db.scalar(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual, TimeBlock.end_at.is_(None)))
    if current is None or current.id != body.current_id:
        raise HTTPException(409, "Current Activity changed")
    if db.get(TaskType, body.task_type_id) is None:
        raise HTTPException(422, "Unknown Task Type")
    now = dt.datetime.now(ZONE)
    start = now.hour * 60 + now.minute
    end = start + body.minutes
    if end > 1440:
        raise HTTPException(422, "This prototype does not span midnight")
    day = db.scalar(select(Day).where(Day.date == now.date()))
    if day is None:
        day = Day(date=now.date()); db.add(day); db.flush()
    affected = list(db.scalars(select(TimeBlock).where(TimeBlock.day_id == day.id, TimeBlock.lane == BlockLane.planned, TimeBlock.start_minute < end, TimeBlock.end_minute > start)))
    for p in affected:
        if p.task_id is not None:
            raise HTTPException(422, "Task-linked plan replacement is outside this review")
        old_end = p.end_minute
        if p.start_minute < start:
            p.end_minute = start
            if old_end > end:
                db.add(TimeBlock(day_id=day.id, lane=BlockLane.planned, task_type_id=p.task_type_id, name=p.name, note=p.note, start_minute=end, end_minute=old_end))
        elif old_end > end:
            p.start_minute = end
        else:
            # Samples may acquire Actual links while exploring the app.
            activity_selection.detach_plan(db, p.id)
            db.delete(p)
    plan = TimeBlock(day_id=day.id, lane=BlockLane.planned, task_type_id=body.task_type_id, name=body.name or None, start_minute=start, end_minute=end,
                     task_id=current.task_id if current.task_type_id == body.task_type_id and (current.name or "") == body.name else None)
    db.add(plan); state.cursor += 1; db.flush()
    result = next(p for p in activity_selection.plans(db, "Asia/Singapore") if p["id"] == plan.id)
    db.commit()
    return result

if __name__ == "__main__":
    seed()
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=12075)
