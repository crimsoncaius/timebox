"""Immutable reviews and admitted synchronous atomic task execution.

No provider work runs here. Admission spans the durable claim and domain commit.
"""
from __future__ import annotations

import copy
import datetime as dt
import hashlib
import re
import time
from contextlib import contextmanager
from uuid import uuid4

from fastapi import HTTPException
from sqlalchemy import case, event, func, or_, select, text
from sqlalchemy.orm import Session, defer

from app.core.config import get_settings
from app.core.time import as_utc, utc_now
from app.db.activity_admission import admission
from app.db.session import get_engine
from app.models.activity import ActivityState
from app.models.assistant import (
    AssistantAttempt,
    AssistantConversation,
    AssistantTaskProposal,
    AssistantTaskSubmission,
)
from app.models.battle_plan import Project, Task
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.battle_plan import TaskCreate, TaskPatch
from app.services import activity_journal
from app.services import task_completion_service as completion
from app.services.activity_service import reporting_settings
from app.services.assistant_limits import (
    MAX_OUTCOME_BYTES,
    MAX_TASK_OUTCOMES,
    MAX_TASK_PARENTS,
    MAX_TASK_RECEIPT_BYTES,
    MAX_TASK_REVIEW_BYTES,
    TASK_ADMISSION_SECONDS,
    TASK_PROPOSAL_SECONDS,
    TASK_REQUEST_SECONDS,
    TASK_TRANSACTION_SECONDS,
)
from app.services.assistant_task_intents import canonical_operations
from app.services.assistant_tasks import encoded
from app.services.battle_plan.tasks import create_task, patch_task

FLAGS = ("ready_to_plan", "is_blocked", "blocking_reason", "reminder_at", "reminder_delivered_at",
         "reminder_skipped_at", "reminder_claim_token", "reminder_claim_until")
BASE = ("id", "parent_id", "recurrence_kind", "recurring_template_id", "title", "status", "archived_at", "deleted_at")
COMPLETION = ("completed_at", "completion_precision", "completion_local_date", "completion_timezone", "last_non_completed_status")


def wire(value):
    if isinstance(value, dt.datetime):
        return as_utc(value).isoformat()
    if isinstance(value, dt.date):
        return value.isoformat()
    return value.value if hasattr(value, "value") else value


def digest(value):
    return hashlib.sha256(encoded(value)).hexdigest()


@contextmanager
def transaction(*, recovery=False, engine=None):
    started = time.monotonic()
    with admission(engine or get_engine(), exclusive=True, timeout=TASK_ADMISSION_SECONDS) as connection:
        deadline = min(started + TASK_REQUEST_SECONDS, time.monotonic() + TASK_TRANSACTION_SECONDS)

        def budget(conn, cursor, statement, parameters, context, executemany):
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("Task transaction deadline exceeded")
            if conn.dialect.name == "postgresql":
                cursor.execute(f"SET LOCAL statement_timeout = '{max(1, int(remaining * 1000))}ms'")
                cursor.execute(f"SET LOCAL lock_timeout = '{max(1, int(remaining * 1000))}ms'")

        event.listen(connection, "before_cursor_execute", budget)
        try:
            with Session(connection, autoflush=False, expire_on_commit=False) as db:
                db.info["stop_budget"] = lambda: event.remove(connection, "before_cursor_execute", budget) if event.contains(connection, "before_cursor_execute", budget) else None
                yield db
        finally:
            if event.contains(connection, "before_cursor_execute", budget):
                event.remove(connection, "before_cursor_execute", budget)


def gate(db, protocol, *, write):
    state = db.get(ActivityState, 1)
    if state is not None and state.cutover:
        if protocol != "activity-online-v1":
            raise HTTPException(426, "Update the current Android/backend pair.", headers={"X-Timebox-Protocol": "activity-online-v1"})
        if write and state.cutover.get("paused"):
            raise HTTPException(503, "Activity updates are paused for recovery.")


def record_event(row, kind, now=None, related=None):
    item = {"event_id": str(uuid4()), "at": (now or utc_now()).isoformat(), "kind": kind}
    if related:
        item["related_operation_id"] = related
    row.events = [*(row.events or []), item]


def transition(row, status, now=None):
    if row.status != status:
        row.status = status
        record_event(row, status, now)


def expire(row, now):
    if row.status == "pending" and dt.datetime.fromisoformat(row.review["expires_at"]) <= now:
        transition(row, "expired", now)


def close_pending(db, conversation_id):
    for row in db.scalars(select(AssistantTaskProposal).where(AssistantTaskProposal.conversation_id == conversation_id,
                         AssistantTaskProposal.status.in_(["draft", "pending"])).with_for_update()):
        transition(row, "cancelled")


def finish_source(db, run_id, status):
    for row in db.scalars(select(AssistantTaskProposal).where(AssistantTaskProposal.run_id == run_id).with_for_update()):
        if row.status != "draft":
            continue  # Completed source eligibility survives later Stop/ack.
        conversation = db.get(AssistantConversation, row.conversation_id, with_for_update=True)
        if status == "completed" and conversation is not None and conversation.closed_at is None:
            for old in db.scalars(select(AssistantTaskProposal).where(
                AssistantTaskProposal.conversation_id == row.conversation_id, AssistantTaskProposal.status == "pending").with_for_update()):
                transition(old, "replaced")
            row.source_completed_at = utc_now()
            transition(row, "pending")
        elif status in {"stopped", "interrupted"} or conversation is None or conversation.closed_at is not None:
            transition(row, "invalid")


def field_names(fields):
    names = set()
    for name in fields:
        if name == "deadline":
            names.update(("deadline_date", "deadline_at"))
        elif name in {"blocked", "blocking_reason"}:
            names.update(("is_blocked", "blocking_reason"))
        elif name == "reminder_at":
            names.update(n for n in FLAGS if n.startswith("reminder_"))
        else:
            names.add(name)
    return names


def values(state, fields):
    result = {}
    for field in fields:
        if field == "deadline":
            result[field] = {"kind": "date", "date": state["deadline_date"]} if state.get("deadline_date") else {"kind": "instant", "instant": state["deadline_at"]} if state.get("deadline_at") else None
        elif field == "blocked":
            result[field] = state["is_blocked"]
        else:
            result[field] = state.get(field)
    return result


def simulate_patch(state, fields, now):
    for field, value in fields.items():
        if field == "deadline":
            state["deadline_date"] = value.get("date") if value else None
            state["deadline_at"] = value.get("instant") if value else None
        elif field == "reminder_at":
            instant = value["instant"] if value else None
            if instant and dt.datetime.fromisoformat(instant) <= now:
                raise ValueError("Reminder must remain in the future")
            state[field] = instant
        else:
            state["is_blocked" if field == "blocked" else field] = value


def effects_for(db, task_id, now, settings, *, dated):
    active_id, keep_id = completion._peek_active_actual(db, task_id)
    active = db.get(TimeBlock, active_id) if active_id else None
    running = {k: wire(getattr(active, k)) for k in ("id", "task_id", "task_type_id", "start_at", "planned_block_id")} if active else None
    if dated:
        return {"running_actual": running, "tracking": "unchanged", "removed_plans": [], "detached_actual_ids": [], "cleared_fields": ["ready_to_plan", "blocked", "blocking_reason", "reminder_at", "reminder_delivery"]}, {"running_actual": running}
    plans = list(db.scalars(completion._planned_for_task_select(task_id)))
    days = {d.id: d for d in db.scalars(select(Day).where(Day.id.in_([p.day_id for p in plans])))}
    removable = completion._removable_future_blocks(plans, days, keep_planned_id=keep_id, completed_at=now, settings=settings)
    actuals = list(db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual,
                              TimeBlock.planned_block_id.in_([p.id for p in removable])).order_by(TimeBlock.id)))
    private = [{k: wire(getattr(p, k)) for k in ("id", "day_id", "task_type_id", "task_id", "name", "note", "start_minute", "end_minute", "start_at", "end_at")} for p in removable]
    for p in private:
        p["note_hash"] = digest(p.pop("note"))
        p["day_date"] = days[p["day_id"]].date.isoformat()
    public = [{k: v for k, v in p.items() if k != "note_hash"} for p in private]
    guarded_actuals = [{k: wire(getattr(a, k)) for k in ("id", "task_id", "task_type_id", "planned_block_id", "start_at", "end_at")} for a in actuals]
    return {"running_actual": running, "tracking": "stop" if running else "unchanged", "removed_plans": public,
            "removed_plan_count": len(public), "detached_actual_ids": [a.id for a in actuals],
            "cleared_fields": ["ready_to_plan", "blocked", "blocking_reason", "reminder_at", "reminder_delivery"]}, {"running_actual": running, "plans": private, "actuals": guarded_actuals}


def build_review(db, operations, anchor, now, zone):
    """Simulate ordered intent in plain values, without changing ORM rows."""
    settings = reporting_settings(db, get_settings())
    originals, states, guards, targets, effects, parents = {}, {}, {}, {}, {}, set()
    transitions = {}
    reference_guards = {}

    def saved(task_id, *, parent_id=None):
        if task_id not in states:
            row = db.scalar(select(Task).options(defer(Task.description)).where(Task.id == task_id))
            if row is None or row.archived_at or row.deleted_at or row.recurrence_kind is not None or row.recurring_template_id is not None:
                raise ValueError("Target is unavailable or is not ordinary saved work")
            state = {c.key: wire(getattr(row, c.key)) for c in Task.__table__.columns if c.key != "description"}
            originals[task_id] = row
            states[task_id] = state
            guards[str(task_id)] = {name: state[name] for name in BASE}
        if states[task_id]["parent_id"] != parent_id:
            raise ValueError("Target parent relationship does not match")
        return task_id

    def target(value, *, parent_id=None):
        if "id" in value:
            return saved(value["id"], parent_id=parent_id)
        ref = value["ref"]
        if ref not in states or states[ref]["parent_id"] != parent_id:
            raise ValueError("Reference must name an earlier create of the expected kind")
        return ref

    def require_open(key):
        if states[key]["status"] == "completed":
            raise ValueError("Completed Tasks need an explicit preceding reopen")

    def guard_fields(key, names):
        if isinstance(key, int):
            for name in names:
                val = wire(getattr(originals[key], name))
                guards[str(key)][name] = {"hash": digest(val)} if name == "description" else val
                if name == "description" and name not in states[key]:
                    states[key][name] = val

    def review_fields(key, fields):
        entry = targets.setdefault(str(key), {"target": {"id": key} if isinstance(key, int) else {"ref": key},
            "kind": "subtask" if states[key]["parent_id"] is not None else "ordinary", "title": states[key]["title"],
            "parent": states[key]["parent_id"], "before": {}, "after": {}, "transitions": []})
        if entry["before"] is not None:
            for name, value in values(states[key], fields).items():
                entry["before"].setdefault(name, value)
        return entry

    def references(fields):
        for name, model in (("project_id", Project), ("task_type_id", TaskType)):
            if fields.get(name) is None:
                continue
            row = db.get(model, fields[name])
            if row is None or (name == "task_type_id" and row.is_merged):
                raise ValueError("Reference unavailable; choose an existing Project or Task Type")
            reference_guards[f"{name}:{row.id}"] = {"id": row.id, "name" if name == "project_id" else "path": row.name}

    for op in operations:
        action = op["op"]
        if action == "create_task":
            key = op["ref"]
            if key in states:
                raise ValueError("Create references must be unique")
            fields = {k: v for k, v in op.items() if k not in {"op", "ref"}}
            references(fields)
            states[key] = {"title": fields["title"], "status": "open", "parent_id": None,
                           "description": "", "ready_to_plan": False, "is_blocked": False,
                           "blocking_reason": None, "project_id": None, "task_type_id": None,
                           "urgency": None, "importance": None, "deadline_date": None, "deadline_at": None, "reminder_at": None}
            entry = review_fields(key, fields)
            entry["before"] = None
            simulate_patch(states[key], fields, now)
            visible = sorted(states[key].keys() - {"parent_id", "is_blocked", "deadline_date", "deadline_at", "description"}) + ["blocked", "deadline"]
            if "description" in fields:
                visible.append("description")
            entry["after"] = values(states[key], visible)
            parents.add(key)
            continue
        if action in {"add_subtask", "set_subtask_checked"}:
            parent = target(op["parent"])
            require_open(parent)
            review_fields(parent, [])
            parents.add(parent)
            if action == "add_subtask":
                key = op["ref"]
                if key in states:
                    raise ValueError("Create references must be unique")
                states[key] = {"title": op["title"], "parent_id": parent, "checked": False, "status": "open"}
                entry = review_fields(key, [])
                entry["before"] = None
                entry["after"] = {"title": op["title"], "checked": False}
                continue
            key = target(op["target"], parent_id=parent)
            guard_fields(key, ["checked"])
            entry = review_fields(key, ["checked"])
            states[key]["checked"] = op["checked"]
            entry["after"]["checked"] = op["checked"]
            continue
        if action == "rename_subtask":
            parent = saved(op["parent_id"])
            require_open(parent)
            review_fields(parent, [])
            parents.add(parent)
            key = saved(op["subtask_id"], parent_id=parent)
            entry = review_fields(key, ["title"])
            states[key]["title"] = op["title"]
            entry["after"]["title"] = op["title"]
            continue
        key = target(op["target"])
        parents.add(key)
        if action == "patch_task":
            require_open(key)
            fields = op["set"]
            references(values(states[key], [name for name in fields if name in {"project_id", "task_type_id"}]))
            references(fields)
            guard_fields(key, field_names(fields))
            entry = review_fields(key, fields)
            simulate_patch(states[key], fields, now)
            entry["after"].update(values(states[key], fields))
            continue
        if key in transitions:
            raise ValueError("Contradictory or repeated completion/reopen transitions")
        transitions[key] = action
        guard_fields(key, COMPLETION)
        entry = review_fields(key, ["status", "completed_at", "completion_precision", "completion_local_date", "completion_timezone"])
        entry["transitions"].append(action)
        if action == "reopen_task":
            if states[key]["status"] != "completed":
                raise ValueError("Only completed Tasks can be reopened")
            states[key].update(status="open", completed_at=None, completion_precision=None, completion_local_date=None, completion_timezone=None)
            entry["after"].update(values(states[key], entry["before"] or {}))
            effects[str(key)] = {"restores_prior_state": False, "restarts_tracking": False}
        else:
            require_open(key)
            guard_fields(key, FLAGS)
            before_flags = values(states[key], ["ready_to_plan", "blocked", "blocking_reason", "reminder_at"])
            if entry["before"] is not None:
                entry["before"].update({k: v for k, v in before_flags.items() if k not in entry["before"]})
            if isinstance(key, int):
                effect, guard = effects_for(db, key, now, settings, dated=action == "complete_at")
                effects[str(key)] = effect
                guards[str(key)]["effects"] = guard
            else:
                effects[str(key)] = {"running_actual": None, "removed_plans": [], "detached_actual_ids": [], "tracking": "unchanged", "cleared_fields": ["ready_to_plan", "blocked", "blocking_reason", "reminder_at"]}
            states[key].update(status="completed", ready_to_plan=False, is_blocked=False, blocking_reason=None, reminder_at=None)
            entry["after"].update(status="completed", completion=op.get("completion") or {"precision": "instant", "at": "confirmation"}, ready_to_plan=False, blocked=False, blocking_reason=None, reminder_at=None)
    if len(parents) > MAX_TASK_PARENTS:
        raise ValueError("Narrow to at most five parent Tasks")
    has_dates = any(op["op"] in {"complete_at", "complete_now"} or any(k in (op.get("set") or op) for k in ("deadline", "reminder_at")) for op in operations)
    all_guards = {"tasks": guards, "references": reference_guards}
    if has_dates:
        if settings.app_timezone != zone:
            raise ValueError("Reporting Time Zone changed; dates require a fresh review")
        all_guards["reporting_timezone"] = settings.app_timezone
    return list(targets.values()), effects, all_guards, sorted(parents, key=str)


def description_marker(value):
    return {"sha256": digest(value or ""), "characters": len(value or ""), "bytes": len((value or "").encode("utf-8")), "separate_review": True}


def public_review(review):
    """No private full text, guard or token leaks into SSE/model input."""
    result = copy.deepcopy(review)
    for op in result["operations"]:
        fields = op.get("set", op)
        if "description" in fields:
            fields["description"] = description_marker(fields["description"])
    for target in result["review_targets"]:
        for key in ("before", "after"):
            if target[key] and "description" in target[key]:
                target[key]["description"] = description_marker(target[key]["description"])
    result.pop("request_intent", None)
    return result


def _new_proposal(db, conversation_id, run_id, operations, anchor, zone, *, intent=None, supersedes=None, now=None):
    now = now or utc_now()
    conversation = db.get(AssistantConversation, conversation_id, with_for_update=True)
    attempt = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == run_id))
    if conversation is None or conversation.closed_at is not None or attempt is None or attempt.conversation_id != conversation_id:
        raise ValueError("Conversation/source response is unavailable")
    targets, effects, guards, parents = build_review(db, operations, anchor, now, zone)
    # Validate the original intermediate states first. Collapse only scalar
    # edits within a lifecycle segment, never completion/reopen transitions.
    canonical = copy.deepcopy(operations)
    seen = {}
    for op in reversed(canonical):
        if op["op"] in {"complete_now", "complete_at", "reopen_task"}:
            seen.pop(str(op["target"]), None)
        elif op["op"] == "patch_task":
            fields = seen.setdefault(str(op["target"]), set())
            remaining = {k: v for k, v in op["set"].items() if k not in fields}
            fields.update(op["set"])
            op["set"] = remaining
    canonical = [op for op in canonical if op["op"] != "patch_task" or op["set"]]
    # Canonicalization must retain the same review/guards; grouping fields such
    # as blocked/reason together prevents a hidden intermediate consequence.
    if canonical != operations:
        _, _, canonical_guards, _ = build_review(db, canonical, anchor, now, zone)
        if canonical_guards == guards:
            operations = canonical
    proposal_id, operation_id = str(uuid4()), str(uuid4())
    review = {"schema_version": 1, "proposal_id": proposal_id, "revision": 1, "operation_id": operation_id,
              "conversation_id": conversation_id, "originating_run_id": run_id, "created_at": now.isoformat(),
              "expires_at": (now + dt.timedelta(seconds=TASK_PROPOSAL_SECONDS)).isoformat(),
              "request_anchor": {"received_at": anchor.isoformat(), "reporting_timezone": zone},
              "operations": operations, "review_targets": targets, "side_effects": effects,
              "references": guards["references"],
              "description_review_available": any("description" in op.get("set", op) for op in operations),
              "parent_ids": [i for i in parents if isinstance(i, int)], "request_intent": intent}
    if supersedes:
        review["supersedes_proposal_id"] = supersedes.id
    review["content_hash"] = digest(review)
    if max(len(encoded(review)), len(encoded(public_review(review)))) > MAX_TASK_REVIEW_BYTES - 1024:
        raise ValueError("Complete review exceeds 64 KiB; narrow the request or use Task Detail")
    row = AssistantTaskProposal(id=proposal_id, operation_id=operation_id, conversation_id=conversation_id,
        run_id=run_id, created_at=now, status="draft", review=review, guards=guards, events=[])
    db.add(row)
    record_event(row, "draft", now)
    if supersedes:
        if supersedes.source_completed_at is None:
            raise ValueError("An unfinished draft cannot be refreshed into eligibility")
        row.source_completed_at = supersedes.source_completed_at
        for old in db.scalars(select(AssistantTaskProposal).where(AssistantTaskProposal.conversation_id == conversation_id,
                               AssistantTaskProposal.status == "pending").with_for_update()):
            transition(old, "replaced", now)
        transition(row, "pending", now)
    return row


def propose(args, conversation_id, run_id, anchor, zone):
    operations = canonical_operations(args, anchor, zone)
    with transaction() as db:
        attempt = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == run_id))
        if attempt is None or attempt.status != "running":
            raise ValueError("A running saved response is required")
        if db.scalar(select(AssistantTaskProposal.id).where(AssistantTaskProposal.run_id == run_id)):
            raise ValueError("Only one task proposal may originate in a response")
        row = _new_proposal(db, conversation_id, run_id, operations, anchor, zone, intent=args.model_dump(exclude_unset=True))
        db.commit()
        return {**public_review(row.review), "status": row.status}


def validate_event(data, conversation_id, run_id):
    with Session(get_engine()) as db:
        row = db.get(AssistantTaskProposal, data.get("proposal_id"))
        if row is None or row.conversation_id != conversation_id or row.run_id != run_id or row.status != "draft":
            raise ValueError("Unknown task proposal")
        expected = {**public_review(row.review), "status": "draft"}
        if data != expected:
            raise ValueError("Task proposal content does not match its saved review")
        return expected


def _get(db, proposal_id):
    row = db.get(AssistantTaskProposal, proposal_id, with_for_update=True)
    if row is None:
        raise HTTPException(404, "Task proposal not found")
    return row


def result(row, submission=None, *, undo=False):
    receipt = row.undo_receipt if undo else row.receipt
    return {"operation_id": row.undo_operation_id if undo else row.operation_id, "proposal_id": row.id,
            "status": "applied" if receipt else "pending" if undo else row.status, "receipt": receipt,
            "submission": {"submission_id": submission.id, "operation_id": submission.operation_id,
                           "state": submission.state, "reason": submission.reason,
                           "created_at": wire(submission.created_at), "updated_at": wire(submission.updated_at)} if submission else None,
            "status_events": row.events[-20:], "older_status_events": max(0, len(row.events) - 20),
            "undo_result": {"operation_id": row.undo_operation_id,
                "status": "applied" if row.undo_receipt else "conflict" if any(e["kind"] == "undo_conflict" for e in row.events) else "available",
                "receipt": row.undo_receipt} if row.undo_operation_id and not undo else None}


def review(proposal_id, protocol=None):
    with transaction(recovery=True) as db:
        gate(db, protocol, write=False)
        row = _get(db, proposal_id)
        expire(row, utc_now())
        db.commit()
        return {**row.review, "status": row.status, "source_completed_at": wire(row.source_completed_at)}


def dismiss(proposal_id, protocol=None):
    with transaction() as db:
        gate(db, protocol, write=False)
        row = _get(db, proposal_id)
        if row.status in {"pending", "draft"}:
            transition(row, "cancelled")
        db.commit()
        return result(row)


def refresh_proposal(proposal_id, protocol=None):
    with transaction() as db:
        gate(db, protocol, write=False)
        old = _get(db, proposal_id)
        if old.receipt or old.status in {"draft", "cancelled", "invalid", "replaced"}:
            raise HTTPException(409, {"reason": "ineligible", **result(old)})
        anchor = old.review["request_anchor"]
        zone = reporting_settings(db, get_settings()).app_timezone
        if zone != anchor["reporting_timezone"] and "reporting_timezone" in old.guards:
            raise HTTPException(409, "Reporting Time Zone changed; make a new request with explicit dates")
        try:
            new = _new_proposal(db, old.conversation_id, old.run_id, old.review["operations"],
                dt.datetime.fromisoformat(anchor["received_at"]), anchor["reporting_timezone"],
                intent=old.review.get("request_intent"), supersedes=old)
        except ValueError as error:
            raise HTTPException(409, str(error)) from None
        db.commit()
        return {**public_review(new.review), "status": new.status, "source_completed_at": wire(new.source_completed_at)}


def lock_domain(db, row):
    """Acquire all set-wide locks first, in the established global order."""
    activity_journal.lock(db)
    ids = [int(i) for i in row.guards["tasks"]]
    tasks = list(db.scalars(select(Task).where(or_(Task.id.in_(ids), Task.parent_id.in_(ids))).order_by(Task.id).with_for_update()))
    task_ids = [t.id for t in tasks]
    plans = list(db.scalars(select(TimeBlock).where(TimeBlock.task_id.in_(task_ids), TimeBlock.lane == BlockLane.planned)))
    list(db.scalars(select(Day).where(Day.id.in_([p.day_id for p in plans])).order_by(Day.id).with_for_update()))
    list(db.scalars(select(TimeBlock).where(TimeBlock.id.in_([p.id for p in plans])).order_by(TimeBlock.id).with_for_update()))
    list(db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual,
        or_(TimeBlock.task_id.in_(task_ids), TimeBlock.planned_block_id.in_([p.id for p in plans]))).order_by(TimeBlock.id).with_for_update()))


def patch_body(fields):
    data = dict(fields)
    if "blocked" in data:
        data["is_blocked"] = data.pop("blocked")
    if "deadline" in data:
        deadline = data.pop("deadline")
        data.update(deadline_date=deadline.get("date") if deadline else None, deadline_at=deadline.get("instant") if deadline else None)
    if "reminder_at" in data:
        data["reminder_at"] = data["reminder_at"]["instant"] if data["reminder_at"] else None
    return data


def apply_operations(db, row, now, settings):
    created, effects, token = {}, {"completion_instants": [], "stopped_actual_ids": [], "removed_plan_ids": [], "detached_actual_links": [], "cleared_fields": []}, None

    def target(value):
        return value["id"] if "id" in value else created[value["ref"]]

    for op in row.review["operations"]:
        action = op["op"]
        if action == "create_task":
            task = create_task(db, TaskCreate.model_validate(patch_body({k: v for k, v in op.items() if k not in {"op", "ref"}})), settings, commit=False)
            created[op["ref"]] = task.id
        elif action == "patch_task":
            patch_task(db, target(op["target"]), TaskPatch.model_validate(patch_body(op["set"])), settings, commit=False)
        elif action == "add_subtask":
            task = create_task(db, TaskCreate(title=op["title"], parent_id=target(op["parent"])), settings, commit=False)
            created[op["ref"]] = task.id
        elif action == "rename_subtask":
            patch_task(db, op["subtask_id"], TaskPatch(title=op["title"]), settings, commit=False)
        elif action == "set_subtask_checked":
            completion.set_subtask_checked(db, target(op["target"]), checked=op["checked"], commit=False)
        elif action == "reopen_task":
            completion.reopen_task(db, target(op["target"]), commit=False)
        elif action == "complete_now":
            task_id = target(op["target"])
            active, _ = completion._peek_active_actual(db, task_id)
            _, token, removed = completion.complete_task(db, task_id, now, settings, commit=False)
            effects["completion_instants"].append({"target_id": task_id, "precision": "instant", "instant": now.isoformat()})
            effects["stopped_actual_ids"].extend([active] if active else [])
            effects["removed_plan_ids"].extend(removed)
        elif action == "complete_at":
            intent = op["completion"]
            task = completion.record_dated_completion(db, target(op["target"]), dt.datetime.fromisoformat(intent["instant"]), commit=False)
            task.completion_precision = intent["precision"]
            task.completion_local_date = dt.date.fromisoformat(intent["local_date"]) if intent.get("local_date") else None
            task.completion_timezone = intent["reporting_timezone"]
            db.flush()
            effects["completion_instants"].append({"target_id": task.id, **intent})
        if action in {"complete_now", "complete_at"}:
            key = str(op["target"].get("id", op["target"].get("ref")))
            effect = row.review["side_effects"][key]
            effects["detached_actual_links"].extend(effect.get("detached_actual_ids", []))
            effects["cleared_fields"].append({"target_id": target(op["target"]), "fields": effect["cleared_fields"]})
    changes = []
    for item in row.review["review_targets"]:
        after = dict(item["after"])
        if "completion" in after:
            after["completion"] = next((v for v in effects["completion_instants"] if v["target_id"] == target(item["target"])), after["completion"])
        if "description" in after:
            after["description"] = description_marker(after["description"])
        changes.append({"target_id": target(item["target"]), "kind": item["kind"], "changed_fields": list(after), "approved_after_values": after})
    receipt = {"schema_version": 1, "operation_id": row.operation_id, "proposal_id": row.id, "revision": 1,
        "content_hash": row.review["content_hash"], "conversation_id": row.conversation_id,
        "originating_run_id": row.run_id, "committed_at": now.isoformat(), "outcome": "applied",
        "created": created, "changes": changes, "effects": effects}
    if len(row.review["operations"]) == 1 and row.review["operations"][0]["op"] == "complete_now":
        row.undo_operation_id, row.undo_token = str(uuid4()), token
        receipt["undo"] = {"undo_operation_id": row.undo_operation_id, "eligible": True,
                           "limitations": "Conflict-checked restoration of Task state/plans; tracking stays stopped."}
    return receipt


def execute(proposal_id, operation_id, submission_id, *, revision=1, protocol=None, undo=False):
    with transaction() as db:
        row = _get(db, proposal_id)
        expected = row.undo_operation_id if undo else row.operation_id
        if revision != 1 or expected != operation_id:
            raise HTTPException(409, {"reason": "identity_mismatch"})
        submission = db.get(AssistantTaskSubmission, submission_id)
        if submission and (submission.operation_id != operation_id or submission.proposal_id != row.id):
            raise HTTPException(409, {"reason": "submission_identity_mismatch"})
        gate(db, protocol, write=False)
        if row.undo_receipt if undo else row.receipt:
            return 200, result(row, submission, undo=undo)
        if submission:
            if submission.state == "executing":
                submission.state, submission.reason, submission.updated_at = "rolled_back", "abandoned_submission", utc_now()
                db.commit()
            return 409, result(row, submission, undo=undo)
        gate(db, protocol, write=True)
        now = utc_now()
        submission = AssistantTaskSubmission(id=submission_id, proposal_id=row.id, operation_id=operation_id,
            state="executing", created_at=now, updated_at=now)
        db.add(submission)
        db.commit()  # Claim survives rollback; exclusive admission remains held.
        try:
            row = _get(db, proposal_id)
            now = utc_now()
            if not undo:
                expire(row, now)
                conversation = db.get(AssistantConversation, row.conversation_id, with_for_update=True)
                if row.status != "pending" or row.source_completed_at is None or conversation.closed_at is not None:
                    raise HTTPException(410 if row.status == "expired" else 409, {"reason": row.status})
                lock_domain(db, row)
                anchor = row.review["request_anchor"]
                try:
                    _, _, guards, _ = build_review(db, row.review["operations"], dt.datetime.fromisoformat(anchor["received_at"]), now, anchor["reporting_timezone"])
                except ValueError:
                    raise HTTPException(409, {"reason": "stale"}) from None
                if guards != row.guards:
                    raise HTTPException(409, {"reason": "stale"})
                receipt = apply_operations(db, row, now, reporting_settings(db, get_settings()))
                transition(row, "applied", now)
                receipt["status_events"] = row.events[-1:]
                row.receipt = receipt
            else:
                if not row.receipt or not row.undo_token:
                    raise HTTPException(409, {"reason": "undo_unavailable"})
                task_id = row.receipt["changes"][0]["target_id"]
                try:
                    completion.undo_task_completion(db, task_id, row.undo_token, commit=False)
                except ValueError:
                    raise HTTPException(409, {"reason": "undo_conflict"}) from None
                record_event(row, "undone", now, operation_id)
                row.undo_receipt = {"schema_version": 1, "operation_id": operation_id, "proposal_id": row.id,
                    "original_operation_id": row.operation_id, "conversation_id": row.conversation_id,
                    "outcome": "applied", "committed_at": now.isoformat(), "target_id": task_id,
                    "tracking_restarted": False, "status_events": row.events[-1:]}
                receipt = row.undo_receipt
            if len(encoded(receipt)) > MAX_TASK_RECEIPT_BYTES:
                raise ValueError("receipt_limit")
            submission.state, submission.updated_at = "applied", now
            db.commit()
            return 200, result(row, submission, undo=undo)
        except Exception as error:
            db.rollback()
            db.info["stop_budget"]()
            if db.get_bind().invalidated:
                # Do not reconnect without admission and guess at an uncertain
                # commit. The next admitted status request establishes outcome.
                raise HTTPException(503, {"status": "checking", "conclusive": False}) from None
            if db.get_bind().dialect.name == "postgresql":
                db.execute(text("SET LOCAL statement_timeout = '5000ms'"))
                db.execute(text("SET LOCAL lock_timeout = '5000ms'"))
            # COMMIT may have succeeded despite a transport error. Re-read its
            # receipt under admission before declaring a terminal rollback.
            db.expire_all()
            row = _get(db, proposal_id)
            submission = db.get(AssistantTaskSubmission, submission_id)
            if row.undo_receipt if undo else row.receipt:
                return 200, result(row, submission, undo=undo)
            reason = error.detail.get("reason", "rejected") if isinstance(error, HTTPException) and isinstance(error.detail, dict) else "transaction_rolled_back"
            submission.state = "rejected" if isinstance(error, (HTTPException, ValueError)) else "rolled_back"
            submission.reason, submission.updated_at = reason, utc_now()
            if reason in {"stale", "expired"}:
                transition(row, reason)
            if reason == "undo_conflict":
                record_event(row, reason, related=operation_id)
            record_event(row, submission.state, related=operation_id)
            db.commit()
            return error.status_code if isinstance(error, HTTPException) else 409 if isinstance(error, ValueError) else 503, result(row, submission, undo=undo)


def undo(operation_id, undo_operation_id, submission_id, protocol=None):
    with Session(get_engine()) as db:
        row = db.scalar(select(AssistantTaskProposal).where(AssistantTaskProposal.operation_id == operation_id))
        if row is None:
            raise HTTPException(404, "Task operation not found")
        proposal_id = row.id
    return execute(proposal_id, undo_operation_id, submission_id, protocol=protocol, undo=True)


def statuses(operations, protocol=None):
    with transaction(recovery=True) as db:
        gate(db, protocol, write=False)
        output = []
        for request in operations:
            operation_id = request["operation_id"]
            row = db.scalar(select(AssistantTaskProposal).where(or_(AssistantTaskProposal.operation_id == operation_id,
                            AssistantTaskProposal.undo_operation_id == operation_id)).with_for_update())
            if row is None:
                raise HTTPException(404, "Task operation not found")
            expire(row, utc_now())
            submission = db.get(AssistantTaskSubmission, request["submission_id"]) if request.get("submission_id") else None
            if submission and submission.operation_id != operation_id:
                raise HTTPException(409, "Submission does not belong to this operation")
            if submission and submission.state == "executing":
                submission.state, submission.reason, submission.updated_at = "rolled_back", "abandoned_submission", utc_now()
            value = result(row, submission, undo=operation_id == row.undo_operation_id)
            if request.get("submission_id") and submission is None:
                value["submission"] = {"submission_id": request["submission_id"], "operation_id": operation_id, "state": "not_seen", "conclusive": False}
            output.append(value)
        db.commit()
        return {"operations": output}


def outcome_context(conversation_id, explicit_ids=(), *, explicit_only=False, reference_text=""):
    """Compact current server evidence, independent of prose acknowledgement."""
    from app.services.assistant_tasks import read_session
    with read_session() as db:
        base = select(AssistantTaskProposal).where(AssistantTaskProposal.conversation_id == conversation_id)
        mentioned = re.findall(r"\b[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\b", reference_text)
        referenced = list(db.scalars(select(AssistantTaskProposal.operation_id).where(
            AssistantTaskProposal.conversation_id == conversation_id, AssistantTaskProposal.operation_id.in_(mentioned)))) if mentioned else []
        explicit_ids = list(dict.fromkeys([*explicit_ids, *referenced]))
        count = len(set(explicit_ids)) if explicit_only else db.scalar(select(func.count()).select_from(AssistantTaskProposal).where(AssistantTaskProposal.conversation_id == conversation_id))
        explicit = list(db.scalars(base.where(AssistantTaskProposal.operation_id.in_(explicit_ids)))) if explicit_ids else []
        recent = [] if explicit_only else list(db.scalars(base.order_by(case((AssistantTaskProposal.status == "pending", 0), else_=1), AssistantTaskProposal.created_at.desc()).limit(MAX_TASK_OUTCOMES)))
        rows = list({r.operation_id: r for r in [*recent, *explicit]}.values())
        by_id = {r.operation_id: r for r in rows}
        if any(i not in by_id for i in explicit_ids):
            raise ValueError("Operation does not belong to this conversation")
        ordered = list(dict.fromkeys([*explicit_ids, *[r.operation_id for r in rows if r.status == "pending"], *[r.operation_id for r in sorted(rows, key=lambda r: r.review["created_at"], reverse=True)]]))
        output = {"operations": [], "omitted_count": max(0, count - len(rows)), "status": "complete"}
        for operation_id in ordered:
            row = by_id[operation_id]
            state = "expired" if row.status == "pending" and dt.datetime.fromisoformat(row.review["expires_at"]) <= utc_now() else row.status
            submission = db.scalar(select(AssistantTaskSubmission).where(AssistantTaskSubmission.operation_id == operation_id).order_by(AssistantTaskSubmission.created_at.desc()).limit(1))
            summary = {"operation_id": operation_id, "proposal_id": row.id, "status": state,
                "target_ids": list(dict.fromkeys([*row.review["parent_ids"], *((row.receipt or {}).get("created") or {}).values()])),
                "committed_at": row.receipt["committed_at"] if row.receipt else None,
                "operation_kinds": list(dict.fromkeys(op["op"] for op in row.review["operations"])),
                "changed_fields": list(dict.fromkeys(field for target in row.review["review_targets"] for field in target["after"])),
                "submission_state": submission.state if submission else "not_seen", "undone": row.undo_receipt is not None}
            if len(output["operations"]) >= MAX_TASK_OUTCOMES or len(encoded({**output, "operations": [*output["operations"], summary]})) > MAX_OUTCOME_BYTES - 128:
                output["omitted_count"] += 1
            else:
                output["operations"].append(summary)
        if output["omitted_count"]:
            output["status"] = "partial"
        return output


def pending_targets(conversation_id):
    with Session(get_engine()) as db:
        rows = db.scalars(select(AssistantTaskProposal).where(AssistantTaskProposal.conversation_id == conversation_id,
                          AssistantTaskProposal.status == "pending"))
        return list(dict.fromkeys(i for row in rows for i in row.review["parent_ids"]))


def pending_children(conversation_id):
    with Session(get_engine()) as db:
        rows = db.scalars(select(AssistantTaskProposal).where(AssistantTaskProposal.conversation_id == conversation_id,
                          AssistantTaskProposal.status == "pending"))
        return {int(key): guard["parent_id"] for row in rows for key, guard in row.guards["tasks"].items()
                if guard["parent_id"] is not None}
