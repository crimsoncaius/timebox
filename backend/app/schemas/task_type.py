from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class TaskTypeRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    created_at: datetime
    updated_at: datetime


class TaskTypeCreate(BaseModel):
    name: str = Field(..., min_length=1)


class TaskTypePatch(BaseModel):
    name: str | None = Field(None, min_length=1)
