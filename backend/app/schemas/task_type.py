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


class TaskTypeCreate(BaseModel):
    name: str = Field(..., min_length=1)


class TaskTypePatch(BaseModel):
    name: str | None = Field(None, min_length=1)
