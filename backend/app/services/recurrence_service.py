from __future__ import annotations

import calendar
import datetime as dt
import json
from dataclasses import dataclass

from dateutil.relativedelta import relativedelta
from sqlalchemy import delete, func, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import now_in_tz, today_in_tz
from app.models.app_settings import AppSettings
from app.models.battle_plan import (
    Project,
    RecurrenceFrequency,
    RecurrenceMode,
    RecurrenceOccurrence,
    RecurrenceStatus,
    RecurringChecklistItem,
    RecurringTemplate,
    Task,
    TaskStatus,
)
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.battle_plan import (
    ProjectRead,
    RecurrencePreviewRead,
    RecurrencePreviewRequest,
    RecurrenceWindow,
    RecurringChecklistRead,
    RecurringTaskLink,
    RecurringTemplateCreate,
    RecurringTemplatePatch,
    RecurringTemplateRead,
)

LEAD_DAYS = 7
INHERITED_FIELDS = {"title", "description", "project_id", "task_type_id", "urgency", "importance"}
CUSTOMIZABLE_FIELDS = INHERITED_FIELDS | {
    "deadline_date", "deadline_at", "reminder_at", "ready_to_plan",
}
SCHEDULE_FIELDS = {
    "frequency", "interval", "weekdays", "month_day", "quota_count",
    "start_date", "end_date", "cycle_limit", "checklist_titles",
}


@dataclass(frozen=True)
class Window:
    key: str
    start: dt.date
    end: dt.date


def _utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _json_list(value: str) -> list:
    try:
        result = json.loads(value)
        return result if isinstance(result, list) else []
    except (TypeError, json.JSONDecodeError):
        return []


def _week_boundary(value: dt.date, week_start: str) -> dt.date:
    first = 6 if week_start == "sunday" else 0
    return value - dt.timedelta(days=(value.weekday() - first) % 7)


def _month_date(year: int, month: int, day: int) -> dt.date:
    return dt.date(year, month, min(day, calendar.monthrange(year, month)[1]))


def _rule_value(rule, name: str):
    if name == "weekdays":
        raw = getattr(rule, "weekdays", None)
        if raw is not None:
            return sorted(set(raw))
        return sorted(set(_json_list(rule.weekdays_json)))
    return getattr(rule, name)


def iter_windows(rule, through: dt.date, week_start: str = "monday") -> list[Window]:
    """Return all cycles from the rule start through `through`, with inclusive endings."""
    start = _rule_value(rule, "start_date")
    end_date = _rule_value(rule, "end_date")
    limit = _rule_value(rule, "cycle_limit")
    frequency = _rule_value(rule, "frequency")
    mode = _rule_value(rule, "mode")
    interval = _rule_value(rule, "interval")
    frequency = RecurrenceFrequency(frequency)
    mode = RecurrenceMode(mode)
    windows: list[Window] = []

    def add(value: Window) -> bool:
        if value.start > through or (end_date is not None and value.start > end_date):
            return False
        if limit is not None and len(windows) >= limit:
            return False
        windows.append(value)
        return True

    if mode == RecurrenceMode.scheduled:
        if frequency == RecurrenceFrequency.daily:
            value = start
            while value <= through:
                if not add(Window(f"scheduled:{value.isoformat()}", value, value)):
                    break
                value += dt.timedelta(days=interval)
        elif frequency == RecurrenceFrequency.weekly:
            weekdays = _rule_value(rule, "weekdays")
            anchor = start - dt.timedelta(days=start.weekday())
            week = anchor
            while week <= through:
                for weekday in weekdays:
                    value = week + dt.timedelta(days=weekday)
                    if value < start:
                        continue
                    if value > through:
                        return windows
                    if not add(Window(f"scheduled:{value.isoformat()}", value, value)):
                        return windows
                week += dt.timedelta(weeks=interval)
        else:
            month_day = _rule_value(rule, "month_day")
            cursor = dt.date(start.year, start.month, 1)
            while cursor <= through:
                value = _month_date(cursor.year, cursor.month, month_day)
                if value >= start:
                    if value > through or not add(Window(f"scheduled:{value.isoformat()}", value, value)):
                        break
                cursor += relativedelta(months=interval)
        return windows

    if frequency == RecurrenceFrequency.daily:
        cursor = start
        while cursor <= through:
            if not add(Window(f"quota:{cursor.isoformat()}", cursor, cursor)):
                break
            cursor += dt.timedelta(days=1)
    elif frequency == RecurrenceFrequency.weekly:
        cursor = _week_boundary(start, week_start)
        while cursor <= through:
            period_end = cursor + dt.timedelta(days=6)
            effective_start = max(cursor, start)
            if not add(Window(f"quota:{cursor.isoformat()}", effective_start, period_end)):
                break
            cursor += dt.timedelta(days=7)
    else:
        cursor = dt.date(start.year, start.month, 1)
        while cursor <= through:
            period_end = _month_date(cursor.year, cursor.month, 31)
            effective_start = max(cursor, start)
            if not add(Window(f"quota:{cursor.isoformat()}", effective_start, period_end)):
                break
            cursor += relativedelta(months=1)
    return windows


def _windows_for_preview(rule, today: dt.date, week_start: str) -> tuple[list[Window], list[Window]]:
    horizon = today + relativedelta(years=20)
    all_windows = iter_windows(rule, horizon, week_start)
    past = [window for window in all_windows if window.end < today]
    upcoming = [window for window in all_windows if window.end >= today][:5]
    return past, upcoming


def preview(rule: RecurrencePreviewRequest, today: dt.date, week_start: str) -> RecurrencePreviewRead:
    past, upcoming = _windows_for_preview(rule, today, week_start)
    tasks_per_cycle = rule.quota_count if rule.mode == RecurrenceMode.quota else 1
    return RecurrencePreviewRead(
        upcoming=[RecurrenceWindow(key=w.key, start=w.start, end=w.end) for w in upcoming],
        past_cycles=len(past),
        past_tasks=len(past) * (tasks_per_cycle or 1),
    )


def _load_template(db: Session, template_id: int) -> RecurringTemplate:
    row = db.execute(
        select(RecurringTemplate)
        .where(RecurringTemplate.id == template_id)
        .options(
            selectinload(RecurringTemplate.project),
            selectinload(RecurringTemplate.task_type),
            selectinload(RecurringTemplate.checklist_items),
        )
    ).scalar_one_or_none()
    if row is None:
        raise ValueError("Recurring template not found")
    return row


def _validate_refs(db: Session, project_id: int | None, task_type_id: int | None) -> None:
    if project_id is not None and db.get(Project, project_id) is None:
        raise ValueError("Project not found")
    if task_type_id is not None and db.get(TaskType, task_type_id) is None:
        raise ValueError("Task type not found")


def _replace_checklist(db: Session, row: RecurringTemplate, titles: list[str]) -> None:
    cleaned = [title.strip() for title in titles if title.strip()]
    db.execute(delete(RecurringChecklistItem).where(RecurringChecklistItem.template_id == row.id))
    for position, title in enumerate(cleaned):
        db.add(RecurringChecklistItem(template_id=row.id, title=title, position=position))


def _next_position(db: Session, status: TaskStatus = TaskStatus.open) -> int:
    maximum = db.execute(
        select(func.max(Task.position)).where(Task.parent_id.is_(None), Task.status == status)
    ).scalar_one_or_none()
    return (maximum or 0) + 1


def _task_kwargs(template: RecurringTemplate, window: Window) -> dict:
    return {
        "project_id": template.project_id,
        "task_type_id": template.task_type_id,
        "title": template.title,
        "description": template.description,
        "status": TaskStatus.open,
        "urgency": template.urgency,
        "importance": template.importance,
        "deadline_date": window.end,
        "recurring_template_id": template.id,
        "occurrence_key": window.key,
        "quota_period_start": window.start if template.mode == RecurrenceMode.quota else None,
        "quota_period_end": window.end if template.mode == RecurrenceMode.quota else None,
        "recurrence_overrides_json": "[]",
    }


def _materialize(db: Session, template: RecurringTemplate, window: Window) -> None:
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
            if template.mode == RecurrenceMode.quota:
                parent = Task(
                    **kwargs, ready_to_plan=False, recurrence_kind="quota_parent",
                    expected_sessions=template.quota_count, position=_next_position(db),
                )
                db.add(parent)
                db.flush()
                for index in range(1, (template.quota_count or 0) + 1):
                    child_kwargs = {**kwargs, "title": f"Session {index}"}
                    db.add(Task(
                        **child_kwargs, parent_id=parent.id,
                        ready_to_plan=True, recurrence_kind="quota_session",
                        expected_sessions=None, session_index=index, position=index - 1,
                    ))
            else:
                parent = Task(
                    **kwargs, ready_to_plan=True, recurrence_kind="scheduled",
                    position=_next_position(db),
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


def _reready_overdue(db: Session, today: dt.date) -> None:
    rows = list(db.execute(
        select(Task).where(
            Task.recurring_template_id.is_not(None),
            Task.deadline_date < today,
            Task.status != TaskStatus.completed,
            Task.archived_at.is_(None),
            Task.deleted_at.is_(None),
            Task.ready_to_plan.is_(False),
            Task.recurrence_kind.in_(["scheduled", "quota_session"]),
        )
    ).scalars())
    for task in rows:
        if not _has_future_planned_block(db, task.id, today):
            task.ready_to_plan = True


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


def synchronize(db: Session, settings: Settings, *, today: dt.date | None = None) -> None:
    today = today or today_in_tz(settings.app_timezone)
    app_settings = db.execute(select(AppSettings).where(AppSettings.id == 1)).scalar_one_or_none()
    week_start = app_settings.week_start if app_settings is not None else "monday"
    templates = list(db.execute(
        select(RecurringTemplate)
        .where(RecurringTemplate.status == RecurrenceStatus.active)
        .options(selectinload(RecurringTemplate.checklist_items))
    ).scalars().unique())
    horizon = today + dt.timedelta(days=LEAD_DAYS)
    for template in templates:
        for window in iter_windows(template, horizon, week_start):
            if window.start < template.generation_start_date:
                continue
            _materialize(db, template, window)
    _reready_overdue(db, today)
    _derive_quota_parents(db)
    db.commit()


def _is_pristine(db: Session, task: Task) -> bool:
    if task.status != TaskStatus.open or task.archived_at is not None or task.deleted_at is not None:
        return False
    if _json_list(task.recurrence_overrides_json):
        return False
    ids = [task.id] + list(db.execute(select(Task.id).where(Task.parent_id == task.id)).scalars())
    if db.execute(select(TimeBlock.id).where(TimeBlock.task_id.in_(ids)).limit(1)).scalar_one_or_none() is not None:
        return False
    children = list(db.execute(select(Task).where(Task.parent_id == task.id)).scalars())
    return all(child.status == TaskStatus.open and not _json_list(child.recurrence_overrides_json) for child in children)


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
    rows = list(db.execute(select(Task).where(
        Task.recurring_template_id == template.id,
        Task.deadline_date >= today,
        Task.status != TaskStatus.completed,
        Task.deleted_at.is_(None),
    )).scalars())
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


def create_template(db: Session, body: RecurringTemplateCreate, settings: Settings) -> RecurringTemplate:
    _validate_refs(db, body.project_id, body.task_type_id)
    title = body.title.strip()
    if not title:
        raise ValueError("Template title is required")
    today = today_in_tz(settings.app_timezone)
    app_settings = db.execute(select(AppSettings).where(AppSettings.id == 1)).scalar_one_or_none()
    past, _ = _windows_for_preview(body, today, app_settings.week_start if app_settings else "monday")
    if past and not body.confirm_backfill:
        task_count = len(past) * ((body.quota_count or 1) if body.mode == RecurrenceMode.quota else 1)
        raise ValueError(f"BACKFILL_CONFIRMATION_REQUIRED:{len(past)}:{task_count}")
    row = RecurringTemplate(
        title=title, description=body.description, project_id=body.project_id,
        task_type_id=body.task_type_id, urgency=body.urgency, importance=body.importance,
        mode=body.mode, frequency=body.frequency, interval=body.interval,
        weekdays_json=json.dumps(sorted(set(body.weekdays))), month_day=body.month_day,
        quota_count=body.quota_count, start_date=body.start_date,
        generation_start_date=body.start_date, end_date=body.end_date,
        cycle_limit=body.cycle_limit,
    )
    db.add(row)
    db.flush()
    _replace_checklist(db, row, body.checklist_titles if body.mode == RecurrenceMode.scheduled else [])
    db.commit()
    synchronize(db, settings, today=today)
    return _load_template(db, row.id)


def patch_template(db: Session, template_id: int, body: RecurringTemplatePatch, settings: Settings) -> RecurringTemplate:
    row = _load_template(db, template_id)
    fields = body.model_fields_set - {"confirm_backfill"}
    _validate_refs(
        db,
        body.project_id if "project_id" in fields else row.project_id,
        body.task_type_id if "task_type_id" in fields else row.task_type_id,
    )
    today = today_in_tz(settings.app_timezone)
    def schedule_value_changed(field: str) -> bool:
        if field == "weekdays":
            return sorted(_json_list(row.weekdays_json)) != sorted(set(body.weekdays or []))
        if field == "checklist_titles":
            before = [item.title for item in sorted(row.checklist_items, key=lambda item: item.position)]
            after = [title.strip() for title in (body.checklist_titles or []) if title.strip()]
            return before != after
        return getattr(row, field) != getattr(body, field)

    schedule_changed = any(schedule_value_changed(field) for field in fields & SCHEDULE_FIELDS)
    latest_preserved: dt.date | None = None
    if schedule_changed:
        _cleanup_future(db, row, today, suppress=False)
        db.flush()
        latest_preserved = db.execute(select(func.max(RecurrenceOccurrence.cycle_end)).where(
            RecurrenceOccurrence.template_id == row.id,
        )).scalar_one_or_none()
    for field in fields - {"weekdays", "checklist_titles"}:
        value = getattr(body, field)
        if field == "title" and value is not None:
            value = value.strip()
        setattr(row, field, value)
    if "weekdays" in fields:
        row.weekdays_json = json.dumps(sorted(set(body.weekdays or [])))
    if "checklist_titles" in fields:
        _replace_checklist(db, row, body.checklist_titles or [])
    if schedule_changed:
        after_history = latest_preserved + dt.timedelta(days=1) if latest_preserved else today
        row.generation_start_date = max(row.start_date, today, after_history)
    # Validate the complete edited rule through the same Pydantic contract used for creation.
    RecurrencePreviewRequest(
        mode=row.mode, frequency=row.frequency, interval=row.interval,
        weekdays=_json_list(row.weekdays_json), month_day=row.month_day,
        quota_count=row.quota_count, start_date=row.start_date,
        end_date=row.end_date, cycle_limit=row.cycle_limit,
    )
    _propagate_template_fields(db, row, today)
    db.commit()
    synchronize(db, settings, today=today)
    return _load_template(db, row.id)


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


def pause_template(db: Session, template_id: int, settings: Settings) -> RecurringTemplate:
    row = _load_template(db, template_id)
    if row.status != RecurrenceStatus.active:
        raise ValueError("Only active templates can be paused")
    today = today_in_tz(settings.app_timezone)
    _cleanup_future(db, row, today, suppress=False)
    row.status = RecurrenceStatus.paused
    row.paused_at = _utc_now()
    db.commit()
    return _load_template(db, row.id)


def resume_template(db: Session, template_id: int, settings: Settings) -> RecurringTemplate:
    row = _load_template(db, template_id)
    if row.status != RecurrenceStatus.paused:
        raise ValueError("Only paused templates can be resumed")
    today = today_in_tz(settings.app_timezone)
    paused = row.paused_at.date() if row.paused_at is not None else today
    app_settings = db.execute(select(AppSettings).where(AppSettings.id == 1)).scalar_one_or_none()
    _suppress_pause_interval(db, row, paused, today, app_settings.week_start if app_settings else "monday")
    row.status = RecurrenceStatus.active
    row.paused_at = None
    db.commit()
    synchronize(db, settings, today=today)
    return _load_template(db, row.id)


def end_template(db: Session, template_id: int, settings: Settings) -> RecurringTemplate:
    row = _load_template(db, template_id)
    if row.status == RecurrenceStatus.ended:
        return row
    today = today_in_tz(settings.app_timezone)
    _cleanup_future(db, row, today, suppress=True)
    row.status = RecurrenceStatus.ended
    row.ended_at = _utc_now()
    db.commit()
    return _load_template(db, row.id)


def delete_template(db: Session, template_id: int) -> None:
    row = _load_template(db, template_id)
    if row.status != RecurrenceStatus.ended:
        raise ValueError("Only ended templates can be permanently deleted")
    tasks = list(db.execute(select(Task).where(Task.recurring_template_id == row.id)).scalars())
    for task in tasks:
        task.recurring_template_id = None
        task.occurrence_key = None
        task.recurrence_kind = None
        task.quota_period_start = None
        task.quota_period_end = None
        task.expected_sessions = None
        task.session_index = None
        task.recurrence_overrides_json = "[]"
    db.delete(row)
    db.commit()


def _cadence(row: RecurringTemplate) -> str:
    frequency = row.frequency.value if hasattr(row.frequency, "value") else str(row.frequency)
    if row.mode == RecurrenceMode.quota:
        period = {"daily": "day", "weekly": "week", "monthly": "month"}[frequency]
        return f"{row.quota_count} times per {period}"
    if row.frequency == RecurrenceFrequency.weekly:
        names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        days = ", ".join(names[index] for index in _json_list(row.weekdays_json))
        return f"Every {row.interval} week{'s' if row.interval != 1 else ''} · {days}"
    if row.frequency == RecurrenceFrequency.monthly:
        return f"Every {row.interval} month{'s' if row.interval != 1 else ''} · day {row.month_day}"
    return "Daily" if row.interval == 1 else f"Every {row.interval} days"


def to_read(db: Session, row: RecurringTemplate, settings: Settings) -> RecurringTemplateRead:
    today = today_in_tz(settings.app_timezone)
    app_settings = db.execute(select(AppSettings).where(AppSettings.id == 1)).scalar_one_or_none()
    windows = [
        window for window in iter_windows(
            row, today + relativedelta(years=20), app_settings.week_start if app_settings else "monday"
        ) if window.end >= today and window.start >= row.generation_start_date
    ][:5]
    tasks = list(db.execute(select(Task).where(
        Task.recurring_template_id == row.id,
        Task.parent_id.is_(None),
        Task.status != TaskStatus.completed,
        Task.deleted_at.is_(None),
    ).order_by(Task.deadline_date, Task.id)).scalars())
    return RecurringTemplateRead(
        id=row.id, title=row.title, description=row.description,
        project_id=row.project_id, project=ProjectRead.model_validate(row.project) if row.project else None,
        task_type_id=row.task_type_id, task_type=row.task_type, mode=row.mode, status=row.status,
        frequency=row.frequency, interval=row.interval, weekdays=_json_list(row.weekdays_json),
        month_day=row.month_day, quota_count=row.quota_count, start_date=row.start_date,
        end_date=row.end_date, cycle_limit=row.cycle_limit, urgency=row.urgency,
        importance=row.importance, paused_at=row.paused_at, ended_at=row.ended_at,
        created_at=row.created_at, updated_at=row.updated_at,
        checklist_items=[RecurringChecklistRead(id=item.id, title=item.title, position=item.position) for item in row.checklist_items],
        upcoming=[RecurrenceWindow(key=w.key, start=w.start, end=w.end) for w in windows],
        current_tasks=[RecurringTaskLink(
            id=task.id, title=task.title, deadline_date=task.deadline_date,
            overdue=task.deadline_date is not None and task.deadline_date < today and task.status != TaskStatus.completed,
        ) for task in tasks],
        cadence=_cadence(row), next_occurrence=windows[0].start if windows else None,
    )


def list_templates(db: Session, status: RecurrenceStatus, settings: Settings) -> list[RecurringTemplateRead]:
    if status == RecurrenceStatus.active:
        synchronize(db, settings)
    rows = list(db.execute(
        select(RecurringTemplate).where(RecurringTemplate.status == status)
        .options(
            selectinload(RecurringTemplate.project), selectinload(RecurringTemplate.task_type),
            selectinload(RecurringTemplate.checklist_items),
        ).order_by(func.lower(RecurringTemplate.title), RecurringTemplate.id)
    ).scalars().unique())
    return [to_read(db, row, settings) for row in rows]


def get_template(db: Session, template_id: int, settings: Settings) -> RecurringTemplateRead:
    synchronize(db, settings)
    return to_read(db, _load_template(db, template_id), settings)


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


def template_type_counts(db: Session) -> dict[int, int]:
    return dict(db.execute(
        select(RecurringTemplate.task_type_id, func.count(RecurringTemplate.id))
        .where(RecurringTemplate.task_type_id.is_not(None))
        .group_by(RecurringTemplate.task_type_id)
    ).all())


def clear_template_type_references(db: Session, task_type_id: int) -> None:
    db.execute(update(RecurringTemplate).where(
        RecurringTemplate.task_type_id == task_type_id
    ).values(task_type_id=None))
    db.commit()


def move_project_templates_to_admin(db: Session, project_id: int) -> None:
    db.execute(update(RecurringTemplate).where(
        RecurringTemplate.project_id == project_id
    ).values(project_id=None))
    db.flush()


def record_task_overrides(task: Task, fields: set[str]) -> None:
    if task.recurring_template_id is None:
        return
    current = set(_json_list(task.recurrence_overrides_json))
    current.update(fields & CUSTOMIZABLE_FIELDS)
    task.recurrence_overrides_json = json.dumps(sorted(current))


def quota_progress(db: Session, parent_id: int) -> int:
    return db.execute(select(func.count(Task.id)).where(
        Task.parent_id == parent_id,
        Task.deleted_at.is_(None),
        Task.status == TaskStatus.completed,
    )).scalar_one()
