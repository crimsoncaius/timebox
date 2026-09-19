"""Explicit read projection: never materialize a Day or recurrence."""
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.time import today_in_tz
from app.db.session import get_engine
from app.models.day import Day
from app.models.time_block import TimeBlock, BlockLane
from app.models.task_type import TaskType
from app.models.battle_plan import Task
from app.services.activity_service import reporting_settings


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
