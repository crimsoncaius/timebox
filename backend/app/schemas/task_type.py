from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class TaskTypeRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    created_at: datetime
    updated_at: datetime


class TaskTypeListItem(TaskTypeRead):
    """List row with the number of time blocks referencing this type."""

    usage_count: int = 0
    task_usage_count: int = 0
    active_task_usage_count: int = 0
    archived_task_usage_count: int = 0
    trashed_task_usage_count: int = 0
    recurring_template_usage_count: int = 0


class TaskTypeCreate(BaseModel):
    name: str = Field(..., min_length=1)


class TaskTypePatch(BaseModel):
    name: str | None = Field(None, min_length=1)

class TaskTypeMergeRequest(BaseModel):
    target_id: int
    preview_token: str | None = None


class TaskTypeMergeChange(BaseModel):
    source_id: int
    source_name: str
    target_name: str
    action: str


class TaskTypeMergePreview(BaseModel):
    source_id: int
    source_name: str
    target_id: int
    target_name: str
    preview_token: str
    changes: list[TaskTypeMergeChange]
    task_count: int
    completed_task_count: int
    archived_task_count: int
    trashed_task_count: int
    planned_block_count: int
    actual_block_count: int
    recurring_series_count: int
