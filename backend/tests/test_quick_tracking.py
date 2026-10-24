import asyncio
import datetime as dt
from uuid import uuid4

import pytest
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.activity import ActivityOperation
from app.models.assistant import AssistantTrackingReview
from app.models.task_type import TaskType
from app.schemas.activity import ActivityCommand
from app.services import activity_service, assistant_reviews
from app.services import quick_tracking as quick


def interpreted(monkeypatch, action="track", minutes=10, task_type="Work", request_id=None):
    async def workflow(state, message, now):
        return quick.language.Decision(action=action, task_type=task_type if action == "track" else None), {}, {
            "timing": "extract", "effective_at": (now - dt.timedelta(minutes=minutes)).isoformat()}
    monkeypatch.setattr(quick.language, "interpret_workflow", workflow)
    with Session(get_engine()) as db:
        if not db.scalar(select(TaskType)):
            db.add_all([TaskType(name="Work"), TaskType(name="Meals")]); db.commit()
    return asyncio.run(quick.interpret(quick.QuickRequest(request_id=request_id or uuid4(), message="Track work", source="tile")))


def execute(result, kind, sequence, target_source=None):
    with Session(get_engine()) as db:
        snapshot = activity_service.read(db, "UTC")
        now = dt.datetime.now(dt.UTC)
        body = ActivityCommand(operation_id=result["request_id"], quick_tracking_id=result["request_id"],
            device_id="quick-test", sequence=sequence, action_at=now, calibration={"server_at": now, "offset_ms": 0},
            base_cursor=snapshot.cursor, target_id=snapshot.current.id if snapshot.current else None,
            kind=kind, task_type_id=result["task_type_id"], selection_snapshot=True,
            target_source=target_source, target_start_at=snapshot.current.start_at if target_source else None,
            effective={"mode": "range" if kind == "edit" else "instant", "at": result["effective_at"]})
        after = activity_service.execute(db, body, "UTC")
        assert activity_service.execute(db, body, "UTC").cursor == after.cursor
        return after, body


def test_interpretation_does_not_mutate_and_retry_returns_same_time(monkeypatch):
    result = interpreted(monkeypatch)
    again = interpreted(monkeypatch, request_id=result["request_id"])
    assert again == result
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(ActivityOperation)) == 0
    after, _ = execute(result, "start", 1)
    assert after.current.task_type.name == "Work"
    assert len(assistant_reviews.listing()["items"]) == 1


def test_start_correction_leaves_gap_then_earlier_stop_removes_later_history(monkeypatch):
    first, _ = execute(interpreted(monkeypatch, minutes=30), "start", 1)
    switched, _ = execute(interpreted(monkeypatch, minutes=20, task_type="Meals"), "switch", 2)
    source = switched.provenance[switched.current.id]
    corrected, _ = execute(interpreted(monkeypatch, minutes=10, task_type="Meals"), "edit", 3, source)
    assert corrected.records[0].end_at == switched.records[0].end_at
    assert corrected.current.start_at > corrected.records[0].end_at
    stopped, _ = execute(interpreted(monkeypatch, action="stop", minutes=25), "stop", 4)
    assert stopped.current is None
    assert len(stopped.records) == 1
    assert stopped.records[0].end_at < first.current.start_at + dt.timedelta(minutes=10)
    rows = assistant_reviews.listing()["items"]
    assert len(rows) == 4 and rows[0]["receipt"]["action"] == "stop"


def test_receipt_and_activity_are_one_transaction(monkeypatch):
    result = interpreted(monkeypatch)
    def fail(*args, **kwargs): raise ValueError("receipt failed")
    monkeypatch.setattr(quick, "receipt", fail)
    with pytest.raises(ValueError, match="receipt failed"):
        execute(result, "start", 1)
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(ActivityOperation)) == 0
        assert db.scalar(select(func.count()).select_from(AssistantTrackingReview)) == 0


def test_interpreted_command_rejects_tampering(monkeypatch):
    result = interpreted(monkeypatch)
    result["task_type_id"] += 1
    with pytest.raises(ValueError, match="selection"):
        execute(result, "start", 1)
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(ActivityOperation)) == 0


def test_late_delivery_keeps_own_receipt_when_another_device_supersedes_it(monkeypatch):
    result = interpreted(monkeypatch)
    received = dt.datetime.fromisoformat(result["received_at"])
    with Session(get_engine()) as db:
        meals = db.scalar(select(TaskType).where(TaskType.name == "Meals"))
        activity_service.execute(db, ActivityCommand(operation_id=uuid4(), device_id="other", sequence=1,
            action_at=received + dt.timedelta(seconds=1), calibration={"server_at": received, "offset_ms": 0},
            base_cursor=0, kind="start", task_type_id=meals.id, selection_snapshot=True,
            effective={"mode": "instant", "at": result["effective_at"]}), "UTC")
        after = activity_service.execute(db, ActivityCommand(operation_id=result["request_id"], quick_tracking_id=result["request_id"],
            device_id="late-client", sequence=1, action_at=received, calibration={"server_at": received, "offset_ms": 0},
            base_cursor=0, kind="start", task_type_id=result["task_type_id"], selection_snapshot=True,
            effective={"mode": "instant", "at": result["effective_at"]}), "UTC")
        assert after.current.task_type.name == "Meals"
        assert after.acknowledgement.outcome == "superseded"
    row = assistant_reviews.listing()["items"][0]
    assert row["record"] is None
    assert row["receipt"]["record"]["task_type"]["name"] == "Work"
