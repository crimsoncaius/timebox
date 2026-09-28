"""Calendar periods and live Actual Block assessments; no stored progress counters."""
from __future__ import annotations

import datetime as dt
from collections import defaultdict
from zoneinfo import ZoneInfo

from dateutil.relativedelta import relativedelta
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from app.models.time_goal import TimeGoal, TimeGoalTarget
from app.schemas.time_goal import GoalBlock, GoalPeriod, TimeGoalCreate, TimeGoalRead, TimeGoalsWeek
from app.services.task_type_service import resolve_task_type_id
from app.services.trends import actual_segments


def monday(date: dt.date) -> dt.date:
    return date - dt.timedelta(days=date.weekday())


def period_bounds(goal: TimeGoal, anchor: dt.date) -> tuple[dt.date, dt.date]:
    """Inclusive natural cycle, clipped only at the deliberately chosen first start."""
    anchor = max(anchor, goal.start_date)
    if goal.unit == "month":
        origin = goal.start_date.replace(day=1)
        months = (anchor.year - origin.year) * 12 + anchor.month - origin.month
        start = origin + relativedelta(months=(months // goal.interval) * goal.interval)
        following = start + relativedelta(months=goal.interval)
    else:
        origin = monday(goal.start_date) if goal.unit == "week" else goal.start_date
        length = goal.interval * (7 if goal.unit == "week" else 1)
        start = origin + dt.timedelta(days=((anchor - origin).days // length) * length)
        following = start + dt.timedelta(days=length)
    return max(start, goal.start_date), following - dt.timedelta(days=1)


def target_at(goal: TimeGoal, start: dt.date) -> int:
    return max((t for t in goal.targets if t.effective_date <= start), key=lambda t: t.effective_date).minutes


def get(db: Session, goal_id: int) -> TimeGoal:
    goal = db.get(TimeGoal, goal_id)
    if goal is None:
        raise ValueError("Time Goal not found")
    return goal


def create(db: Session, body: TimeGoalCreate) -> TimeGoal:
    goal = TimeGoal(
        task_type_id=resolve_task_type_id(db, body.task_type_id), unit=body.unit,
        interval=body.interval, start_date=body.start_date,
        targets=[TimeGoalTarget(effective_date=body.start_date, minutes=body.target_minutes)],
    )
    # Validate arithmetic before persisting (including a following period for target edits).
    _, end = period_bounds(goal, body.start_date)
    period_bounds(goal, end + dt.timedelta(days=1))
    db.add(goal)
    db.flush()
    return goal


def change_target(db: Session, goal: TimeGoal, minutes: int, today: dt.date) -> None:
    require_active(goal)
    _, end = period_bounds(goal, today)
    effective = end + dt.timedelta(days=1)
    existing = next((t for t in goal.targets if t.effective_date == effective), None)
    if existing:
        existing.minutes = minutes
    else:
        goal.targets.append(TimeGoalTarget(effective_date=effective, minutes=minutes))
    db.flush()


def require_active(goal: TimeGoal) -> None:
    if goal.end_date is not None:
        raise ValueError("This Time Goal has ended")


def end_goal(goal: TimeGoal, today: dt.date) -> None:
    require_active(goal)
    if today < goal.start_date:
        raise ValueError("Delete a Time Goal that has not started yet")
    goal.end_date = today


def read_goal(db: Session, goal: TimeGoal, anchor: dt.date, week: dt.date,
              timezone: str, now: dt.datetime) -> TimeGoalRead:
    today = now.astimezone(ZoneInfo(timezone)).date()
    anchor = min(anchor, goal.end_date) if goal.end_date else anchor
    start, natural_end = period_bounds(goal, anchor)
    end = min(natural_end, goal.end_date) if goal.end_date else natural_end
    days: dict[dt.date, float] = defaultdict(float)
    blocks = {}
    duration = 0.0
    path = goal.task_type.name
    for record, day, left, right in actual_segments(
        db, min(start, week), max(end, week + dt.timedelta(days=6)), timezone, now,
    ):
        if record.task_type.name != path and not record.task_type.name.startswith(path + "/"):
            continue
        if day < goal.start_date or (goal.end_date and day > goal.end_date):
            continue
        seconds = (right - left).total_seconds()
        if week <= day <= week + dt.timedelta(days=6):
            days[day] += seconds
        if start <= day <= end:
            duration += seconds
            if record.id not in blocks:
                blocks[record.id] = GoalBlock(
                    id=record.id, task_type=record.task_type.name, name=record.name or "",
                    start_at=record.start_at, end_at=record.end_at, credited_seconds=0,
                )
            blocks[record.id].credited_seconds += seconds
    target = target_at(goal, start)
    outcome = "met" if duration >= target * 60 else (
        "upcoming" if start > today else
        "excused" if goal.end_date is not None and end == goal.end_date else
        "missed" if end < today else "in_progress"
    )
    current_start, current_end = period_bounds(goal, today)
    following = current_end + dt.timedelta(days=1)
    pending = next((t.minutes for t in goal.targets if t.effective_date == following), None)
    return TimeGoalRead(
        id=goal.id, task_type_id=goal.task_type_id, task_type=path, unit=goal.unit,
        interval=goal.interval, start_date=goal.start_date, end_date=goal.end_date,
        target_minutes=target_at(goal, current_start), next_target_minutes=pending, next_target_date=following,
        period=GoalPeriod(start=start, end=end, target_minutes=target, duration_seconds=duration,
                          outcome=outcome, blocks=sorted(blocks.values(), key=lambda b: b.start_at)),
        days=days,
    )


def read_week(db: Session, week: dt.date | None, timezone: str, now: dt.datetime) -> TimeGoalsWeek:
    today = now.astimezone(ZoneInfo(timezone)).date()
    week = monday(week or today)
    if week > monday(today):
        raise ValueError("Choose this week or an earlier week")
    all_goals = db.scalars(select(TimeGoal).options(joinedload(TimeGoal.task_type)).order_by(TimeGoal.id)).all()
    earliest = monday(min([today] + [g.start_date for g in all_goals]))
    anchor = min(today, week + dt.timedelta(days=6))
    visible = [g for g in all_goals if (g.start_date <= week + dt.timedelta(days=6) or week == monday(today))
               and (g.end_date is None or g.end_date >= week)]
    return TimeGoalsWeek(
        today=today, week_start=week, earliest_week_start=earliest, timezone=timezone, captured_at=now,
        goals=[read_goal(db, g, anchor, week, timezone, now) for g in visible],
    )
