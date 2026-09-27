"""Explicit read projection: never materialize a Day or recurrence."""

from __future__ import annotations

import datetime as dt
from typing import Literal
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict
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


class ReadActivityArgs(BaseModel):
    model_config = ConfigDict(extra="forbid")
    lane: Literal["planned", "actual", "both"] = "planned"
    when: str = "today"
    group_by: Literal["blocks"] = "blocks"
    task_type: str | None = None
    include_text: bool = False


def read_activity(args: ReadActivityArgs) -> dict:
    """Read stored blocks only; the same captured instant bounds every running block."""
    now = utc_now()
    with Session(get_engine(), autoflush=False) as db:
        zone = reporting_settings(db, get_settings()).app_timezone
        today = now.astimezone(ZoneInfo(zone)).date()
        date = {"today": today, "yesterday": today - dt.timedelta(days=1)}.get(args.when)
        if date is None:
            try:
                date = dt.date.fromisoformat(args.when)
            except ValueError:
                return {"error": "Blocks require one date: today, yesterday, or YYYY-MM-DD."}
        path = None
        if args.task_type is not None:
            known = db.scalars(select(TaskType.name).where(TaskType.is_merged.is_(False))).all()
            path = next((p for p in known if p.lower() == args.task_type.strip().lower()), None)
            if path is None:
                return {"error": f"No existing Task Type matches {args.task_type}. Choose an existing Task Type Path."}
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
