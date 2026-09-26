import asyncio
import importlib.util
from pathlib import Path
from uuid import uuid4

import pytest
from alembic.migration import MigrationContext
from alembic.operations import Operations
from sqlalchemy import create_engine, func, inspect, select
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

from app.api.routes import assistant
from app.db.session import get_engine
from app.models.assistant import AssistantAttempt, AssistantConversation
from app.services import assistant_storage
from app.services.assistant_presentation import snapshot
from app.services.assistant_sessions import Conversations, conversations
from tests.test_assistant import decode, isolated_assistant, send  # noqa: F401 -- autouse fixture


def attempts():
    with Session(get_engine()) as db:
        return list(db.scalars(select(AssistantAttempt).order_by(AssistantAttempt.id)))


def test_completed_record_survives_cache_loss_and_new_conversation(client, monkeypatch):
    async def fake(messages, snapshots=None):
        yield "text_delta", {"text": "Saved answer"}
    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    frames = decode(send(client, key))
    row = attempts()[0]
    assert (row.question, row.answer, row.status, row.acknowledged) == (
        "Today's plan?", "Saved answer", "completed", False,
    )
    # The last answer is captured even when the user never sends a follow-up.
    conversations.items.clear()
    assert conversations.get(key).messages == []
    run = frames[0][1]["run_id"]
    assert client.post(f"/assistant/conversations/{key}/runs/{run}/ack").status_code == 204
    conversations.items.clear()
    assert conversations.get(key).messages[-1].content == "Saved answer"
    assert client.delete(f"/assistant/conversations/{key}").status_code == 204
    assert client.delete(f"/assistant/conversations/{key}").status_code == 204
    assert send(client, key).status_code == 410
    assert attempts()[0].answer == "Saved answer"
    with Session(get_engine()) as db:
        assert db.get(AssistantConversation, key).closed_at is not None
    new = client.post("/assistant/conversations").json()["conversation_id"]
    assert conversations.get(new).messages == []


def test_rolling_context_preserves_full_record_and_bounds_snapshots(client, monkeypatch):
    seen = []
    plans = []
    async def fake(messages, snapshots=None):
        seen.append((messages, dict(snapshots)))
        item = snapshot({"date": "2026-09-25", "reporting_timezone": "UTC", "planned_blocks": []})
        plans.append(item)
        yield "snapshot_read", item
        yield "plan_card", item
        yield "text_delta", {"text": str(len(plans))}
    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations", json={"capabilities": ["plan_card_v1"]}).json()["conversation_id"]
    for index in range(23):
        run = str(uuid4())
        response = client.post(f"/assistant/conversations/{key}/messages", json={"message": f"Question {index}", "run_id": run})
        assert decode(response)[-1][0] == "completed"
        assert client.post(f"/assistant/conversations/{key}/runs/{run}/ack").status_code == 204
    assert len(attempts()) == 23
    conversations.items.clear()
    item = conversations.get(key)
    assert len(item.messages) == 40
    assert item.messages[0].content == "Question 3"
    assert set(item.snapshots) == {plan["snapshot_id"] for plan in plans[-20:]}
    assert len(seen[-1][1]) == 20
    assert seen[-1][0][2].content == "Question 2"  # after the Now and historical-snapshots system messages


def test_historical_card_stays_available_when_original_read_leaves_window(client, monkeypatch):
    plan = snapshot({"date": "2026-09-25", "reporting_timezone": "UTC", "planned_blocks": []})
    count = 0
    async def fake(messages, snapshots=None):
        nonlocal count
        if count == 0:
            yield "snapshot_read", plan
        count += 1
        yield "plan_card", plan
    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations", json={"capabilities": ["plan_card_v1"]}).json()["conversation_id"]
    for _ in range(22):
        frames = decode(send(client, key))
        assert frames[-1][0] == "completed"
        client.post(f"/assistant/conversations/{key}/runs/{frames[0][1]['run_id']}/ack")
    assert conversations.get(key).snapshots == {plan["snapshot_id"]: plan}


@pytest.mark.parametrize("cancelled", [False, True])
def test_partial_attempts_and_cards_are_captured_but_never_context(client, monkeypatch, cancelled):
    plan = snapshot({"date": "2026-09-25", "reporting_timezone": "UTC", "planned_blocks": []})
    async def fake(messages, snapshots=None):
        yield "snapshot_read", plan
        yield "plan_card", plan
        yield "text_delta", {"text": "Partial answer"}
        if cancelled:
            raise asyncio.CancelledError()
        raise RuntimeError("private provider detail")
    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations", json={"capabilities": ["plan_card_v1"]}).json()["conversation_id"]
    frames = decode(send(client, key))
    row = attempts()[0]
    assert row.answer == "Partial answer"
    assert row.displayed_plan == plan
    assert row.status == ("stopped" if cancelled else "interrupted")
    client.post(f"/assistant/conversations/{key}/runs/{frames[0][1]['run_id']}/ack")
    assert conversations.get(key).messages == []
    assert conversations.get(key).snapshots == {}


def test_message_save_failure_prevents_generation_and_releases_run(client, monkeypatch):
    key = client.post("/assistant/conversations").json()["conversation_id"]
    def broken_engine():
        raise OperationalError("test", {}, Exception("secret connection details"))
    with monkeypatch.context() as patch:
        patch.setattr(assistant_storage, "get_engine", broken_engine)
        response = send(client, key)
    assert response.status_code == 503
    assert "message could not be saved" in response.text
    assert "secret" not in response.text
    assert conversations.get(key).run_id is None
    assert attempts() == []


def test_response_save_failure_is_visible_and_does_not_complete(client, monkeypatch):
    async def fake(messages, snapshots=None):
        yield "text_delta", {"text": "Visible answer"}
    def failed_capture(*args, **kwargs):
        raise assistant_storage.CaptureError("The response could not be saved. Please retry.")
    monkeypatch.setattr(assistant, "agent_events", fake)
    monkeypatch.setattr(assistant_storage, "capture", failed_capture)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    frames = decode(send(client, key))
    assert [kind for kind, _ in frames] == ["started", "text_delta", "failed"]
    assert "could not be saved" in frames[-1][1]["message"]
    assert conversations.get(key).messages == []
    assert attempts()[0].question == "Today's plan?"


def test_duplicate_run_never_overwrites_or_regenerates(client, monkeypatch):
    async def fake(messages, snapshots=None):
        yield "text_delta", {"text": "Original"}
    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    run = str(uuid4())
    endpoint = f"/assistant/conversations/{key}/messages"
    assert decode(client.post(endpoint, json={"message": "First", "run_id": run}))[-1][0] == "completed"
    assert client.post(endpoint, json={"message": "Replacement", "run_id": run}).status_code == 409
    assert [(a.question, a.answer) for a in attempts()] == [("First", "Original")]


def test_restart_recovery_keeps_partial_output_and_does_not_acknowledge():
    store = Conversations()
    key = store.create()
    run = str(uuid4())
    assistant_storage.begin(key, run, "Question", "test-model")
    assistant_storage.capture(run, "Partial", {}, None)
    assistant_storage.recover_interrupted()
    assert attempts()[0].status == "interrupted"
    assert attempts()[0].answer == "Partial"
    assert Conversations().get(key).messages == []


def load_migration(name):
    path = Path(__file__).parents[1] / f"alembic/versions/{name}.py"
    spec = importlib.util.spec_from_file_location(name, path)
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    return migration


def test_migration_can_store_records_and_survive_engine_reopen(tmp_path, monkeypatch):
    migration = load_migration("033_assistant_conversations")
    proposals = load_migration("035_assistant_tracking_proposal")
    url = "sqlite:///" + str(tmp_path / "assistant.db")
    engine = create_engine(url)
    with engine.begin() as connection, Operations.context(MigrationContext.configure(connection)):
        migration.upgrade()
        proposals.upgrade()
    monkeypatch.setattr(assistant_storage, "get_engine", lambda: engine)
    key = Conversations().create(["plan_card_v1"])
    run = str(uuid4())
    assistant_storage.begin(key, run, "Persist me", "test-model")
    assistant_storage.capture(run, "Durable", {}, None, "completed")
    assistant_storage.acknowledge(key, run)
    engine.dispose()
    engine = create_engine(url)
    assert Conversations().get(key).messages[-1].content == "Durable"
    with Session(engine) as db:
        assert db.scalar(select(func.count()).select_from(AssistantAttempt)) == 1
    with engine.begin() as connection, Operations.context(MigrationContext.configure(connection)):
        proposals.downgrade()
        assert "tracking_proposal" not in {c["name"] for c in inspect(connection).get_columns("assistant_attempts")}
        migration.downgrade()
        assert "assistant_attempts" not in inspect(connection).get_table_names()
    engine.dispose()
