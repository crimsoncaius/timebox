from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.time_block import BlockLane
from app.schemas.task_type import TaskTypeRead


class LinkedTaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True, use_enum_values=True)

    id: int
    title: str
    status: str
    task_type_id: int | None
    archived_at: datetime | None
    deleted_at: datetime | None


class TimeBlockRead(BaseModel):
    model_config = ConfigDict(from_attributes=True, use_enum_values=True)

    id: int
    lane: BlockLane
    task_type_id: int
    task_type: TaskTypeRead
    task_id: int | None = None
    task: LinkedTaskRead | None = None
    note: str | None = None
    planned_block_id: int | None = None
    start_minute: int = Field(..., ge=0, le=1440)
    end_minute: int = Field(..., ge=0, le=1440)
    created_at: datetime
    updated_at: datetime


class TimeBlockCreate(BaseModel):
    lane: BlockLane
    task_type_id: int
    task_id: int | None = None
    note: str | None = None
    start_minute: int = Field(..., ge=0, le=1440)
    end_minute: int = Field(..., ge=0, le=1440)


class TimeBlockPatch(BaseModel):
    task_type_id: int | None = None
    task_id: int | None = None
    note: str | None = None
    start_minute: int | None = Field(None, ge=0, le=1440)
    end_minute: int | None = Field(None, ge=0, le=1440)
