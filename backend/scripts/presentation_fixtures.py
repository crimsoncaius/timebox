"""Synthetic, coherent account for the observational Assistant study."""

import datetime as dt
import sys
from contextlib import ExitStack, contextmanager
from types import SimpleNamespace
from unittest.mock import patch
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.activity import ActivityState
from app.models.battle_plan import Project, Task, TaskStatus
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock

ZONE = "Asia/Singapore"
NOW = "2026-10-08T15:30:00+08:00"


def instant(date, minutes):
    return (dt.datetime.combine(date, dt.time(), ZoneInfo(ZONE)) + dt.timedelta(minutes=minutes)).astimezone(dt.UTC)


def seed(engine):
    """Seed an empty study DB only. No existing account reads or model calls."""
    today = dt.date(2026, 10, 8)
    with Session(engine) as db:
        if db.scalar(select(Task.id).limit(1)) is not None:
            raise ValueError("Study seed requires an empty database")
        types = {name: TaskType(name=name) for name in
                 ("unspecified", "Work", "Work/Coding", "Work/Admin", "Reading", "Meals", "Exercise/Walking")}
        projects = {name: Project(name=name) for name in ("Timebox", "Home")}
        db.add_all([*types.values(), *projects.values()])
        db.flush()
        tasks = {}
        for title, project, kind, extra in [
            ("Fix calendar export", "Timebox", "Work/Coding", {"ready_to_plan": True, "deadline_date": today}),
            ("Quarterly report", "Timebox", "Work/Admin", {"deadline_date": today + dt.timedelta(days=1)}),
            ("Weekly report", "Timebox", "Work/Admin", {}),
            ("Review pull request", "Timebox", "Work/Coding", {"ready_to_plan": True}),
            ("Send invoice", "Timebox", "Work/Admin", {"deadline_date": today - dt.timedelta(days=2)}),
            ("Renew insurance", "Home", "Work/Admin", {"is_blocked": True, "blocking_reason": "Waiting for the quote"}),
            ("Buy groceries", "Home", None, {}),
            ("Read database chapter", None, "Reading", {}),
            ("Update changelog", "Timebox", "Work/Admin", {"status": TaskStatus.completed,
                "completed_at": instant(today - dt.timedelta(days=1), 960), "completion_precision": "instant"}),
        ]:
            row = Task(title=title, project_id=projects[project].id if project else None,
                       task_type_id=types[kind].id if kind else None, **extra)
            tasks[title] = row
            db.add(row)
        db.flush()
        tasks["Quarterly report"].description = "Compare revenue with last quarter. Explain the support cost increase."
        tasks["Fix calendar export"].description = "Reproduce the missing timezone offset, patch it, then test an exported event."
        db.add_all([Task(title="Reproduce timezone bug", parent_id=tasks["Fix calendar export"].id, checked=True),
                    Task(title="Patch export", parent_id=tasks["Fix calendar export"].id),
                    Task(title="Verify exported event", parent_id=tasks["Fix calendar export"].id)])
        # Two complete calendar weeks of stored data; future dates contain plans only.
        for offset in range(-10, 2):
            date = today + dt.timedelta(days=offset)
            if date.weekday() >= 5:
                continue
            day = Day(date=date)
            db.add(day)
            db.flush()
            schedule = [(540, 660, "Work/Coding", "Fix calendar export"),
                        (720, 780, "Meals", None), (840, 960, "Reading", "Read database chapter"),
                        (990, 1050, "Work/Admin", "Quarterly report")]
            for start, end, kind, title in schedule:
                db.add(TimeBlock(day_id=day.id, lane=BlockLane.planned, start_minute=start,
                                 end_minute=end, start_at=instant(date, start), end_at=instant(date, end),
                                 task_type_id=types[kind].id, task_id=tasks[title].id if title else None,
                                 name=title))
            if offset > 0:
                continue
            actual = [(555, 645, "Work/Coding", "Fix calendar export"),
                      (645, 690, "Work/Admin", "Send invoice"), (725, 770, "Meals", None),
                      (840, None if offset == 0 else (900 if offset < -3 else 945), "Reading", "Read database chapter")]
            for start, end, kind, title in actual:
                db.add(TimeBlock(lane=BlockLane.actual, start_at=instant(date, start),
                                 end_at=instant(date, end) if end else None, task_type_id=types[kind].id,
                                 task_id=tasks[title].id if title else None, name=title, activity_source="study",
                                 note="Spent extra time understanding transaction isolation." if kind == "Reading" else None))
        db.add(ActivityState(id=1, enabled=True, reporting_timezone=ZONE, cutover={"paused": False}))
        db.commit()


def move_afternoon_plan(engine):
    """Scenario event: another client moves today's report block, without a model."""
    with Session(engine) as db:
        block = db.scalar(select(TimeBlock).join(Day).where(
            Day.date == dt.date(2026, 10, 8), TimeBlock.lane == BlockLane.planned,
            TimeBlock.name == "Quarterly report"))
        block.start_minute, block.end_minute = 1080, 1140
        block.start_at = instant(dt.date(2026, 10, 8), 1080)
        block.end_at = instant(dt.date(2026, 10, 8), 1140)
        db.commit()


@contextmanager
def frozen_clock(value):
    """Replace application wall clocks only; provider and timeout clocks remain real."""
    now = dt.datetime.fromisoformat(value).astimezone(dt.UTC)

    class DateTimeMeta(type):
        def __instancecheck__(cls, value):
            return isinstance(value, dt.datetime)

    class FrozenDateTime(dt.datetime, metaclass=DateTimeMeta):
        @classmethod
        def now(cls, tz=None):
            return now.astimezone(tz) if tz else now.replace(tzinfo=None)

        @classmethod
        def utcnow(cls):
            return now.replace(tzinfo=None)

    proxy = SimpleNamespace(**{key: getattr(dt, key) for key in dir(dt) if not key.startswith("__")})
    proxy.datetime = FrozenDateTime
    with ExitStack() as stack:
        for name, module in list(sys.modules.items()):
            if name.startswith("app.") and module:
                for key, val in list(vars(module).items()):
                    if val is dt:
                        stack.enter_context(patch.object(module, key, proxy))
        yield


