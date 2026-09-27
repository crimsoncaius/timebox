import asyncio
import json

import pytest
from langchain_core.messages import AIMessage, HumanMessage

from app.api.routes import assistant
from app.services import assistant_agent, assistant_storage
from app.services.assistant_agent import build_agent, translate_events
from app.services.assistant_limits import MAX_CARDS, MAX_READ_CALLS
from app.services.assistant_presentation import PresentationParser
from app.services.assistant_sessions import conversations
from tests.test_assistant import decode, isolated_assistant, send  # noqa: F401
from tests.test_assistant_presentation import plan
from tests.test_assistant_storage import attempts
from tests.test_assistant_tracking import CONTEXT, scripted, sgt


def call(name="read_activity", args=None, index=0):
    return AIMessage(content="Discard this preamble", tool_calls=[
        {"id": f"call-{index}", "name": name, "args": args or {}}],
        response_metadata={"finish_reason": "tool_calls"})


def answer(keys=()):
    header = {"presentation": "snapshots", "snapshot_ids": list(keys)} if keys else {"presentation": "none"}
    return AIMessage(content=json.dumps(header) + "\nCompare these reads.", response_metadata={"finish_reason": "stop"})


def run(model, tracking=None):
    async def collect():
        return [item async for item in translate_events(build_agent(model, tracking).astream_events(
            {"messages": [HumanMessage("Compare, then track what I planned since 2")]}, version="v2"))]
    return asyncio.run(collect())


def test_three_reads_then_proposal_and_ordered_cards(monkeypatch):
    plans = [plan() for _ in range(MAX_READ_CALLS)]
    pending = iter(plans)
    monkeypatch.setattr(assistant_agent, "read_activity", lambda args: next(pending))
    # The tool assigns fresh snapshot IDs; retain deterministic IDs for the selector.
    monkeypatch.setattr(assistant_agent, "snapshot", lambda value: value)
    model = scripted(*(call(index=i) for i in range(MAX_READ_CALLS)),
                     call("propose_tracking", {"action": "track", "task_type_paths": ["Meals"], "hour": 2}, 3),
                     answer([plans[2]["snapshot_id"], plans[0]["snapshot_id"]]))
    events = run(model, {"sent_at": sgt(25, 16), "context": CONTEXT})
    assert [v for k, v in events if k == "snapshot_read"] == plans
    assert [v for k, v in events if k == "plan_card"] == [plans[2], plans[0]]
    assert len([k for k, _ in events if k == "tracking_proposal"]) == 1
    assert ''.join(v["text"] for k, v in events if k == "text_delta") == "Compare these reads."
    assert model.script == []


@pytest.mark.parametrize("tracking_enabled", [False, True])
def test_fourth_read_cannot_execute(monkeypatch, tracking_enabled):
    executed = []
    monkeypatch.setattr(assistant_agent, "read_activity", lambda args: executed.append(args) or plan())
    tracking = {"sent_at": sgt(25, 16), "context": CONTEXT} if tracking_enabled else None
    with pytest.raises(RuntimeError, match="unsupported tool|did not finish"):
        run(scripted(*(call(index=i) for i in range(MAX_READ_CALLS + 1))), tracking)
    assert len(executed) == MAX_READ_CALLS


@pytest.mark.parametrize("next_call", [call(), call("propose_tracking", {"action": "stop"})])
def test_proposal_forces_tool_free_finish(next_call):
    with pytest.raises(RuntimeError, match="did not finish"):
        run(scripted(call("propose_tracking", {"action": "stop"}), next_call),
            {"sent_at": sgt(25, 16), "context": CONTEXT})


def test_multiple_cards_all_chunk_boundaries_and_duplicate_limit():
    plans = [plan() for _ in range(MAX_CARDS)]
    snapshots = {p["snapshot_id"]: p for p in plans}
    wire = json.dumps({"presentation": "snapshots", "snapshot_ids": list(reversed(snapshots))})
    for split in range(len(wire) + 1):
        parser = PresentationParser(snapshots)
        assert parser.feed(wire[:split]) + parser.feed(wire[split:]) == []
        assert parser.finish(successful_terminal=True) == [("plan_card", p) for p in reversed(plans)]
    for keys in [[], list(snapshots) * 2, [next(iter(snapshots))] * 2, ["unknown"]]:
        with pytest.raises(ValueError):
            PresentationParser(snapshots).feed(json.dumps({"presentation": "snapshots", "snapshot_ids": keys}) + "\n")


@pytest.mark.parametrize("interrupted", [False, True])
@pytest.mark.parametrize("capabilities", [[], ["plan_card_v1"]])
def test_multiple_cards_capture_ack_and_oldest_read_eviction(client, monkeypatch, interrupted, capabilities):
    plans = [plan() for _ in range(MAX_CARDS)]
    async def fake(messages, snapshots):
        for value in plans:
            yield "snapshot_read", value
        for value in reversed(plans):
            yield "plan_card", value
        if interrupted:
            raise asyncio.CancelledError()
        yield "text_delta", {"text": "Comparison"}
    monkeypatch.setattr(assistant, "agent_events", fake)
    # Exactly the newest two whole snapshots fit; card selection order must not alter age.
    newest = {p["snapshot_id"]: p for p in plans[1:]}
    monkeypatch.setattr(assistant_storage, "MAX_CONTEXT_READ_BYTES", len(json.dumps(newest).encode("utf-8")))
    key = client.post("/assistant/conversations", json={"capabilities": capabilities}).json()["conversation_id"]
    events = decode(send(client, key))
    assert events[-1][0] == ("stopped" if interrupted else "completed")
    assert attempts()[0].displayed_plan == list(reversed(plans))

    assert len(attempts()[0].snapshots) == MAX_READ_CALLS
    assert conversations.get(key).snapshots == {}
    client.post(f"/assistant/conversations/{key}/runs/{events[0][1]['run_id']}/ack")
    conversations.items.clear()
    item = conversations.get(key)
    assert item.snapshots == ({} if interrupted else newest)
    if not interrupted:
        assert plans[0]["snapshot_id"] not in str(item.messages)
        assert "No Planned Blocks" not in str(item.messages)
        assert len({c["id"] for m in item.messages for c in getattr(m, "tool_calls", [])}) == 2
    # Context pruning never edits the retained cards or data.
    assert attempts()[0].displayed_plan == list(reversed(plans))


@pytest.mark.parametrize("stopped", [False, True])
def test_later_model_call_still_obeys_stop_and_response_deadline(client, monkeypatch, stopped):
    original = assistant_agent.call_model
    calls = 0
    async def delayed(model, messages):
        nonlocal calls
        calls += 1
        if calls == 3:
            if stopped:
                conversations.get(key).task.cancel()
            await asyncio.sleep(5)
        return await original(model, messages)
    monkeypatch.setattr(assistant_agent, "call_model", delayed)
    monkeypatch.setattr(assistant_agent, "read_activity", lambda args: plan())
    monkeypatch.setattr(assistant, "RESPONSE_TIMEOUT", 2)
    async def fake(messages, snapshots):
        graph = build_agent(scripted(call(index=0), call(index=1), answer()))
        async for event in translate_events(graph.astream_events({"messages": messages}, version="v2"), snapshots):
            yield event
    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    events = decode(send(client, key))
    assert calls == 3
    assert events[-1][0] == ("stopped" if stopped else "failed")
    row = attempts()[0]
    assert len(row.snapshots) == 2 and row.status == ("stopped" if stopped else "interrupted")
    client.post(f"/assistant/conversations/{key}/runs/{row.run_id}/ack")
    assert conversations.get(key).snapshots == {}

