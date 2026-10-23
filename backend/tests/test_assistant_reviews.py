import datetime as dt
from uuid import uuid4

import pytest
from fastapi import HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.assistant import AssistantTrackingReview
from app.models.task_type import TaskType
from app.models.time_block import TimeBlock
from app.services import assistant_reviews as reviews
from app.services import assistant_storage as storage


def request(action="track", mode="review_later", status="completed", at=None):
    now = dt.datetime.now(dt.UTC)
    with Session(get_engine()) as db:
        kind = db.scalar(select(TaskType))
        if not kind:
            kind = TaskType(name="Gym"); db.add(kind); db.commit()
        type_id = kind.id
    conversation, run = str(uuid4()), str(uuid4())
    storage.create(conversation, ["tracking_proposal_v1"])
    storage.begin(conversation, run, "Going to the gym", "test")
    reviews.configure(run, mode)
    storage.capture(run, "", {}, None, status, proposal={
        "proposal_id": str(uuid4()), "action": action, "task_types": [{"id": type_id, "path": "Gym"}],
        "block_name": "Workout", "at": at, "proposed_at": now.isoformat(),
        "expires_at": (now + dt.timedelta(minutes=15)).isoformat(), "reporting_timezone": "UTC"})
    return run


def test_execute_once_and_batch_review_does_not_change_activity():
    run = request(); result = reviews.execute_run(run)
    assert result["status"] == "recorded", result
    assert reviews.execute_run(run) == result
    item = reviews.listing()["items"][0]
    reviews.acknowledge([{"id": item["id"], "revision": item["revision"]}])
    assert reviews.listing()["unchecked_count"] == 0
    assert reviews.listing(reviewed=True)["items"][0]["reviewed"]
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(TimeBlock)) == 1
        assert db.scalar(select(func.count()).select_from(AssistantTrackingReview)) == 1


@pytest.mark.parametrize("mode,status", [("confirm", "completed"), ("review_later", "running"), ("review_later", "stopped")])
def test_no_execution_without_opt_in_and_completion(mode, status):
    run = request(mode=mode, status=status)
    try:
        assert reviews.execute_run(run)["status"] == "not_recorded"
    except HTTPException as error:
        assert error.status_code == 409
    assert not reviews.listing()["items"]
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(TimeBlock)) == 0


def test_switch_stop_and_correct_with_stale_guard_and_retry():
    reviews.execute_run(request(at=(dt.datetime.now(dt.UTC) - dt.timedelta(minutes=20)).isoformat()))
    switched = reviews.execute_run(request()); assert switched.get("action") == "switch", switched
    stopped = reviews.execute_run(request(action="stop")); assert stopped.get("action") == "stop", stopped
    item = reviews.listing()["items"][0]; record = item["record"]
    body = {"operation_id": uuid4(), "revision": item["revision"], "name": "Corrected workout",
            "task_type_id": record["task_type_id"], "start_at": reviews.instant(record["start_at"]), "end_at": reviews.instant(record["end_at"])}
    corrected = reviews.correct(item["id"], body)
    assert corrected["record"]["name"] == "Corrected workout"
    assert corrected["receipt"]["question"] == "Going to the gym"
    assert reviews.correct(item["id"], body)["revision"] == corrected["revision"]
    with pytest.raises(HTTPException) as error:
        reviews.acknowledge([{"id": item["id"], "revision": item["revision"]}])
    assert error.value.status_code == 409


def test_receipt_failure_rolls_back_activity(monkeypatch):
    run = request()
    def fail(**kwargs):
        raise ValueError("receipt failed")
    monkeypatch.setattr(reviews, "AssistantTrackingReview", fail)
    assert reviews.execute_run(run)["status"] == "not_recorded"
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(TimeBlock)) == 0


def test_restart_keeps_receipt_and_marks_unexecuted_command_not_recorded():
    completed = request()
    saved = reviews.execute_run(completed)
    abandoned = request()
    storage.recover_interrupted()
    assert reviews.result(completed) == saved
    assert reviews.result(abandoned)["status"] == "not_recorded"
    assert len(reviews.listing()["items"]) == 1


def test_batch_is_atomic_when_one_revision_is_stale():
    reviews.execute_run(request())
    reviews.execute_run(request())
    items = reviews.listing()["items"]
    with pytest.raises(HTTPException):
        reviews.acknowledge([{"id": items[0]["id"], "revision": items[0]["revision"]}, {"id": items[1]["id"], "revision": "0" * 64}])
    assert reviews.listing()["unchecked_count"] == 2


def test_stream_mode_is_explicit_and_suppresses_confirmation_text(client, monkeypatch):
    from app.api.routes import assistant
    from app.core.config import get_settings
    from app.models.assistant import AssistantAttempt
    from app.services.assistant_sessions import conversations
    conversations.items.clear()
    monkeypatch.setattr(get_settings(), "openrouter_api_key", "test-key")
    seed = request()
    with Session(get_engine()) as db:
        proposal = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == seed)).tracking_proposal
    async def fake(*args, **kwargs):
        yield "tracking_proposal", {**proposal, "proposal_id": str(uuid4())}
        yield "text_delta", {"text": "Confirm to start tracking"}
    monkeypatch.setattr(assistant, "agent_events", fake)
    for mode in ("confirm", "review_later"):
        conversation = client.post("/assistant/conversations", json={"capabilities": ["tracking_proposal_v1"]}).json()["conversation_id"]
        body = {"run_id": str(uuid4()), "message": "Gym"}
        if mode == "review_later": body["tracking_mode"] = mode
        response = client.post(f"/assistant/conversations/{conversation}/messages", json=body)
        assert response.status_code == 200
        if mode == "confirm":
            assert "Confirm to start" in response.text
            assert not reviews.listing()["items"]
        else:
            assert "Confirm to start" not in response.text
            assert '"status": "recorded"' in response.text
            assert len(reviews.listing()["items"]) == 1
    conversations.items.clear()


def test_task_proposal_never_enters_immediate_tracking_execution(client, monkeypatch):
    from app.api.routes import assistant
    from app.core.config import get_settings
    from app.services.assistant_sessions import conversations
    conversations.items.clear()
    monkeypatch.setattr(get_settings(), "openrouter_api_key", "test-key")
    async def fake(*args, **kwargs):
        yield "task_proposal", {"kind": "task"}
        yield "text_delta", {"text": "Confirm the task changes."}
    monkeypatch.setattr(assistant, "agent_events", fake)
    monkeypatch.setattr(assistant.assistant_task_operations, "validate_event", lambda *args: {"kind": "task"})
    def forbidden(*args):
        pytest.fail("Task proposals must not enter immediate tracking execution")
    monkeypatch.setattr(reviews, "execute_run", forbidden)
    conversation = client.post("/assistant/conversations", json={"capabilities": ["tracking_proposal_v1"]}).json()["conversation_id"]
    response = client.post(f"/assistant/conversations/{conversation}/messages", json={"run_id": str(uuid4()), "message": "Complete my task", "tracking_mode": "review_later"})
    assert 'event: completed' in response.text
    assert not reviews.listing()["items"]
    conversations.items.clear()
