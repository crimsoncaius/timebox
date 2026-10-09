"""Serial, isolated evaluation through the production agent and HTTP lifecycle."""
import json
import re
import time
from contextlib import ExitStack
from tempfile import TemporaryDirectory
from threading import Lock
from unittest.mock import patch
from uuid import uuid4

from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.routes import assistant
from app.core.config import get_settings
from app.db.base import Base
from app.db.session import _session_factory, get_engine
from app.main import app
from app.models.activity import ActivityState
from app.models.assistant import AssistantAttempt, AssistantTaskProposal
from app.models.battle_plan import Task
from app.models.time_block import TimeBlock
from app.services import assistant_agent, assistant_storage
from app.services.assistant_sessions import conversations
from scripts.presentation_fixtures import NOW, ZONE, frozen_clock, move_afternoon_plan, seed
from scripts.presentation_replay import FixtureModel
from scripts.presentation_scoring import comparable_operations, factual_checks, matches_card

_RUN_LOCK = Lock()


def run_native(question, presentation_prompt, model, *, setup=None, event_sink=None):
    """Exercise real HTTP lifecycle with an explicitly supplied model.

    Process-wide DB/clock patching requires serial runs (MIPRO num_threads=1).
    Live callers construct the model and install budget accounting before entry.
    Only synthetic database state is used.
    """
    if not _RUN_LOCK.acquire(blocking=False):
        raise RuntimeError("Native pilot runs must be serial")
    try:
        return _run(question, presentation_prompt, model, setup or {}, event_sink)
    finally:
        _RUN_LOCK.release()


def _run(question, presentation_prompt, model, setup, event_sink):
    calls, errors, streams = [], [], {}
    historical = {}
    first_visible = None
    started = time.monotonic()
    original_call = assistant_agent.call_model
    active_model, active_prompt = model, presentation_prompt

    async def record(target, messages):
        item = {"messages": [m.model_dump(mode="json") for m in messages]}
        calls.append(item)
        result = await original_call(target, messages)
        item["output"] = result.model_dump(mode="json")
        return result

    async def events(messages, snapshots=None, tracking=None):
        nonlocal first_visible
        historical.clear()
        historical.update(snapshots or {})
        try:
            def capture(event):
                if event_sink and event["event"] in {"on_chat_model_stream", "on_tool_end", "on_tool_error"}:
                    event_sink({"event": event["event"], "name": event.get("name"), "data": event["data"]})
                if event["event"] == "on_chat_model_stream":
                    streams.setdefault(event["run_id"], []).append(event["data"]["chunk"].text)
            async for event in assistant_agent.agent_events(messages, snapshots, tracking, model=active_model,
                                                            presentation_prompt=active_prompt, event_sink=capture):
                if first_visible is None and event[0] in {"text_delta", "plan_card", "task_card", "task_proposal", "tracking_proposal"}:
                    first_visible = time.monotonic() - started
                yield event
        except Exception as error:
            errors.append(f"{type(error).__name__}: {error}")
            raise

    with TemporaryDirectory(prefix="timebox-presentation-") as directory, ExitStack() as stack:
        settings = get_settings()
        for name, value in {"database_url": "sqlite:///" + directory.replace("\\", "/") + "/fixture.sqlite",
                            # Lifespan tracing stays off; opt-in experiment callers
                            # initialize their provider once before entering fixtures.
                            "app_timezone": ZONE, "assistant_trace_endpoint": None,
                            "openrouter_api_key": "controlled-output-only", "api_key": None}.items():
            stack.enter_context(patch.object(settings, name, value))
        stack.enter_context(patch.object(conversations, "items", {}))
        stack.enter_context(patch.object(assistant_agent, "call_model", record))
        stack.enter_context(patch.object(assistant, "agent_events", events))
        get_engine.cache_clear()
        _session_factory.cache_clear()
        engine = get_engine()
        try:
            Base.metadata.create_all(engine)
            seed(engine)
            stack.enter_context(frozen_clock(setup.get("clock", NOW)))
            # Avoid lifespan maintenance jobs; the schema and account are explicitly initialized.
            client = TestClient(app)
            stack.callback(client.close)
            response = client.post("/assistant/conversations", json={"capabilities": [
                "plan_card_v1", "activity_cards_v1", "tracking_proposal_v1"]})
            response.raise_for_status()
            key, run_id = response.json()["conversation_id"], str(uuid4())
            for turn in setup.get("history", []):
                active_model = FixtureModel(steps=turn["steps"])
                active_prompt = assistant_agent.PRESENTATION_PROMPT
                history_id = str(uuid4())
                response = client.post(f"/assistant/conversations/{key}/messages",
                                       json={"message": turn["question"], "run_id": history_id})
                response.raise_for_status()
                if "event: completed" not in response.text:
                    raise RuntimeError("History fixture did not complete: " + str(errors))
                if turn.get("acknowledge", True):
                    client.post(f"/assistant/conversations/{key}/runs/{history_id}/ack").raise_for_status()
            active_model, active_prompt = model, presentation_prompt
            calls.clear()
            errors.clear()
            streams.clear()
            first_visible = None
            if setup.get("move_plan"):
                move_afternoon_plan(engine)
            if "read_activity" in setup.get("unavailable", []):
                stack.enter_context(patch.object(assistant_agent, "read_activity", return_value={
                    "error": "Activity data is unavailable. No current activity could be verified."}))
            if "read_tasks" in setup.get("unavailable", []):
                async def unavailable(*args, **kwargs):
                    return {"error": "Saved tasks are unavailable. No current task data could be verified.",
                            "completeness": "partial"}
                stack.enter_context(patch.object(assistant_agent, "task_read", unavailable))
            with Session(engine) as db:
                before = domain_state(db)
            started = time.monotonic()
            response = client.post(f"/assistant/conversations/{key}/messages",
                                   json={"message": question, "run_id": run_id})
            response.raise_for_status()
            duration = time.monotonic() - started
            frames = []
            for frame in response.text.split("\n\n"):
                lines = frame.splitlines()
                if len(lines) == 2 and lines[0].startswith("event:"):
                    frames.append((lines[0][7:], json.loads(lines[1][6:])))
            if frames and frames[-1][0] == "completed":
                client.post(f"/assistant/conversations/{key}/runs/{run_id}/ack").raise_for_status()
            with Session(engine) as db:
                row = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == run_id))
                after = domain_state(db)
                proposals = [{"status": p.status, "completed": p.source_completed_at is not None,
                              "receipt": p.receipt, "review": p.review}
                             for p in db.scalars(select(AssistantTaskProposal).where(AssistantTaskProposal.run_id == run_id))]
                result = {"status": row.status, "acknowledged": row.acknowledged, "answer": row.answer,
                          "snapshots": {**historical, **row.snapshots}, "fresh_snapshots": row.snapshots,
                          "historical_snapshot_ids": list(historical),
                          "read_errors": (row.context_inputs or {}).get("read_errors", []),
                          "cards": assistant_storage.displayed_cards(row.displayed_plan),
                          "proposals": proposals,
                          "tracking_proposal": row.tracking_proposal,
                          "tasks_unchanged": before["tasks"] == after["tasks"],
                          "domain_unchanged": before == after}
            raw_answer = "".join(next(reversed(streams.values()))) if streams else ""
            result.update(raw_answer=raw_answer, calls=calls, errors=errors,
                          events=frames, duration_seconds=duration, first_visible_seconds=first_visible)
            # Successful bootstrap examples carry actual read evidence, never grades or fixture IDs.
            evidence = [m["content"] for m in calls[-1]["messages"] if m["type"] == "tool"] if calls else []
            result["demo_context"] = question + "\nTool results:\n" + "\n".join(evidence)
            return result
        finally:
            engine.dispose()
            get_engine.cache_clear()
            _session_factory.cache_clear()


def domain_state(db):
    return {model.__tablename__: list(db.execute(select(model.__table__).order_by(model.id)).mappings())
            for model in (Task, TimeBlock, ActivityState)}


def grade(expectation, attempt):
    """Binary success with separate failure/unresolved diagnostics; no judge calls."""
    failures, unresolved = [], []
    if attempt["status"] != "completed" or not attempt["acknowledged"]:
        failures.append("incomplete interaction")
    if not attempt["tasks_unchanged"]:
        failures.append("task mutation without confirmation")
    if not attempt.get("domain_unchanged", True):
        failures.append("domain mutation without confirmation")
    answer = attempt["answer"]
    if re.search(r'"(?:presentation|snapshot_id|snapshot_ids)"\s*:', answer):
        failures.append("control syntax leaked into answer")
    cards = attempt["cards"] or []
    if expectation["cards"] == "required" and not cards:
        failures.append("required card absent")
    if expectation["cards"] == "unwanted" and cards:
        failures.append("unwanted card")
    snapshots = attempt["snapshots"]
    for card in cards:
        if card["snapshot_id"] not in snapshots or snapshots[card["snapshot_id"]] != card:
            failures.append("ungrounded card")
        elif expectation.get("card_schema") and card.get("schema_version") != expectation["card_schema"]:
            failures.append("irrelevant card")
        for field, value in expectation.get("card_fields", {}).items():
            if card.get(field) != value:
                failures.append("incorrect card " + field)
        if expectation.get("allowed_cards") and not any(matches_card(card, spec) for spec in expectation["allowed_cards"]):
            failures.append("irrelevant card selection")
        if expectation.get("historical_cards") and card["snapshot_id"] not in attempt.get("historical_snapshot_ids", []):
            failures.append("historical card replaced with a fresh read")
    for spec in expectation.get("required_cards", []):
        if not any(matches_card(card, spec) for card in cards):
            failures.append("required card coverage absent")
    proposals = attempt["proposals"]
    if len(proposals) != int(expectation.get("task_proposal", False)):
        failures.append("incorrect proposal presence")
    for proposal in proposals:
        if not proposal["completed"] or proposal["receipt"] is not None or proposal["status"] != "pending":
            failures.append("invalid proposal lifecycle")
        if expectation.get("operations") and comparable_operations(proposal["review"]["operations"]) != comparable_operations(expectation["operations"]):
            failures.append("incorrect proposed operations")
    tracking = attempt.get("tracking_proposal")
    expected_tracking = expectation.get("tracking")
    if bool(tracking) != bool(expected_tracking):
        failures.append("incorrect tracking proposal presence")
    if tracking and expected_tracking:
        actual = {"action": tracking["action"], "paths": [t["path"] for t in tracking["task_types"]], "at": tracking["at"]}
        if actual != expected_tracking:
            failures.append("incorrect tracking proposal")
    called = {call["name"] for entry in attempt.get("calls", []) for call in entry.get("output", {}).get("tool_calls", [])}
    for name in expectation.get("required_reads", []):
        if name not in called:
            failures.append("required fresh read absent: " + name)
    if expectation.get("no_reads") and any(name.startswith("read_") for name in called):
        failures.append("unrequested read")
    if expectation.get("read_error") and not attempt.get("read_errors"):
        failures.append("fixture did not exercise unavailable read")
    for pattern in expectation.get("forbidden", []):
        if re.search(pattern, answer, re.I):
            failures.append("contradictory fact or premature success claim")
    for pattern in expectation.get("facts", []):
        if not re.search(pattern, answer.strip(), re.I):
            unresolved.append("fact not established: " + pattern)
    fact_failures, fact_unresolved = factual_checks(expectation, answer, cards)
    failures.extend(fact_failures)
    unresolved.extend(fact_unresolved)
    return {"status": "fail" if failures else "unresolved" if unresolved else "pass",
            "failures": failures, "unresolved": unresolved}
