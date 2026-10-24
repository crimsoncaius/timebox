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


def event_body(item, at=None, **fields):
    stop = item["receipt"]["action"] == "stop"
    record = item["record"]
    return {"operation_id": uuid4(), "revision": item["revision"],
            "effective_at": reviews.instant(at or record["end_at" if stop else "start_at"]),
            **({} if stop else {"task_type_id": record["task_type_id"], "name": record["name"]}), **fields}


def test_start_event_correction_preserves_later_end_and_marks_reviewed_atomically():
    now = dt.datetime.now(dt.UTC)
    first = reviews.execute_run(request(at=(now - dt.timedelta(minutes=40)).isoformat()))
    reviews.execute_run(request(at=(now - dt.timedelta(minutes=20)).isoformat()))
    item = next(r for r in reviews.listing()["items"] if r["id"] == first["review_id"])
    with Session(get_engine()) as db:
        category = TaskType(name="Learning"); db.add(category); db.commit(); type_id = category.id
    body = event_body(item, now - dt.timedelta(minutes=35), task_type_id=type_id, name="Study")
    saved = reviews.correct(item["id"], body)
    assert saved["reviewed"] and not saved["changed_since_review"]
    assert saved["record"]["end_at"] == item["record"]["end_at"]
    assert saved["record"]["task_type_id"] == type_id
    assert reviews.instant(saved["record"]["start_at"]) == body["effective_at"]
    assert saved["receipt"] == item["receipt"]
    assert reviews.listing()["unchecked_count"] == 1
    assert reviews.correct(item["id"], body)["revision"] == saved["revision"]
    with pytest.raises(HTTPException) as error:
        reviews.correct(item["id"], {**body, "name": "different"})
    assert error.value.status_code == 409


def test_running_start_correction_keeps_open_end_and_leaves_gap():
    now = dt.datetime.now(dt.UTC)
    reviews.execute_run(request(at=(now - dt.timedelta(minutes=40)).isoformat()))
    reviews.execute_run(request(at=(now - dt.timedelta(minutes=20)).isoformat()))
    items = reviews.listing()["items"]
    item, earlier = items
    saved = reviews.correct(item["id"], event_body(item, now - dt.timedelta(minutes=10)))
    assert saved["record"]["end_at"] is None
    assert reviews.instant(saved["record"]["start_at"]) > reviews.instant(earlier["record"]["end_at"])
    assert saved["reviewed"]


def test_stop_event_correction_preserves_start_category_and_later_activity():
    now = dt.datetime.now(dt.UTC)
    reviews.execute_run(request(at=(now - dt.timedelta(minutes=40)).isoformat()))
    stop = reviews.execute_run(request(action="stop", at=(now - dt.timedelta(minutes=20)).isoformat()))
    reviews.execute_run(request(at=(now - dt.timedelta(minutes=10)).isoformat()))
    rows = reviews.listing()["items"]
    item = next(r for r in rows if r["id"] == stop["review_id"])
    current = rows[0]["record"]
    body = event_body(item, now - dt.timedelta(minutes=15))
    saved = reviews.correct(item["id"], body)
    assert saved["record"]["start_at"] == item["record"]["start_at"]
    assert saved["record"]["task_type_id"] == item["record"]["task_type_id"]
    assert saved["record"]["name"] == item["record"]["name"]
    assert reviews.instant(saved["record"]["end_at"]) == body["effective_at"]
    assert saved["reviewed"] and saved["correction_available"]
    assert reviews.listing()["items"][0]["record"] == current
    again = reviews.correct(saved["id"], event_body(saved, now - dt.timedelta(minutes=18)))
    assert again["reviewed"]


@pytest.mark.parametrize("case", ["future", "overlap", "stale", "wrong_fields", "before_start"])
def test_invalid_stop_correction_does_not_acknowledge_or_mutate(case):
    now = dt.datetime.now(dt.UTC)
    reviews.execute_run(request(at=(now - dt.timedelta(minutes=40)).isoformat()))
    stop = reviews.execute_run(request(action="stop", at=(now - dt.timedelta(minutes=20)).isoformat()))
    reviews.execute_run(request(at=(now - dt.timedelta(minutes=10)).isoformat()))
    item = next(r for r in reviews.listing()["items"] if r["id"] == stop["review_id"])
    body = event_body(item, now - dt.timedelta(minutes=15))
    if case == "future": body["effective_at"] = now + dt.timedelta(hours=1)
    if case == "overlap": body["effective_at"] = now - dt.timedelta(minutes=5)
    if case == "stale": body["revision"] = "0" * 64
    if case == "wrong_fields": body["task_type_id"] = item["record"]["task_type_id"]
    if case == "before_start": body["effective_at"] = now - dt.timedelta(minutes=50)
    with pytest.raises(HTTPException): reviews.correct(item["id"], body)
    unchanged = next(r for r in reviews.listing()["items"] if r["id"] == item["id"])
    assert not unchanged["reviewed"] and unchanged["record"] == item["record"]
    with Session(get_engine()) as db:
        from app.models.activity import ActivityOperation
        assert db.get(ActivityOperation, str(body["operation_id"])) is None


def test_event_endpoint_validates_and_saves(client):
    reviews.execute_run(request(at=(dt.datetime.now(dt.UTC) - dt.timedelta(minutes=20)).isoformat()))
    item = reviews.listing()["items"][0]
    body = event_body(item)
    payload = {k: str(v) if isinstance(v, (dt.datetime, type(uuid4()))) else v for k, v in body.items()}
    assert client.post(f"/assistant/reviews/{item['id']}/correct-event", json={**payload, "end_at": None}).status_code == 422
    response = client.post(f"/assistant/reviews/{item['id']}/correct-event", json=payload)
    assert response.status_code == 200, response.text
    assert response.json()["reviewed"]


def test_correction_and_acknowledgement_roll_back_together(monkeypatch):
    reviews.execute_run(request(at=(dt.datetime.now(dt.UTC) - dt.timedelta(minutes=20)).isoformat()))
    item = reviews.listing()["items"][0]
    body = event_body(item, name="Changed")
    original_view = reviews._view
    def failed_ack(db, state, row):
        if row.reviewed_at is not None:
            raise RuntimeError("acknowledgement failed")
        return original_view(db, state, row)
    monkeypatch.setattr(reviews, "_view", failed_ack)
    with pytest.raises(RuntimeError, match="acknowledgement failed"):
        reviews.correct(item["id"], body)
    monkeypatch.setattr(reviews, "_view", original_view)
    unchanged = reviews.listing()["items"][0]
    assert unchanged["record"] == item["record"] and not unchanged["reviewed"]
    with Session(get_engine()) as db:
        from app.models.activity import ActivityOperation
        assert db.get(ActivityOperation, str(body["operation_id"])) is None
