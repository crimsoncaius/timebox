from __future__ import annotations

import asyncio
import json
import time
from contextlib import suppress
from uuid import UUID

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, HumanMessage
from opentelemetry import trace
from pydantic import BaseModel, Field, field_validator

from app.core.config import get_settings
from app.services.assistant_agent import MODEL, agent_events
from app.services.assistant_sessions import conversations

router = APIRouter(prefix="/assistant", tags=["assistant"])
RESPONSE_TIMEOUT = 120


class MessageRequest(BaseModel):
    run_id: UUID
    message: str = Field(min_length=1, max_length=4000)

    @field_validator("message")
    @classmethod
    def not_blank(cls, value):
        if not value.strip():
            raise ValueError("Enter a message.")
        return value


@router.post("/conversations")
async def create():
    return {"conversation_id": conversations.create()}


@router.delete("/conversations/{conversation_id}", status_code=204)
async def delete(conversation_id: str):
    conversations.delete(conversation_id)


@router.post("/conversations/{conversation_id}/runs/{run_id}/stop", status_code=204)
async def stop(conversation_id: str, run_id: str):
    conversations.stop(conversation_id, run_id)


@router.post("/conversations/{conversation_id}/runs/{run_id}/ack", status_code=204)
async def acknowledge(conversation_id: str, run_id: str):
    conversations.acknowledge(conversation_id, run_id)


def public_error(error):
    status = getattr(error, "status_code", None)
    if status == 401 or status == 403:
        return "OpenRouter authentication failed. Check the backend API key."
    if status == 402:
        return "OpenRouter credits are exhausted. Add credits before retrying."
    if status == 429:
        return "OpenRouter is rate limited. Wait before retrying."
    if isinstance(error, TimeoutError):
        return "The response exceeded two minutes. Please retry."
    return "The response was interrupted. The model or today's plan could not be read. Please retry."


@router.post("/conversations/{conversation_id}/messages")
async def send(conversation_id: str, body: MessageRequest):
    if not get_settings().openrouter_api_key:
        raise HTTPException(503, "Set OPENROUTER_API_KEY on the backend to use Assistant.")
    run_id = str(body.run_id)
    conversation = conversations.reserve(conversation_id, run_id)
    queue = asyncio.Queue()

    async def produce():
        output = ""
        started = time.monotonic()
        with trace.get_tracer(__name__).start_as_current_span("assistant.response", record_exception=False) as span:
            span.set_attributes({"openinference.span.kind": "CHAIN", "session.id": conversation_id,
                                 "assistant.run_id": run_id, "llm.model_name": MODEL,
                                 "input.value": json.dumps([m.model_dump(mode="json") for m in conversation.messages] +
                                                           [{"role": "user", "content": body.message}]),
                                 "input.mime_type": "application/json"})
            trace_id = format(span.get_span_context().trace_id, "032x")
            queue.put_nowait(("started", {"trace_id": trace_id}))
            outcome = "interrupted"
            try:
                async with asyncio.timeout(RESPONSE_TIMEOUT):
                    async for kind, data in agent_events([*conversation.messages, HumanMessage(body.message)]):
                        if kind == "text_delta":
                            if not output:
                                span.set_attribute("assistant.first_text_ms", (time.monotonic() - started) * 1000)
                            output += data["text"]
                        queue.put_nowait((kind, data))
                    if not output.strip():
                        raise RuntimeError("Empty response")
                    # Commit only after Android confirms receiving the terminal event.
                    conversation.pending = (run_id, [HumanMessage(body.message), AIMessage(output)])
                    outcome = "completed"
                    span.set_status(trace.Status(trace.StatusCode.OK))
                    queue.put_nowait(("completed", {}))
            except asyncio.CancelledError:
                outcome = "stopped"
                queue.put_nowait(("stopped", {}))
            except Exception as error:
                message = public_error(error)
                span.set_status(trace.Status(trace.StatusCode.ERROR, message))
                queue.put_nowait(("failed", {"message": message}))
            finally:
                span.set_attributes({"assistant.outcome": outcome, "output.value": output})
                conversation.run_id = None
                conversation.task = None
                conversation.touched = time.monotonic()
                queue.put_nowait(None)

    conversation.task = asyncio.create_task(produce())
    task = conversation.task

    def release_unstarted(done):
        # Cancellation can arrive before the producer enters its try/finally.
        if conversation.task is done:
            conversation.task = None
            conversation.run_id = None
            conversation.touched = time.monotonic()
            queue.put_nowait(("stopped", {}))
            queue.put_nowait(None)

    task.add_done_callback(release_unstarted)

    async def events():
        sequence = 0
        try:
            while True:
                try:
                    item = await asyncio.wait_for(queue.get(), timeout=10)
                except TimeoutError:
                    yield ": keepalive\n\n"
                    continue
                if item is None:
                    break
                kind, data = item
                sequence += 1
                payload = {"run_id": run_id, "sequence": sequence, **data}
                yield f"event: {kind}\ndata: {json.dumps(payload)}\n\n"
        finally:
            if not task.done():
                task.cancel()
            with suppress(asyncio.CancelledError):
                await task

    return StreamingResponse(events(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})
