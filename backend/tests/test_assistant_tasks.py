import datetime as dt
import json
from uuid import uuid4

import pytest
from pydantic import ValidationError
from sqlalchemy import event, select
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.assistant import AssistantAttempt, AssistantConversation, AssistantQuery
from app.models.battle_plan import Project, RecurrenceOccurrence, RecurringTemplate, Task, TaskStatus
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import TimeBlock
from app.services import assistant_storage
from app.services.assistant_task_context import refresh
from app.services.assistant_tasks import ReadTaskChoicesArgs, ReadTasksArgs, encoded, read_tasks, task_card

NOW = dt.datetime(2026, 10, 4, 8, tzinfo=dt.UTC)


@pytest.fixture
def conversation():
    key = str(uuid4())
    with Session(get_engine()) as db:
        db.add(AssistantConversation(id=key, capabilities=[])); db.commit()
    return key


def read(key, **args):
    return read_tasks(ReadTasksArgs(**args), key, now=NOW)


def test_model_tool_adapter_preserves_omitted_mode_fields(conversation):
    import asyncio

    from app.services.assistant_agent import read_tasks_tool, task_read_context

    async def invoke():
        token = task_read_context.set({"conversation_id": conversation, "bytes": 0})
        try:
            search = await read_tasks_tool.ainvoke({"mode": "search"})
            invalid = await read_tasks_tool.ainvoke({"mode": "search", "task_ids": None})
            return search, invalid
        finally:
            task_read_context.reset(token)

    result, invalid = asyncio.run(invoke())
    assert "error" not in result, result
    assert result["kind"] == "tasks"
    assert "Fields do not apply" in invalid["error"]


def test_default_privacy_purity_literal_title_and_child_identity(conversation):
    with Session(get_engine()) as db:
        project = Project(name="One"); kind = TaskType(name="work"); db.add_all([project, kind]); db.flush()
        task = Task(title="Report", description="SECRET", is_blocked=True, project_id=project.id, task_type_id=kind.id)
        other = Task(title="Report"); db.add_all([task, other]); db.flush()
        db.add_all([Task(parent_id=task.id, title="100%_done", checked=True),
                    Task(title="Finished", status=TaskStatus.completed), Task(title="Trash SECRET", deleted_at=NOW),
                    Task(title="Archived SECRET", archived_at=NOW)])
        db.commit(); task_id = task.id
    sql = []
    def record(conn, cursor, statement, parameters, context, executemany): sql.append(statement)
    event.listen(get_engine(), "before_cursor_execute", record)
    try:
        result = read(conversation, mode="search")
    finally:
        event.remove(get_engine(), "before_cursor_execute", record)
    assert [r["title"] for r in result["rows"]] == ["Report", "Report"]
    assert result["rows"][0]["blocked"] is True
    assert "SECRET" not in json.dumps(result)
    assert not any(s.lstrip().upper().startswith(("INSERT", "UPDATE", "DELETE")) and "assistant_quer" not in s for s in sql)
    assert not any("tasks.description" in s for s in sql)
    match = read(conversation, mode="search", query="%_")
    assert [r["id"] for r in match["rows"]] == [task_id]
    assert len(match["rows"][0]["matched_subtask_ids"]) == 1
    private = read(conversation, mode="get", task_ids=[task_id], include_description=True)
    assert private["rows"][0]["description"] == {"text": "SECRET", "truncated": False}
    assert "SECRET" not in json.dumps(task_card(private))


def test_pagination_manifest_is_fixed_and_conversation_bound(conversation):
    with Session(get_engine()) as db:
        db.add_all([Task(title=f"Task {i}", position=i) for i in range(23)]); db.commit()
    page = read(conversation, mode="search")
    assert page["matching_count"] == 23 and len(page["rows"]) == 20
    with Session(get_engine()) as db:
        row = db.get(Task, 21); row.archived_at = NOW
        db.add(Task(title="New first", position=-1)); db.commit()
    second = read(conversation, mode="search", cursor=page["next_cursor"])
    assert second["rows"][0] == {"id": 21, "kind": "ordinary", "availability": "unavailable"}
    assert [r["id"] for r in second["rows"]] == [21, 22, 23]
    assert page["snapshot_id"] != second["snapshot_id"]
    assert second["matching_count"] == 23
    with pytest.raises(ValueError, match="different query"):
        read(conversation, mode="search", query="changed", cursor=page["next_cursor"])
    with pytest.raises(ValueError, match="Unknown cursor"):
        read(str(uuid4()), mode="search", cursor=page["next_cursor"])
    with pytest.raises(ValueError, match="expired"):
        read_tasks(ReadTasksArgs(mode="search", cursor=page["next_cursor"]), conversation, now=NOW + dt.timedelta(minutes=15))
    with Session(get_engine()) as db:
        assert db.scalar(select(AssistantQuery)) is not None


def test_manifest_lower_bound_large_unicode_results_and_description_excerpts(conversation):
    with Session(get_engine()) as db:
        db.add_all([Task(title="界" * 500, description="界" * 2400, position=i) for i in range(1002)]); db.commit()
    result = read(conversation, mode="search", search_in="descriptions", query="界")
    assert result["count_relation"] == "at_least" and result["matching_count"] == 1001
    assert result["completeness"] == "partial" and "payload_limit" not in result["limitations"]
    assert len(encoded(result)) > 65536
    available = [r for r in result["rows"] if r["availability"] == "available"]
    assert available and all(len(r["description"]["text"]) == 2000 and r["description"]["truncated"] for r in available)
    assert len(available) == 20


def test_saved_recurrence_uses_day_carry_oldest_and_never_generates(conversation):
    with Session(get_engine()) as db:
        kind = TaskType(name="work"); db.add(kind); db.flush()
        scheduled = RecurringTemplate(title="Routine", mode="scheduled", frequency="daily", start_date=NOW.date(), generation_start_date=NOW.date())
        quota = RecurringTemplate(title="Quota", mode="quota", frequency="weekly", quota_count=2, start_date=NOW.date(), generation_start_date=NOW.date())
        db.add_all([scheduled, quota]); db.flush()
        carry = Task(title="Saved carry", recurrence_kind="scheduled", recurring_template_id=scheduled.id)
        current = Task(title="Current occurrence", recurrence_kind="scheduled", recurring_template_id=scheduled.id)
        tracker = Task(title="Past quota", recurrence_kind="quota_parent", recurring_template_id=quota.id,
                       quota_period_start=NOW.date()-dt.timedelta(days=7), quota_period_end=NOW.date()-dt.timedelta(days=1), expected_sessions=2)
        db.add_all([carry, current, tracker]); db.flush()
        db.add_all([RecurrenceOccurrence(template_id=scheduled.id, task_id=carry.id, occurrence_key="old", cycle_start=NOW.date()-dt.timedelta(days=1), cycle_end=NOW.date()-dt.timedelta(days=1)),
                    RecurrenceOccurrence(template_id=scheduled.id, task_id=current.id, occurrence_key="current", cycle_start=NOW.date(), cycle_end=NOW.date()),
                    Task(title="Past session", parent_id=tracker.id, recurrence_kind="quota_session", recurring_template_id=quota.id)])
        day = Day(date=NOW.date()); db.add(day); db.flush()
        db.add(TimeBlock(task_id=carry.id, day_id=day.id, task_type_id=kind.id, lane="planned", start_minute=0, end_minute=30))
        db.commit(); carry_id = carry.id
    result = read(conversation, mode="search")
    assert [r["id"] for r in result["rows"]] == [carry_id]
    assert "unverified_readiness" in result["limitations"]
    history = read(conversation, mode="search", scope="history", kinds=["quota_tracker", "session"], filters={"completion": "any"})
    assert {r["kind"] for r in history["rows"]} == {"quota_tracker", "session"}
    assert all(r["relevance"] == "history" for r in history["rows"])
    future = read(conversation, mode="search", scope="saved_future")
    assert future["rows"] == [] and "saved_only" in future["limitations"]


def test_refresh_related_state_children_clock_and_retained_replay(conversation):
    with Session(get_engine()) as db:
        project = Project(name="Old"); kind = TaskType(name="old/type"); db.add_all([project, kind]); db.flush()
        task = Task(title="Root", description="PRIVATE", project_id=project.id, task_type_id=kind.id)
        db.add(task); db.flush()
        child = Task(title="Child", parent_id=task.id); db.add(child)
        day = Day(date=NOW.date()); db.add(day); db.flush()
        db.add(TimeBlock(task_id=task.id, day_id=day.id, task_type_id=kind.id, lane="planned", start_minute=60, end_minute=90)); db.commit()
        tid, cid, pid, type_id, did, version = task.id, child.id, project.id, kind.id, day.id, task.version
    original = read(conversation, mode="get", task_ids=[tid], include_description=True)
    snapshots = {original["snapshot_id"]: original}
    with Session(get_engine()) as db:
        db.get(Project, pid).name = "New"; db.get(TaskType, type_id).name = "new/type"
        db.get(Task, cid).title = "Renamed child"; db.get(Day, did).date += dt.timedelta(days=1); db.commit()
        assert db.get(Task, tid).version == version
    refreshed = refresh(snapshots, now=NOW)
    assert refreshed["refreshed_ids"] == [tid]
    assert {"project", "task_type", "subtasks", "planned_dates"} <= set(refreshed["changed_fields"][str(tid)])
    assert "PRIVATE" not in json.dumps(refreshed)
    assert original["rows"][0]["project"]["name"] == "Old"
    assert refreshed["children"][str(cid)]["title"] == "Renamed child"
    attempt = AssistantAttempt(run_id=str(uuid4()), conversation_id=conversation, question="Which?", answer="This one", snapshots=snapshots, displayed_plan=original)
    replay = assistant_storage.replay(attempt, snapshots)
    assert replay[1].tool_calls[0]["name"] == "read_tasks"
    assert replay[1].tool_calls[0]["args"]["task_ids"] == [tid]
    assert len(assistant_storage.replay(attempt, {})) == 2


def test_refresh_bounds_and_failure_are_explicit(conversation, monkeypatch):
    with Session(get_engine()) as db:
        db.add_all([Task(title=f"Task {i}") for i in range(50)]); db.commit()
    pages = [read(conversation, mode="search")]
    pages.append(read(conversation, mode="search", cursor=pages[-1]["next_cursor"]))
    pages.append(read(conversation, mode="search", cursor=pages[-1]["next_cursor"]))
    snapshots = {p["snapshot_id"]: p for p in pages}
    result = refresh(snapshots, now=NOW)
    assert len(result["refreshed_ids"]) == 40
    assert set(result["refreshed_ids"]) | set(result["unverified_ids"]) == set(range(1, 51))
    assert len(encoded(result)) > 24576
    import app.services.assistant_task_context as context
    monkeypatch.setattr(context, "project_ids", lambda *a: (_ for _ in ()).throw(RuntimeError("secret")))
    result = refresh(snapshots, now=NOW)
    assert result["status"] == "unverified" and len(result["unverified_ids"]) == 50
    assert "secret" not in json.dumps(result)


def test_choices_typed_replay_no_cards_and_invalid_combinations(conversation):
    from app.services.assistant_presentation import PresentationParser
    with Session(get_engine()) as db:
        db.add_all([Project(name="z"), Project(name="A")]); db.commit()
    choices = read_tasks(ReadTaskChoicesArgs(kind="projects"), conversation, choices=True, now=NOW)
    assert [r["name"] for r in choices["rows"]] == ["A", "z"]
    attempt = AssistantAttempt(run_id=str(uuid4()), question="Projects?", snapshots={choices["snapshot_id"]: choices}, answer="A", tracking_proposal=None)
    assert assistant_storage.replay(attempt)[1].tool_calls[0]["name"] == "read_task_choices"
    with pytest.raises(ValueError, match="Choices"):
        PresentationParser({choices["snapshot_id"]: choices}).cards([choices["snapshot_id"]])
    for args in [{"mode": "search", "task_ids": [1]}, {"mode": "get", "task_ids": [True]},
                 {"mode": "children", "parent_id": 1}, {"mode": "search", "scope": "current", "filters": {"completion": "any"}},
                 {"mode": "get", "task_ids": [1], "unknown": True}]:
        with pytest.raises(ValidationError): ReadTasksArgs.model_validate(args)


def test_nested_pages_keep_removed_child_positions_and_sessions_are_typed(conversation):
    with Session(get_engine()) as db:
        parent = Task(title="Parent"); quota = Task(title="Quota", recurrence_kind="quota_parent", expected_sessions=1)
        db.add_all([parent, quota]); db.flush()
        db.add_all([Task(title=f"Child {i}", parent_id=parent.id, position=i) for i in range(23)])
        db.add(Task(title="Session", parent_id=quota.id, recurrence_kind="quota_session")); db.commit()
        parent_id, quota_id = parent.id, quota.id
    first = read(conversation, mode="get", task_ids=[parent_id])
    row = first["rows"][0]
    assert len(row["subtasks"]) == 20 and row["subtasks_count"] == 23
    with Session(get_engine()) as db:
        child = db.scalar(select(Task).where(Task.title == "Child 20")); cid = child.id; child.deleted_at = NOW; db.commit()
    following = read(conversation, mode="get", task_ids=[parent_id], cursor=row["subtasks_next_cursor"])
    assert following["rows"][0]["subtasks"][0] == {"id": cid, "availability": "unavailable"}
    quota = read(conversation, mode="get", task_ids=[quota_id])["rows"][0]
    assert quota["sessions"][0]["kind"] == "session" and "subtasks" not in quota
    assert quota["quota"] == {"required": 1, "completed": 0, "saved_session_count": 1}
    with pytest.raises(ValueError, match="different collections"):
        read(conversation, mode="children", parent_id=quota_id, kind="subtasks")


def test_clock_transition_refresh_does_not_persist_skip(conversation):
    with Session(get_engine()) as db:
        template = RecurringTemplate(title="Daily", mode="scheduled", frequency="daily", start_date=NOW.date(), generation_start_date=NOW.date())
        db.add(template); db.flush()
        task = Task(title="Today", recurrence_kind="scheduled", recurring_template_id=template.id); db.add(task); db.flush()
        db.add(RecurrenceOccurrence(template_id=template.id, task_id=task.id, occurrence_key="today", cycle_start=NOW.date(), cycle_end=NOW.date()))
        db.commit(); tid = task.id; version = task.version
    original = read(conversation, mode="get", task_ids=[tid])
    later = refresh({original["snapshot_id"]: original}, now=NOW + dt.timedelta(days=1))
    assert later["projections"][0]["relevance"] == "history"
    assert later["projections"][0]["lifecycle"] == "skipped"
    with Session(get_engine()) as db:
        assert db.get(Task, tid).version == version
        assert not db.scalar(select(RecurrenceOccurrence.skipped))


def test_task_sse_is_safe_and_exact_preflight_input_is_captured(client, monkeypatch):
    from app.api.routes import assistant
    from app.core.config import get_settings
    from app.services.assistant_sessions import conversations
    from tests.test_assistant import decode, send
    conversations.items.clear()
    monkeypatch.setattr(get_settings(), "openrouter_api_key", "not-real")
    with Session(get_engine()) as db:
        task = Task(title="Original", description="PRIVATE"); db.add(task); db.commit(); tid = task.id
    key = client.post("/assistant/conversations").json()["conversation_id"]
    original = read(key, mode="get", task_ids=[tid], include_description=True)
    inputs = []
    async def fake(messages, snapshots=None):
        inputs.append(messages)
        if not snapshots:
            yield "snapshot_read", original
            yield "task_card", original
        else:
            yield "text_delta", {"text": "Current title"}
    monkeypatch.setattr(assistant, "agent_events", fake)
    events = decode(send(client, key))
    assert events[-1][0] == "completed"
    wire = next(data for kind, data in events if kind == "task_card")
    assert "PRIVATE" not in json.dumps(wire) and "source" not in wire
    run_id = events[0][1]["run_id"]
    client.post(f"/assistant/conversations/{key}/runs/{run_id}/ack")
    with Session(get_engine()) as db:
        db.get(Task, tid).title = "New title"; db.commit()
    assert decode(send(client, key))[-1][0] == "completed"
    with Session(get_engine()) as db:
        attempts = list(db.scalars(select(AssistantAttempt).order_by(AssistantAttempt.id)))
        evidence = attempts[-1].context_inputs
        assert evidence["messages"] == [m.model_dump(mode="json") for m in inputs[-1]]
        assert evidence["task_refresh_v1"]["projections"][0]["title"] == "New title"
        assert attempts[0].snapshots[original["snapshot_id"]]["rows"][0]["title"] == "Original"
    conversations.items.clear()


def test_refresh_nested_identities_share_the_forty_identity_bound(conversation):
    with Session(get_engine()) as db:
        parents = [Task(title="First"), Task(title="Second")]; db.add_all(parents); db.flush()
        ids = [p.id for p in parents]
        db.add_all([Task(title=f"Child {i}", parent_id=p.id) for p in parents for i in range(20)]); db.commit()
    original = read(conversation, mode="get", task_ids=ids)
    current = refresh({original["snapshot_id"]: original}, now=NOW)
    assert current["refreshed_ids"] == [ids[0]]
    assert current["unverified_ids"] == [ids[1]]
    assert sum(c["availability"] == "available" for c in current["children"].values()) == 20
