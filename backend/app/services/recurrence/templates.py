from __future__ import annotations

import datetime as dt
import json
import uuid

from dateutil.relativedelta import relativedelta
from sqlalchemy import delete, func, select, update
from sqlalchemy.orm import Session, selectinload

from app.core.config import Settings
from app.core.time import today_in_tz
from app.models.app_settings import AppSettings
from app.models.battle_plan import (
    RecurrenceMode,
    RecurrenceOccurrence,
    RecurrenceStatus,
    RecurringPlannedBlockRealization,
    RecurringPlannedBlockState,
    RecurringPreplanningSlot,
    RecurringTemplate,
    Task,
    TaskStatus,
)
from app.schemas.battle_plan import (
    RecurrencePreviewRequest,
    RecurrenceWindow,
    RecurringChecklistRead,
    RecurringTaskLink,
    RecurringTemplateCreate,
    RecurringTemplatePatch,
    RecurringTemplateRead,
    RecurringPreplanningScheduleRead,
    RecurringPreplanningSlotRead,
    RecurringPreplanningUnavailableSlotRead,
    RecurringPreplanningScheduleWrite,
    RecurringPreplanningSlotWrite,
    validate_preplanning_schedule,
)

from app.services.recurrence.cadence import _cadence
from app.services.recurrence.common import (
    SCHEDULE_FIELDS,
    _date_in_tz,
    _json_list,
    _utc_now,
)
from app.services.recurrence.helpers import (
    _load_template,
    _next_position,
    _replace_checklist,
    _validate_refs,
)
from app.services.recurrence.preview import _windows_for_preview
from app.services.recurrence.synchronization import (
    _cleanup_future,
    _propagate_template_fields,
    _rebuild_unprotected_subtasks,
    _suppress_pause_interval,
    synchronize,
)
from app.services.recurrence.windows import iter_windows


def create_template(
    db: Session, body: RecurringTemplateCreate, settings: Settings
) -> RecurringTemplate:
    _validate_refs(db, body.task_type_id)
    title = body.title.strip()
    if not title:
        raise ValueError("Template title is required")
    today = today_in_tz(settings.app_timezone)
    app_settings = db.execute(
        select(AppSettings).where(AppSettings.id == 1)
    ).scalar_one_or_none()
    past, _ = _windows_for_preview(
        body, today, app_settings.week_start if app_settings else "monday"
    )
    if past and not body.confirm_backfill:
        task_count = len(past) * (
            (body.quota_count or 1) if body.mode == RecurrenceMode.quota else 1
        )
        raise ValueError(f"BACKFILL_CONFIRMATION_REQUIRED:{len(past)}:{task_count}")
    row = RecurringTemplate(
        title=title,
        description=body.description,
        task_type_id=body.task_type_id,
        urgency=body.urgency,
        importance=body.importance,
        mode=body.mode,
        frequency=body.frequency,
        interval=body.interval,
        weekdays_json=json.dumps(sorted(set(body.weekdays))),
        month_day=body.month_day,
        quota_count=body.quota_count,
        start_date=body.start_date,
        generation_start_date=body.start_date,
        end_date=body.end_date,
        cycle_limit=body.cycle_limit,
        keep_unfinished_overdue=body.keep_unfinished_overdue,
        position=_next_position(db),
    )
    db.add(row)
    db.flush()
    _replace_checklist(
        db, row, body.checklist_titles if body.mode == RecurrenceMode.scheduled else []
    )
    if body.preplanning_schedule is not None:
        for position, slot in enumerate(body.preplanning_schedule.slots):
            db.add(RecurringPreplanningSlot(
                template_id=row.id,
                slot_key=str(uuid.uuid4()),
                position=position,
                weekday=slot.weekday,
                start_minute=slot.start_minute,
                end_minute=slot.end_minute,
            ))
    db.commit()
    synchronize(db, settings, today=today)
    return _load_template(db, row.id)


def _replace_preplanning_schedule(
    db: Session,
    row: RecurringTemplate,
    schedule: RecurringPreplanningScheduleWrite | None,
) -> None:
    active_by_key = {
        slot.slot_key: slot for slot in row.preplanning_slots if slot.removed_at is None
    }
    removed_slots = sorted(
        (slot for slot in row.preplanning_slots if slot.removed_at is not None),
        key=lambda slot: (slot.removed_at, slot.id),
        reverse=True,
    )
    requested = schedule.slots if schedule is not None else []
    requested_keys = {slot.key for slot in requested if slot.key is not None}
    unknown = requested_keys - active_by_key.keys()
    if unknown:
        raise ValueError("Pre-planning slot not found on this Recurring Task Series")
    restored_by_position: dict[int, RecurringPreplanningSlot] = {}
    for position, value in enumerate(requested):
        if value.key is not None:
            continue
        matches = [
            slot
            for slot in removed_slots
            if slot.weekday == value.weekday
            and slot.start_minute == value.start_minute
            and slot.end_minute == value.end_minute
        ]
        if len(matches) > 1:
            raise ValueError(
                "Keyless pre-planning slot reactivation is ambiguous"
            )
        if matches:
            restored_by_position[position] = matches[0]

    for temporary_position, slot in enumerate(active_by_key.values(), start=1):
        slot.position = -temporary_position
    db.flush()

    removed_at = _utc_now()
    for key, slot in active_by_key.items():
        if key not in requested_keys:
            slot.removed_at = removed_at
    db.flush()

    for position, value in enumerate(requested):
        if value.key is None:
            restored = restored_by_position.get(position)
            if restored is not None:
                restored.position = position
                restored.removed_at = None
                continue
            db.add(RecurringPreplanningSlot(
                template_id=row.id,
                slot_key=str(uuid.uuid4()),
                position=position,
                weekday=value.weekday,
                start_minute=value.start_minute,
                end_minute=value.end_minute,
            ))
            continue
        slot = active_by_key[value.key]
        slot.position = position
        slot.weekday = value.weekday
        slot.start_minute = value.start_minute
        slot.end_minute = value.end_minute


def patch_template(
    db: Session, template_id: int, body: RecurringTemplatePatch, settings: Settings
) -> RecurringTemplate:
    row = _load_template(db, template_id)
    fields = body.model_fields_set - {"confirm_backfill"}
    _validate_refs(
        db, body.task_type_id if "task_type_id" in fields else row.task_type_id
    )
    today = today_in_tz(settings.app_timezone)
    next_mode = body.mode if "mode" in fields and body.mode is not None else row.mode
    next_keep_overdue = (
        body.keep_unfinished_overdue
        if "keep_unfinished_overdue" in fields
        else row.keep_unfinished_overdue
    )
    if next_keep_overdue is None:
        raise ValueError("Keep unfinished overdue must be true or false")
    if next_mode == RecurrenceMode.quota and next_keep_overdue:
        raise ValueError("Quota shortfalls cannot carry into the next period")

    def schedule_value_changed(field: str) -> bool:
        if field == "weekdays":
            return sorted(_json_list(row.weekdays_json)) != sorted(
                set(body.weekdays or [])
            )
        if field == "checklist_titles":
            before = [
                item.title
                for item in sorted(row.checklist_items, key=lambda item: item.position)
            ]
            after = [
                title.strip()
                for title in (body.checklist_titles or [])
                if title.strip()
            ]
            return before != after
        return getattr(row, field) != getattr(body, field)

    cadence_fields = SCHEDULE_FIELDS - {"checklist_titles"}
    cadence_changed = any(
        schedule_value_changed(field) for field in fields & cadence_fields
    )
    checklist_changed = "checklist_titles" in fields and schedule_value_changed(
        "checklist_titles"
    )
    if cadence_changed:
        _cleanup_future(db, row, today, suppress=False)
    for field in fields - {"weekdays", "checklist_titles", "preplanning_schedule"}:
        value = getattr(body, field)
        if field == "title" and value is not None:
            value = value.strip()
        setattr(row, field, value)
    if "weekdays" in fields:
        row.weekdays_json = json.dumps(sorted(set(body.weekdays or [])))
    if "checklist_titles" in fields:
        _replace_checklist(db, row, body.checklist_titles or [])
    active_schedule = RecurringPreplanningScheduleWrite(slots=[
        RecurringPreplanningSlotWrite(
            key=slot.slot_key,
            weekday=slot.weekday,
            start_minute=slot.start_minute,
            end_minute=slot.end_minute,
        )
        for slot in row.preplanning_slots
        if slot.removed_at is None
    ]) if any(slot.removed_at is None for slot in row.preplanning_slots) else None
    next_preplanning_schedule = (
        body.preplanning_schedule
        if "preplanning_schedule" in fields
        else active_schedule
    )
    validate_preplanning_schedule(
        row.mode,
        row.frequency,
        _json_list(row.weekdays_json),
        next_preplanning_schedule,
    )
    if "preplanning_schedule" in fields:
        _replace_preplanning_schedule(db, row, body.preplanning_schedule)
    if cadence_changed:
        # New cadence starts now. Protected old-cadence occurrences coexist as
        # exceptions; they must never push the new generation window forward.
        row.generation_start_date = max(row.start_date, today)
    # Validate the complete edited rule through the same Pydantic contract used for creation.
    RecurrencePreviewRequest(
        mode=row.mode,
        frequency=row.frequency,
        interval=row.interval,
        weekdays=_json_list(row.weekdays_json),
        month_day=row.month_day,
        quota_count=row.quota_count,
        start_date=row.start_date,
        end_date=row.end_date,
        cycle_limit=row.cycle_limit,
    )
    _propagate_template_fields(db, row, today)
    if checklist_changed and row.mode == RecurrenceMode.scheduled:
        _rebuild_unprotected_subtasks(db, row, today, body.checklist_titles or [])
    db.commit()
    synchronize(db, settings, today=today)
    return _load_template(db, row.id)


def pause_template(
    db: Session, template_id: int, settings: Settings
) -> RecurringTemplate:
    row = _load_template(db, template_id)
    if row.status != RecurrenceStatus.active:
        raise ValueError("Only active templates can be paused")
    today = today_in_tz(settings.app_timezone)
    _cleanup_future(db, row, today, suppress=False)
    row.status = RecurrenceStatus.paused
    row.paused_at = _utc_now()
    db.commit()
    return _load_template(db, row.id)


def resume_template(
    db: Session, template_id: int, settings: Settings
) -> RecurringTemplate:
    row = _load_template(db, template_id)
    if row.status != RecurrenceStatus.paused:
        raise ValueError("Only paused templates can be resumed")
    today = today_in_tz(settings.app_timezone)
    paused = (
        _date_in_tz(row.paused_at, settings.app_timezone)
        if row.paused_at is not None
        else today
    )
    app_settings = db.execute(
        select(AppSettings).where(AppSettings.id == 1)
    ).scalar_one_or_none()
    if paused < today:
        _suppress_pause_interval(
            db,
            row,
            paused,
            today,
            app_settings.week_start if app_settings else "monday",
        )
    row.status = RecurrenceStatus.active
    row.paused_at = None
    db.commit()
    synchronize(db, settings, today=today)
    return _load_template(db, row.id)


def end_template(
    db: Session, template_id: int, settings: Settings
) -> RecurringTemplate:
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
    # Detach every generated Task, including archived/trash rows and Session
    # Tasks. Keep their execution roles, completion and block history intact.
    tasks = db.scalars(select(Task).where(Task.recurring_template_id == row.id)).all()
    for task in tasks:
        task.recurring_template = None
    # Only generation tombstones are disposable. Occurrence ledgers attached to
    # surviving Tasks retain their skipped history via the SET NULL foreign key.
    db.execute(
        delete(RecurrenceOccurrence).where(
            RecurrenceOccurrence.template_id == row.id,
            RecurrenceOccurrence.task_id.is_(None),
        )
    )
    db.delete(row)
    db.commit()


def _to_read_current_tasks(
    tasks_window: list[Task], today: dt.date
) -> list[RecurringTaskLink]:
    return [
        RecurringTaskLink(
            id=task.id,
            title=task.title,
            deadline_date=task.deadline_date,
            overdue=task.deadline_date is not None
            and task.deadline_date < today
            and task.status != TaskStatus.completed,
        )
        for task in tasks_window
    ]


def _to_read_windows(windows: list) -> list[RecurrenceWindow]:
    return [
        RecurrenceWindow(key=window.key, start=window.start, end=window.end)
        for window in windows
    ]


def to_read(
    db: Session, row: RecurringTemplate, settings: Settings
) -> RecurringTemplateRead:
    today = today_in_tz(settings.app_timezone)
    app_settings = db.execute(
        select(AppSettings).where(AppSettings.id == 1)
    ).scalar_one_or_none()
    suppressed_keys = set(
        db.execute(
            select(RecurrenceOccurrence.occurrence_key).where(
                RecurrenceOccurrence.template_id == row.id,
                RecurrenceOccurrence.suppressed.is_(True),
                RecurrenceOccurrence.cycle_end >= today,
            )
        ).scalars()
    )
    windows = [
        window
        for window in iter_windows(
            row,
            today + relativedelta(years=20),
            app_settings.week_start if app_settings else "monday",
        )
        if (
            window.end >= today
            and window.start >= row.generation_start_date
            and window.key not in suppressed_keys
        )
    ][:5]
    tasks = list(
        db.execute(
            select(Task)
            .join(RecurrenceOccurrence, RecurrenceOccurrence.task_id == Task.id)
            .where(
                Task.recurring_template_id == row.id,
                Task.parent_id.is_(None),
                Task.status != TaskStatus.completed,
                Task.deleted_at.is_(None),
                Task.archived_at.is_(None),
                RecurrenceOccurrence.skipped.is_(False),
                RecurrenceOccurrence.cycle_start <= today,
            )
            .order_by(RecurrenceOccurrence.cycle_start, Task.id)
        ).scalars()
    )
    active_slots = {
        slot.slot_key: slot
        for slot in row.preplanning_slots
        if slot.removed_at is None
    }
    unavailable_slots = []
    if row.status == RecurrenceStatus.active and active_slots:
        unavailable_slots = [
            RecurringPreplanningUnavailableSlotRead(
                date=occurrence.cycle_start,
                slot_key=realization.slot_key,
                start_minute=active_slots[realization.slot_key].start_minute,
                end_minute=active_slots[realization.slot_key].end_minute,
            )
            for occurrence, realization in db.execute(
                select(RecurrenceOccurrence, RecurringPlannedBlockRealization)
                .join(
                    RecurringPlannedBlockRealization,
                    RecurringPlannedBlockRealization.occurrence_id == RecurrenceOccurrence.id,
                )
                .where(
                    RecurrenceOccurrence.template_id == row.id,
                    RecurrenceOccurrence.cycle_start >= today,
                    RecurringPlannedBlockRealization.state == RecurringPlannedBlockState.untouched,
                    RecurringPlannedBlockRealization.planned_block_id.is_(None),
                )
                .order_by(
                    RecurrenceOccurrence.cycle_start,
                    RecurringPlannedBlockRealization.slot_key,
                )
            ).all()
            if realization.slot_key in active_slots
        ]
    return RecurringTemplateRead(
        id=row.id,
        title=row.title,
        description=row.description,
        task_type_id=row.task_type_id,
        task_type=row.task_type,
        mode=row.mode,
        status=row.status,
        frequency=row.frequency,
        interval=row.interval,
        weekdays=_json_list(row.weekdays_json),
        month_day=row.month_day,
        quota_count=row.quota_count,
        start_date=row.start_date,
        end_date=row.end_date,
        cycle_limit=row.cycle_limit,
        keep_unfinished_overdue=row.keep_unfinished_overdue,
        preplanning_schedule=(
            RecurringPreplanningScheduleRead(
                slots=[
                    RecurringPreplanningSlotRead(
                        id=slot.id,
                        key=slot.slot_key,
                        position=slot.position,
                        weekday=slot.weekday,
                        start_minute=slot.start_minute,
                        end_minute=slot.end_minute,
                    )
                    for slot in row.preplanning_slots
                    if slot.removed_at is None
                ],
                unavailable_slots=unavailable_slots,
            )
            if active_slots else None
        ),
        urgency=row.urgency,
        importance=row.importance,
        paused_at=row.paused_at,
        ended_at=row.ended_at,
        created_at=row.created_at,
        updated_at=row.updated_at,
        checklist_items=[
            RecurringChecklistRead(id=item.id, title=item.title, position=item.position)
            for item in row.checklist_items
        ],
        upcoming=_to_read_windows(windows),
        current_tasks=_to_read_current_tasks(tasks, today),
        cadence=_cadence(row),
        next_occurrence=windows[0].start if windows else None,
    )


def list_templates(
    db: Session, status: RecurrenceStatus, settings: Settings
) -> list[RecurringTemplateRead]:
    if status == RecurrenceStatus.active:
        synchronize(db, settings)
    rows = list(
        db.execute(
            select(RecurringTemplate)
            .where(RecurringTemplate.status == status)
            .options(
                selectinload(RecurringTemplate.task_type),
                selectinload(RecurringTemplate.checklist_items),
                selectinload(RecurringTemplate.preplanning_slots),
            )
            .order_by(func.lower(RecurringTemplate.title), RecurringTemplate.id)
        )
        .scalars()
        .unique()
    )
    return [to_read(db, row, settings) for row in rows]


def get_template(
    db: Session, template_id: int, settings: Settings
) -> RecurringTemplateRead:
    synchronize(db, settings)
    return to_read(db, _load_template(db, template_id), settings)


def template_type_counts(db: Session) -> dict[int, int]:
    return dict(
        db.execute(
            select(RecurringTemplate.task_type_id, func.count(RecurringTemplate.id))
            .where(RecurringTemplate.task_type_id.is_not(None))
            .group_by(RecurringTemplate.task_type_id)
        ).all()
    )


def clear_template_type_references(db: Session, task_type_id: int) -> None:
    db.execute(
        update(RecurringTemplate)
        .where(RecurringTemplate.task_type_id == task_type_id)
        .values(task_type_id=None)
    )
    db.commit()
