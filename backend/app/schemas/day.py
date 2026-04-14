from __future__ import annotations

from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.time_block import TimeBlockRead


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
    meta: DayMeta


class DayPatch(BaseModel):
    start_hour: int | None = Field(None, ge=0, le=23)
    end_hour: int | None = Field(None, ge=1, le=24)
    show_full_day: bool | None = None


class DayListItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    date: date
    start_hour: int
    end_hour: int
    show_full_day: bool
    updated_at: datetime
