from __future__ import annotations

from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.time_block import ActualBlockDayProjectionRead, PlannedBlockRead, TimeBlockRead


class PlanningPlacementCreate(BaseModel):
    date: date
    task_id: int
    task_type_id: int | None = None
    start_minute: int = Field(..., ge=0, le=1440)
    end_minute: int = Field(..., ge=0, le=1440)


class PlanningCommitCreate(BaseModel):
    placements: list[PlanningPlacementCreate] = Field(..., min_length=1)


class DayMeta(BaseModel):
    timezone: str
    today: date
    server_now_iso: str


class DayRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    date: date
    start_hour: int = Field(..., ge=0, le=23)
    end_hour: int = Field(..., ge=0, le=24)
    show_full_day: bool
    created_at: datetime
    updated_at: datetime
    time_blocks: list[TimeBlockRead]
    planned_blocks: list[PlannedBlockRead] = Field(default_factory=list)
    actual_blocks: list[ActualBlockDayProjectionRead] = Field(default_factory=list)
    planned_minutes: int = 0
    actual_minutes: int = 0
    meta: DayMeta


class PlanningCommitRead(BaseModel):
    days: list[DayRead]


class DayPreviewRead(BaseModel):
    """Renderable day data that may describe a date not yet stored."""

    date: date
    start_hour: int = Field(..., ge=0, le=23)
    end_hour: int = Field(..., ge=0, le=24)
    show_full_day: bool
    time_blocks: list[TimeBlockRead]
    planned_blocks: list[PlannedBlockRead] = Field(default_factory=list)
    actual_blocks: list[ActualBlockDayProjectionRead] = Field(default_factory=list)
    planned_minutes: int = 0
    actual_minutes: int = 0
    meta: DayMeta


class DaySummaryRow(BaseModel):
    """Planned vs actual minutes for one task type on a single day."""

    task_type_id: int
    task_type_name: str
    planned_minutes: int
    actual_minutes: int


class DaySummaryRead(BaseModel):
    date: date
    planned_minutes: int
    actual_minutes: int
    rows: list[DaySummaryRow]
    meta: DayMeta


class DayListItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    date: date
    start_hour: int
    end_hour: int
    show_full_day: bool
    updated_at: datetime
    #: How many blocks the day holds — lets a client tell an opened-but-empty day apart.
    block_count: int
