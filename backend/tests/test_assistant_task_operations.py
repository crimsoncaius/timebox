import datetime as dt
import json
from concurrent.futures import ThreadPoolExecutor
from threading import Event
from uuid import uuid4

import pytest
from fastapi import HTTPException
from pydantic import ValidationError
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.time import utc_now
from app.db.activity_admission import admission
from app.db.session import get_engine
from app.models.activity import ActivityOperation, ActivityState
from app.models.assistant import AssistantTaskProposal, AssistantTaskSubmission
from app.models.battle_plan import Project, Task, TaskStatus
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import TimeBlock
from app.services import assistant_storage
from app.services import assistant_task_operations as ops
from app.services.assistant_task_intents import ProposeTaskChangesArgs, canonical_operations
from app.services.assistant_tasks import ReadTasksArgs, read_tasks


def draft(operations, *, completed=True, conversation=None, zone="UTC", now=None):
    key, run = conversation or str(uuid4()), str(uuid4())
    if conversation is None:
        assistant_storage.create(key, [])
    assistant_storage.begin(key, run, "Explicit change", "deterministic")
    proposal = ops.propose(ProposeTaskChangesArgs.model_validate({"operations": operations}), key, run, now or utc_now(), zone)
    if completed:
        assistant_storage.capture(run, "Review these changes.", {}, None, "completed")
    return proposal


def confirm(proposal, submission=None, **kwargs):
    return ops.execute(proposal["proposal_id"], proposal["operation_id"], submission or str(uuid4()), **kwargs)


def task(**kwargs):
    with Session(get_engine()) as db:
        row = Task(title="Report", **kwargs)
        db.add(row); db.commit()
        return row.id


def patch(task_id, **fields):
    return {"op": "patch_task", "target": {"id": task_id}, "set": fields}


def test_dependent_creates_are_atomic_and_duplicate_receipt_survives_close_stop_restart():
    p = draft([{"op": "create_task", "ref": "parent", "title": "Parent"},
               {"op": "add_subtask", "ref": "child", "parent": {"ref": "parent"}, "title": "Child"},
               {"op": "set_subtask_checked", "target": {"ref": "child"}, "parent": {"ref": "parent"}, "checked": True}])
    submission = str(uuid4())
    code, first = confirm(p, submission)
    assert code == 200, first
    receipt = first["receipt"]
    assert set(receipt["created"]) == {"parent", "child"}
    assert "undo" not in receipt
    assistant_storage.stop(p["conversation_id"], p["originating_run_id"])
    assistant_storage.close(p["conversation_id"])
    assistant_storage.recover_interrupted()
    assert confirm(p, submission)[1]["receipt"] == receipt
    assert confirm(p)[1]["receipt"] == receipt
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(Task)) == 2
        assert db.get(Task, receipt["created"]["child"]).checked
        parent = db.get(Task, receipt["created"]["parent"])
        assert parent.status == TaskStatus.open and parent.description == "" and not parent.ready_to_plan
    assert ops.outcome_context(p["conversation_id"])["operations"][0]["status"] == "applied"


def test_fault_after_later_step_rolls_back_domain_and_receipt_and_fences_submission(monkeypatch):
    p = draft([{"op": "create_task", "ref": "p", "title": "Parent"},
               {"op": "add_subtask", "ref": "s", "parent": {"ref": "p"}, "title": "Child"}])
    original = ops.create_task
    count = 0
    def fault(*args, **kwargs):
        nonlocal count
        result = original(*args, **kwargs)
        count += 1
        if count == 2:
            raise RuntimeError("fault after child flush")
        return result
    sid = str(uuid4())
    with monkeypatch.context() as patcher:
        patcher.setattr(ops, "create_task", fault)
        code, result = confirm(p, sid)
    assert code == 503 and result["submission"]["state"] == "rolled_back"
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(Task)) == 0
        assert db.scalar(select(func.count()).select_from(ActivityOperation)) == 0
        assert db.get(AssistantTaskProposal, p["proposal_id"]).receipt is None
    assert confirm(p, sid)[0] == 409
    assert confirm(p)[0] == 200


def test_relevant_guards_preserve_unrelated_fields_and_stale_requires_refresh():
    tid = task(description="private")
    p = draft([patch(tid, ready_to_plan=True)])
    with Session(get_engine()) as db:
        db.get(Task, tid).importance = "high"; db.commit()
    assert confirm(p)[0] == 200
    with Session(get_engine()) as db:
        assert db.get(Task, tid).importance == "high"
    p = draft([patch(tid, title="New")])
    with Session(get_engine()) as db:
        db.get(Task, tid).title = "Changed elsewhere"; db.commit()
    code, result = confirm(p)
    assert code == 409 and result["status"] == "stale"
    refreshed = ops.refresh_proposal(p["proposal_id"])
    assert refreshed["operation_id"] != p["operation_id"]
    assert confirm(refreshed)[0] == 200


def test_description_private_full_review_and_oversize_before_rejected():
    tid = task(description="PRIVATE CURRENT")
    p = draft([patch(tid, description="PRIVATE AFTER")])
    assert "PRIVATE" not in json.dumps(p)
    full = ops.review(p["proposal_id"])
    assert full["review_targets"][0]["before"]["description"] == "PRIVATE CURRENT"
    result = confirm(p)[1]
    assert "PRIVATE" not in json.dumps(result)
    assert "description" in result["receipt"]["changes"][0]["changed_fields"]
    with Session(get_engine()) as db:
        db.get(Task, tid).description = "長" * 30000; db.commit()
    with pytest.raises(ValueError, match="64 KiB"):
        draft([patch(tid, description="small")])


@pytest.mark.parametrize("operation", [
    {"op": "create_task", "ref": "p", "title": " "},
    {"op": "create_task", "ref": "p", "title": "x", "description": "長" * 8000},
    {"op": "patch_task", "target": {"id": 1}, "set": {"ready_to_plan": None}},
    {"op": "patch_task", "target": {"id": 1}, "set": {"blocked": False, "blocking_reason": "why"}},
    {"op": "patch_task", "target": {"id": 1}, "set": {"subtasks": []}},
    {"op": "delete_task", "target": {"id": 1}},
    {"op": "patch_task", "target": {"id": 1}, "set": {"reminder_at": {"kind": "day_offset", "days": 1}}},
])
def test_strict_intent_rejects_unsupported_or_ambiguous_input(operation):
    with pytest.raises(ValidationError):
        ProposeTaskChangesArgs.model_validate({"operations": [operation]})


def test_subtasks_parent_completion_reopen_and_recurring_boundaries():
    tid = task(status=TaskStatus.completed, completed_at=utc_now())
    with Session(get_engine()) as db:
        child = Task(title="Checked", parent_id=tid, checked=True); db.add(child); db.commit(); cid = child.id
    rename = {"op": "rename_subtask", "subtask_id": cid, "parent_id": tid, "title": "Still checked"}
    with pytest.raises(ValueError, match="reopen"):
        draft([rename])
    p = draft([{"op": "reopen_task", "target": {"id": tid}}, rename, patch(tid, ready_to_plan=True)])
    assert confirm(p)[0] == 200
    with Session(get_engine()) as db:
        assert db.get(Task, cid).checked
        assert db.get(Task, cid).title == "Still checked"
    for kind in ("scheduled", "quota_parent", "quota_session"):
        rid = task(recurrence_kind=kind)
        with pytest.raises(ValueError, match="ordinary"):
            draft([patch(rid, title="wrong")])
    with pytest.raises(ValueError, match="earlier create"):
        draft([{"op": "add_subtask", "parent": {"ref": "future"}, "ref": "c", "title": "x"}])
    with pytest.raises(ValueError, match="five"):
        draft([{"op": "create_task", "ref": str(i), "title": str(i)} for i in range(6)])


def test_date_intents_earlier_completion_and_reopen_metadata():
    tid = task()
    anchor = utc_now()
    p = draft([{"op": "complete_at", "target": {"id": tid}, "when": {"kind": "day_offset", "days": -1}}], now=anchor)
    assert confirm(p)[0] == 200
    with Session(get_engine()) as db:
        row = db.get(Task, tid)
        assert row.completion_precision == "date"
        assert row.completion_local_date == anchor.date() - dt.timedelta(days=1)
        assert row.completed_at.hour == 12
    read = read_tasks(ReadTasksArgs(mode="get", task_ids=[tid]), p["conversation_id"])
    assert read["rows"][0]["completion_precision"] == "date"
    reopen = draft([{"op": "reopen_task", "target": {"id": tid}}])
    assert confirm(reopen)[0] == 200
    with Session(get_engine()) as db:
        assert db.get(Task, tid).completion_precision is None
    with pytest.raises(ValueError, match="earlier today"):
        draft([{"op": "complete_at", "target": {"id": tid}, "when": {"kind": "day_offset", "days": 0}}])


@pytest.mark.parametrize("date,time,offset,valid", [
    ("2026-03-08", "02:30", None, False),
    ("2026-11-01", "01:30", None, False),
    ("2026-11-01", "01:30", "-04:00", True),
    ("2026-11-01", "01:30", "-05:00", True),
    ("2026-11-01", "01:30", "+08:00", False),
])
def test_dst_intents(date, time, offset, valid):
    args = ProposeTaskChangesArgs.model_validate({"operations": [patch(1, deadline={"date": {"kind": "absolute", "date": date}, "local_time": time, "utc_offset": offset})]})
    if valid:
        assert canonical_operations(args, utc_now(), "America/New_York")[0]["set"]["deadline"]["kind"] == "instant"
    else:
        with pytest.raises(ValueError, match="nonexistent or ambiguous"):
            canonical_operations(args, utc_now(), "America/New_York")


def test_not_seen_is_inconclusive_abandoned_claim_is_terminal_and_pause_allows_status(client):
    p = draft([{"op": "create_task", "ref": "p", "title": "Once"}])
    sid = str(uuid4())
    body = {"operations": [{"operation_id": p["operation_id"], "submission_id": sid}]}
    result = client.post("/assistant/task-operations/status", json=body)
    assert result.status_code == 200
    assert result.json()["operations"][0]["submission"]["conclusive"] is False
    with Session(get_engine()) as db:
        db.add(AssistantTaskSubmission(id=sid, proposal_id=p["proposal_id"], operation_id=p["operation_id"], state="executing", created_at=utc_now(), updated_at=utc_now()))
        db.add(ActivityState(id=1, enabled=False, cursor=0, cutover={"paused": True})); db.commit()
    headers = {"X-Timebox-Protocol": "activity-online-v1"}
    result = client.post("/assistant/task-operations/status", json=body, headers=headers)
    assert result.status_code == 200
    assert result.json()["operations"][0]["submission"]["state"] == "rolled_back"
    assert client.get(f"/assistant/task-proposals/{p['proposal_id']}", headers=headers).status_code == 200
    assert confirm(p, sid, protocol="activity-online-v1")[0] == 409
    with pytest.raises(HTTPException) as error:
        confirm(p, protocol="activity-online-v1")
    assert error.value.status_code == 503


def test_draft_lifecycle_completed_stop_replacement_close_and_identity():
    tid = task()
    p = draft([patch(tid, title="New")], completed=False)
    assert confirm(p)[0] == 409
    assistant_storage.capture(p["originating_run_id"], "Ready", {}, None, "completed")
    assistant_storage.stop(p["conversation_id"], p["originating_run_id"])
    assert ops.review(p["proposal_id"])["status"] == "pending"
    replacement = draft([patch(tid, title="Newest")], conversation=p["conversation_id"])
    assert confirm(p)[0] == 409
    with pytest.raises(HTTPException):
        confirm(replacement, revision=2)
    assistant_storage.create(str(uuid4()), [], p["conversation_id"])
    assert confirm(replacement)[0] == 409
    assert ops.review(replacement["proposal_id"])["status"] == "cancelled"


def completion_fixture():
    now = utc_now()
    with Session(get_engine()) as db:
        kind = TaskType(name="Work"); day = Day(date=now.date() + dt.timedelta(days=1))
        t = Task(title="Finish", ready_to_plan=True, is_blocked=True, blocking_reason="why")
        db.add_all([kind, day, t]); db.flush()
        future = TimeBlock(day_id=day.id, lane="planned", task_type_id=kind.id, task_id=t.id, start_minute=600, end_minute=660, note="PRIVATE NOTE")
        db.add(future); db.flush()
        running = TimeBlock(lane="actual", task_type_id=kind.id, task_id=t.id, start_at=now-dt.timedelta(hours=1))
        db.add(running); db.commit()
        return t.id, future.id, running.id


def test_complete_now_effects_and_durable_undo_keeps_tracking_stopped():
    tid, plan_id, actual_id = completion_fixture()
    p = draft([{"op": "complete_now", "target": {"id": tid}}])
    assert "PRIVATE NOTE" not in json.dumps(ops.review(p["proposal_id"]))
    code, result = confirm(p)
    assert code == 200, result
    receipt = result["receipt"]
    assert receipt["effects"]["removed_plan_ids"] == [plan_id]
    assert receipt["effects"]["stopped_actual_ids"] == [actual_id]
    undo_id, sid = receipt["undo"]["undo_operation_id"], str(uuid4())
    code, undone = ops.undo(p["operation_id"], undo_id, sid)
    assert code == 200, undone
    assert ops.undo(p["operation_id"], undo_id, sid)[1]["receipt"] == undone["receipt"]
    with Session(get_engine()) as db:
        assert db.get(TimeBlock, actual_id).end_at is not None
        assert db.get(TimeBlock, plan_id) is not None
        assert db.get(Task, tid).ready_to_plan
        assert db.get(AssistantTaskProposal, p["proposal_id"]).receipt == receipt
    assert ops.outcome_context(p["conversation_id"])["operations"][0]["undone"]


def test_earlier_completion_leaves_all_blocks_and_plan_change_stales_now():
    tid, plan_id, actual_id = completion_fixture()
    p = draft([{"op": "complete_now", "target": {"id": tid}}])
    with Session(get_engine()) as db:
        db.get(TimeBlock, plan_id).end_minute = 670; db.commit()
    assert confirm(p)[1]["status"] == "stale"
    earlier = draft([{"op": "complete_at", "target": {"id": tid}, "when": {"kind": "day_offset", "days": -1}}])
    code, result = confirm(earlier)
    assert code == 200 and "undo" not in result["receipt"]
    with Session(get_engine()) as db:
        assert db.get(TimeBlock, plan_id) is not None
        assert db.get(TimeBlock, actual_id).end_at is None


@pytest.mark.skipif(get_engine().dialect.name != "postgresql", reason="Real PostgreSQL race")
def test_postgres_duplicate_confirmation_and_writer_admission():
    p = draft([{"op": "create_task", "ref": "p", "title": "Exactly once"}])
    sid = str(uuid4())
    with ThreadPoolExecutor(2) as pool:
        first, second = [f.result(timeout=15) for f in [pool.submit(confirm, p, sid), pool.submit(confirm, p, sid)]]
    assert first[0] == second[0] == 200
    assert first[1]["receipt"] == second[1]["receipt"]
    tid = first[1]["receipt"]["created"]["p"]
    p = draft([patch(tid, title="Reviewed")])
    admitted, release = Event(), Event()
    def manual():
        with admission(get_engine()) as connection, Session(connection) as db:
            db.get(Task, tid).title = "Manual winner"
            admitted.set(); assert release.wait(5); db.commit()
    with ThreadPoolExecutor(2) as pool:
        writer = pool.submit(manual); assert admitted.wait(5)
        confirmation = pool.submit(confirm, p)
        assert not confirmation.done()
        release.set(); writer.result(timeout=10)
        assert confirmation.result(timeout=10)[1]["status"] == "stale"


@pytest.mark.skipif(get_engine().dialect.name != "postgresql", reason="Real PostgreSQL races")
@pytest.mark.parametrize("mutation", ["delete", "plan_insert", "plan_move", "activity_switch", "project_delete", "type_rename", "type_merge", "reminder_claim", "unrelated"])
def test_postgres_review_sensitive_writer_races(mutation):
    from app.core.config import get_settings
    from app.schemas.task_type import TaskTypePatch
    from app.services import task_type_merge, task_type_service
    from app.services.battle_plan import projects, reminders, tasks
    tid, plan_id, actual_id = completion_fixture()
    with Session(get_engine()) as db:
        t = db.get(Task, tid)
        project = Project(name="Assigned"); other = TaskType(name="Other")
        db.add_all([project, other]); db.flush()
        t.project_id = project.id
        t.reminder_at = utc_now() - dt.timedelta(seconds=5)
        db.commit(); project_id, other_id = project.id, other.id
        type_id = db.get(TimeBlock, plan_id).task_type_id
    operation = patch(tid, task_type_id=type_id) if mutation.startswith("type_") else {"op": "complete_now", "target": {"id": tid}}
    p = draft([operation])
    admitted, release = Event(), Event()
    def manual():
        with admission(get_engine(), exclusive=mutation == "type_merge") as connection, Session(connection) as db:
            admitted.set(); assert release.wait(5)
            if mutation == "delete":
                tasks.trash_task(db, tid)
            elif mutation == "project_delete":
                projects.delete_project(db, project_id)
            elif mutation == "type_rename":
                task_type_service.patch_task_type(db, type_id, TaskTypePatch(name="Renamed"))
            elif mutation == "type_merge":
                preview = task_type_merge.preview(db, type_id, other_id)
                task_type_merge.merge(db, type_id, other_id, preview.preview_token)
                db.commit()
            elif mutation == "reminder_claim":
                reminders.claim_reminder(db, tid, db.get(Task, tid).reminder_at)
            elif mutation == "plan_move":
                db.get(TimeBlock, plan_id).start_minute = 620; db.commit()
            elif mutation == "plan_insert":
                plan = db.get(TimeBlock, plan_id)
                db.add(TimeBlock(day_id=plan.day_id, lane="planned", task_type_id=type_id, task_id=tid, start_minute=800, end_minute=860)); db.commit()
            elif mutation == "activity_switch":
                db.get(TimeBlock, actual_id).end_at = utc_now(); db.flush()
                db.add(TimeBlock(lane="actual", task_type_id=other_id, start_at=utc_now())); db.commit()
            else:
                from app.schemas.battle_plan import TaskPatch
                tasks.patch_task(db, tid, TaskPatch(importance="high"), get_settings())
    with ThreadPoolExecutor(2) as pool:
        writer = pool.submit(manual); assert admitted.wait(5)
        confirmation = pool.submit(confirm, p)
        assert not confirmation.done()
        release.set(); writer.result(timeout=10)
        code, result = confirmation.result(timeout=10)
        assert code == (200 if mutation == "unrelated" else 409), result
        assert result["status"] == ("applied" if mutation == "unrelated" else "stale")


@pytest.mark.skipif(get_engine().dialect.name != "postgresql", reason="Real PostgreSQL lifecycle races")
@pytest.mark.parametrize("lifecycle", ["dismiss", "close", "replace"])
def test_postgres_lifecycle_vs_confirmation_has_one_winner(lifecycle):
    p = draft([{"op": "create_task", "ref": "p", "title": "One winner"}])
    def mutate_lifecycle():
        if lifecycle == "dismiss":
            return ops.dismiss(p["proposal_id"])
        if lifecycle == "close":
            return assistant_storage.close(p["conversation_id"])
        return draft([{"op": "create_task", "ref": "p", "title": "Replacement"}], conversation=p["conversation_id"])
    with ThreadPoolExecutor(2) as pool:
        execution = pool.submit(confirm, p)
        change = pool.submit(mutate_lifecycle)
        code, result = execution.result(timeout=15); change.result(timeout=15)
    current = ops.review(p["proposal_id"])
    with Session(get_engine()) as db:
        count = db.scalar(select(func.count()).select_from(Task))
    assert (code, count, current["status"]) in {(200, 1, "applied"), (409, 0, "cancelled"), (409, 0, "replaced")}


def test_reminder_expiry_zone_change_and_canonical_scalar_edits(monkeypatch):
    tid = task()
    p = draft([patch(tid, title="First"), patch(tid, title="Final")])
    assert p["operations"] == [patch(tid, title="Final")]
    assert p["review_targets"][0]["before"]["title"] == "Report"
    assert confirm(p)[0] == 200
    p = draft([patch(tid, reminder_at={"kind": "relative_minutes", "minutes": 1})])
    monkeypatch.setattr(ops, "utc_now", lambda: utc_now() + dt.timedelta(minutes=2))
    assert confirm(p)[1]["status"] == "stale"
    monkeypatch.undo()
    p = draft([patch(tid, deadline={"kind": "day_offset", "days": 1})])
    with Session(get_engine()) as db:
        state = db.get(ActivityState, 1)
        state.reporting_timezone = "Asia/Singapore"; db.commit()
    assert confirm(p)[1]["status"] == "stale"
    with pytest.raises(HTTPException, match="Reporting Time Zone"):
        ops.refresh_proposal(p["proposal_id"])


def test_completion_journal_rolls_back_with_later_failure_and_undo_conflict(client, monkeypatch):
    from app.core.config import Settings, get_settings
    from app.main import app
    from tests.test_completion_tracking import setup_activity
    app.dependency_overrides[get_settings] = lambda: Settings(activity_tracking_dev=True)
    try:
        _, tid, _ = setup_activity(client)
        p = draft([{"op": "complete_now", "target": {"id": tid}}, {"op": "create_task", "ref": "p", "title": "Later"}])
        with Session(get_engine()) as db:
            before_count = db.scalar(select(func.count()).select_from(ActivityOperation))
        with monkeypatch.context() as patcher:
            def fail(*args, **kwargs): raise RuntimeError("later failure")
            patcher.setattr(ops, "create_task", fail)
            assert confirm(p)[0] == 503
        with Session(get_engine()) as db:
            assert db.scalar(select(func.count()).select_from(ActivityOperation)) == before_count
            assert db.get(Task, tid).status != TaskStatus.completed
        sole = draft([{"op": "complete_now", "target": {"id": tid}}])
        receipt = confirm(sole)[1]["receipt"]
        with Session(get_engine()) as db:
            entries = list(db.scalars(select(ActivityOperation)))
            assert len(entries) == before_count + 1
            db.get(Task, tid).title = "Newer state"; db.commit()
        undo_id, sid = receipt["undo"]["undo_operation_id"], str(uuid4())
        code, result = ops.undo(sole["operation_id"], undo_id, sid)
        assert code == 409 and result["submission"]["reason"] == "undo_conflict"
        assert ops.undo(sole["operation_id"], undo_id, sid)[0] == 409
    finally:
        app.dependency_overrides.pop(get_settings, None)


def test_deadline_patch_does_not_rearm_reminder_or_change_existing_block_type():
    tid, plan_id, _ = completion_fixture()
    with Session(get_engine()) as db:
        row = db.get(Task, tid)
        row.reminder_at = utc_now() - dt.timedelta(minutes=1)
        row.reminder_delivered_at = utc_now()
        new_type = TaskType(name="New classification"); db.add(new_type); db.commit()
        new_type_id = new_type.id
        block_type = db.get(TimeBlock, plan_id).task_type_id
    p = draft([patch(tid, deadline={"kind": "day_offset", "days": 2}, task_type_id=new_type_id)])
    assert confirm(p)[0] == 200
    with Session(get_engine()) as db:
        row = db.get(Task, tid)
        assert row.reminder_delivered_at is not None
        assert row.task_type_id == new_type_id
        assert db.get(TimeBlock, plan_id).task_type_id == block_type


def test_commit_succeeded_reply_failed_recovers_original_receipt(monkeypatch):
    p = draft([{"op": "create_task", "ref": "p", "title": "Recover"}])
    original = Session.commit
    lost = False
    def lost_commit_reply(db):
        nonlocal lost
        applied = any(isinstance(row, AssistantTaskProposal) and row.receipt for row in db.dirty)
        original(db)
        if applied and not lost:
            lost = True
            raise RuntimeError("commit response lost")
    sid = str(uuid4())
    with monkeypatch.context() as patcher:
        patcher.setattr(Session, "commit", lost_commit_reply)
        code, first = confirm(p, sid)
    assert lost and code == 200
    if get_engine().dialect.name == "postgresql":
        get_engine().dispose()
    assistant_storage.recover_interrupted()
    recovered = ops.statuses([{"operation_id": p["operation_id"], "submission_id": sid}])["operations"][0]
    assert recovered["receipt"] == first["receipt"]
    assert confirm(p, sid)[1]["receipt"] == first["receipt"]


def test_expiry_fences_late_arrival_but_preserves_applied_receipt(monkeypatch):
    pending = draft([{"op": "create_task", "ref": "p", "title": "Never"}])
    applied = draft([{"op": "create_task", "ref": "p", "title": "Saved"}])
    receipt = confirm(applied)[1]["receipt"]
    monkeypatch.setattr(ops, "utc_now", lambda: utc_now() + dt.timedelta(minutes=16))
    code, result = confirm(pending)
    assert code == 410 and result["status"] == "expired"
    assert confirm(applied)[1]["receipt"] == receipt


def test_plan_crossing_start_requires_review_without_normal_elapsed_tracking_conflict(monkeypatch):
    tid, pid, _ = completion_fixture()
    now = utc_now()
    with Session(get_engine()) as db:
        plan = db.get(TimeBlock, pid)
        plan.start_at, plan.end_at = now + dt.timedelta(seconds=30), now + dt.timedelta(hours=1)
        db.commit()
    p = draft([{"op": "complete_now", "target": {"id": tid}}])
    monkeypatch.setattr(ops, "utc_now", lambda: now + dt.timedelta(seconds=60))
    assert confirm(p)[1]["status"] == "stale"


@pytest.mark.skipif(get_engine().dialect.name != "postgresql", reason="Real PostgreSQL admission budget")
def test_admission_timeout_is_checking_and_never_submits(client, monkeypatch):
    p = draft([{"op": "create_task", "ref": "p", "title": "Wait"}])
    sid = str(uuid4())
    monkeypatch.setattr(ops, "TASK_ADMISSION_SECONDS", 0.03)
    admitted, release = Event(), Event()
    def writer():
        with admission(get_engine()):
            admitted.set(); assert release.wait(5)
    with ThreadPoolExecutor(1) as pool:
        future = pool.submit(writer); assert admitted.wait(5)
        result = client.post(f"/assistant/task-proposals/{p['proposal_id']}/confirm", json={"revision": 1, "operation_id": p["operation_id"], "submission_id": sid})
        release.set(); future.result(timeout=5)
    assert result.status_code == 202 and result.json()["conclusive"] is False
    status = ops.statuses([{"operation_id": p["operation_id"], "submission_id": sid}])["operations"][0]
    assert status["submission"]["state"] == "not_seen"


def test_outcome_bound_explicit_priority_and_cross_conversation_rejection():
    p = draft([{"op": "create_task", "ref": "p", "title": "Initial"}])
    for i in range(21):
        draft([{"op": "create_task", "ref": "p", "title": f"Later {i}"}], conversation=p["conversation_id"])
    outcome = ops.outcome_context(p["conversation_id"], reference_text=p["operation_id"])
    assert outcome["operations"][0]["operation_id"] == p["operation_id"]
    assert len(outcome["operations"]) <= 20 and outcome["omitted_count"] >= 2
    assert len(json.dumps(outcome).encode()) <= 8192
    explicit = read_tasks(ReadTasksArgs(mode="outcomes", operation_ids=[p["operation_id"]]), p["conversation_id"])
    assert len(explicit["operations"]) == 1 and explicit["omitted_count"] == 0
    with pytest.raises(ValueError, match="conversation"):
        ops.outcome_context(str(uuid4()), [p["operation_id"]])
