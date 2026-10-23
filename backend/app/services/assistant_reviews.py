"""Online Assistant tracking uses the canonical activity journal and atomic receipts.

Review acknowledges an executed command, not future edits to its Actual Block.
Corrections from Review use the same journal and reopen that receipt for review.
"""
import datetime as dt
import hashlib
import json
from uuid import UUID

from fastapi import HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.db.session import get_engine
from app.models.activity import ActivityOperation
from app.models.assistant import AssistantAttempt, AssistantTrackingReview
from app.models.time_block import TimeBlock
from app.schemas.activity import ActivityCommand
from app.schemas.time_block import ActualBlockRead
from app.services import activity_service


def instant(value):
    if isinstance(value, str):
        value = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    return value.replace(tzinfo=dt.UTC) if value.tzinfo is None else value.astimezone(dt.UTC)


def configure(run_id, mode):
    with Session(get_engine()) as db:
        row = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == run_id))
        row.tracking_mode = mode
        db.commit()


def _record(db, state, row):
    source = row.receipt["record_source"]
    provenance = (state.reconciliation or {}).get("provenance", {})
    ids = [int(key) for key, value in provenance.items() if value == source]
    records = list(db.scalars(select(TimeBlock).where(TimeBlock.id.in_(ids)))) if ids else list(db.scalars(
        select(TimeBlock).where(TimeBlock.activity_source == source)))
    # A later reconciliation may split or delete the record. Never edit a guessed fragment.
    return records[0] if len(records) == 1 else None


def _view(db, state, row):
    block = _record(db, state, row)
    current = ActualBlockRead.model_validate(block).model_dump(mode="json") if block else None
    if current:
        current.pop("note", None)
    operation = db.get(ActivityOperation, row.operation_id)
    outcome = operation.outcome if operation else "unavailable"
    revision = hashlib.sha256(json.dumps([current, outcome], sort_keys=True).encode()).hexdigest()
    return {"id": row.id, "run_id": row.run_id, "receipt": row.receipt, "record": current,
            "outcome": outcome, "revision": revision, "reviewed": row.reviewed_at is not None,
            "changed_since_review": row.reviewed_revision is not None and row.reviewed_revision != revision,
            "reviewed_at": row.reviewed_at}


def _command(db, state, *, kind, operation_id, at, target=None, **fields):
    now = dt.datetime.now(dt.UTC)
    device = "assistant-online"
    sequence = (db.scalar(select(func.max(ActivityOperation.sequence)).where(ActivityOperation.device_id == device)) or 0) + 1
    return ActivityCommand(operation_id=operation_id, device_id=device, sequence=sequence, action_at=now,
        calibration={"server_at": now, "offset_ms": 0}, base_cursor=state.cursor, kind=kind,
        target_id=target.id if target else None, effective={"mode": "instant", "at": at}, **fields)


def execute_run(run_id):
    """Only explicitly opted-in, completed Tracking Proposals can execute here."""
    try:
        with Session(get_engine()) as db:
            state = activity_service._lock(db)
            attempt = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == run_id))
            if not attempt or attempt.tracking_mode != "review_later" or attempt.status != "completed":
                raise ValueError("Tracking was not authorized for this completed response.")
            if attempt.tracking_result:
                return attempt.tracking_result
            proposal = attempt.tracking_proposal
            if not proposal:
                raise ValueError("No tracking action was proposed.")
            if instant(proposal["expires_at"]) < dt.datetime.now(dt.UTC):
                raise ValueError("The tracking request expired. Send a new command.")
            snapshot = activity_service._snapshot(db, state, get_settings().app_timezone)
            current = db.get(TimeBlock, snapshot.current.id) if snapshot.current else None
            stop = proposal["action"] == "stop"
            if stop and current is None:
                raise ValueError("Nothing was running, so nothing was stopped.")
            kind = "stop" if stop else "switch" if current else "start"
            selected = None if stop else next(iter(proposal["task_types"]), None)
            if not stop and not selected:
                raise ValueError("No existing Task Type matched. Use normal confirmation to choose one.")
            command = _command(db, state, kind=kind, operation_id=proposal["proposal_id"],
                at=instant(proposal["at"] or proposal["proposed_at"]), target=current,
                task_type_id=selected["id"] if selected else None, name=proposal.get("block_name"), selection_snapshot=True)
            old_source = snapshot.provenance.get(current.id, f"baseline:{current.id}") if current else None
            after = activity_service.execute(db, command, get_settings().app_timezone, commit=False)
            if after.acknowledgement.outcome != "applied":
                raise ValueError("Tracking changed before this command could apply. Review the current activity and try again.")
            source = old_source if stop else str(command.operation_id)
            block = next((item for item in after.records if after.provenance.get(item.id) == source), None)
            if block is None:
                raise ValueError("The recorded activity could not be identified. No change was saved.")
            saved = block.model_dump(mode="json")
            saved.pop("note", None)
            row = AssistantTrackingReview(run_id=run_id, operation_id=str(command.operation_id), receipt={
                "action": kind, "question": attempt.question, "conversation_id": attempt.conversation_id,
                "recorded_at": dt.datetime.now(dt.UTC).isoformat(), "record_source": source,
                "record": saved, "timezone": after.reporting_timezone,
            })
            db.add(row)
            db.flush()
            result = {"status": "recorded", "review_id": row.id, "action": kind,
                      "name": block.name or block.task_type.name, "at": after.acknowledgement.effective_at.isoformat()}
            attempt.tracking_result = result
            db.commit()
            return result
    except ValueError as error:
        with Session(get_engine()) as db:
            activity_service._lock(db)
            attempt = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == run_id))
            if attempt and attempt.tracking_result:
                return attempt.tracking_result
            if attempt and attempt.tracking_mode == "review_later" and not attempt.tracking_result:
                attempt.tracking_result = {"status": "not_recorded", "message": str(error)}
                db.commit()
                return attempt.tracking_result
        raise HTTPException(409, str(error)) from None


def result(run_id):
    with Session(get_engine()) as db:
        row = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == run_id))
        if not row:
            raise HTTPException(404, "Response not found")
        return row.tracking_result or {"status": "pending" if (row.status == "running" or (row.status == "completed" and row.tracking_proposal)) and row.tracking_mode == "review_later" else "not_recorded",
            "message": "No saved execution result yet. Refresh Review before issuing the same activity again."}


def listing(before=None, reviewed=False):
    with Session(get_engine()) as db:
        state = activity_service._lock(db)
        query = select(AssistantTrackingReview).where(AssistantTrackingReview.reviewed_at.is_not(None) if reviewed else AssistantTrackingReview.reviewed_at.is_(None))
        if before:
            query = query.where(AssistantTrackingReview.id < before)
        rows = list(db.scalars(query.order_by(AssistantTrackingReview.id.desc()).limit(51)))
        count = db.scalar(select(func.count()).select_from(AssistantTrackingReview).where(AssistantTrackingReview.reviewed_at.is_(None)))
        payload = {"items": [_view(db, state, row) for row in rows[:50]], "next_before": rows[49].id if len(rows) > 50 else None,
                   "task_types": [t.model_dump(mode="json") for t in activity_service._snapshot(db, state, get_settings().app_timezone).task_types],
                   "unchecked_count": count, "timezone": state.reporting_timezone or get_settings().app_timezone}
        db.commit()
        return payload


def acknowledge(items):
    with Session(get_engine()) as db:
        state = activity_service._lock(db)
        for item in items:
            row = db.get(AssistantTrackingReview, item["id"])
            if not row or _view(db, state, row)["revision"] != item["revision"]:
                raise HTTPException(409, "An activity changed. Refresh and review the updated records.")
            row.reviewed_at = dt.datetime.now(dt.UTC)
            row.reviewed_revision = item["revision"]
        db.commit()
    return {"reviewed": len(items)}


def correct(review_id, body):
    with Session(get_engine()) as db:
        state = activity_service._lock(db)
        row = db.get(AssistantTrackingReview, review_id)
        if not row:
            raise HTTPException(404, "Review not found")
        operation_id = str(body["operation_id"])
        previous = db.get(ActivityOperation, operation_id)
        if previous:
            # Retry identity is scoped to this receipt and exact edit, not merely a UUID.
            expected = {"review_id": review_id, **{k: str(v) if isinstance(v, (dt.datetime, UUID)) else v for k, v in body.items()}}
            if previous.envelope.get("device_id") != "assistant-review:" + hashlib.sha256(json.dumps(expected, sort_keys=True).encode()).hexdigest():
                raise HTTPException(409, "Correction operation ID was already used.")
            return _view(db, state, row)
        if _view(db, state, row)["revision"] != body["revision"]:
            raise HTTPException(409, "This activity changed. Refresh before correcting it.")
        target = _record(db, state, row)
        if target is None:
            raise HTTPException(409, "The activity was removed or split. Open Day to inspect it.")
        start, end = instant(body["start_at"]), instant(body["end_at"]) if body.get("end_at") else None
        if end is not None and end <= start:
            raise HTTPException(422, "End must be after start.")
        # Review does not restart a stopped activity or stop a running one through an edit.
        if (target.end_at is None) != (end is None):
            raise HTTPException(422, "Use tracking controls to stop or restart an activity.")
        expected = {"review_id": review_id, **{k: str(v) if isinstance(v, (dt.datetime, UUID)) else v for k, v in body.items()}}
        device = "assistant-review:" + hashlib.sha256(json.dumps(expected, sort_keys=True).encode()).hexdigest()
        now = dt.datetime.now(dt.UTC)
        command = ActivityCommand(operation_id=operation_id, device_id=device, sequence=1, action_at=now,
            calibration={"server_at": now, "offset_ms": 0}, base_cursor=state.cursor, kind="edit",
            target_id=target.id, target_source=row.receipt["record_source"], target_start_at=instant(target.start_at),
            effective={"mode": "range", "at": start, "end": end}, task_type_id=body["task_type_id"],
            name=body["name"], clear_fields=["name"] if not body["name"] else [])
        try:
            snapshot = activity_service.execute(db, command, get_settings().app_timezone, commit=False)
        except ValueError as error:
            raise HTTPException(409, str(error)) from None
        if snapshot.acknowledgement.outcome != "applied":
            raise HTTPException(409, "The correction conflicted with newer activity. Refresh and try again.")
        row.reviewed_at = None
        row.reviewed_revision = None
        payload = _view(db, state, row)
        db.commit()
        return payload
