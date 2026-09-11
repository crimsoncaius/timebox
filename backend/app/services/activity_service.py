"""Online admission with explicit revision conflicts, not arrival-order replay.

All reads and commands lock the singleton so the cursor and records form one
snapshot even at READ COMMITTED. Receipts and interval changes commit together.
The immutable envelope is retained for the later offline reconciliation slice.
"""
import datetime as dt

from sqlalchemy import func, select, update
from sqlalchemy.dialects import postgresql, sqlite
from sqlalchemy.orm import Session

from app.models.activity import ActivityOperation, ActivityState
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.activity import ActivityCommand, ActivitySnapshot, ActivityAcknowledgement
from app.schemas.time_block import ActualBlockRead
from app.services import actual_block_service as actuals


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
    records = [ActualBlockRead.model_validate(row) for row in db.scalars(
        select(TimeBlock).where(TimeBlock.lane == BlockLane.actual, TimeBlock.start_at.is_not(None))
        .order_by(TimeBlock.start_at, TimeBlock.id)
    )]
    return ActivitySnapshot(
        cursor=state.cursor, server_at=dt.datetime.now(dt.timezone.utc),
        reporting_timezone=timezone, records=records,
        current=next((row for row in records if row.end_at is None), None),
        acknowledgement=acknowledgement,
    )


def read(db: Session, timezone: str) -> ActivitySnapshot:
    state = _lock(db)
    result = _snapshot(db, state, timezone)
    db.commit()
    return result


def execute(db: Session, body: ActivityCommand, timezone: str) -> ActivitySnapshot:
    state = _lock(db)
    envelope = body.model_dump(mode="json")
    previous = db.get(ActivityOperation, str(body.operation_id))
    if previous:
        if previous.envelope != envelope:
            raise ValueError("Operation ID already used with different content")
        effective_at = previous.effective_at
        if effective_at is not None and effective_at.tzinfo is None:
            effective_at = effective_at.replace(tzinfo=dt.timezone.utc)
        result = _snapshot(db, state, timezone, ActivityAcknowledgement(
            operation_id=previous.operation_id, outcome=previous.outcome,
            effective_at=effective_at,
        ))
        db.commit()
        return result
    last = db.scalar(select(func.max(ActivityOperation.sequence)).where(ActivityOperation.device_id == body.device_id))
    if last is not None and body.sequence <= last:
        raise ValueError("Device sequence must increase; retry the original operation ID")
    current = actuals.get_active_actual_block(db)
    outcome = "applied"
    effective_at = None
    if body.base_cursor != state.cursor or body.target_id != (current.id if current else None):
        outcome = "conflict"
    elif (body.kind == "start") == (current is not None):
        outcome = "conflict"
    else:
        effective_at = actuals.transition_unplanned_activity(
            db, kind=body.kind, task_type_id=body.task_type_id, name=body.name,
        )
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
