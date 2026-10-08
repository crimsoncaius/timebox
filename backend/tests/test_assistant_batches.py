"""Replay issue 314 through the real graph/tools; no paid model generations."""
import asyncio
import datetime as dt
import json
from pathlib import Path

import pytest
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.routes import assistant
from app.db.session import get_engine
from app.models.battle_plan import Project, Task
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.services import assistant_agent, assistant_plan, assistant_storage
from app.services.assistant_agent import build_agent, translate_events
from app.services.assistant_limits import MAX_MODEL_CALLS, MAX_READ_ROUNDS
from app.services.assistant_tasks import encoded
from tests.test_assistant import decode, isolated_assistant, send  # noqa: F401
from tests.test_assistant_loop import answer, call, run
from tests.test_assistant_presentation import plan
from tests.test_assistant_storage import attempts
from tests.test_assistant_tracking import CONTEXT, scripted, sgt

CAPTURED = json.loads((Path(__file__).parent / "fixtures/assistant_issue_314.json").read_text())


def batch(*calls):
    return AIMessage(content="", tool_calls=[c for message in calls for c in message.tool_calls],
                     response_metadata={"finish_reason": "tool_calls"})


@pytest.mark.parametrize("case", CAPTURED, ids=lambda case: case["scenario"])
def test_captured_batches_return_grounded_data_and_allow_proposal(client, monkeypatch, case):
    now = dt.datetime(2026, 10, 8, 7, 30, tzinfo=dt.UTC)
    monkeypatch.setattr(assistant_plan, "utc_now", lambda: now)
    with Session(get_engine()) as db:
        project, coding, reading = Project(name="Timebox"), TaskType(name="Work/Coding"), TaskType(name="Reading")
        db.add_all([project, coding, reading]); db.flush()
        db.add_all([Task(title="Due export", deadline_date=dt.date(2026, 10, 9)),
                    Task(title="Ready export", ready_to_plan=True)])
        for day, hours in [(6, 2), (1, 1)]:
            start = now.replace(day=day, hour=1)
            db.add(TimeBlock(lane=BlockLane.actual, task_type_id=reading.id,
                             start_at=start, end_at=start + dt.timedelta(hours=hours)))
        db.commit()
        project_id, coding_id = project.id, coding.id
    messages = [AIMessage(content="", tool_calls=case["calls"], response_metadata={"finish_reason": "tool_calls"})]
    creating = case["scenario"] == "09-create-task"
    if creating:
        messages.append(call("propose_task_changes", {"operations": [{"op": "create_task", "ref": "new",
            "title": "Check export on Android", "project_id": project_id, "task_type_id": coding_id,
            "deadline": {"kind": "day_offset", "days": 1}}]}, index=2))
    messages.append(answer())
    model = scripted(*messages)
    inputs = []
    original = assistant_agent.call_model

    async def record(model, messages):
        inputs.append(list(messages))
        return await original(model, messages)

    monkeypatch.setattr(assistant_agent, "call_model", record)

    async def fake(messages, snapshots, **kwargs):
        async for event in translate_events(build_agent(model).astream_events({"messages": messages}, version="v2"), snapshots):
            yield event

    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    events = decode(send(client, key))
    assert events[-1][0] == "completed"
    results = [m for m in inputs[1] if isinstance(m, ToolMessage)]
    assert [m.tool_call_id for m in results] == [c["id"] for c in case["calls"]]
    values = [json.loads(m.content) for m in results]
    assert all("error" not in v for v in values)
    assert len(attempts()[0].snapshots) == 2
    if case["scenario"] == "03-next-task":
        assert [[r["title"] for r in v["rows"]] for v in values] == [["Due export"], ["Ready export"]]
    elif case["scenario"] == "06-week-comparison":
        assert values[0]["types"][0]["actual_seconds"] == 7200
        assert values[1]["types"][0]["actual_seconds"] == 3600
    else:
        assert values[0]["rows"][0]["id"] == project_id
        assert values[1]["rows"][0]["id"] == coding_id
        assert len([v for k, v in events if k == "task_proposal"]) == 1
        with Session(get_engine()) as db:
            assert db.scalar(select(Task).where(Task.title == "Check export on Android")) is None


def test_mixed_tools_large_batch_counts_one_round_and_preserves_order(monkeypatch):
    executed = []
    monkeypatch.setattr(assistant_agent, "read_activity", lambda args: executed.append("activity") or plan())

    async def saved(arguments, choices):
        executed.append("choices" if choices else "tasks")
        return {"data": "界" * 30000}

    monkeypatch.setattr(assistant_agent, "task_read", saved)
    mixed = batch(call("read_tasks", {"mode": "search"}, 0),
                  call("read_task_choices", {"kind": "projects"}, 1),
                  *(call(index=i) for i in range(2, 14)))
    model = scripted(mixed, *(call(index=i + 14) for i in range(MAX_READ_ROUNDS - 1)),
                     call("propose_tracking", {"action": "stop"}, 20), answer())

    async def invoke():
        return await build_agent(model, {"sent_at": sgt(25, 16), "context": CONTEXT}).ainvoke(
            {"messages": [HumanMessage("Read then propose")]})

    state = asyncio.run(invoke())
    assert executed == ["tasks", "choices"] + ["activity"] * 16
    assert state["read_rounds"] == MAX_READ_ROUNDS
    assert state["model_calls"] == MAX_MODEL_CALLS
    results = [m for m in state["messages"] if isinstance(m, ToolMessage)]
    assert [m.tool_call_id for m in results[:14]] == [c["id"] for c in mixed.tool_calls]
    assert all("error" not in json.loads(m.content) for m in results)
    assert sum(len(m.content.encode()) for m in results[:2]) > 65536


@pytest.mark.parametrize("bad", [call("unknown"), call("propose_task_changes", {"operations": []}),
                                  call("propose_tracking", {"action": "stop"})])
def test_rejected_batch_executes_nothing_and_can_recover(monkeypatch, bad):
    executed = []
    monkeypatch.setattr(assistant_agent, "read_activity", lambda args: executed.append(args) or plan())
    rejected = batch(call(index=1), bad)
    events = run(scripted(rejected, call(index=2), answer()))
    assert len(executed) == 1
    assert [v["tool_call_id"] for k, v in events if k == "read_error"] == [c["id"] for c in rejected.tool_calls]
    assert not any(k.endswith("proposal") for k, _ in events)


def test_rejected_rounds_cannot_loop_indefinitely():
    model = scripted(*(call("unknown", index=i) for i in range(6)), answer())
    events = run(model)
    assert len([v for k, v in events if k == "read_error"]) == 6
    assert model.script == []


@pytest.mark.parametrize("failure", ["validation", "runtime"])
def test_failed_read_keeps_other_results_and_does_not_retry(monkeypatch, failure):
    executed = []

    def read(args):
        executed.append(args)
        if failure == "runtime" and len(executed) == 1:
            raise RuntimeError("private database detail")
        return plan()

    monkeypatch.setattr(assistant_agent, "read_activity", read)
    first = call(args={"lane": "invalid"} if failure == "validation" else {}, index=1)
    events = run(scripted(batch(first, call(index=2)), answer()))
    assert len([v for k, v in events if k == "read_error"]) == 1
    assert len([v for k, v in events if k == "snapshot_read"]) == 1
    assert len(executed) == (1 if failure == "validation" else 2)
    assert len([k for k, _ in events if k == "tool_started"]) == len([k for k, _ in events if k == "tool_completed"])
    assert "private database detail" not in str(events)


def test_cancellation_stops_batch_before_next_read(monkeypatch):
    executed = []

    async def invoke():
        entered = asyncio.Event()

        async def blocked(arguments, choices):
            executed.append(arguments)
            entered.set()
            await asyncio.Future()

        monkeypatch.setattr(assistant_agent, "task_read", blocked)
        model = scripted(batch(call("read_tasks", {"mode": "search"}, 1),
                               call("read_tasks", {"mode": "search"}, 2)), answer())

        async def collect():
            return [event async for event in build_agent(model).astream_events(
                {"messages": [HumanMessage("Read tasks")]}, version="v2")]

        task = asyncio.create_task(collect())
        await asyncio.wait_for(entered.wait(), timeout=5)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task

    asyncio.run(invoke())
    assert len(executed) == 1


def test_large_snapshots_survive_route_capture_and_historical_reload(client, monkeypatch):
    # Real read projections exceed both former per-read and cumulative byte caps.
    with Session(get_engine()) as db:
        db.add_all([Task(title="界" * 500, description="界" * 2400) for _ in range(20)])
        db.commit()
    model = scripted(batch(*(call("read_tasks", {"mode": "search", "search_in": "descriptions", "query": "界"}, i)
                             for i in range(6))), answer())

    async def fake(messages, snapshots, **kwargs):
        async for event in translate_events(build_agent(model).astream_events({"messages": messages}, version="v2"), snapshots):
            yield event

    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    events = decode(send(client, key))
    assert events[-1][0] == "completed"
    row = attempts()[0]
    assert len(row.snapshots) == 6
    assert all(len(encoded(v)) > 65536 for v in row.snapshots.values())
    client.post(f"/assistant/conversations/{key}/runs/{row.run_id}/ack")
    assert assistant_storage.load(key)[2] == row.snapshots
    captured = []

    async def second(messages, snapshots, **kwargs):
        captured.extend(messages)
        yield "text_delta", {"text": "History available."}

    monkeypatch.setattr(assistant, "agent_events", second)
    assert decode(send(client, key))[-1][0] == "completed"
    historical = next(m.content for m in captured if m.content.startswith("Historical snapshots"))
    assert len(historical.encode()) > 65536
