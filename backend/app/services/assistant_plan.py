"""Explicit read projection: never materialize a Day or recurrence."""

from __future__ import annotations

import datetime as dt
from collections import defaultdict
from typing import Annotated, Literal
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.time import as_utc, today_in_tz, utc_now
from app.db.session import get_engine
from app.models.battle_plan import Task
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.services.activity_service import reporting_settings

TEXT_LIMIT = 2000


class ReadWhen(BaseModel):
    model_config = ConfigDict(extra="forbid")
    period: Literal["today", "yesterday", "this_week", "last_week", "this_month", "last_month", "last_n_days"] | None = None
    start: dt.date | None = None
    end: dt.date | None = None
    n: int | None = Field(default=None, ge=1, le=3660)
    weekdays: list[Annotated[int, Field(ge=0, le=6)]] | None = Field(default=None, min_length=1, max_length=7, description="Monday=0 through Sunday=6")

    @model_validator(mode="after")
    def selection(self):
        if self.period is not None and (self.start is not None or self.end is not None):
            raise ValueError("Choose a period or start/end dates, not both.")
        if self.period is None and (self.start is None or self.end is None):
            raise ValueError("Custom ranges require both start and end dates.")
        if (self.period == "last_n_days") != (self.n is not None):
            raise ValueError("Supply n only for last_n_days.")
        return self


def resolve_when(value, today):
    selection = value if isinstance(value, ReadWhen) else None
    period = selection.period if selection else value
    weekdays = selection.weekdays if selection else None
    monday = today - dt.timedelta(days=today.weekday())
    month = today.replace(day=1)
    named = {"today": (today, today), "yesterday": (today-dt.timedelta(days=1), today-dt.timedelta(days=1)),
             "this_week": (monday, monday+dt.timedelta(days=6)),
             "last_week": (monday-dt.timedelta(days=7), monday-dt.timedelta(days=1)),
             "this_month": (month, (month+dt.timedelta(days=32)).replace(day=1)-dt.timedelta(days=1)),
             "last_month": ((month-dt.timedelta(days=1)).replace(day=1), month-dt.timedelta(days=1))}
    if selection and period is None:
        start, end = selection.start, selection.end
    elif selection and period == "last_n_days":
        start, end = today-dt.timedelta(days=selection.n-1), today
    elif period in named:
        start, end = named[period]
    else:
        start = end = dt.date.fromisoformat(period)
    if end < start:
        raise ValueError("End must be on or after start.")
    if end == dt.date.max:
        raise ValueError("End must be earlier than 9999-12-31.")
    return start, end, weekdays


class ReadActivityArgs(BaseModel):
    model_config = ConfigDict(extra="forbid")
    lane: Literal["planned", "actual", "both"] = "planned"
    when: str | ReadWhen = "today"
    group_by: Literal["blocks", "task_type"] = "blocks"
    detail: Literal["total", "day", "week"] = "total"
    task_type: str | None = None
    include_text: bool = False


def read_activity(args: ReadActivityArgs) -> dict:
    """Read stored blocks only; the same captured instant bounds every running block."""
    now = utc_now()
    with Session(get_engine(), autoflush=False) as db:
        zone = reporting_settings(db, get_settings()).app_timezone
        today = now.astimezone(ZoneInfo(zone)).date()
        try:
            date, last, weekdays = resolve_when(args.when, today)
        except (ValueError, TypeError, OverflowError) as error:
            return {"error": f"Invalid when selection: {error}. Use a date, named period, or start/end dates."}
        if args.group_by == "blocks" and (date != last or weekdays is not None or args.detail != "total"):
            return {"error": "Blocks require one date without weekday filters or detail. Use group_by task_type for ranges."}
        if args.group_by == "task_type" and args.include_text:
            return {"error": "include_text is available only with group_by blocks."}
        path = None
        if args.task_type is not None:
            known = db.scalars(select(TaskType.name).where(TaskType.is_merged.is_(False))).all()
            path = next((p for p in known if p.lower() == args.task_type.strip().lower()), None)
            if path is None:
                return {"error": f"No existing Task Type matches {args.task_type}. Choose an existing Task Type Path."}
        if args.group_by == "task_type":
            return read_task_types(db, args, date, last, weekdays, path, zone, now, today)
        start = dt.datetime.combine(date, dt.time(), ZoneInfo(zone)).astimezone(dt.UTC)
        end = dt.datetime.combine(date + dt.timedelta(days=1), dt.time(), ZoneInfo(zone)).astimezone(dt.UTC)
        rows = (select(TimeBlock, TaskType.name, Task).select_from(TimeBlock)
                .join(TaskType, TaskType.id == TimeBlock.task_type_id)
                .outerjoin(Task, Task.id == TimeBlock.task_id)
                .outerjoin(Day, Day.id == TimeBlock.day_id))
        planned = (TimeBlock.lane == BlockLane.planned) & (Day.date == date)
        actual = (TimeBlock.lane == BlockLane.actual) & (TimeBlock.start_at < min(end, now)) & (or_(TimeBlock.end_at.is_(None), TimeBlock.end_at > start))
        lanes = []
        if args.lane != "actual":
            lanes.append(planned)
        if args.lane != "planned" and date <= today:
            lanes.append(actual)
        blocks = []
        for block, kind, task in db.execute(rows.where(or_(*lanes) if lanes else False)):
            if path is not None and kind != path and not kind.startswith(path + "/"):
                continue
            if block.lane == BlockLane.planned:
                block_start = dt.datetime.combine(date, dt.time(), ZoneInfo(zone)) + dt.timedelta(minutes=block.start_minute)
                block_end = dt.datetime.combine(date, dt.time(), ZoneInfo(zone)) + dt.timedelta(minutes=block.end_minute)
                duration = block.end_minute - block.start_minute
                inside = duration
            else:
                block_start = as_utc(block.start_at)
                block_end = as_utc(block.end_at) if block.end_at else now
                duration = max(0, int((block_end - block_start).total_seconds() // 60))
                inside = max(0, int((min(block_end, end, now) - max(block_start, start)).total_seconds() // 60))
            row = {"id": block.id, "lane": block.lane.value, "start_at": block_start.astimezone(ZoneInfo(zone)).isoformat(),
                   "end_at": block_end.astimezone(ZoneInfo(zone)).isoformat(), "running": block.lane == BlockLane.actual and block.end_at is None,
                   "duration_minutes": duration, "minutes_in_date": inside, "name": block.name,
                   "task_type": kind, "task_id": task.id if task else None, "task_title": task.title if task else None,
                   "planned_block_id": block.planned_block_id}
            if args.include_text:
                row.update(supporting_note=(block.note or "")[:TEXT_LIMIT],
                           task_description=(task.description or "")[:TEXT_LIMIT] if task else "")
            blocks.append(row)
        blocks.sort(key=lambda row: (dt.datetime.fromisoformat(row["start_at"]), row["id"]))
        return {"schema_version": 2, "date": date.isoformat(), "reporting_timezone": zone,
                "read_at": now.isoformat(), "group_by": "blocks", "lane": args.lane,
                "task_type": path, "blocks": blocks, "recurring_not_materialized": date > today,
                "actual_unavailable_reason": "Actual Blocks are unavailable for future dates." if date > today and args.lane != "planned" else None}


def reporting_timezone() -> str:
    with Session(get_engine(), autoflush=False) as db:
        return reporting_settings(db, get_settings()).app_timezone


def read_today_plan() -> dict:
    with Session(get_engine(), autoflush=False) as db:
        settings = reporting_settings(db, get_settings())
        today = today_in_tz(settings.app_timezone)
        rows = db.execute(
            select(TimeBlock.start_minute, TimeBlock.end_minute, TimeBlock.name,
                   TaskType.name.label("task_type"), Task.id.label("task_id"),
                   Task.title.label("task_title"))
            .join(Day, Day.id == TimeBlock.day_id)
            .join(TaskType, TaskType.id == TimeBlock.task_type_id)
            .outerjoin(Task, Task.id == TimeBlock.task_id)
            .where(Day.date == today, TimeBlock.lane == BlockLane.planned)
            .order_by(TimeBlock.start_minute, TimeBlock.id)
        ).mappings()
        return {"date": today.isoformat(), "reporting_timezone": settings.app_timezone,
                "planned_blocks": [dict(row) for row in rows]}


def read_task_types(db, args, start, end, weekdays, path, zone, now, today):
    from app.services.trends import report

    limit = {"total": 3660, "day": 62, "week": 366}[args.detail]
    if (end-start).days+1 > limit:
        return {"error": f"detail {args.detail} supports at most {limit} calendar days. Shorten the range or use total detail (up to 3660 days)."}
    # Reuse Trends' exact clipping, midnight splitting and hierarchy rollup.
    actual = {}
    def collect(nodes):
        for node in nodes:
            actual[node.path] = node.days
            collect(node.children)
    if args.lane != "planned":
        collect(report(db, start, end, zone, now).types)
    planned = defaultdict(lambda: defaultdict(float))
    if args.lane != "actual":
        rows = db.execute(select(TaskType.name, Day.date, TimeBlock.start_minute, TimeBlock.end_minute)
                          .select_from(TimeBlock).join(TaskType, TaskType.id == TimeBlock.task_type_id)
                          .join(Day, Day.id == TimeBlock.day_id)
                          .where(TimeBlock.lane == BlockLane.planned, Day.date.between(start, end)))
        for kind, day, left, right in rows:
            segments = kind.split("/")
            for depth in range(1, len(segments)+1):
                planned["/".join(segments[:depth])][day] += (right-left)*60
    rows = []
    for kind in sorted(planned.keys() | actual.keys()):
        if path is not None and kind != path and not kind.startswith(path+"/"):
            continue
        groups = defaultdict(lambda: [0.0, 0.0])
        for index, days in enumerate((planned.get(kind, {}), actual.get(kind, {}))):
            for day, seconds in days.items():
                if weekdays is not None and day.weekday() not in weekdays:
                    continue
                bucket = ("total" if args.detail == "total" else
                          (day-dt.timedelta(days=day.weekday())).isoformat() if args.detail == "week" else day.isoformat())
                groups[bucket][index] += seconds
        for bucket, (plan, done) in sorted(groups.items()):
            row = {"task_type": kind, "period": bucket}
            if args.lane != "actual":
                row["planned_seconds"] = plan
            if args.lane != "planned":
                row["actual_seconds"] = done
            if args.lane == "both":
                row["difference_seconds"] = done-plan
            rows.append(row)
    if len(rows) > 2000:
        return {"error": "Result exceeds 2000 Task Type rows. Choose a task_type subtree, shorten the range, or use total detail."}
    return {"schema_version": 3, "date": start.isoformat(), "start": start.isoformat(), "end": end.isoformat(),
            "reporting_timezone": zone, "read_at": now.isoformat(), "group_by": "task_type", "lane": args.lane,
            "detail": args.detail, "weekdays": weekdays, "task_type": path, "types": rows,
            "recurring_not_materialized": end > today}
