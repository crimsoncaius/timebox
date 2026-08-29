from __future__ import annotations

from datetime import date, datetime

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
    actual_block_id: int | None = None
    start_minute: int | None = Field(None, ge=0, le=1440)
    end_minute: int | None = Field(None, ge=0, le=1440)
    start_at: datetime | None = None
    end_at: datetime | None = None
    created_at: datetime
    updated_at: datetime


class PlannedBlockRead(BaseModel):
    model_config = ConfigDict(from_attributes=True, use_enum_values=True)

    id: int
    day_id: int
    task_type_id: int
    task_id: int | None = None
    note: str | None = None
    start_minute: int = Field(..., ge=0, le=1440)
    end_minute: int = Field(..., ge=0, le=1440)
    actual_block_id: int | None = None
    created_at: datetime
    updated_at: datetime


class ActualBlockRead(BaseModel):
    model_config = ConfigDict(from_attributes=True, use_enum_values=True)

    id: int
    task_type_id: int
    task_type: TaskTypeRead
    task_id: int | None = None
    task: LinkedTaskRead | None = None
    note: str | None = None
    planned_block_id: int | None = None
    start_at: datetime
    end_at: datetime | None
    created_at: datetime
    updated_at: datetime


class ActualBlockDayProjectionRead(BaseModel):
    """One Day's view of an authoritative Actual Block; never a stored split."""

    actual_block: ActualBlockRead
    date: date
    start_minute: int = Field(..., ge=0, le=1440)
    end_minute: int = Field(..., ge=0, le=1440)
    duration_minutes: int = Field(..., ge=0, le=1440)


class ActualBlockDayRead(BaseModel):
    date: date
    actual_blocks: list[ActualBlockDayProjectionRead]
    actual_minutes: int


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
