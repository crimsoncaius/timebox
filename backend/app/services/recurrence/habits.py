"""The Habits view: each Habit's Habit Periods across one Calendar Week.

A Habit is a Recurring Task Series with ``track_as_habit`` set. Everything shown
here is derived from the occurrence ledger and Task Completions; ticking a day
records an ordinary Task Completion dated that day (ADR 0015).
"""

from __future__ import annotations

import datetime as dt
from collections import Counter
from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.time import get_zone, today_in_tz, utc_now
from app.models.battle_plan import (
    RecurrenceFrequency,
    RecurrenceMode,
    RecurrenceOccurrence,
    RecurrenceStatus,
    RecurringTemplate,
    Task,
    TaskStatus,
)
from app.schemas.battle_plan import HabitDayRead, HabitRead, HabitsWeekRead, HabitTotalRead
from app.services import task_completion_service
from app.services.recurrence.common import Window, _date_in_tz, _json_list
from app.services.recurrence.helpers import _load_template, _task_kwargs
from app.services.recurrence.synchronization import _derive_quota_parents, synchronize
from app.services.recurrence.windows import iter_windows

NO_PERIOD = "No Habit Period on this day"


def _monday(day: dt.date) -> dt.date:
    return day - dt.timedelta(days=day.weekday())


def _habit_start(template: RecurringTemplate) -> dt.date:
    return max(template.start_date, template.generation_start_date)


def _done(task: Task | None) -> bool:
    return task is not None and task.deleted_at is None and task.status == TaskStatus.completed


@dataclass
class _Habit:
    """One template's ledger, Tasks and lifecycle bounds, read once per request."""

    template: RecurringTemplate
    tz: str
    today: dt.date
    windows: list[Window]
    ledger: dict[str, RecurrenceOccurrence]
    tasks: dict[int, Task]
    sessions: dict[int, list[Task]]
    paused_on: dt.date | None
    ended_on: dt.date | None

    @classmethod
    def load(cls, db: Session, template: RecurringTemplate, through: dt.date, tz: str, today: dt.date) -> _Habit:
        windows = [
            window for window in iter_windows(template, through)
            if window.start >= template.generation_start_date
        ]
        ledger = {
            row.occurrence_key: row
            for row in db.scalars(
                select(RecurrenceOccurrence).where(RecurrenceOccurrence.template_id == template.id)
            )
        }
        tasks = {
            row.id: row
            for row in db.scalars(select(Task).where(Task.recurring_template_id == template.id))
        }
        sessions: dict[int, list[Task]] = {}
        for task in tasks.values():
            if task.recurrence_kind == "quota_session" and task.deleted_at is None and task.parent_id is not None:
                sessions.setdefault(task.parent_id, []).append(task)
        for children in sessions.values():
            children.sort(key=lambda task: (task.session_index or 0, task.id))
        return cls(
            template=template,
            tz=tz,
            today=today,
            windows=windows,
            ledger=ledger,
            tasks=tasks,
            sessions=sessions,
            paused_on=(
                _date_in_tz(template.paused_at, tz)
                if template.status == RecurrenceStatus.paused and template.paused_at
                else None
            ),
            ended_on=(
                _date_in_tz(template.ended_at, tz)
                if template.status == RecurrenceStatus.ended and template.ended_at
                else None
            ),
        )

    @property
    def scheduled(self) -> bool:
        return self.template.mode == RecurrenceMode.scheduled

    def window_for(self, day: dt.date) -> Window | None:
        return next((w for w in self.windows if w.start <= day <= w.end), None)

    def outside_lifecycle(self, day: dt.date) -> bool:
        template = self.template
        return (
            day < _habit_start(template)
            or (template.end_date is not None and day > template.end_date)
            or (self.paused_on is not None and day >= self.paused_on)
            or (self.ended_on is not None and day > self.ended_on)
        )

    def excused(self, day: dt.date, window: Window | None) -> bool:
        if window is None or self.outside_lifecycle(day):
            return True
        occurrence = self.ledger.get(window.key)
        if occurrence is None:
            # Past cycles that were never generated have nothing to record.
            return window.start <= self.today
        return occurrence.suppressed

    def root_task(self, window: Window) -> Task | None:
        occurrence = self.ledger.get(window.key)
        if occurrence is None or occurrence.task_id is None:
            return None
        task = self.tasks.get(occurrence.task_id)
        if task is None or task.deleted_at is not None or task.archived_at is not None:
            return None
        return task

    def completed_sessions(self, tracker: Task | None) -> list[Task]:
        if tracker is None:
            return []
        return [task for task in self.sessions.get(tracker.id, []) if _done(task)]

    def target(self, tracker: Task | None) -> int:
        return (tracker.expected_sessions if tracker else None) or self.template.quota_count or 0

    def visible_in(self, week_start: dt.date) -> bool:
        """Shown only in weeks where the series was active on at least one day."""

        days = [week_start + dt.timedelta(days=offset) for offset in range(7)]
        if all(self.outside_lifecycle(day) for day in days):
            return False
        week_end = days[-1]
        in_week = [w for w in self.windows if w.end >= week_start and w.start <= week_end]
        if in_week and all(
            self.ledger.get(w.key) is not None and self.ledger[w.key].suppressed for w in in_week
        ):
            return False
        limit = self.template.cycle_limit
        if limit is not None and len(self.windows) >= limit and self.windows[-1].end < week_start:
            return False
        return True


def _period_total(cells: list[HabitDayRead], week_ended: bool) -> HabitTotalRead:
    periods = [cell for cell in cells if cell.state in {"met", "missed", "open", "upcoming", "partial"}]
    done = sum(cell.state == "met" for cell in periods)
    target = len(periods)
    tone = "met" if target and done >= target else "missed" if week_ended else "open"
    return HabitTotalRead(done=done, target=target, unit="days", tone=tone)


def _scheduled_row(habit: _Habit, days: list[dt.date]) -> tuple[list[HabitDayRead], HabitTotalRead]:
    today = habit.today
    by_start = {w.start: w for w in habit.windows}
    cells: list[HabitDayRead] = []
    for day in days:
        window = by_start.get(day)
        if window is None:
            cells.append(HabitDayRead(date=day, state="not_due"))
            continue
        if habit.excused(day, window):
            cells.append(HabitDayRead(date=day, state="excused"))
            continue
        task = habit.root_task(window)
        recordable = task is not None and day <= today
        if _done(task):
            state = "met"
        elif day > today:
            state = "upcoming"
        elif day == today:
            state = "open"
        else:
            state = "missed"
        cells.append(HabitDayRead(date=day, state=state, tickable=recordable))
    return cells, _period_total(cells, days[-1] < today)


def _quota_row(habit: _Habit, days: list[dt.date]) -> tuple[list[HabitDayRead], HabitTotalRead]:
    today = habit.today
    frequency = habit.template.frequency
    cells: list[HabitDayRead] = []
    per_day = Counter(
        _date_in_tz(task.completed_at, habit.tz)
        for children in habit.sessions.values()
        for task in children
        if _done(task) and task.completed_at is not None
    )
    for day in days:
        window = habit.window_for(day)
        if habit.excused(day, window):
            cells.append(HabitDayRead(date=day, state="excused"))
            continue
        assert window is not None
        if day > today:
            cells.append(HabitDayRead(date=day, state="upcoming"))
            continue
        tracker = habit.root_task(window)
        recordable = tracker is not None
        if frequency == RecurrenceFrequency.daily:
            count = len(habit.completed_sessions(tracker))
            target = habit.target(tracker)
            if count >= target > 0:
                state = "met"
            elif day == today:
                state = "open"
            elif count:
                state = "partial"
            else:
                state = "missed"
            cells.append(HabitDayRead(date=day, state=state, count=count, target=target, tickable=recordable))
            continue
        count = per_day[day]
        state = "count" if count else "open" if day == today else "empty"
        cells.append(HabitDayRead(date=day, state=state, count=count, tickable=recordable))

    if frequency == RecurrenceFrequency.daily:
        return cells, _period_total(cells, days[-1] < today)

    week_end = days[-1]
    anchor = min(week_end, today) if days[0] <= today else days[0]
    window = habit.window_for(anchor)
    tracker = habit.root_task(window) if window else None
    target = habit.target(tracker)
    if frequency == RecurrenceFrequency.weekly:
        done = len(habit.completed_sessions(tracker))
        ended = window is not None and window.end < today
        tone = "met" if done >= target > 0 else "missed" if ended else "open"
        return cells, HabitTotalRead(done=done, target=target, unit="sessions", tone=tone)

    # Monthly: month-to-date as of the viewed week.
    through = min(week_end, today)
    done = sum(
        1 for task in habit.completed_sessions(tracker)
        if task.completed_at is not None and _date_in_tz(task.completed_at, habit.tz) <= through
    )
    ended = window is not None and window.end < today and window.end <= week_end
    tone = "met" if done >= target > 0 else "missed" if ended else "open"
    return cells, HabitTotalRead(
        done=done, target=target, unit="month", month=anchor.replace(day=1), tone=tone
    )


def _to_read(habit: _Habit, week_start: dt.date) -> HabitRead:
    days = [week_start + dt.timedelta(days=offset) for offset in range(7)]
    cells, total = (_scheduled_row if habit.scheduled else _quota_row)(habit, days)
    template = habit.template
    return HabitRead(
        template_id=template.id,
        title=template.title,
        mode=template.mode,
        status=template.status,
        frequency=template.frequency,
        interval=template.interval,
        weekdays=_json_list(template.weekdays_json),
        month_day=template.month_day,
        quota_count=template.quota_count,
        days=cells,
        total=total,
    )


def read_week(
    db: Session, week: dt.date | None, settings: Settings, *, today: dt.date | None = None
) -> HabitsWeekRead:
    tz = settings.app_timezone
    today = today or today_in_tz(tz)
    synchronize(db, settings, today=today)
    templates = db.scalars(
        select(RecurringTemplate)
        .where(RecurringTemplate.track_as_habit.is_(True))
        .order_by(RecurringTemplate.position, RecurringTemplate.id)
    ).all()
    current = _monday(today)
    earliest = min([_monday(_habit_start(t)) for t in templates] + [current])
    week_start = min(max(_monday(week or today), earliest), current)
    through = week_start + dt.timedelta(days=6)
    habits = []
    for template in templates:
        habit = _Habit.load(db, template, through, tz, today)
        if habit.visible_in(week_start):
            habits.append(_to_read(habit, week_start))
    return HabitsWeekRead(today=today, week_start=week_start, earliest_week_start=earliest, habits=habits)


def _recordable(db: Session, template_id: int, day: dt.date, settings: Settings, today: dt.date):
    template = _load_template(db, template_id)
    if day > today:
        raise ValueError("Future days cannot be recorded")
    synchronize(db, settings, today=today)
    habit = _Habit.load(db, template, max(day, today), settings.app_timezone, today)
    window = habit.window_for(day)
    if habit.scheduled and (window is None or window.start != day):
        raise ValueError(NO_PERIOD)
    if habit.excused(day, window):
        raise ValueError(NO_PERIOD)
    assert window is not None
    root = habit.root_task(window)
    if root is None:
        raise ValueError(NO_PERIOD)
    return habit, window, root


def _completion_instant(day: dt.date, tz: str) -> dt.datetime:
    """A past day's completion is recorded at local noon: the date is what counts."""

    return dt.datetime.combine(day, dt.time(12), tzinfo=get_zone(tz))


def _extra_session(db: Session, habit: _Habit, window: Window, tracker: Task) -> Task:
    siblings = habit.sessions.get(tracker.id, [])
    index = max([task.session_index or 0 for task in siblings] + [tracker.expected_sessions or 0]) + 1
    extra = Task(
        **{**_task_kwargs(habit.template, window), "title": f"Session {index}"},
        parent_id=tracker.id,
        ready_to_plan=False,
        recurrence_kind="quota_session",
        expected_sessions=None,
        session_index=index,
        position=index - 1,
    )
    db.add(extra)
    db.commit()
    return extra


def tick(
    db: Session,
    template_id: int,
    day: dt.date,
    settings: Settings,
    *,
    today: dt.date | None = None,
    now: dt.datetime | None = None,
) -> None:
    """Record the Habit as done on ``day``: one occurrence, or one more session."""

    tz = settings.app_timezone
    today = today or today_in_tz(tz)
    habit, window, root = _recordable(db, template_id, day, settings, today)
    if habit.scheduled:
        if _done(root):
            return
        target_id = root.id
    else:
        open_sessions = [task for task in habit.sessions.get(root.id, []) if not _done(task)]
        target_id = (open_sessions[0] if open_sessions else _extra_session(db, habit, window, root)).id

    if day == today:
        task_completion_service.complete_task(db, target_id, now or utc_now(), settings)
    else:
        task_completion_service.record_dated_completion(db, target_id, _completion_instant(day, tz))

    occurrence = habit.ledger[window.key]
    db.refresh(occurrence)
    tracker = db.get(Task, root.id)
    if occurrence.skipped and _done(tracker):
        # A completion dated inside the period reverses the skip (CONTEXT.md).
        occurrence.skipped = False
        db.commit()


def untick(
    db: Session,
    template_id: int,
    day: dt.date,
    settings: Settings,
    *,
    today: dt.date | None = None,
) -> None:
    """Remove the Habit's record on ``day``: reopen the occurrence, or one session."""

    tz = settings.app_timezone
    today = today or today_in_tz(tz)
    habit, _, root = _recordable(db, template_id, day, settings, today)
    if habit.scheduled:
        if _done(root):
            task_completion_service.reopen_task(db, root.id)
    else:
        completed = [
            task
            for children in habit.sessions.values()
            for task in children
            if _done(task) and task.completed_at is not None and _date_in_tz(task.completed_at, tz) == day
        ]
        if not completed and habit.template.frequency == RecurrenceFrequency.daily:
            completed = habit.completed_sessions(root)
        if completed:
            def is_extra(task: Task) -> bool:
                parent = habit.tasks.get(task.parent_id) if task.parent_id else None
                return (task.session_index or 0) > ((parent.expected_sessions if parent else None) or 0)

            session = max(completed, key=lambda task: (is_extra(task), task.session_index or 0, task.id))
            if is_extra(session):
                # Extra Session Tasks exist only once recorded as done.
                session.deleted_at = utc_now()
                _derive_quota_parents(db)
                db.commit()
            else:
                task_completion_service.reopen_task(db, session.id)
    # An ended period that is no longer met becomes skipped again.
    synchronize(db, settings, today=today)
