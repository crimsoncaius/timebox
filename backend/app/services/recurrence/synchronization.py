from __future__ import annotations

import datetime as dt

from sqlalchemy import delete, or_, select
from sqlalchemy.orm import selectinload
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.time import today_in_tz
from app.models.app_settings import AppSettings
from app.models.battle_plan import (
    RecurrenceFrequency,
    RecurrenceMode,
    RecurrenceOccurrence,
    RecurrenceStatus,
    RecurringTemplate,
    Task,
    TaskStatus,
)
from app.models.day import Day
from app.models.time_block import BlockLane, TimeBlock

from app.services.recurrence.common import LEAD_DAYS, _json_list
from app.services.recurrence.helpers import _task_kwargs
from app.services.recurrence.protection import occurrence_is_protected
from app.services.recurrence.windows import iter_windows


def _has_future_planned_block(db: Session, task_id: int, today: dt.date) -> bool:
    return db.execute(
        select(TimeBlock.id)
        .join(Day, Day.id == TimeBlock.day_id)
        .where(
            TimeBlock.task_id == task_id,
            TimeBlock.lane == BlockLane.planned,
            Day.date >= today,
        ).limit(1)
    ).scalar_one_or_none() is not None


def _set_occurrence_ready(db: Session, task: Task, today: dt.date, ready: bool) -> None:
    if task.recurrence_kind == "quota_parent":
        sessions = list(db.execute(select(Task).where(Task.parent_id == task.id)).scalars())
        for session in sessions:
            session.ready_to_plan = (
                ready
                and session.status != TaskStatus.completed
                and not _has_future_planned_block(db, session.id, today)
            )
        return
    task.ready_to_plan = (
        ready
        and task.status != TaskStatus.completed
        and not _has_future_planned_block(db, task.id, today)
    )


def _close_expired_occurrences(db: Session, today: dt.date) -> None:
    rows = db.execute(
        select(RecurrenceOccurrence, RecurringTemplate, Task)
        .join(RecurringTemplate, RecurringTemplate.id == RecurrenceOccurrence.template_id)
        .join(Task, Task.id == RecurrenceOccurrence.task_id)
        .where(
            RecurrenceOccurrence.cycle_end < today,
            RecurrenceOccurrence.skipped.is_(False),
            Task.deleted_at.is_(None),
            Task.archived_at.is_(None),
            Task.status != TaskStatus.completed,
        )
        .order_by(RecurrenceOccurrence.cycle_end, RecurrenceOccurrence.id)
    ).all()
    for occurrence, template, task in rows:
        keep_current = (
            template.mode == RecurrenceMode.scheduled
            and (
                template.keep_unfinished_overdue
                or _has_future_planned_block(db, task.id, today)
            )
        )
        if keep_current:
            _set_occurrence_ready(db, task, today, True)
            continue
        occurrence.skipped = True
        _set_occurrence_ready(db, task, today, False)


def _set_period_availability(
    db: Session,
    today: dt.date,
    *,
    planning_date: dt.date | None = None,
) -> None:
    rows = db.execute(
        select(RecurrenceOccurrence, Task)
        .join(Task, Task.id == RecurrenceOccurrence.task_id)
        .where(
            RecurrenceOccurrence.skipped.is_(False),
            Task.deleted_at.is_(None),
            Task.archived_at.is_(None),
            Task.status != TaskStatus.completed,
        )
    ).all()
    for occurrence, task in rows:
        current = occurrence.cycle_start <= today <= occurrence.cycle_end
        requested = (
            planning_date is not None
            and occurrence.cycle_start <= planning_date <= occurrence.cycle_end
        )
        if occurrence.cycle_start > today and not requested:
            _set_occurrence_ready(db, task, today, False)
        elif current or requested:
            _set_occurrence_ready(db, task, today, True)


def _materialize(db: Session, template: RecurringTemplate, window, today: dt.date) -> None:
    existing = db.execute(
        select(RecurrenceOccurrence).where(
            RecurrenceOccurrence.template_id == template.id,
            RecurrenceOccurrence.occurrence_key == window.key,
        )
    ).scalar_one_or_none()
    if existing is not None:
        return
    try:
        with db.begin_nested():
            ledger = RecurrenceOccurrence(
                template_id=template.id,
                occurrence_key=window.key,
                cycle_start=window.start,
                cycle_end=window.end,
            )
            db.add(ledger)
            db.flush()
            kwargs = _task_kwargs(template, window)
            available = window.start <= today <= window.end
            if template.mode == RecurrenceMode.quota:
                parent = Task(
                    **kwargs, ready_to_plan=False, recurrence_kind="quota_parent",
                    expected_sessions=template.quota_count, position=template.position,
                )
                db.add(parent)
                db.flush()
                for index in range(1, (template.quota_count or 0) + 1):
                    child_kwargs = {**kwargs, "title": f"Session {index}"}
                    db.add(Task(
                        **child_kwargs, parent_id=parent.id,
                        ready_to_plan=available, recurrence_kind="quota_session",
                        expected_sessions=None, session_index=index, position=index - 1,
                    ))
            else:
                parent = Task(
                    **kwargs, ready_to_plan=available, recurrence_kind="scheduled",
                    position=template.position,
                )
                db.add(parent)
                db.flush()
                for item in sorted(template.checklist_items, key=lambda value: value.position):
                    child_kwargs = {**kwargs, "title": item.title}
                    db.add(Task(
                        **child_kwargs, parent_id=parent.id,
                        ready_to_plan=False, recurrence_kind="checklist",
                        position=item.position,
                    ))
            db.flush()
            ledger.task_id = parent.id
    except IntegrityError:
        # Another request won the uniqueness race. Its transaction owns the occurrence.
        return


def _is_pristine(db: Session, task: Task) -> bool:
    if occurrence_is_protected(db, task.id):
        return False
    if task.status != TaskStatus.open or task.archived_at is not None or task.deleted_at is not None:
        return False
    if _json_list(task.recurrence_overrides_json):
        return False
    ids = [task.id] + list(db.execute(select(Task.id).where(Task.parent_id == task.id)).scalars())
    if db.execute(select(TimeBlock.id).where(TimeBlock.task_id.in_(ids)).limit(1)).scalar_one_or_none() is not None:
        return False
    children = list(db.execute(select(Task).where(Task.parent_id == task.id)).scalars())
    return all(
        child.status == TaskStatus.open
        and not child.checked
        and not _json_list(child.recurrence_overrides_json)
        for child in children
    )


def _cleanup_future(db: Session, template: RecurringTemplate, today: dt.date, *, suppress: bool) -> None:
    ledgers = list(db.execute(
        select(RecurrenceOccurrence).where(
            RecurrenceOccurrence.template_id == template.id,
            RecurrenceOccurrence.cycle_start >= today,
        )
    ).scalars())
    for ledger in ledgers:
        task = db.get(Task, ledger.task_id) if ledger.task_id is not None else None
        if task is not None and _is_pristine(db, task):
            ledger.task_id = None
            db.delete(task)
            if suppress:
                ledger.suppressed = True
            else:
                db.delete(ledger)


def _propagate_template_fields(db: Session, template: RecurringTemplate, today: dt.date) -> None:
    eligible_roots = select(RecurrenceOccurrence.task_id).where(
        RecurrenceOccurrence.template_id == template.id,
        RecurrenceOccurrence.cycle_end >= today,
        RecurrenceOccurrence.task_id.is_not(None),
    )
    rows = list(db.execute(
        select(Task)
        .where(
            or_(Task.id.in_(eligible_roots), Task.parent_id.in_(eligible_roots)),
            Task.status != TaskStatus.completed,
            Task.deleted_at.is_(None),
        )
    ).scalars())
    values = {
        "title": template.title,
        "description": template.description,
        "project_id": template.project_id,
        "task_type_id": template.task_type_id,
        "urgency": template.urgency,
        "importance": template.importance,
    }
    for task in rows:
        overrides = set(_json_list(task.recurrence_overrides_json))
        for field, value in values.items():
            if field in overrides or (field == "title" and task.recurrence_kind in {"checklist", "quota_session"}):
                continue
            setattr(task, field, value)


def _rebuild_unprotected_subtasks(
    db: Session,
    template: RecurringTemplate,
    today: dt.date,
    titles: list[str],
) -> None:
    """Replace complete Subtask snapshots without replacing Task Occurrences."""

    rows = list(db.execute(
        select(Task)
        .join(RecurrenceOccurrence, RecurrenceOccurrence.task_id == Task.id)
        .where(
            RecurrenceOccurrence.template_id == template.id,
            RecurrenceOccurrence.cycle_start >= today,
            RecurrenceOccurrence.structurally_protected.is_(False),
            Task.recurrence_kind == "scheduled",
            Task.status != TaskStatus.completed,
            Task.deleted_at.is_(None),
        )
        .order_by(Task.id)
    ).scalars())
    cleaned = [title.strip() for title in titles if title.strip()]
    for task in rows:
        db.execute(delete(Task).where(Task.parent_id == task.id))
        for position, title in enumerate(cleaned):
            db.add(Task(
                parent_id=task.id,
                project_id=task.project_id,
                task_type_id=task.task_type_id,
                recurring_template_id=task.recurring_template_id,
                occurrence_key=task.occurrence_key,
                recurrence_kind="checklist",
                title=title,
                description="",
                ready_to_plan=False,
                status=TaskStatus.open,
                checked=False,
                urgency=task.urgency,
                importance=task.importance,
                position=position,
            ))


def _derive_quota_parents(db: Session) -> None:
    parents = list(db.execute(
        select(Task).where(Task.recurrence_kind == "quota_parent", Task.deleted_at.is_(None))
    ).scalars())
    for parent in parents:
        children = list(db.execute(select(Task).where(Task.parent_id == parent.id)).scalars())
        completed = sum(child.deleted_at is None and child.status == TaskStatus.completed for child in children)
        progressed = completed > 0 or any(
            child.deleted_at is None and child.status != TaskStatus.open for child in children
        )
        if completed == (parent.expected_sessions or 0) and (parent.expected_sessions or 0) > 0:
            parent.status = TaskStatus.completed
        elif progressed:
            parent.status = TaskStatus.in_progress
        else:
            parent.status = TaskStatus.open


def _suppress_pause_interval(db: Session, template: RecurringTemplate, start: dt.date, end: dt.date, week_start: str) -> None:
    for window in iter_windows(template, end, week_start):
        if window.start < start or window.start > end:
            continue
        existing = db.execute(select(RecurrenceOccurrence).where(
            RecurrenceOccurrence.template_id == template.id,
            RecurrenceOccurrence.occurrence_key == window.key,
        )).scalar_one_or_none()
        if existing is None:
            db.add(RecurrenceOccurrence(
                template_id=template.id, occurrence_key=window.key,
                cycle_start=window.start, cycle_end=window.end, suppressed=True,
            ))


def synchronize(
    db: Session,
    settings: Settings,
    *,
    today: dt.date | None = None,
    planning_date: dt.date | None = None,
) -> None:
    today = today or today_in_tz(settings.app_timezone)
    app_settings = db.execute(select(AppSettings).where(AppSettings.id == 1)).scalar_one_or_none()
    week_start = app_settings.week_start if app_settings is not None else "monday"
    templates = list(db.execute(
        select(RecurringTemplate)
        .where(RecurringTemplate.status == RecurrenceStatus.active)
        .options(selectinload(RecurringTemplate.checklist_items))
    ).scalars().unique())
    horizon = max(today + dt.timedelta(days=LEAD_DAYS), planning_date or today)
    for template in templates:
        for window in iter_windows(template, horizon, week_start):
            if window.start < template.generation_start_date:
                continue
            _materialize(db, template, window, today)
    _close_expired_occurrences(db, today)
    _set_period_availability(db, today, planning_date=planning_date)
    _derive_quota_parents(db)
    db.commit()


def recalculate_weekly_quotas(db: Session, settings: Settings) -> None:
    today = today_in_tz(settings.app_timezone)
    templates = list(db.execute(select(RecurringTemplate).where(
        RecurringTemplate.status == RecurrenceStatus.active,
        RecurringTemplate.mode == RecurrenceMode.quota,
        RecurringTemplate.frequency == RecurrenceFrequency.weekly,
    )).scalars())
    for row in templates:
        _cleanup_future(db, row, today, suppress=False)
    db.commit()
    synchronize(db, settings, today=today)
