from __future__ import annotations

import datetime as dt
import enum

from sqlalchemy import Boolean, Date, DateTime, Enum, ForeignKey, Integer, Text, func
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
    title: Mapped[str] = mapped_column(Text, nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="", server_default="")
    ready_to_plan: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    status: Mapped[TaskStatus] = mapped_column(
        Enum(TaskStatus, name="task_status", native_enum=False, length=24),
        nullable=False,
        default=TaskStatus.open,
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
