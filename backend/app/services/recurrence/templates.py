from __future__ import annotations

import datetime as dt
import json

from dateutil.relativedelta import relativedelta
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import today_in_tz
from app.models.app_settings import AppSettings
from app.models.battle_plan import (
    RecurrenceMode,
    RecurrenceOccurrence,
    RecurrenceStatus,
    RecurringTemplate,
    Task,
    TaskStatus,
)
from app.schemas.battle_plan import (
    ProjectRead,
    RecurrencePreviewRequest,
    RecurrenceWindow,
    RecurringChecklistRead,
    RecurringTaskLink,
    RecurringTemplateCreate,
    RecurringTemplatePatch,
    RecurringTemplateRead,
)

from app.services.recurrence.cadence import _cadence
from app.services.recurrence.common import SCHEDULE_FIELDS, _date_in_tz, _json_list, _utc_now
from app.services.recurrence.helpers import _load_template, _replace_checklist, _validate_refs
from app.services.recurrence.preview import _windows_for_preview
from app.services.recurrence.synchronization import (
    _cleanup_future,
    _propagate_template_fields,
    _suppress_pause_interval,
    synchronize,
)
from app.services.recurrence.windows import iter_windows


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
        weekdays_json=json.dumps(sorted(set(body.weekdays))),
        month_day=body.month_day,
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
    paused = _date_in_tz(row.paused_at, settings.app_timezone) if row.paused_at is not None else today
    app_settings = db.execute(select(AppSettings).where(AppSettings.id == 1)).scalar_one_or_none()
    if paused < today:
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


def _to_read_current_tasks(tasks_window: list[Task], today: dt.date) -> list[RecurringTaskLink]:
    return [
        RecurringTaskLink(
            id=task.id, title=task.title, deadline_date=task.deadline_date,
            overdue=task.deadline_date is not None and task.deadline_date < today and task.status != TaskStatus.completed,
        ) for task in tasks_window
    ]


def _to_read_windows(windows: list) -> list[RecurrenceWindow]:
    return [RecurrenceWindow(key=window.key, start=window.start, end=window.end) for window in windows]


def to_read(db: Session, row: RecurringTemplate, settings: Settings) -> RecurringTemplateRead:
    today = today_in_tz(settings.app_timezone)
    app_settings = db.execute(select(AppSettings).where(AppSettings.id == 1)).scalar_one_or_none()
    suppressed_keys = set(db.execute(select(RecurrenceOccurrence.occurrence_key).where(
        RecurrenceOccurrence.template_id == row.id,
        RecurrenceOccurrence.suppressed.is_(True),
        RecurrenceOccurrence.cycle_end >= today,
    )).scalars())
    windows = [
        window for window in iter_windows(
            row, today + relativedelta(years=20), app_settings.week_start if app_settings else "monday"
        ) if (
            window.end >= today
            and window.start >= row.generation_start_date
            and window.key not in suppressed_keys
        )
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
        upcoming=_to_read_windows(windows),
        current_tasks=_to_read_current_tasks(tasks, today),
        cadence=_cadence(row),
        next_occurrence=windows[0].start if windows else None,
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
