import asyncio
import datetime as dt
import json
import re
from uuid import uuid4

import pytest
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage
from langchain_core.outputs import ChatGeneration, ChatGenerationChunk, ChatResult

from app.api.routes import assistant
from app.services import assistant_tracking
from app.services.assistant_agent import build_agent, translate_events
from app.services.assistant_sessions import conversations
from app.services.assistant_tracking import ProposeTrackingArgs, StatedTime, propose, resolve_time
from tests.test_assistant import decode, isolated_assistant  # noqa: F401 -- autouse fixture

SGT = "Asia/Singapore"
CONTEXT = {"reporting_timezone": SGT, "task_types": [{"id": 1, "path": "Meals"}, {"id": 2, "path": "Exercise/Gym"},
                                                      {"id": 3, "path": "Exercise/Running"}]}


def sgt(day, hour, minute=0):
    return dt.datetime(2026, 9, day, hour, minute, tzinfo=dt.timezone(dt.timedelta(hours=8))).astimezone(dt.UTC)


@pytest.mark.parametrize("stated, sent, expected", [
    (StatedTime(minutes_ago=10), sgt(25, 12, 50), sgt(25, 12, 40)),
    (StatedTime(hour=3), sgt(25, 16), sgt(25, 15)),          # "since 3": most recent past 3:00
    (StatedTime(hour=3), sgt(25, 2), sgt(24, 15)),            # both 3:00s are ahead today
    (StatedTime(hour=3, meridiem="am"), sgt(25, 16), sgt(25, 3)),
    (StatedTime(hour=12, minute=30, meridiem="am"), sgt(25, 1), sgt(25, 0, 30)),
    (StatedTime(hour=21), sgt(25, 16), sgt(24, 21)),          # 24-hour clock, yesterday evening
    (None, sgt(25, 16), None),                                # no stated time: the confirmation instant
])
def test_stated_times_resolve_in_reporting_time_zone_and_never_future(stated, sent, expected):
    assert resolve_time(stated, sent, SGT) == expected


def test_stated_time_requires_exactly_one_form():
    with pytest.raises(ValueError):
        StatedTime()
    with pytest.raises(ValueError):
        StatedTime(minutes_ago=5, hour=3)


def test_proposal_keeps_existing_paths_and_never_creates_task_types():
    result = propose(ProposeTrackingArgs(action="track", task_type_paths=["exercise/gym", "Exercise/Running", "Yoga"]),
                     sgt(25, 12, 50), CONTEXT)
    assert [t["path"] for t in result["proposal"]["task_types"]] == ["Exercise/Gym", "Exercise/Running"]
    assert result["ignored_paths"] == ["Yoga"]
    missing = propose(ProposeTrackingArgs(action="track", task_type_paths=["Yoga"]), sgt(25, 12, 50), CONTEXT)
    assert "proposal" not in missing and "cannot create" in missing["error"]


def test_nested_time_from_the_model_is_not_dropped():
    args = ProposeTrackingArgs.model_validate({"action": "track", "task_type_paths": ["Meals"], "time": {"minutes_ago": 10}})
    assert args.stated_time() == StatedTime(minutes_ago=10)


def test_proposal_is_fixed_at_sending_and_expires_after_fifteen_minutes():
    result = propose(ProposeTrackingArgs(action="stop", minutes_ago=5, task_type_paths=["Meals"]), sgt(25, 12, 50), CONTEXT)["proposal"]
    assert result["action"] == "stop" and result["task_types"] == []
    assert result["at"] == "2026-09-25T04:45:00Z"
    assert result["proposed_at"] == "2026-09-25T04:50:00Z"
    assert result["expires_at"] == "2026-09-25T05:05:00Z"


class ScriptedModel(BaseChatModel):
    """Streams one scripted message per call, including tool calls and the provider finish reason."""
    script: list

    @property
    def _llm_type(self):
        return "scripted"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=self.script.pop(0))])

    async def _astream(self, messages, stop=None, run_manager=None, **kwargs):
        message = self.script.pop(0)
        chunk = ChatGenerationChunk(message=AIMessageChunk(
            content=message.content, response_metadata=message.response_metadata,
            tool_call_chunks=[{"name": c["name"], "args": json.dumps(c["args"]), "id": c["id"], "index": 0}
                              for c in message.tool_calls]))
        if run_manager:
            await run_manager.on_llm_new_token(message.content, chunk=chunk)
        yield chunk


def scripted(*messages):
    return ScriptedModel(script=list(messages))


def proposal_turn(args, answer):
    return scripted(
        AIMessage(content="", tool_calls=[{"id": "call1", "name": "propose_tracking", "args": args}],
                  response_metadata={"finish_reason": "tool_calls"}),
        AIMessage(content='{"presentation":"none"}\n' + answer, response_metadata={"finish_reason": "stop"}),
    )


async def events_for(model, tracking):
    graph = build_agent(model, tracking)
    return [event async for event in translate_events(graph.astream_events({"messages": [HumanMessage("x")]}, version="v2"))]


def test_graph_calls_propose_tracking_and_emits_card_before_text():
    tracking = {"sent_at": sgt(25, 12, 50), "context": CONTEXT}
    model = proposal_turn({"action": "track", "task_type_paths": ["Meals"], "minutes_ago": 10}, "Confirm below.")
    events = asyncio.run(events_for(model, tracking))
    kinds = [k for k, _ in events]
    assert kinds.index("tracking_proposal") < kinds.index("text_delta")
    proposal = dict(events)["tracking_proposal"]
    assert proposal["at"] == "2026-09-25T04:40:00Z" and proposal["task_types"] == [{"id": 1, "path": "Meals"}]


def test_model_reads_proposal_times_in_the_reporting_time_zone():
    tracking = {"sent_at": sgt(25, 12, 50), "context": CONTEXT}
    model = proposal_turn({"action": "track", "task_type_paths": ["Meals"], "minutes_ago": 10}, "Confirm below.")

    async def tool_outputs():
        graph = build_agent(model, tracking)
        return [e["data"]["output"] async for e in graph.astream_events({"messages": [HumanMessage("x")]}, version="v2")
                if e["event"] == "on_tool_end"]

    [output] = asyncio.run(tool_outputs())
    assert json.loads(output.content)["proposal"]["at"] == "Friday 2026-09-25 12:40 in Asia/Singapore"
    assert "04:40" not in output.content
    assert output.artifact["proposal"]["at"] == "2026-09-25T04:40:00Z"  # the card still gets the UTC instant


def test_recalled_proposal_names_local_time():
    proposal = propose(ProposeTrackingArgs(action="stop", minutes_ago=10), sgt(25, 12, 50), CONTEXT)["proposal"]
    assert assistant_tracking.context_line(proposal).endswith("Stop tracking at Friday 2026-09-25 12:40 in Asia/Singapore]")


@pytest.mark.parametrize("args", [{"action": "track", "task_type_paths": ["Yoga"]},  # not an existing path
                                  {"action": "track"},                                 # no path at all
                                  {"action": "stop", "minutes_ago": 5, "hour": 3}])     # two time forms
def test_invalid_proposal_reaches_only_the_model(args):
    tracking = {"sent_at": sgt(25, 12, 50), "context": CONTEXT}
    model = proposal_turn(args, "Which Task Type should I use?")
    kinds = [k for k, _ in asyncio.run(events_for(model, tracking))]
    assert "tracking_proposal" not in kinds and "text_delta" in kinds


def test_text_after_a_proposal_tolerates_a_missing_or_glued_selector():
    tracking = {"sent_at": sgt(25, 12, 50), "context": CONTEXT}
    for answer in ["Confirm below.", '{"presentation":"none"}Confirm below.', '{"presentation": "none"}\nConfirm below.']:
        model = scripted(
            AIMessage(content="", tool_calls=[{"id": "c", "name": "propose_tracking", "args": {"action": "stop"}}],
                      response_metadata={"finish_reason": "tool_calls"}),
            AIMessage(content=answer, response_metadata={"finish_reason": "stop"}))
        events = asyncio.run(events_for(model, tracking))
        assert "".join(d["text"] for k, d in events if k == "text_delta") == "Confirm below."


def test_tool_is_unavailable_without_the_capability():
    model = proposal_turn({"action": "stop"}, "x")
    with pytest.raises(RuntimeError, match="unsupported tool"):
        asyncio.run(events_for(model, None))


@pytest.mark.parametrize("capable", [True, False])
def test_route_negotiates_stores_and_recalls_proposal(client, monkeypatch, capable):
    seen = {}

    async def fake(messages, snapshots, tracking=None):
        seen["tracking"] = tracking
        seen["messages"] = messages
        if tracking:
            result = propose(ProposeTrackingArgs(action="track", task_type_paths=["Meals"]), tracking["sent_at"], tracking["context"])
            yield "tracking_proposal", result["proposal"]
        yield "text_delta", {"text": "Confirm below."}

    monkeypatch.setattr(assistant, "agent_events", fake)
    monkeypatch.setattr(assistant, "tracking_context", lambda: CONTEXT)
    monkeypatch.setattr(assistant, "reporting_timezone", lambda: SGT)
    body = {"capabilities": ["plan_card_v1", "tracking_proposal_v1"]} if capable else {}
    created = client.post("/assistant/conversations", json=body).json()
    assert ("tracking_proposal_v1" in created["capabilities"]) == capable
    key, run = created["conversation_id"], str(uuid4())
    events = decode(client.post(f"/assistant/conversations/{key}/messages", json={"message": "eating", "run_id": run}))
    kinds = [k for k, _ in events]
    # Every conversation knows the current time in the Reporting Time Zone, not only proposal-capable ones.
    assert re.fullmatch(r"Now: \w+day \d{4}-\d\d-\d\d \d\d:\d\d in Asia/Singapore\.", seen["messages"][0].content)
    if not capable:
        assert seen["tracking"] is None and "tracking_proposal" not in kinds
        return
    assert kinds == ["started", "tracking_proposal", "text_delta", "completed"]
    assert "Task Type Paths" in seen["messages"][1].content and "Meals" in seen["messages"][1].content
    client.post(f"/assistant/conversations/{key}/runs/{run}/ack")
    conversations.items.clear()
    remembered = conversations.get(key).messages[-1].content
    assert "Displayed Tracking Proposal, not necessarily confirmed: Track Meals" in remembered


def test_real_context_excludes_merged_and_unspecified(client):
    client.post("/task-types", json={"name": "reading"})
    client.post("/task-types", json={"name": "unspecified"})
    paths = [t["path"] for t in assistant_tracking.tracking_context()["task_types"]]
    assert "reading" in paths and "unspecified" not in paths
