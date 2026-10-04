from __future__ import annotations

import datetime as dt

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, Index, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class AssistantConversation(Base):
    __tablename__ = "assistant_conversations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    capabilities: Mapped[list] = mapped_column(JSON, nullable=False)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    closed_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True))


class AssistantAttempt(Base):
    __tablename__ = "assistant_attempts"
    __table_args__ = (Index("ix_assistant_attempts_conversation_id_id", "conversation_id", "id"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    run_id: Mapped[str] = mapped_column(String(36), unique=True)
    conversation_id: Mapped[str] = mapped_column(ForeignKey("assistant_conversations.id"), nullable=False)
    question: Mapped[str] = mapped_column(Text, nullable=False)
    answer: Mapped[str] = mapped_column(Text, nullable=False, default="")
    status: Mapped[str] = mapped_column(String(24), nullable=False, default="running")
    acknowledged: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    model: Mapped[str] = mapped_column(Text, nullable=False)
    snapshots: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    # Historical single-card records are dictionaries; multi-card records keep ordered lists.
    displayed_plan: Mapped[dict | list | None] = mapped_column(JSON, nullable=True)
    tracking_proposal: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    error: Mapped[str | None] = mapped_column(Text)
    context_inputs: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())


class AssistantQuery(Base):
    __tablename__ = "assistant_queries"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    conversation_id: Mapped[str] = mapped_column(ForeignKey("assistant_conversations.id"), nullable=False, index=True)
    evidence: Mapped[dict] = mapped_column(JSON, nullable=False)


class AssistantQueryCursor(Base):
    __tablename__ = "assistant_query_cursors"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    query_id: Mapped[str] = mapped_column(ForeignKey("assistant_queries.id"), nullable=False)
    offset: Mapped[int] = mapped_column(Integer, nullable=False)


class AssistantTaskProposal(Base):
    __tablename__ = "assistant_task_proposals"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    operation_id: Mapped[str] = mapped_column(String(36), unique=True, nullable=False)
    conversation_id: Mapped[str] = mapped_column(ForeignKey("assistant_conversations.id"), index=True)
    run_id: Mapped[str] = mapped_column(ForeignKey("assistant_attempts.run_id"), index=True)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    status: Mapped[str] = mapped_column(String(24), nullable=False, default="draft")
    review: Mapped[dict] = mapped_column(JSON, nullable=False)
    guards: Mapped[dict] = mapped_column(JSON, nullable=False)
    source_completed_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True))
    receipt: Mapped[dict | None] = mapped_column(JSON)
    undo_operation_id: Mapped[str | None] = mapped_column(String(36), unique=True)
    undo_token: Mapped[str | None] = mapped_column(Text)
    undo_receipt: Mapped[dict | None] = mapped_column(JSON)
    events: Mapped[list] = mapped_column(JSON, nullable=False, default=list)


class AssistantTaskSubmission(Base):
    __tablename__ = "assistant_task_submissions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    proposal_id: Mapped[str] = mapped_column(ForeignKey("assistant_task_proposals.id"), index=True)
    operation_id: Mapped[str] = mapped_column(String(36), nullable=False)
    state: Mapped[str] = mapped_column(String(24), nullable=False)
    reason: Mapped[str | None] = mapped_column(String(80))
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), nullable=False)
