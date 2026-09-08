from __future__ import annotations

import datetime as dt
import enum

from sqlalchemy import (
    Boolean, Date, DateTime, Enum, ForeignKey, Index, Integer, Text,
    UniqueConstraint, func, text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class TaskStatus(str, enum.Enum):
    open = "open"
    in_progress = "in_progress"
    blocked = "blocked"
    completed = "completed"


class PriorityLevel(str, enum.Enum):
    low = "low"
    medium = "medium"
    high = "high"


class RecurrenceMode(str, enum.Enum):
    scheduled = "scheduled"
    quota = "quota"


class RecurrenceStatus(str, enum.Enum):
    active = "active"
    paused = "paused"
    ended = "ended"


class RecurrenceFrequency(str, enum.Enum):
    daily = "daily"
    weekly = "weekly"
    monthly = "monthly"


class RecurringPlannedBlockState(str, enum.Enum):
    untouched = "untouched"
    customized = "customized"
    deleted = "deleted"


class Project(Base):
    __tablename__ = "projects"

    position: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    tasks: Mapped[list["Task"]] = relationship(
        "Task", back_populates="project", cascade="all, delete"
    )
class RecurringTemplate(Base):
    __tablename__ = "recurring_templates"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    task_type_id: Mapped[int | None] = mapped_column(
        ForeignKey("task_types.id", ondelete="SET NULL"), nullable=True, index=True
    )
    title: Mapped[str] = mapped_column(Text, nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="", server_default="")
    mode: Mapped[RecurrenceMode] = mapped_column(
        Enum(RecurrenceMode, name="recurrence_mode", native_enum=False, length=16), nullable=False
    )
    status: Mapped[RecurrenceStatus] = mapped_column(
        Enum(RecurrenceStatus, name="recurrence_status", native_enum=False, length=16),
        nullable=False, default=RecurrenceStatus.active, server_default="active",
    )
    frequency: Mapped[RecurrenceFrequency] = mapped_column(
        Enum(RecurrenceFrequency, name="recurrence_frequency", native_enum=False, length=16),
        nullable=False,
    )
    interval: Mapped[int] = mapped_column(Integer, nullable=False, default=1, server_default="1")
    weekdays_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]", server_default="[]")
    month_day: Mapped[int | None] = mapped_column(Integer, nullable=True)
    quota_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    start_date: Mapped[dt.date] = mapped_column(Date, nullable=False)
    generation_start_date: Mapped[dt.date] = mapped_column(Date, nullable=False)
    end_date: Mapped[dt.date | None] = mapped_column(Date, nullable=True)
    cycle_limit: Mapped[int | None] = mapped_column(Integer, nullable=True)
    keep_unfinished_overdue: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    position: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    urgency: Mapped[PriorityLevel | None] = mapped_column(
        Enum(PriorityLevel, name="recurring_urgency", native_enum=False, length=16), nullable=True
    )
    importance: Mapped[PriorityLevel | None] = mapped_column(
        Enum(PriorityLevel, name="recurring_importance", native_enum=False, length=16), nullable=True
    )
    paused_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    ended_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    task_type: Mapped["TaskType | None"] = relationship("TaskType", back_populates="recurring_templates")
    checklist_items: Mapped[list["RecurringChecklistItem"]] = relationship(
        "RecurringChecklistItem", back_populates="template", cascade="all, delete-orphan",
        order_by="RecurringChecklistItem.position",
    )
    preplanning_slots: Mapped[list["RecurringPreplanningSlot"]] = relationship(
        "RecurringPreplanningSlot", back_populates="template", cascade="all",
        order_by="RecurringPreplanningSlot.position",
    )
    occurrences: Mapped[list["RecurrenceOccurrence"]] = relationship(
        "RecurrenceOccurrence", back_populates="template", passive_deletes=True
    )


class RecurringChecklistItem(Base):
    __tablename__ = "recurring_checklist_items"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    template_id: Mapped[int] = mapped_column(
        ForeignKey("recurring_templates.id", ondelete="CASCADE"), nullable=False, index=True
    )
    title: Mapped[str] = mapped_column(Text, nullable=False)
    position: Mapped[int] = mapped_column(Integer, nullable=False)
    template: Mapped[RecurringTemplate] = relationship("RecurringTemplate", back_populates="checklist_items")


class RecurringPreplanningSlot(Base):
    __tablename__ = "recurring_preplanning_slots"
    __table_args__ = (
        Index(
            "uq_recurring_preplanning_slot_position",
            "template_id",
            "position",
            unique=True,
            postgresql_where=text("removed_at IS NULL"),
            sqlite_where=text("removed_at IS NULL"),
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    slot_key: Mapped[str] = mapped_column(Text, nullable=False, unique=True, index=True)
    template_id: Mapped[int] = mapped_column(
        ForeignKey("recurring_templates.id", ondelete="CASCADE"), nullable=False, index=True
    )
    position: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    weekday: Mapped[int | None] = mapped_column(Integer, nullable=True)
    start_minute: Mapped[int] = mapped_column(Integer, nullable=False)
    end_minute: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
    removed_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    template: Mapped[RecurringTemplate] = relationship(
        "RecurringTemplate", back_populates="preplanning_slots"
    )
    realizations: Mapped[list["RecurringPlannedBlockRealization"]] = relationship(
        "RecurringPlannedBlockRealization", back_populates="slot", passive_deletes=True
    )


class RecurrenceOccurrence(Base):
    __tablename__ = "recurrence_occurrences"
    __table_args__ = (
        UniqueConstraint("template_id", "occurrence_key", name="uq_recurrence_occurrence_key"),
        Index("uq_recurrence_occurrences_task_id", "task_id", unique=True),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    template_id: Mapped[int | None] = mapped_column(
        ForeignKey("recurring_templates.id", ondelete="SET NULL"), nullable=True, index=True
    )
    occurrence_key: Mapped[str] = mapped_column(Text, nullable=False)
    cycle_start: Mapped[dt.date] = mapped_column(Date, nullable=False)
    cycle_end: Mapped[dt.date] = mapped_column(Date, nullable=False)
    task_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True, index=True
    )
    suppressed: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False, server_default="false")
    skipped: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False, server_default="false")
    structurally_protected: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    template: Mapped[RecurringTemplate | None] = relationship("RecurringTemplate", back_populates="occurrences")
    task: Mapped["Task | None"] = relationship("Task", foreign_keys=[task_id])
    preplanning_realizations: Mapped[list["RecurringPlannedBlockRealization"]] = relationship(
        "RecurringPlannedBlockRealization", back_populates="occurrence", cascade="all, delete-orphan"
    )


class RecurringPlannedBlockRealization(Base):
    __tablename__ = "recurring_planned_block_realizations"
    __table_args__ = (
        UniqueConstraint("occurrence_id", "slot_key", name="uq_recurring_planned_block_realization"),
        UniqueConstraint("planned_block_id", name="uq_recurring_planned_block_realization_block"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    occurrence_id: Mapped[int] = mapped_column(
        ForeignKey("recurrence_occurrences.id", ondelete="CASCADE"), nullable=False, index=True
    )
    slot_id: Mapped[int | None] = mapped_column(
        ForeignKey("recurring_preplanning_slots.id", ondelete="SET NULL"), nullable=True, index=True
    )
    slot_key: Mapped[str] = mapped_column(Text, nullable=False, index=True)
    planned_block_id: Mapped[int | None] = mapped_column(
        ForeignKey("time_blocks.id", ondelete="SET NULL"), nullable=True, index=True
    )
    state: Mapped[RecurringPlannedBlockState] = mapped_column(
        Enum(
            RecurringPlannedBlockState,
            name="recurring_planned_block_state",
            native_enum=False,
            length=16,
        ),
        nullable=False,
        default=RecurringPlannedBlockState.untouched,
        server_default="untouched",
    )
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    occurrence: Mapped[RecurrenceOccurrence] = relationship(
        "RecurrenceOccurrence", back_populates="preplanning_realizations"
    )
    slot: Mapped[RecurringPreplanningSlot | None] = relationship(
        "RecurringPreplanningSlot", back_populates="realizations"
    )
    planned_block: Mapped["TimeBlock | None"] = relationship("TimeBlock")


class Task(Base):
    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    parent_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="CASCADE"), nullable=True, index=True
    )
    project_id: Mapped[int | None] = mapped_column(
        ForeignKey("projects.id", ondelete="CASCADE"), nullable=True, index=True
    )
    task_type_id: Mapped[int | None] = mapped_column(
        ForeignKey("task_types.id", ondelete="SET NULL"), nullable=True, index=True
    )
    recurring_template_id: Mapped[int | None] = mapped_column(
        ForeignKey("recurring_templates.id", ondelete="SET NULL"), nullable=True, index=True
    )
    occurrence_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    recurrence_kind: Mapped[str | None] = mapped_column(Text, nullable=True)
    quota_period_start: Mapped[dt.date | None] = mapped_column(Date, nullable=True)
    quota_period_end: Mapped[dt.date | None] = mapped_column(Date, nullable=True)
    expected_sessions: Mapped[int | None] = mapped_column(Integer, nullable=True)
    session_index: Mapped[int | None] = mapped_column(Integer, nullable=True)
    recurrence_overrides_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]", server_default="[]")
    title: Mapped[str] = mapped_column(Text, nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="", server_default="")
    ready_to_plan: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    is_blocked: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    blocking_reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    status: Mapped[TaskStatus] = mapped_column(
        Enum(TaskStatus, name="task_status", native_enum=False, length=24),
        nullable=False,
        default=TaskStatus.open,
    )
    # `checked` is the durable Subtask fact.  It is intentionally independent of
    # Task Completion so completing a Parent Task never has to rewrite checklist
    # history.  The column also exists on Battle Plan Task rows because the
    # coordinated migration keeps their existing identities in this table.
    checked: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    completed_at: Mapped[dt.datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    # Used by conflict-aware Task Completion Undo. SQLAlchemy increments this for
    # ordinary ORM updates; completion operations can snapshot the exact version
    # they produced and reject restoration over newer user intent.
    version: Mapped[int] = mapped_column(
        Integer, nullable=False, default=1, server_default="1"
    )
    last_non_completed_status: Mapped[TaskStatus | None] = mapped_column(
        Enum(TaskStatus, name="last_non_completed_task_status", native_enum=False, length=24),
        nullable=True,
    )
    urgency: Mapped[PriorityLevel | None] = mapped_column(
        Enum(PriorityLevel, name="task_urgency", native_enum=False, length=16), nullable=True
    )
    importance: Mapped[PriorityLevel | None] = mapped_column(
        Enum(PriorityLevel, name="task_importance", native_enum=False, length=16), nullable=True
    )
    deadline_date: Mapped[dt.date | None] = mapped_column(Date, nullable=True)
    deadline_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    reminder_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    reminder_delivered_at: Mapped[dt.datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    position: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    archived_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    deleted_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    parent: Mapped["Task | None"] = relationship(
        "Task", back_populates="subtasks", remote_side="Task.id"
    )
    subtasks: Mapped[list["Task"]] = relationship(
        "Task", back_populates="parent", cascade="all, delete-orphan"
    )
    project: Mapped[Project | None] = relationship("Project", back_populates="tasks")
    task_type: Mapped["TaskType | None"] = relationship("TaskType", back_populates="tasks")
    time_blocks: Mapped[list["TimeBlock"]] = relationship("TimeBlock", back_populates="task")
    recurring_template: Mapped[RecurringTemplate | None] = relationship("RecurringTemplate", foreign_keys=[recurring_template_id])
    occurrence: Mapped["RecurrenceOccurrence | None"] = relationship(
        "RecurrenceOccurrence",
        primaryjoin="Task.id == RecurrenceOccurrence.task_id",
        foreign_keys="RecurrenceOccurrence.task_id",
        uselist=False,
        viewonly=True,
    )

    __mapper_args__ = {"version_id_col": version}


class TaskCompletionOperation(Base):
    __tablename__ = "task_completion_operations"

    token: Mapped[str] = mapped_column(Text, primary_key=True)
    root_task_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True, index=True
    )
    snapshot_json: Mapped[str] = mapped_column(Text, nullable=False)
    completed_task_version: Mapped[int | None] = mapped_column(Integer, nullable=True)
    undone_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
