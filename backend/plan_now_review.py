"""Issue 297 experiment: real app API, disposable SQLite data, local-only launcher.

Run with .venv/Scripts/python plan_now_review.py. Never imported by production.
Plan creation and tracking are separate requests in this experiment; atomic retry,
undo, recurrence and multi-device reconciliation remain production work.
"""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "artifacts" / "plan-now-297-same-activity.sqlite"
DATA.parent.mkdir(exist_ok=True)
os.environ["DATABASE_URL"] = "sqlite:///" + DATA.as_posix()
os.environ["APP_TIMEZONE"] = "Asia/Singapore"
os.environ["ACTIVITY_TRACKING_DEV"] = "true"
os.environ["API_KEY"] = ""

import datetime as dt
from zoneinfo import ZoneInfo
from fastapi import Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import Column, ForeignKey, Integer, Table, Text, select
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

# Review-only precision, persisted with the disposable database. The production
# Day model still uses whole minutes; do not mistake this for its migration.
review_intervals = Table("prototype_plan_intervals", Base.metadata,
    Column("plan_id", ForeignKey("time_blocks.id", ondelete="CASCADE"), primary_key=True),
    Column("start_minute", Integer, nullable=False), Column("end_minute", Integer, nullable=False),
    Column("start_at", Text, nullable=False), Column("end_at", Text, nullable=False))
grid_plans = activity_selection.plans

def exact_plans(db, timezone):
    result = grid_plans(db, timezone)
    for plan in result:
        saved = db.execute(select(review_intervals).where(review_intervals.c.plan_id == plan["id"])).mappings().first()
        row = db.get(TimeBlock, plan["id"])
        if saved and (saved["start_minute"], saved["end_minute"]) == (row.start_minute, row.end_minute):
            plan.update(start_at=saved["start_at"], end_at=saved["end_at"])
    return result

activity_selection.plans = exact_plans

def save_interval(db, plan, start, end):
    db.flush()
    db.execute(review_intervals.delete().where(review_intervals.c.plan_id == plan.id))
    db.execute(review_intervals.insert().values(plan_id=plan.id, start_minute=plan.start_minute,
        end_minute=plan.end_minute, start_at=start.isoformat(), end_at=end.isoformat()))

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
        writing_plan = TimeBlock(day_id=day.id, lane=BlockLane.planned, task_type_id=writing.id, name="Draft the proposal", note="Keep this Writing note", start_minute=max(0, minute-20), end_minute=min(minute+40, 1440))
        db.add(writing_plan); db.flush()
        db.add(TimeBlock(lane=BlockLane.actual, task_type_id=writing.id, name="Draft the proposal", planned_block_id=writing_plan.id, start_at=now-dt.timedelta(minutes=20), activity_source="review-seed"))
        db.add(TimeBlock(day_id=day.id, lane=BlockLane.planned, task_type_id=meeting.id, name="Team catch-up", start_minute=min(minute+60, 1400), end_minute=min(minute+90, 1440)))
        db.commit()

class PlanRequest(BaseModel):
    minutes: int = Field(ge=1, le=90)
    task_type_id: int
    name: str = Field(max_length=500)
    current_id: int
    effective_at: dt.datetime | None = None

@app.post("/prototype/plan-now")
def plan_now(body: PlanRequest, db: Session = Depends(get_db)):
    state = activity_service._lock(db)
    current = db.scalar(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual, TimeBlock.end_at.is_(None)))
    if current is None or current.id != body.current_id:
        raise HTTPException(409, "Current Activity changed")
    if db.get(TaskType, body.task_type_id) is None:
        raise HTTPException(422, "Unknown Task Type")
    received = dt.datetime.now(ZONE)
    now = body.effective_at or received
    if now.tzinfo is None or not -5 <= (received - now).total_seconds() <= 30:
        raise HTTPException(409, "Save time is stale. Refresh and try again.")
    now = now.astimezone(ZONE)
    exact_end = now + dt.timedelta(minutes=body.minutes)
    start = now.hour * 60 + now.minute
    end = start + body.minutes
    if exact_end > dt.datetime.combine(now.date() + dt.timedelta(days=1), dt.time(), ZONE):
        raise HTTPException(422, "This prototype does not span midnight")
    day = db.scalar(select(Day).where(Day.date == now.date()))
    if day is None:
        day = Day(date=now.date()); db.add(day); db.flush()
    intervals = {p["id"]: (dt.datetime.fromisoformat(p["start_at"]), dt.datetime.fromisoformat(p["end_at"]))
                 for p in exact_plans(db, "Asia/Singapore")}
    # Resizing the linked, matching Current Activity keeps its plan identity,
    # preceding intended time and Supporting Note; it has no resumed remainder.
    matching = db.get(TimeBlock, current.planned_block_id) if current.planned_block_id else None
    if matching is not None and not (
        matching.day_id == day.id and intervals[matching.id][0] <= now < intervals[matching.id][1]
        and matching.task_type_id == body.task_type_id == current.task_type_id
        and (matching.name or "") == body.name == (current.name or "")
    ):
        matching = None
    if matching is not None and matching.task_id is not None:
        raise HTTPException(422, "Task-linked plan adjustment is outside this review")
    affected = [p for p in db.scalars(select(TimeBlock).where(TimeBlock.day_id == day.id, TimeBlock.lane == BlockLane.planned))
                if intervals[p.id][0] < exact_end and intervals[p.id][1] > now]
    for p in affected:
        if matching is not None and p.id == matching.id:
            continue
        if p.task_id is not None:
            raise HTTPException(422, "Task-linked plan replacement is outside this review")
        old_start_at, old_end_at = intervals[p.id]
        old_end = p.end_minute
        if old_start_at < now:
            p.end_minute = start
            save_interval(db, p, old_start_at, now)
            if old_end_at > exact_end:
                remainder = TimeBlock(day_id=day.id, lane=BlockLane.planned, task_type_id=p.task_type_id, name=p.name, note=p.note, start_minute=end, end_minute=old_end)
                db.add(remainder)
                save_interval(db, remainder, exact_end, old_end_at)
        elif old_end_at > exact_end:
            p.start_minute = end
            save_interval(db, p, exact_end, old_end_at)
        else:
            # Samples may acquire Actual links while exploring the app.
            activity_selection.detach_plan(db, p.id)
            db.delete(p)
    if matching is not None:
        plan = matching
        plan.end_minute = end
    else:
        plan = TimeBlock(day_id=day.id, lane=BlockLane.planned, task_type_id=body.task_type_id, name=body.name or None, start_minute=start, end_minute=end,
                         task_id=current.task_id if current.task_type_id == body.task_type_id and (current.name or "") == body.name else None)
        db.add(plan)
    state.cursor += 1; db.flush()
    save_interval(db, plan, intervals[matching.id][0] if matching is not None else now, exact_end)
    result = next(p for p in activity_selection.plans(db, "Asia/Singapore") if p["id"] == plan.id)
    db.commit()
    return result

if __name__ == "__main__":
    seed()
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=12075)
