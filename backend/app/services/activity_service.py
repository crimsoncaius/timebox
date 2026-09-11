"""Transactional admission, deterministic range replay and canonical snapshots.

All reads and commands lock the singleton so the cursor and records form one
snapshot even at READ COMMITTED. Receipts and interval changes commit together.
Original online receipts remain replayable across the gated protocol upgrade.
"""
import datetime as dt

from sqlalchemy import func, select, update
from sqlalchemy.dialects import postgresql, sqlite
from sqlalchemy.orm import Session

from app.models.activity import ActivityOperation, ActivityState
from app.models.time_block import BlockLane, TimeBlock
from app.models.task_type import TaskType
from app.schemas.activity import ActivityCommand, ActivitySnapshot, ActivityAcknowledgement
from app.schemas.time_block import ActualBlockRead
from app.services import actual_block_service as actuals
from app.services import activity_reconciliation as reconciliation
from app.services import activity_selection


def _lock(db: Session) -> ActivityState:
    insert = postgresql.insert if db.bind.dialect.name == "postgresql" else sqlite.insert
    db.execute(insert(ActivityState).values(id=1, enabled=False, cursor=0).on_conflict_do_nothing())
    # A write lock works for SQLite as well as PostgreSQL, including first start.
    db.execute(update(ActivityState).where(ActivityState.id == 1).values(cursor=ActivityState.cursor))
    state = db.get(ActivityState, 1, populate_existing=True)
    if not state.enabled:
        if db.scalar(select(TimeBlock.id).where(TimeBlock.lane == BlockLane.actual).limit(1)):
            raise ValueError("Activity development requires an isolated database without legacy Actuals")
        state.enabled = True
        db.flush()
    return state


def _snapshot(db, state, timezone, acknowledgement=None):
    timezone = state.reporting_timezone or timezone
    records = [ActualBlockRead.model_validate(row) for row in db.scalars(
        select(TimeBlock).where(TimeBlock.lane == BlockLane.actual, TimeBlock.start_at.is_not(None))
        .order_by(TimeBlock.start_at, TimeBlock.id)
    )]
    return ActivitySnapshot(
        cursor=state.cursor, server_at=dt.datetime.now(dt.timezone.utc),
        reporting_timezone=timezone, reporting_timezone_initialized=state.reporting_timezone is not None, records=records,
        plans=activity_selection.plans(db, timezone),
        task_types=list(db.scalars(select(TaskType))),
        current=next((row for row in records if row.end_at is None), None),
        acknowledgement=acknowledgement,
        tombstones=(state.reconciliation or {}).get("tombstones", []),
        provenance=(state.reconciliation or {}).get("provenance", {}),
        coverage=reconciliation.coverage(state, list(db.scalars(select(ActivityOperation)))),
        operation_outcomes={op.operation_id: {"device_id": op.device_id, "outcome": op.outcome}
                            for op in db.scalars(select(ActivityOperation)) if op.intent},
    )


def read(db: Session, timezone: str) -> ActivitySnapshot:
    state = _lock(db)
    result = _snapshot(db, state, timezone)
    db.commit()
    return result


def execute(db: Session, body: ActivityCommand, timezone: str) -> ActivitySnapshot:
    state = _lock(db)
    timezone = state.reporting_timezone or timezone
    envelope = body.model_dump(mode="json")
    previous = db.get(ActivityOperation, str(body.operation_id))
    if previous:
        if ActivityCommand.model_validate(previous.envelope).model_dump(mode="json") != envelope:
            raise ValueError("Operation ID already used with different content")
        effective_at = previous.effective_at
        if effective_at is not None and effective_at.tzinfo is None:
            effective_at = effective_at.replace(tzinfo=dt.timezone.utc)
        if effective_at is not None:
            effective_at = effective_at.astimezone(dt.timezone.utc)
        result = _snapshot(db, state, timezone, ActivityAcknowledgement(
            operation_id=previous.operation_id, outcome=previous.outcome,
            effective_at=effective_at,
        ))
        db.commit()
        return result
    if body.effective.mode != "server_now" or state.reconciliation is not None or body.selection_snapshot or body.task_id is not None or body.planned_block_id is not None:
        reconciliation.initialize(db, state)
        operations = list(db.scalars(select(ActivityOperation)))
        intent, effective_at = reconciliation.prepare(db, state, body, operations, timezone)
        state.cursor += 1
        operation = ActivityOperation(operation_id=str(body.operation_id), device_id=body.device_id,
                                      sequence=body.sequence, envelope=envelope, cursor=state.cursor,
                                      outcome="applied", effective_at=effective_at, intent=intent)
        db.add(operation)
        operations.append(operation)
        reconciliation.materialize(db, state, operations)
        db.flush()
        result = _snapshot(db, state, timezone, ActivityAcknowledgement(
            operation_id=operation.operation_id, outcome=operation.outcome, effective_at=effective_at))
        db.commit()
        return result
    last = db.scalar(select(func.max(ActivityOperation.sequence)).where(ActivityOperation.device_id == body.device_id))
    if last is not None and body.sequence <= last:
        raise ValueError("Device sequence must increase; retry the original operation ID")
    current = actuals.get_active_actual_block(db)
    outcome = "applied"
    effective_at = None
    # A queued command names its immutable predecessor, never a guessed server ID.
    # Only an uninterrupted applied chain from this installation may advance it.
    predecessor = db.get(ActivityOperation, str(body.predecessor_id)) if body.predecessor_id else None
    follows = bool(predecessor and predecessor.device_id == body.device_id
                   and predecessor.sequence < body.sequence and predecessor.outcome == "applied"
                   and predecessor.cursor == state.cursor)
    if (body.predecessor_id and not follows) or (not body.predecessor_id and (
            body.base_cursor != state.cursor or body.target_id != (current.id if current else None))):
        outcome = "conflict"
    elif (body.kind == "start") == (current is not None):
        outcome = "conflict"
    else:
        requested = body.effective.at if body.effective.mode == "instant" else None
        if body.effective.mode == "instant":
            if requested is None or requested != body.action_at:
                raise ValueError("Immediate offline commands require their original action instant")
            if requested > dt.datetime.now(dt.timezone.utc) + dt.timedelta(seconds=5):
                raise ValueError("Activity instant is in the future; check the device clock")
            latest_end = db.scalar(select(func.max(TimeBlock.end_at)).where(TimeBlock.lane == BlockLane.actual))
            if latest_end is not None:
                if latest_end.tzinfo is None:
                    latest_end = latest_end.replace(tzinfo=dt.timezone.utc)
                if requested < latest_end:
                    raise ValueError("Activity instant overlaps saved history")
        selection = activity_selection.resolve(db, body, requested or dt.datetime.now(dt.timezone.utc), timezone) if body.kind != "stop" else {}
        effective_at = actuals.transition_unplanned_activity(
            db, kind=body.kind, task_type_id=selection.get("task_type_id"), name=selection.get("name"),
            effective_at=requested,
        )
        if body.kind != "stop":
            row = db.get(TimeBlock, actuals.get_active_actual_block(db).id)
            row.activity_source = str(body.operation_id)
            for field, value in selection.items():
                setattr(row, field, value)
            if row.task_id is not None:
                actuals.protect_task_occurrence(db, row.task_id)
    state.cursor += 1
    db.add(ActivityOperation(operation_id=str(body.operation_id), device_id=body.device_id,
                             sequence=body.sequence, envelope=envelope, cursor=state.cursor,
                             outcome=outcome, effective_at=effective_at))
    db.flush()
    result = _snapshot(db, state, timezone, ActivityAcknowledgement(
        operation_id=str(body.operation_id), outcome=outcome, effective_at=effective_at,
    ))
    db.commit()
    return result


def set_reporting_timezone(db: Session, timezone: str, fallback: str, *, initialize: bool):
    from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
    try:
        ZoneInfo(timezone)
    except (ZoneInfoNotFoundError, ValueError):
        raise ValueError("Choose a valid IANA time zone")
    state = _lock(db)
    if not initialize or state.reporting_timezone is None:
        if state.reporting_timezone != timezone:
            state.reporting_timezone = timezone
            state.cursor += 1
    result = _snapshot(db, state, fallback)
    db.commit()
    return result


def reporting_settings(db: Session, settings):
    state = db.get(ActivityState, 1)
    if state and state.reporting_timezone:
        return settings.model_copy(update={"app_timezone": state.reporting_timezone})
    return settings
