import asyncio
import datetime as dt
import json
import time
from uuid import uuid4

import pytest
from fastapi import HTTPException
from langchain_core.messages import HumanMessage, AIMessage, AIMessageChunk
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.api.routes import assistant
from app.core.config import get_settings
from app.db.session import get_engine
from app.models.day import Day
from app.models.time_block import TimeBlock, BlockLane
from app.models.task_type import TaskType
from app.models.battle_plan import Task
from app.services.assistant_plan import read_today_plan
from app.services.assistant_sessions import Conversations, conversations


@pytest.fixture(autouse=True)
def isolated_assistant(monkeypatch):
    conversations.items.clear()
    monkeypatch.setattr(get_settings(), "openrouter_api_key", "test-key-not-real")
    monkeypatch.setattr(get_settings(), "assistant_trace_endpoint", None)
    yield
    conversations.items.clear()


def decode(response):
    result = []
    for frame in response.text.split("\n\n"):
        lines = frame.splitlines()
        if len(lines) == 2 and lines[0].startswith("event:"):
            result.append((lines[0][7:], json.loads(lines[1][6:])))
    return result


def send(client, conversation):
    return client.post(f"/assistant/conversations/{conversation}/messages",
                       json={"message": "Today's plan?", "run_id": str(uuid4())})


def test_plan_is_read_only_and_projects_only_agreed_fields(monkeypatch):
    from app.services import assistant_plan
    today = dt.date(2026, 9, 19)
    monkeypatch.setattr(assistant_plan, "today_in_tz", lambda zone: today)
    assert read_today_plan()["planned_blocks"] == []
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(Day)) == 0
        day = Day(date=today)
        kind = TaskType(name="Work")
        task = Task(title="Prepare demo", description="PRIVATE DESCRIPTION")
        db.add_all([day, kind, task]); db.flush()
        db.add(TimeBlock(day_id=day.id, lane=BlockLane.planned, start_minute=540,
                         end_minute=600, task_type_id=kind.id, task_id=task.id,
                         name="Demo", note="PRIVATE NOTE"))
        db.commit()
        task_id = task.id
    result = read_today_plan()
    assert result["planned_blocks"] == [{"start_minute": 540, "end_minute": 600,
        "name": "Demo", "task_type": "Work", "task_id": task_id, "task_title": "Prepare demo"}]
    assert "PRIVATE" not in json.dumps(result)


def test_reporting_timezone_resolved_at_tool_execution(monkeypatch):
    from app.services import assistant_plan
    zones = []
    monkeypatch.setattr(assistant_plan, "reporting_settings", lambda db, s: s.model_copy(update={"app_timezone": "Asia/Singapore"}))
    monkeypatch.setattr(assistant_plan, "today_in_tz", lambda zone: zones.append(zone) or dt.date(2026, 9, 20))
    assert read_today_plan()["date"] == "2026-09-20"
    assert zones == ["Asia/Singapore"]


def test_stream_context_committed_only_after_ack(client, monkeypatch):
    seen = []
    async def fake(messages):
        seen.append(messages)
        yield "tool_started", {}
        yield "tool_completed", {}
        yield "text_delta", {"text": "Plan"}
    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    frames = decode(send(client, key))
    assert [k for k, _ in frames] == ["started", "tool_started", "tool_completed", "text_delta", "completed"]
    assert [v["sequence"] for _, v in frames] == list(range(1, 6))
    assert conversations.get(key).messages == []
    run = frames[0][1]["run_id"]
    client.post(f"/assistant/conversations/{key}/runs/{run}/ack")
    client.post(f"/assistant/conversations/{key}/runs/{run}/ack")
    send(client, key)
    assert len(seen[1]) == 3


@pytest.mark.parametrize("error", [RuntimeError("SECRET PROVIDER ERROR"), TimeoutError()])
def test_interrupted_run_never_enters_history(client, monkeypatch, error):
    async def fake(messages):
        yield "text_delta", {"text": "Partial"}
        raise error
    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    response = send(client, key)
    assert decode(response)[-1][0] == "failed"
    assert "SECRET" not in response.text
    assert conversations.get(key).messages == []
    assert conversations.get(key).run_id is None


def test_timeout_and_stop_release_session(client, monkeypatch):
    async def slow(messages):
        yield "text_delta", {"text": "Partial"}
        await asyncio.sleep(1)
    monkeypatch.setattr(assistant, "agent_events", slow)
    monkeypatch.setattr(assistant, "RESPONSE_TIMEOUT", .01)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    assert "two minutes" in send(client, key).text
    assert conversations.get(key).messages == []


def test_limits_and_expiry(client):
    store = Conversations()
    key = store.create()
    item = store.reserve(key, "first")
    with pytest.raises(HTTPException) as error:
        store.reserve(key, "second")
    assert error.value.status_code == 409
    item.run_id = None
    item.messages = [HumanMessage("q"), AIMessage("a")] * 20
    with pytest.raises(HTTPException): store.reserve(key, "third")
    item.touched = time.monotonic() - 3601
    with pytest.raises(HTTPException) as error: store.get(key)
    assert error.value.status_code == 410
    key = client.post("/assistant/conversations").json()["conversation_id"]
    for message in ["", "   ", "x" * 4001]:
        assert client.post(f"/assistant/conversations/{key}/messages", json={"message": message, "run_id": str(uuid4())}).status_code == 422


def test_stop_and_reset_cancel_work():
    async def scenario():
        store = Conversations()
        key = store.create()
        item = store.reserve(key, "run")
        item.task = asyncio.create_task(asyncio.sleep(60))
        store.stop(key, "other")
        assert not item.task.cancelling()
        store.stop(key, "run")
        assert item.task.cancelling()
        store.delete(key)
        assert key not in store.items
        with pytest.raises(asyncio.CancelledError): await item.task
    asyncio.run(scenario())


def test_provider_errors_are_actionable_without_raw_details():
    for code, text in [(401, "authentication"), (402, "credits"), (429, "rate limited")]:
        error = RuntimeError("SECRET")
        error.status_code = code
        assert text in assistant.public_error(error)
        assert "SECRET" not in assistant.public_error(error)


def test_trace_export_redacts_credentials():
    from app.services.assistant_tracing import RedactingExporter
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import SimpleSpanProcessor
    from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter
    sink = InMemorySpanExporter()
    provider = TracerProvider()
    provider.add_span_processor(SimpleSpanProcessor(RedactingExporter(sink, ["sentinel-secret"])))
    with provider.get_tracer("test").start_as_current_span("response") as span:
        span.set_attribute("input.value", "Plan sentinel-secret")
        span.record_exception(RuntimeError("sentinel-secret"))
    exported = sink.get_finished_spans()[0]
    assert exported.attributes["input.value"] == "Plan [redacted]"
    assert "sentinel-secret" not in str(exported.events[0].attributes)


def test_assistant_requires_timebox_key(client, monkeypatch):
    monkeypatch.setattr(get_settings(), "api_key", "backend-secret")
    assert client.post("/assistant/conversations").status_code == 401
    assert client.post("/assistant/conversations", headers={"X-API-Key": "backend-secret"}).status_code == 200


def test_tool_read_failure_is_not_an_empty_plan(monkeypatch):
    from app.services import assistant_agent
    def broken(): raise RuntimeError("database connection SECRET")
    monkeypatch.setattr(assistant_agent, "read_today_plan", broken)
    with pytest.raises(RuntimeError, match="could not be read") as error:
        asyncio.run(assistant_agent.read_today_plan_tool.ainvoke({}))
    assert "SECRET" not in str(error.value)


def test_graph_only_allows_one_known_tool_and_final_model_cannot_loop(monkeypatch):
    from app.services import assistant_agent
    class Model:
        def bind_tools(self, tools): return self
        async def astream(self, messages):
            yield AIMessageChunk(content="", tool_calls=[{"name": "write_plan", "args": {}, "id": "call1"}])
    monkeypatch.setattr(assistant_agent, "create_model", Model)
    with pytest.raises(RuntimeError, match="unsupported tool"):
        asyncio.run(assistant_agent.build_agent().ainvoke({"messages": [HumanMessage("change my plan")]}))


def test_missing_provider_finish_event_is_interrupted(monkeypatch):
    from app.services import assistant_agent
    class Graph:
        def __init__(self, model): pass
        async def astream_events(self, *args, **kwargs):
            yield {"event": "on_chat_model_end", "data": {"output": AIMessage("truncated")}}
    monkeypatch.setattr(assistant_agent, "build_agent", Graph)
    async def consume():
        return [event async for event in assistant_agent.agent_events([HumanMessage("hello")])]
    with pytest.raises(RuntimeError, match="incomplete"):
        asyncio.run(consume())


def test_provider_503_is_not_retried():
    import httpx
    from app.services.assistant_agent import create_model
    requests = []
    async def scenario():
        model = create_model()
        config = model.client.sdk_configuration
        await config.async_client.aclose()
        def fail(request):
            requests.append(request)
            return httpx.Response(503, json={"error": {"message": "test unavailable", "code": 503}})
        config.async_client = httpx.AsyncClient(transport=httpx.MockTransport(fail))
        with model.client:
            async with model.client:
                async def consume():
                    async for _ in model.astream([HumanMessage("hello")]): pass
                with pytest.raises(Exception) as error:
                    await asyncio.wait_for(consume(), timeout=2)
                assert not isinstance(error.value, TimeoutError)
    asyncio.run(scenario())
    assert len(requests) == 1
