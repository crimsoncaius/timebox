from __future__ import annotations

import datetime as dt
import enum

from sqlalchemy import Boolean, Date, DateTime, Enum, ForeignKey, Integer, Text, UniqueConstraint, func
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


class Project(Base):
    __tablename__ = "projects"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="", server_default="")
    deadline_date: Mapped[dt.date | None] = mapped_column(Date, nullable=True)
    deadline_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    tasks: Mapped[list["Task"]] = relationship(
        "Task", back_populates="project", cascade="all, delete"
    )
    recurring_templates: Mapped[list["RecurringTemplate"]] = relationship(
        "RecurringTemplate", back_populates="project"
    )


class RecurringTemplate(Base):
    __tablename__ = "recurring_templates"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    project_id: Mapped[int | None] = mapped_column(
        ForeignKey("projects.id", ondelete="SET NULL"), nullable=True, index=True
    )
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

    project: Mapped[Project | None] = relationship("Project", back_populates="recurring_templates")
    task_type: Mapped["TaskType | None"] = relationship("TaskType", back_populates="recurring_templates")
    checklist_items: Mapped[list["RecurringChecklistItem"]] = relationship(
        "RecurringChecklistItem", back_populates="template", cascade="all, delete-orphan",
        order_by="RecurringChecklistItem.position",
    )
    occurrences: Mapped[list["RecurrenceOccurrence"]] = relationship(
        "RecurrenceOccurrence", back_populates="template", cascade="all, delete-orphan"
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


class RecurrenceOccurrence(Base):
    __tablename__ = "recurrence_occurrences"
    __table_args__ = (UniqueConstraint("template_id", "occurrence_key", name="uq_recurrence_occurrence_key"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    template_id: Mapped[int] = mapped_column(
        ForeignKey("recurring_templates.id", ondelete="CASCADE"), nullable=False, index=True
    )
    occurrence_key: Mapped[str] = mapped_column(Text, nullable=False)
    cycle_start: Mapped[dt.date] = mapped_column(Date, nullable=False)
    cycle_end: Mapped[dt.date] = mapped_column(Date, nullable=False)
    task_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True, index=True
    )
    suppressed: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False, server_default="false")
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    template: Mapped[RecurringTemplate] = relationship("RecurringTemplate", back_populates="occurrences")
    task: Mapped["Task | None"] = relationship("Task", foreign_keys=[task_id])


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


class TaskCompletionOperation(Base):
    __tablename__ = "task_completion_operations"

    token: Mapped[str] = mapped_column(Text, primary_key=True)
    root_task_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True, index=True
    )
    snapshot_json: Mapped[str] = mapped_column(Text, nullable=False)
    undone_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
