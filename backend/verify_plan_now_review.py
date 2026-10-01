"""Small isolated check for the review-only plan splitting; no review DB writes."""
import datetime as dt
from unittest.mock import patch
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from plan_now_review import Base, ActivityState, Day, TimeBlock, BlockLane, TaskType, PlanRequest, plan_now, exact_plans, ZONE

engine = create_engine("sqlite:///:memory:")
Base.metadata.create_all(engine)
class ReviewClock(dt.datetime):
    @classmethod
    def now(cls, tz=None):
        return cls(2026, 10, 1, 10, 20, 59, 732000, tzinfo=ZONE).astimezone(tz or ZONE)

with patch("plan_now_review.dt.datetime", ReviewClock), Session(engine) as db:
    now = dt.datetime.now(ZONE)
    minute = now.hour * 60 + now.minute
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
    assert dt.datetime.fromisoformat(result["start_at"]) == now
    assert dt.datetime.fromisoformat(result["end_at"]) == now + dt.timedelta(minutes=15)
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
    # Attach the Current Activity to its matching plan, then shorten and extend.
    current.task_type_id = email.id
    current.name = "Email"
    current.planned_block_id = plans[1].id
    linked = plans[1]
    linked.note = "Keep the linked note"
    linked_start, linked_id = linked.start_minute, linked.id
    db.commit()
    for minutes in (15, 30):
        saved = plan_now(PlanRequest(minutes=minutes, task_type_id=email.id, name="Email", current_id=current.id), db)
        plans = list(db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.planned).order_by(TimeBlock.start_minute)))
        assert len(plans) == 2 and saved["id"] == linked_id
        assert linked.start_minute == linked_start and linked.end_minute == minute + minutes
        assert linked.note == "Keep the linked note"
        assert current.start_at == start and current.planned_block_id == linked_id
        assert dt.datetime.fromisoformat(saved["end_at"]) == now + dt.timedelta(minutes=minutes)
    # Network delay must not move the planned end beyond the captured Save instant.
    pressed_save = now + dt.timedelta(seconds=1)
    with patch.object(ReviewClock, "now", return_value=now + dt.timedelta(seconds=3)):
        saved = plan_now(PlanRequest(minutes=30, task_type_id=email.id, name="Email", current_id=current.id, effective_at=pressed_save), db)
    assert dt.datetime.fromisoformat(saved["end_at"]) - pressed_save == dt.timedelta(minutes=30)
with Session(engine) as db:
    persisted = next(p for p in exact_plans(db, "Asia/Singapore") if p["id"] == linked_id)
    assert persisted["end_at"] == saved["end_at"]
print("PASS: exact Save + duration at second 59.732, persisted precision, interruption/resume, same-activity identity/note/Actual start")
