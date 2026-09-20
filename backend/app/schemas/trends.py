from __future__ import annotations

import datetime as dt

from pydantic import BaseModel, Field


class TrendNode(BaseModel):
    path: str
    name: str
    duration_seconds: float
    direct_seconds: float
    days: dict[dt.date, float]
    direct_days: dict[dt.date, float]
    children: list[TrendNode] = Field(default_factory=list)


class TrendsRead(BaseModel):
    start: dt.date
    end: dt.date
    today: dt.date
    timezone: str
    captured_at: dt.datetime
    duration_seconds: float
    types: list[TrendNode]
