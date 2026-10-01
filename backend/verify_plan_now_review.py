"""Small isolated check for the review-only plan splitting; no review DB writes."""
import datetime as dt
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from plan_now_review import Base, ActivityState, Day, TimeBlock, BlockLane, TaskType, PlanRequest, plan_now, ZONE

engine = create_engine("sqlite:///:memory:")
Base.metadata.create_all(engine)
with Session(engine) as db:
    now = dt.datetime.now(ZONE)
    minute = now.hour * 60 + now.minute
    assert minute < 1320, "Run this daytime fixture before 22:00 Singapore time"
    writing, email = TaskType(name="Writing"), TaskType(name="Email")
    db.add_all([writing, email]); db.flush()
    day = Day(date=now.date()); db.add(day); db.flush()
    db.add(ActivityState(id=1, enabled=True, cursor=0, reporting_timezone="Asia/Singapore"))
    current = TimeBlock(lane=BlockLane.actual, task_type_id=writing.id, start_at=now-dt.timedelta(minutes=20), activity_source="test")
    original = TimeBlock(day_id=day.id, lane=BlockLane.planned, task_type_id=writing.id, name="Writing", note="Keep this note", start_minute=max(0, minute-20), end_minute=minute+40)
    db.add_all([current, original]); db.commit()
    original_id = original.id
    start = current.start_at
    result = plan_now(PlanRequest(minutes=15, task_type_id=email.id, name="Email", current_id=current.id), db)
    plans = list(db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.planned).order_by(TimeBlock.start_minute)))
    assert len(plans) == 3
    assert plans[0].id == original_id and plans[0].end_minute == minute
    assert plans[1].id == result["id"] and plans[1].start_minute == minute and plans[1].end_minute == minute+15
    assert plans[2].start_minute == minute+15 and plans[2].end_minute == minute+40 and plans[2].note == "Keep this note"
    assert current.start_at == start
    # A longer replacement consumes the future fragments without overlapping.
    plan_now(PlanRequest(minutes=60, task_type_id=email.id, name="Email", current_id=current.id), db)
    plans = list(db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.planned).order_by(TimeBlock.start_minute)))
    assert len(plans) == 2 and plans[0].end_minute == plans[1].start_minute
    assert plans[1].end_minute == minute+60
print("PASS: split/resume, preserved identity/note, longer replacement, unchanged Actual start")
