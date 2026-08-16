from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class SettingsRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    start_hour: int = Field(..., ge=0, le=23)
    end_hour: int = Field(..., ge=1, le=24)
    show_full_day: bool
    week_start: Literal["monday", "sunday"] = "monday"
    created_at: datetime
    updated_at: datetime


class SettingsPatch(BaseModel):
    start_hour: int | None = Field(None, ge=0, le=23)
    end_hour: int | None = Field(None, ge=1, le=24)
    show_full_day: bool | None = None
    week_start: Literal["monday", "sunday"] | None = None
