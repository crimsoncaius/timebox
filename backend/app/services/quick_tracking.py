"""Interpret without mutation; admit quick commands through the ordinary journal."""
import datetime as dt
from typing import Literal
from uuid import UUID, uuid4

from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.db.session import get_engine
from app.models.activity import ActivityOperation
from app.models.assistant import AssistantAttempt, AssistantConversation, AssistantTrackingReview
from app.models.task_type import TaskType
from app.schemas.time_block import ActualBlockRead
from app.services import activity_service
from app.services import quick_tracking_interpretation as language
from app.services.activity_reconciliation import instant


class CurrentActivity(BaseModel):
    model_config = ConfigDict(extra="forbid")
    task_type_id: int
    name: str | None = Field(default=None, max_length=500)


class QuickRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    request_id: UUID
    message: str = Field(min_length=1, max_length=4000)
    source: Literal["tile", "assistant", "app"]
    current: CurrentActivity | None = None


async def interpret(body: QuickRequest):
    received = dt.datetime.now(dt.UTC)
    request = body.model_dump(mode="json")
    with Session(get_engine()) as db:
        existing = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == str(body.request_id)))
        if existing:
            if existing.tracking_mode != "quick" or existing.context_inputs["request"] != request:
                raise HTTPException(409, "Request ID already used.")
            return existing.context_inputs["result"]
        snapshot = activity_service.read(db, get_settings().app_timezone)
    types = {t.id: t.name for t in snapshot.task_types}
    current = None
    if body.current:
        if body.current.task_type_id not in types:
            raise HTTPException(409, "Task Types changed. Refresh Activity Tracking and retry.")
        current = {"task_type": types[body.current.task_type_id], "block_name": body.current.name, "end": None}
    state = {"timezone": snapshot.reporting_timezone, "task_types": list(types.values()), "blocks": [current] if current else []}
    decision, usage, validation = await language.interpret_workflow(state, body.message, received)
    result = {"request_id": str(body.request_id), "question": body.message, "source": body.source,
              "action": decision.action, "task_type_id": next((i for i, name in types.items() if name == decision.task_type), None),
              "effective_at": validation.get("effective_at"), "received_at": received.isoformat(),
              "timezone": snapshot.reporting_timezone, "timing": validation.get("timing"),
              "message": validation.get("rejection") or ("Ready to record." if decision.action else "No tracking change requested." if validation.get("choice") == "no_change" else "Please describe one activity to track."),
              "usage": usage}
    with Session(get_engine()) as db:
        activity_service._lock(db)
        existing = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == str(body.request_id)))
        if existing:
            if existing.tracking_mode != "quick" or existing.context_inputs["request"] != request:
                raise HTTPException(409, "Request ID already used.")
            return existing.context_inputs["result"]
        conversation = str(uuid4())
        db.add(AssistantConversation(id=conversation, capabilities=["quick_tracking_v1"], closed_at=received))
        db.flush()
        db.add(AssistantAttempt(run_id=str(body.request_id), conversation_id=conversation, question=body.message,
            model=language.DECISION_MODEL, status="completed", tracking_mode="quick",
            context_inputs={"request": request, "result": result, "validation": validation}))
        db.commit()
    return result


def validate(db, body, timezone):
    attempt = db.scalar(select(AssistantAttempt).where(AssistantAttempt.run_id == str(body.quick_tracking_id)))
    if not attempt or attempt.tracking_mode != "quick" or attempt.status != "completed":
        raise ValueError("Quick tracking interpretation is unavailable. Retry the command.")
    result = attempt.context_inputs["result"]
    if str(body.operation_id) != str(body.quick_tracking_id):
        raise ValueError("Quick tracking must reuse its request ID.")
    if result["action"] not in ("track", "stop") or body.effective.at != instant(result["effective_at"]):
        raise ValueError("Quick tracking must use its interpreted effective time.")
    if timezone != result["timezone"]:
        raise ValueError("Reporting Time Zone changed. Retry the command.")
    language.validate_instant(body.effective.at, instant(result["received_at"]), timezone)
    received = instant(result["received_at"])
    if not received <= body.action_at <= received + dt.timedelta(minutes=15):
        raise ValueError("Quick tracking must be saved within fifteen minutes of interpretation.")
    if body.effective.mode != ("range" if body.kind == "edit" else "instant"):
        raise ValueError("Quick tracking requires its original explicit time.")
    if body.kind not in ({"stop"} if result["action"] == "stop" else {"start", "switch", "edit"}):
        raise ValueError("Quick tracking action does not match its interpretation.")
    if body.task_type_id != result["task_type_id"] or body.effective.end is not None:
        raise ValueError("Quick tracking selection does not match its interpretation.")
    if body.kind == "edit" and result["timing"] != "extract":
        raise ValueError("Start correction requires explicit timing.")
    return attempt


def receipt(db, body, before, after, attempt):
    """Called before the journal transaction commits, including stopped/removed ranges."""
    source = str(body.operation_id)
    old = before.current
    if body.kind == "stop":
        old = next((r for r in before.records if instant(r.start_at) < body.effective.at and (r.end_at is None or instant(r.end_at) >= body.effective.at)), None) or old
    old = old or next((r for r in before.records if instant(r.end_at or before.server_at) > body.effective.at), None)
    if body.kind == "stop" and old:
        source = before.provenance.get(old.id, f"baseline:{old.id}")
    if body.kind == "edit":
        source = body.target_source
    record = next((r for r in after.records if after.provenance.get(r.id) == source), None)
    if record is None and body.kind != "stop":
        # A newer device operation can supersede this one before its first delivery.
        # Preserve this operation's admitted selection, never another activity's identity.
        part = db.get(ActivityOperation, str(body.operation_id)).intent["ranges"][-1]
        data = part["data"]
        record = ActualBlockRead.model_validate({**data, "start_at": part["start"], "end_at": part["end"],
            "task_type": db.get(TaskType, data["task_type_id"])})
    record = record or old
    if record is None:
        raise ValueError("No affected activity could be found.")
    saved = record.model_dump(mode="json")
    saved.pop("note", None)
    result = attempt.context_inputs["result"]
    db.add(AssistantTrackingReview(run_id=attempt.run_id, operation_id=str(body.operation_id), receipt={
        "action": "correct_start" if body.kind == "edit" else body.kind, "question": attempt.question,
        "conversation_id": attempt.conversation_id, "source": result["source"],
        "recorded_at": dt.datetime.now(dt.UTC).isoformat(), "record_source": source,
        "record": saved, "timezone": after.reporting_timezone, "effective_at": result["effective_at"],
    }))
    attempt.tracking_result = {"status": "recorded", "operation_id": str(body.operation_id)}
