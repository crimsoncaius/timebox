"""Durable capture is separate from the bounded, acknowledged model context."""

import datetime as dt

from fastapi import HTTPException
from langchain_core.messages import AIMessage, HumanMessage
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.assistant import AssistantAttempt, AssistantConversation


class CaptureError(Exception):
    pass


def create(key, capabilities):
    try:
        with Session(get_engine()) as db:
            db.add(AssistantConversation(id=key, capabilities=capabilities))
            db.commit()
    except SQLAlchemyError:
        raise HTTPException(503, "Conversation could not be saved. Please retry.") from None


def load(key):
    try:
        with Session(get_engine()) as db:
            row = db.get(AssistantConversation, key)
            if row is None or row.closed_at is not None:
                raise HTTPException(410, "Conversation closed. Start a new conversation.")
            attempts = list(db.scalars(select(AssistantAttempt).where(
                AssistantAttempt.conversation_id == key,
                AssistantAttempt.status == "completed",
                AssistantAttempt.acknowledged.is_(True),
            ).order_by(AssistantAttempt.id.desc()).limit(20)))
            messages, snapshots = [], {}
            for attempt in reversed(attempts):
                answer = attempt.answer
                if attempt.displayed_plan:
                    plan = attempt.displayed_plan
                    snapshots[plan["snapshot_id"]] = plan
                    answer += "\n[Displayed plan snapshot: " + plan["snapshot_id"] + "]"
                snapshots.update(attempt.snapshots)
                messages.extend([HumanMessage(attempt.question), AIMessage(answer)])
            return row.capabilities, messages, snapshots
    except SQLAlchemyError:
        raise HTTPException(503, "Conversation could not be loaded. Please retry.") from None


def begin(key, run_id, question, model):
    try:
        with Session(get_engine()) as db:
            db.add(AssistantAttempt(conversation_id=key, run_id=run_id, question=question, model=model))
            db.commit()
    except IntegrityError:
        raise HTTPException(409, "This response attempt already exists. Please retry as a new attempt.") from None
    except SQLAlchemyError:
        raise HTTPException(503, "Your message could not be saved. Please retry.") from None


def capture(run_id, answer, reads, card, status="running", error=None):
    try:
        with Session(get_engine()) as db:
            db.execute(update(AssistantAttempt).where(AssistantAttempt.run_id == run_id).values(
                answer=answer, snapshots=reads, displayed_plan=card, status=status, error=error,
            ))
            db.commit()
    except SQLAlchemyError:
        raise CaptureError("The response could not be saved. Please retry.") from None


def acknowledge(key, run_id):
    try:
        with Session(get_engine()) as db:
            db.execute(update(AssistantAttempt).where(
                AssistantAttempt.conversation_id == key,
                AssistantAttempt.run_id == run_id,
                AssistantAttempt.status == "completed",
            ).values(acknowledged=True))
            db.commit()
    except SQLAlchemyError:
        raise HTTPException(503, "Response receipt could not be saved. Please retry.") from None


def stop(key, run_id):
    try:
        with Session(get_engine()) as db:
            db.execute(update(AssistantAttempt).where(
                AssistantAttempt.conversation_id == key,
                AssistantAttempt.run_id == run_id,
                AssistantAttempt.acknowledged.is_(False),
            ).values(status="stopped"))
            db.commit()
    except SQLAlchemyError:
        raise HTTPException(503, "The stopped status could not be saved. Please retry.") from None


def close(key):
    try:
        with Session(get_engine()) as db:
            db.execute(update(AssistantConversation).where(
                AssistantConversation.id == key, AssistantConversation.closed_at.is_(None),
            ).values(closed_at=dt.datetime.now(dt.UTC)))
            db.commit()
    except SQLAlchemyError:
        raise HTTPException(503, "Conversation could not be closed. Please retry.") from None


def recover_interrupted():
    """Single-worker startup: preserve captured output, never resume generation."""
    with Session(get_engine()) as db:
        db.execute(update(AssistantAttempt).where(AssistantAttempt.status == "running").values(
            status="interrupted", error="The backend restarted before this response finished.",
        ))
        db.commit()
