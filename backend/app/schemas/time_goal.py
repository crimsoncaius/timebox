from __future__ import annotations

import datetime as dt
from typing import Literal

from pydantic import BaseModel, Field


class TimeGoalCreate(BaseModel):
    task_type_id: int
    unit: Literal["day", "week", "month"] = "week"
    interval: int = Field(default=1, ge=1, le=100)
    start_date: dt.date
    target_minutes: int = Field(ge=1, le=525600)


class TimeGoalTargetChange(BaseModel):
    target_minutes: int = Field(ge=1, le=525600)


class TimeGoalReplace(BaseModel):
    task_type_id: int
    unit: Literal["day", "week", "month"]
    interval: int = Field(ge=1, le=100)
    target_minutes: int = Field(ge=1, le=525600)


class GoalBlock(BaseModel):
    id: int
    task_type: str
    name: str
    start_at: dt.datetime
    end_at: dt.datetime | None
    credited_seconds: float


class GoalPeriod(BaseModel):
    start: dt.date
    end: dt.date
    target_minutes: int
    duration_seconds: float
    outcome: Literal["met", "missed", "in_progress", "excused", "upcoming"]
    blocks: list[GoalBlock]


class TimeGoalRead(BaseModel):
    id: int
    task_type_id: int
    task_type: str
    unit: str
    interval: int
    start_date: dt.date
    end_date: dt.date | None
    # Current/pending targets are independent of the historical selected period.
    target_minutes: int
    next_target_minutes: int | None
    next_target_date: dt.date
    period: GoalPeriod
    days: dict[dt.date, float]


class TimeGoalsWeek(BaseModel):
    today: dt.date
    week_start: dt.date
    earliest_week_start: dt.date
    timezone: str
    captured_at: dt.datetime
    goals: list[TimeGoalRead]


class ArchivedTimeGoal(BaseModel):
    id: int
    task_type_id: int
    task_type: str
    unit: str
    interval: int
    start_date: dt.date
    end_date: dt.date
    target_minutes: int


class TimeGoalArchive(BaseModel):
    today: dt.date
    timezone: str
    captured_at: dt.datetime
    goals: list[ArchivedTimeGoal]


class TimeGoalHistory(BaseModel):
    goal: ArchivedTimeGoal
    timezone: str
    captured_at: dt.datetime
    periods: list[GoalPeriod]
    next_before: dt.date | None
