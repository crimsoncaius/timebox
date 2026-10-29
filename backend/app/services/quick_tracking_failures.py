"""Durable product history, with separately bounded diagnostic retention."""
import asyncio
import datetime as dt
import traceback
from typing import Literal
from uuid import UUID, uuid4

from fastapi import HTTPException
from pydantic import AwareDatetime, BaseModel, ConfigDict, Field
from sqlalchemy import delete, select, update
from sqlalchemy.dialects import postgresql, sqlite
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.assistant import AssistantTrackingReview, QuickTrackingDiagnostic, QuickTrackingFailure


class HistoryEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")
    at: AwareDatetime
    command: str | None = Field(default=None, max_length=4000)
    reason: str = Field(max_length=1000)


class FailureReport(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: UUID
    device_id: UUID
    revision: int = Field(ge=1)
    occurred_at: AwareDatetime
    command: str | None = Field(default=None, max_length=4000)
    status: Literal["failed", "uncertain", "recorded", "information"]
    reason: str = Field(max_length=1000)
    stage: Literal["capture", "interpretation", "recording", "sync"]
    retryable: bool = False
    request_ids: list[UUID] = Field(default_factory=list)
    history: list[HistoryEvent] = Field(default_factory=list)


class Diagnostic(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: UUID
    occurred_at: AwareDatetime
    stage: Literal["capture", "interpretation", "recording", "sync"]
    request_id: UUID | None = None
    exception_type: str = Field(max_length=200)
    # Stack frames only: never exception messages, HTTP bodies, headers or locals.
    frames: list[str] = Field(default_factory=list, max_length=40)
    app_version: str = Field(default="unknown", max_length=80)
    android_version: str = Field(default="unknown", max_length=80)
    error_code: int | None = None
    http_status: int | None = Field(default=None, ge=100, le=599)


def insert(db, model):
    return (postgresql.insert if db.bind.dialect.name == "postgresql" else sqlite.insert)(model)


def cleanup():
    with Session(get_engine()) as db:
        db.execute(delete(QuickTrackingDiagnostic).where(QuickTrackingDiagnostic.expires_at <= dt.datetime.now(dt.UTC)))
        db.commit()


async def retention_loop():
    while True:
        try:
            await asyncio.to_thread(cleanup)
        except Exception:
            # Retention retries independently of user recording and API availability.
            import logging
            logging.getLogger(__name__).exception("Quick Tracking diagnostic retention failed")
        await asyncio.sleep(3600)


def view(db, row):
    data = dict(row.data)
    receipts = list(db.scalars(select(AssistantTrackingReview).where(
        AssistantTrackingReview.run_id.in_(data["request_ids"]))))
    if receipts:
        data.update(status="recorded", reason="Recorded. Open the activity in Day.", retryable=False)
    return {**data, "dismissed": row.dismissed, "review_id": receipts[-1].id if receipts else None,
            "recorded_at": receipts[-1].receipt.get("effective_at") or receipts[-1].receipt["record"].get("start_at") if receipts else None}


def upload(body: FailureReport):
    with Session(get_engine()) as db:
        db.execute(insert(db, QuickTrackingFailure).values(id=str(body.id), device_id=str(body.device_id),
            revision=body.revision, data=body.model_dump(mode="json"), created_at=body.occurred_at, dismissed=False).on_conflict_do_nothing())
        row = db.scalar(select(QuickTrackingFailure).where(QuickTrackingFailure.id == str(body.id)).with_for_update())
        if row and row.device_id != str(body.device_id):
            raise HTTPException(409, "This attempt belongs to another installation.")
        if body.revision > row.revision:
            row.data = body.model_dump(mode="json")
            row.revision = body.revision
        db.flush()
        result = view(db, row)
        db.commit()
        return result


def listing(history=False, before: str | None = None):
    with Session(get_engine()) as db:
        rows = db.scalars(select(QuickTrackingFailure).order_by(QuickTrackingFailure.created_at.desc(), QuickTrackingFailure.id.desc()))
        items = []
        reached = before is None
        for row in rows:
            if not reached:
                reached = row.id == before
                continue
            item = view(db, row)
            resolved = row.dismissed or item["status"] in ("recorded", "information")
            if resolved == history:
                items.append(item)
                if len(items) == 51:
                    break
        return {"items": items[:50], "next_before": items[49]["id"] if len(items) > 50 else None}


def dismiss(attempt_id: UUID):
    with Session(get_engine()) as db:
        result = db.execute(update(QuickTrackingFailure).where(QuickTrackingFailure.id == str(attempt_id)).values(dismissed=True))
        if not result.rowcount:
            raise HTTPException(404, "Attempt unavailable.")
        db.commit()
        return {"dismissed": True}


def diagnostic(attempt_id: UUID, body: Diagnostic):
    expires = min(body.occurred_at, dt.datetime.now(dt.UTC)) + dt.timedelta(days=30)
    with Session(get_engine()) as db:
        if expires > dt.datetime.now(dt.UTC):
            data = body.model_dump(mode="json")
            # Limit individual frames as well as the number of frames.
            data["frames"] = [frame[:300] for frame in body.frames]
            db.execute(insert(db, QuickTrackingDiagnostic).values(id=str(body.id), attempt_id=str(attempt_id), data=data, expires_at=expires).on_conflict_do_nothing())
        db.execute(delete(QuickTrackingDiagnostic).where(QuickTrackingDiagnostic.expires_at <= dt.datetime.now(dt.UTC)))
        db.commit()
    return {"accepted": True}


def get(attempt_id: UUID):
    with Session(get_engine()) as db:
        row = db.get(QuickTrackingFailure, str(attempt_id))
        if not row:
            raise HTTPException(404, "Attempt unavailable.")
        return view(db, row)


def capture_request(body):
    if body.device_id and body.attempt_id:
        report = FailureReport(id=body.attempt_id, device_id=body.device_id, revision=1,
            occurred_at=body.reference_at or dt.datetime.now(dt.UTC), command=body.message,
            status="uncertain", stage="interpretation", reason="Waiting for the originating phone to check recording.",
            request_ids=[body.request_id])
        upload(report)


def server_error(attempt_id: UUID, request_id: UUID, error: Exception):
    while error.__cause__ is not None:
        error = error.__cause__
    diagnostic(attempt_id, Diagnostic(id=uuid4(), occurred_at=dt.datetime.now(dt.UTC), stage="interpretation",
        request_id=request_id, exception_type=type(error).__name__, http_status=getattr(error, "status_code", None),
        frames=[f"{frame.filename.rsplit('/', 1)[-1].rsplit(chr(92), 1)[-1]}:{frame.lineno}:{frame.name}"
                for frame in traceback.extract_tb(error.__traceback__)[-40:]], app_version="backend"))
    with Session(get_engine()) as db:
        row = db.scalar(select(QuickTrackingFailure).where(QuickTrackingFailure.id == str(attempt_id)).with_for_update())
        if row and row.data["status"] == "uncertain" and row.data["stage"] == "interpretation":
            row.data = {**row.data, "status": "failed", "reason": "The server could not interpret this command. Retry on the originating phone.", "retryable": True}
            db.commit()
