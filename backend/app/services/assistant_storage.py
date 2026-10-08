"""Durable capture is separate from the bounded, acknowledged model context."""

import datetime as dt
import json

from fastapi import HTTPException
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.assistant import AssistantAttempt, AssistantConversation
from app.services.assistant_limits import MAX_CONTEXT_EXCHANGES
from app.services.assistant_presentation import text_schedule
from app.services.assistant_tracking import replay_proposal


class CaptureError(Exception):
    pass


def write_session():
    from app.services.assistant_task_operations import transaction
    return transaction(engine=get_engine())


def create(key, capabilities, previous_conversation_id=None):
    try:
        with write_session() as db:
            if previous_conversation_id:
                from app.services.assistant_task_operations import close_pending
                previous = db.get(AssistantConversation, previous_conversation_id, with_for_update=True)
                if previous is None:
                    raise HTTPException(404, "Previous conversation not found")
                previous.closed_at = previous.closed_at or dt.datetime.now(dt.UTC)
                close_pending(db, previous_conversation_id)
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
            ).order_by(AssistantAttempt.id.desc()).limit(MAX_CONTEXT_EXCHANGES)))
            messages, snapshots = [], {}
            for attempt in reversed(attempts):
                snapshots.update(attempt.snapshots)
                for card in displayed_cards(attempt.displayed_plan):
                    snapshots.setdefault(card["snapshot_id"], card)
            for attempt in reversed(attempts):
                messages.extend(replay(attempt, snapshots))
            return row.capabilities, messages, snapshots
    except SQLAlchemyError:
        raise HTTPException(503, "Conversation could not be loaded. Please retry.") from None


def displayed_cards(value):
    return value if isinstance(value, list) else [value] if value else []


def replay(attempt, snapshots=None):
    """An exchange as the model produced it: its tool call, if any, then the selector-prefixed answer.

    History is the model's strongest example, so it must show the calls and format the prompt asks for.
    """
    messages = [HumanMessage(attempt.question)]
    call_id = "replay-" + attempt.run_id
    for index, plan in enumerate(attempt.snapshots.values()):
        if snapshots is not None and plan["snapshot_id"] not in snapshots:
            continue
        read_id = f"{call_id}-{index}"
        args = {}
        name = "read_today_plan"
        if plan.get("schema_version") == 4:
            name = plan["source"]["tool"]
            args = plan["source"]["normalized_arguments"]
        elif plan.get("schema_version", 1) > 1:
            name = "read_activity"
            args = {"lane": plan["lane"], "group_by": plan["group_by"], "when": plan["date"]}
            if plan["schema_version"] == 3:
                args.update(when={"start": plan["start"], "end": plan["end"], "weekdays": plan["weekdays"]}, detail=plan["detail"])
            if plan["task_type"]:
                args["task_type"] = plan["task_type"]
        # The rows reach the model once, through the Historical snapshots context.
        messages += [AIMessage("", tool_calls=[{"id": read_id, "name": name, "args": args}]),
                     ToolMessage(json.dumps({"snapshot_id": plan["snapshot_id"], "read_at": plan["read_at"],
                                             "rows": "in Historical snapshots"}), tool_call_id=read_id)]
    if attempt.tracking_proposal:
        messages += replay_proposal(attempt.tracking_proposal, call_id)
    keys = [card["snapshot_id"] for card in displayed_cards(attempt.displayed_plan)
            if snapshots is None or card["snapshot_id"] in snapshots]
    selector = ({"presentation": "snapshot", "snapshot_id": keys[0]} if len(keys) == 1 else
                {"presentation": "snapshots", "snapshot_ids": keys} if keys else {"presentation": "none"})
    header = json.dumps(selector, separators=(",", ":"))
    answer = attempt.answer
    # Legacy clients receive card rows as text. Do not smuggle those rows back
    # into context through the answer after the corresponding read is evicted.
    for card in displayed_cards(attempt.displayed_plan):
        if card.get("schema_version") != 4:
            answer = answer.removeprefix(text_schedule(card))
    messages.append(AIMessage(header + "\n" + answer if answer else header))
    return messages


def begin(key, run_id, question, model):
    try:
        with write_session() as db:
            db.add(AssistantAttempt(conversation_id=key, run_id=run_id, question=question, model=model))
            db.commit()
    except IntegrityError:
        raise HTTPException(409, "This response attempt already exists. Please retry as a new attempt.") from None
    except SQLAlchemyError:
        raise HTTPException(503, "Your message could not be saved. Please retry.") from None


def capture(run_id, answer, reads, card, status="running", error=None, proposal=None):
    cards = displayed_cards(card)
    card = cards[0] if len(cards) == 1 else cards or None
    try:
        with write_session() as db:
            db.execute(update(AssistantAttempt).where(AssistantAttempt.run_id == run_id).values(
                answer=answer, snapshots=reads, displayed_plan=card, tracking_proposal=proposal, status=status, error=error,
            ))
            from app.services.assistant_task_operations import finish_source
            finish_source(db, run_id, status)
            db.commit()
    except (SQLAlchemyError, TimeoutError):
        raise CaptureError("The response could not be saved. Please retry.") from None


def capture_inputs(run_id, inputs):
    try:
        with write_session() as db:
            db.execute(update(AssistantAttempt).where(AssistantAttempt.run_id == run_id).values(context_inputs=inputs))
            db.commit()
    except SQLAlchemyError:
        raise CaptureError("The response context could not be saved. Please retry.") from None


def acknowledge(key, run_id):
    try:
        with write_session() as db:
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
        with write_session() as db:
            db.execute(update(AssistantAttempt).where(
                AssistantAttempt.conversation_id == key,
                AssistantAttempt.run_id == run_id,
                AssistantAttempt.acknowledged.is_(False),
            ).values(status="stopped"))
            from app.services.assistant_task_operations import finish_source
            finish_source(db, run_id, "stopped")
            db.commit()
    except SQLAlchemyError:
        raise HTTPException(503, "The stopped status could not be saved. Please retry.") from None


def close(key):
    try:
        with write_session() as db:
            db.execute(update(AssistantConversation).where(
                AssistantConversation.id == key, AssistantConversation.closed_at.is_(None),
            ).values(closed_at=dt.datetime.now(dt.UTC)))
            from app.services.assistant_task_operations import close_pending
            close_pending(db, key)
            db.commit()
    except SQLAlchemyError:
        raise HTTPException(503, "Conversation could not be closed. Please retry.") from None


def recover_interrupted():
    """Single-worker startup: preserve captured output, never resume generation."""
    with write_session() as db:
        from app.models.assistant import AssistantTaskProposal
        from app.services.assistant_task_operations import transition
        for proposal in db.scalars(select(AssistantTaskProposal).where(AssistantTaskProposal.status == "draft").with_for_update()):
            transition(proposal, "invalid")
        db.execute(update(AssistantAttempt).where(AssistantAttempt.status == "running").values(
            status="interrupted", error="The backend restarted before this response finished.",
        ))
        db.commit()
