from uuid import uuid4

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.routes import assistant
from app.db.session import get_engine
from app.models.assistant import AssistantAttempt, AssistantTaskProposal
from app.models.battle_plan import Task
from app.services import assistant_storage
from app.services import assistant_task_operations as ops
from app.services.assistant_agent import build_agent, translate_events
from app.services.assistant_task_context import refresh
from tests.test_assistant import decode, isolated_assistant  # noqa: F401
from tests.test_assistant_loop import answer, call
from tests.test_assistant_task_operations import confirm, draft, patch, task
from tests.test_assistant_tracking import scripted


@pytest.mark.parametrize("failure", [None, "incomplete", "save", "mixed", "unknown"])
def test_task_proposal_stream_eligibility_and_truthful_captured_outcomes(client, monkeypatch, failure):
    model = scripted(call("propose_task_changes", {"operations": [{"op": "create_task", "ref": "p", "title": "From a model proposal"}]}), answer())
    async def fake(messages, snapshots, **kwargs):
        async for kind, data in translate_events(build_agent(model).astream_events({"messages": messages}, version="v2"), snapshots):
            yield kind, data
            if kind == "task_proposal":
                if failure == "incomplete":
                    raise RuntimeError("incomplete response")
                if failure == "mixed":
                    yield "tracking_proposal", {}
                if failure == "unknown":
                    yield "unknown_required_event", {}
    monkeypatch.setattr(assistant, "agent_events", fake)
    if failure == "save":
        original = assistant_storage.capture
        def failed_save(run, text, reads, cards, status="running", *args, **kwargs):
            if status == "completed":
                raise assistant_storage.CaptureError("Save failed")
            return original(run, text, reads, cards, status, *args, **kwargs)
        monkeypatch.setattr(assistant_storage, "capture", failed_save)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    response = client.post(f"/assistant/conversations/{key}/messages", json={"message": "Create a Task", "run_id": str(uuid4())})
    events = decode(response)
    proposal = next(data for kind, data in events if kind == "task_proposal")
    with Session(get_engine()) as db:
        row = db.get(AssistantTaskProposal, proposal["proposal_id"])
        assert row.status == ("pending" if failure is None else "invalid")
        assert (row.source_completed_at is not None) == (failure is None)
        assert list(db.scalars(select(Task))) == []
    assert events[-1][0] == ("completed" if failure is None else "failed")
    if failure is not None:
        assert confirm(proposal)[0] == 409
        return
    assert confirm(proposal)[0] == 200
    captured = []
    async def next_response(messages, snapshots, **kwargs):
        captured.extend(messages)
        yield "text_delta", {"text": "The receipt confirms the Task was created."}
    monkeypatch.setattr(assistant, "agent_events", next_response)
    run = str(uuid4())
    assert decode(client.post(f"/assistant/conversations/{key}/messages", json={"message": "Was it saved?", "run_id": run}))[-1][0] == "completed"
    assert any("task_outcomes_v1" in str(m.content) and '"status": "applied"' in str(m.content) for m in captured)
    with Session(get_engine()) as db:
        inputs = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == run)).context_inputs
        assert inputs["task_outcomes_v1"]["operations"][0]["status"] == "applied"
    assert ops.outcome_context(str(uuid4()))["operations"] == []


def test_proposal_only_selector_and_shared_tool_bound(client, monkeypatch):
    # An eligible task proposal can be the complete answer, but another proposal
    # or read after it cannot execute, even when the first tool validation fails.
    import asyncio

    from langchain_core.messages import AIMessage

    from app.services.assistant_presentation import PresentationParser
    parser = PresentationParser({}); parser.allow_empty = True
    parser.feed('{"presentation":"none"}')
    assert parser.finish(successful_terminal=True) == []
    for next_call in (call("read_tasks", {"mode": "search"}), call("propose_tracking", {"action": "stop"})):
        model = scripted(call("propose_task_changes", {"operations": []}), next_call)
        async def run(model=model):
            return [event async for event in build_agent(model).astream_events({"messages": [AIMessage("Request")]}, version="v2")]
        with pytest.raises(RuntimeError, match="did not finish"):
            asyncio.run(run())


def test_description_proposal_remains_available_after_three_real_task_reads(client, monkeypatch):
    tid = task(description="Private original")
    replacement = "Revised description: ready for review.\nKeep this line, too."
    model = scripted(
        call("read_tasks", {"mode": "search", "query": "Report"}),
        call("read_tasks", {"mode": "get", "task_ids": [tid], "include_description": True}),
        call("read_tasks", {"mode": "get", "task_ids": [tid]}),
        call("propose_task_changes", {"operations": [patch(tid, description=replacement)]}),
        answer(),
    )

    async def fake(messages, snapshots, **kwargs):
        async for event in translate_events(build_agent(model).astream_events({"messages": messages}, version="v2"), snapshots):
            yield event

    monkeypatch.setattr(assistant, "agent_events", fake)
    key = client.post("/assistant/conversations").json()["conversation_id"]
    response = client.post(f"/assistant/conversations/{key}/messages", json={"message": "Read and replace the description", "run_id": str(uuid4())})
    events = decode(response)
    assert events[-1][0] == "completed"
    proposals = [data for kind, data in events if kind == "task_proposal"]
    assert len(proposals) == 1
    assert "Private original" not in str(proposals[0])
    review = client.get(f"/assistant/task-proposals/{proposals[0]['proposal_id']}").json()
    assert review["review_targets"][0]["after"]["description"] == replacement
    assert confirm(proposals[0])[0] == 200
    with Session(get_engine()) as db:
        assert db.get(Task, tid).description == replacement


def test_pending_target_priority_includes_known_child_outside_first_page():
    tid = task()
    with Session(get_engine()) as db:
        children = [Task(parent_id=tid, title=str(i), position=i) for i in range(25)]
        db.add_all(children); db.commit(); cid = children[-1].id
    p = draft([{"op": "set_subtask_checked", "target": {"id": cid}, "parent": {"id": tid}, "checked": True}])
    evidence = refresh({}, priority_ids=ops.pending_targets(p["conversation_id"]), priority_children=ops.pending_children(p["conversation_id"]))
    assert evidence["requested_ids"][0] == tid
    assert evidence["children"][str(cid)]["availability"] == "available"


def test_http_confirmation_only_accepts_server_identity_and_status_association(client):
    p = draft([patch(task(), title="New")])
    url = f"/assistant/task-proposals/{p['proposal_id']}/confirm"
    body = {"revision": 1, "operation_id": p["operation_id"], "submission_id": str(uuid4())}
    assert client.post(url, json={**body, "operations": []}).status_code == 422
    assert client.post(url, json={**body, "revision": 2}).status_code == 422
    assert client.post(url, json={**body, "operation_id": str(uuid4())}).status_code == 409
    result = client.post(url, json=body)
    assert result.status_code == 200, result.text
    other = draft([patch(task(), title="Other")])
    assert client.post("/assistant/task-operations/status", json={"operations": [{"operation_id": other["operation_id"], "submission_id": body["submission_id"]}]}).status_code == 409
    assert client.post(url, json=body).json()["receipt"] == result.json()["receipt"]
