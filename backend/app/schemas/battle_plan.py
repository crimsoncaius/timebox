from __future__ import annotations

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.models.battle_plan import (
    PriorityLevel, RecurrenceFrequency, RecurrenceMode, RecurrenceStatus, TaskStatus,
)
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


class TaskOccurrenceIdentityRead(BaseModel):
    """Stable identity for one Task Occurrence within a Recurring Task Series."""

    id: int
    recurring_task_series_id: int
    occurrence_key: str


class SubtaskRead(BaseModel):
    """Definitive Subtask contract, intentionally free of Task lifecycle state."""

    id: int
    parent_task_id: int
    title: str
    checked: bool
    effectively_resolved: bool
    position: int
    created_at: datetime
    updated_at: datetime


class TaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True, use_enum_values=True)

    id: int
    parent_id: int | None
    parent_title: str | None = None
    project_id: int | None
    project: ProjectRead | None = None
    task_type_id: int | None
    task_type: TaskTypeRead | None = None
    recurring_template_id: int | None = None
    recurring_template_title: str | None = None
    occurrence_key: str | None = None
    recurrence_kind: str | None = None
    quota_period_start: date | None = None
    quota_period_end: date | None = None
    expected_sessions: int | None = None
    session_index: int | None = None
    quota_completed: int | None = None
    title: str
    description: str
    ready_to_plan: bool
    is_blocked: bool
    blocking_reason: str | None
    status: TaskStatus
    completed_at: datetime | None
    version: int
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
    planned_dates: list[date] = Field(default_factory=list)
    occurrence: TaskOccurrenceIdentityRead | None = None
    subtasks: list[SubtaskRead] = Field(default_factory=list)
    # Quota Session Tasks are independently completable Tasks, not Subtasks.
    session_tasks: list["TaskRead"] = Field(default_factory=list)


class TaskListRead(BaseModel):
    items: list[TaskRead]
    timezone: str
    server_now_iso: str


class TaskCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=500)
    description: str = ""
    ready_to_plan: bool = False
    is_blocked: bool = False
    blocking_reason: str | None = Field(None, max_length=1000)
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
    ready_to_plan: bool | None = None
    is_blocked: bool | None = None
    blocking_reason: str | None = Field(None, max_length=1000)
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


class TaskCompletionCreate(BaseModel):
    planned_time: Literal["keep", "remove"]


class TaskCompletionRead(BaseModel):
    task: TaskRead
    undo_token: str
    removed_planned_block_ids: list[int]


class TaskCompletionUndo(BaseModel):
    undo_token: str = Field(..., min_length=1)


class ReminderRead(BaseModel):
    id: int
    title: str
    deadline_date: date | None
    deadline_at: datetime | None
    reminder_at: datetime


class RecurrenceRuleFields(BaseModel):
    mode: RecurrenceMode
    frequency: RecurrenceFrequency
    interval: int = Field(1, ge=1, le=365)
    weekdays: list[int] = Field(default_factory=list)
    month_day: int | None = Field(None, ge=1, le=31)
    quota_count: int | None = Field(None, ge=1, le=100)
    start_date: date
    end_date: date | None = None
    cycle_limit: int | None = Field(None, ge=1, le=10000)

    @model_validator(mode="after")
    def validate_rule(self):
        if self.end_date is not None and self.end_date < self.start_date:
            raise ValueError("End date cannot be before the start date")
        if self.end_date is not None and self.cycle_limit is not None:
            raise ValueError("Use either an end date or cycle limit, not both")
        if self.mode == RecurrenceMode.quota:
            if self.interval != 1:
                raise ValueError("Quota intervals are always one calendar period")
            if self.quota_count is None:
                raise ValueError("Quota count is required")
        elif self.quota_count is not None:
            raise ValueError("Quota count is only valid for quota templates")
        if self.frequency == RecurrenceFrequency.weekly and self.mode == RecurrenceMode.scheduled:
            if not self.weekdays or any(day < 0 or day > 6 for day in self.weekdays):
                raise ValueError("Weekly schedules require weekdays from 0 (Monday) to 6 (Sunday)")
        elif self.weekdays:
            raise ValueError("Weekdays are only valid for weekly scheduled recurrence")
        if self.frequency == RecurrenceFrequency.monthly and self.mode == RecurrenceMode.scheduled:
            if self.month_day is None:
                raise ValueError("Monthly schedules require a month day")
        elif self.month_day is not None:
            raise ValueError("Month day is only valid for monthly scheduled recurrence")
        return self


class RecurringTemplateCreate(RecurrenceRuleFields):
    title: str = Field(..., min_length=1, max_length=500)
    description: str = ""
    project_id: int | None = None
    task_type_id: int | None = None
    urgency: PriorityLevel | None = None
    importance: PriorityLevel | None = None
    checklist_titles: list[str] = Field(default_factory=list)
    confirm_backfill: bool = False


class RecurringTemplatePatch(BaseModel):
    title: str | None = Field(None, min_length=1, max_length=500)
    description: str | None = None
    project_id: int | None = None
    task_type_id: int | None = None
    urgency: PriorityLevel | None = None
    importance: PriorityLevel | None = None
    frequency: RecurrenceFrequency | None = None
    interval: int | None = Field(None, ge=1, le=365)
    weekdays: list[int] | None = None
    month_day: int | None = Field(None, ge=1, le=31)
    quota_count: int | None = Field(None, ge=1, le=100)
    start_date: date | None = None
    end_date: date | None = None
    cycle_limit: int | None = Field(None, ge=1, le=10000)
    checklist_titles: list[str] | None = None
    confirm_backfill: bool = False


class RecurrencePreviewRequest(RecurrenceRuleFields):
    pass


class RecurrenceWindow(BaseModel):
    key: str
    start: date
    end: date


class RecurrencePreviewRead(BaseModel):
    upcoming: list[RecurrenceWindow]
    past_cycles: int
    past_tasks: int


class RecurringChecklistRead(BaseModel):
    id: int
    title: str
    position: int


class RecurringTaskLink(BaseModel):
    id: int
    title: str
    deadline_date: date | None
    overdue: bool


class RecurringTemplateRead(BaseModel):
    model_config = ConfigDict(from_attributes=True, use_enum_values=True)

    id: int
    title: str
    description: str
    project_id: int | None
    project: ProjectRead | None = None
    task_type_id: int | None
    task_type: TaskTypeRead | None = None
    mode: RecurrenceMode
    status: RecurrenceStatus
    frequency: RecurrenceFrequency
    interval: int
    weekdays: list[int]
    month_day: int | None
    quota_count: int | None
    start_date: date
    end_date: date | None
    cycle_limit: int | None
    urgency: PriorityLevel | None
    importance: PriorityLevel | None
    paused_at: datetime | None
    ended_at: datetime | None
    created_at: datetime
    updated_at: datetime
    checklist_items: list[RecurringChecklistRead] = Field(default_factory=list)
    upcoming: list[RecurrenceWindow] = Field(default_factory=list)
    current_tasks: list[RecurringTaskLink] = Field(default_factory=list)
    cadence: str
    next_occurrence: date | None = None
