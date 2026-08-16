from __future__ import annotations

from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.models.battle_plan import PriorityLevel, TaskStatus
from app.schemas.task_type import TaskTypeRead


class DeadlineFields(BaseModel):
    deadline_date: date | None = None
    deadline_at: datetime | None = None

    @model_validator(mode="after")
    def one_deadline_kind(self):
        if self.deadline_date is not None and self.deadline_at is not None:
            raise ValueError("Use either deadline_date or deadline_at, not both")
        return self


class ProjectRead(DeadlineFields):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    description: str
    created_at: datetime
    updated_at: datetime


class ProjectCreate(DeadlineFields):
    name: str = Field(..., min_length=1, max_length=200)
    description: str = ""


class ProjectPatch(BaseModel):
    name: str | None = Field(None, min_length=1, max_length=200)
    description: str | None = None
    deadline_date: date | None = None
    deadline_at: datetime | None = None


class TaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True, use_enum_values=True)

    id: int
    parent_id: int | None
    project_id: int | None
    project: ProjectRead | None = None
    task_type_id: int | None
    task_type: TaskTypeRead | None = None
    title: str
    description: str
    status: TaskStatus
    urgency: PriorityLevel | None
    importance: PriorityLevel | None
    deadline_date: date | None
    deadline_at: datetime | None
    reminder_at: datetime | None
    reminder_delivered_at: datetime | None
    position: int
    archived_at: datetime | None
    deleted_at: datetime | None
    created_at: datetime
    updated_at: datetime
    overdue: bool = False
    subtasks: list["TaskRead"] = Field(default_factory=list)


class TaskListRead(BaseModel):
    items: list[TaskRead]
    timezone: str
    server_now_iso: str


class TaskCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=500)
    description: str = ""
    status: TaskStatus = TaskStatus.open
    project_id: int | None = None
    parent_id: int | None = None
    task_type_id: int | None = None
    urgency: PriorityLevel | None = None
    importance: PriorityLevel | None = None
    deadline_date: date | None = None
    deadline_at: datetime | None = None
    reminder_at: datetime | None = None

    @model_validator(mode="after")
    def validate_deadline(self):
        if self.deadline_date is not None and self.deadline_at is not None:
            raise ValueError("Use either deadline_date or deadline_at, not both")
        return self


class TaskPatch(BaseModel):
    title: str | None = Field(None, min_length=1, max_length=500)
    description: str | None = None
    status: TaskStatus | None = None
    project_id: int | None = None
    task_type_id: int | None = None
    urgency: PriorityLevel | None = None
    importance: PriorityLevel | None = None
    deadline_date: date | None = None
    deadline_at: datetime | None = None
    reminder_at: datetime | None = None


class TaskPlacement(BaseModel):
    task_id: int
    status: TaskStatus
    position: int = Field(..., ge=0)


class TaskReorder(BaseModel):
    placements: list[TaskPlacement]


class TaskIds(BaseModel):
    task_ids: list[int]


class ReminderRead(BaseModel):
    id: int
    title: str
    deadline_date: date | None
    deadline_at: datetime | None
    reminder_at: datetime
