"""Explicit restored-copy cutover; schema installation never runs this import."""
import datetime as dt
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.orm import Session
from sqlalchemy.orm.attributes import flag_modified

from app.db.activity_admission import admission
from app.models.activity import ActivityState, ActivityOperation
from app.models.time_block import TimeBlock, BlockLane
from app.services import activity_reconciliation as reconciliation


def record_values(row):
    return {column.name: (reconciliation.stamp(getattr(row, column.name))
            if isinstance(getattr(row, column.name), dt.datetime) else getattr(row, column.name))
            for column in TimeBlock.__table__.columns}


def local_instant(day, minute, zone):
    local = dt.datetime.combine(day, dt.time()) + dt.timedelta(minutes=minute)
    candidates = {local.replace(tzinfo=zone, fold=fold).astimezone(dt.timezone.utc)
                  for fold in (0, 1)
                  if local.replace(tzinfo=zone, fold=fold).astimezone(dt.timezone.utc)
                  .astimezone(zone).replace(tzinfo=None) == local}
    if len(candidates) != 1:
        raise ValueError(f"Ambiguous or nonexistent legacy local time: {local}")
    return candidates.pop()


def preflight(db, timezone):
    zone = ZoneInfo(timezone)
    rows = list(db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual).order_by(TimeBlock.id)))
    archive, intervals = [], []
    for row in rows:
        source = record_values(row)
        if row.task_type is None or (row.task_id is not None and row.task is None):
            raise ValueError(f"Invalid reference on Actual {row.id}")
        if row.task is not None and row.task.recurrence_kind == "quota_parent":
            raise ValueError(f"Quota Tracker cannot receive Actual {row.id}")
        if row.planned_block_id is not None and (row.planned_block is None or row.planned_block.lane != BlockLane.planned):
            raise ValueError(f"Invalid plan correspondence on Actual {row.id}")
        if row.start_at is None:
            if row.day is None or row.start_minute is None or row.end_minute is None or not 0 <= row.start_minute < row.end_minute <= 1440:
                raise ValueError(f"Invalid legacy grid Actual {row.id}")
            start, end = (local_instant(row.day.date, minute, zone) for minute in (row.start_minute, row.end_minute))
            source["reporting_date"] = row.day.date.isoformat()
        else:
            start = reconciliation.instant(row.start_at)
            end = reconciliation.instant(row.end_at) if row.end_at else None
        if end is not None and end <= start:
            raise ValueError(f"Invalid interval on Actual {row.id}")
        intervals.append((start, end, row))
        archive.append(source)
    previous_end = None
    for index, (start, end, row) in enumerate(sorted(intervals, key=lambda item: item[0])):
        if index and (previous_end is None or start < previous_end):
            raise ValueError(f"Overlapping or multiple running Actuals at {row.id}")
        previous_end = end
    return archive, intervals


def apply(engine, timezone):
    with admission(engine, exclusive=True) as connection, Session(connection) as db:
        state = db.get(ActivityState, 1)
        if state and state.cutover:
            return state.cutover
        if state and state.enabled:
            raise ValueError("Already enabled development timeline is not a legacy cutover")
        archive, intervals = preflight(db, timezone)
        state = state or ActivityState(id=1, cursor=0, enabled=False)
        if db.scalar(select(ActivityOperation.operation_id).limit(1)):
            raise ValueError("Legacy database unexpectedly contains activity operations")
        db.add(state)
        state.cutover = {"version": 1, "timezone": timezone, "source": archive,
                         "paused": False, "baseline_cursor": state.cursor or 0}
        for start, end, row in intervals:
            original_updated = row.updated_at
            row.day_id = row.start_minute = row.end_minute = None
            row.start_at, row.end_at = start, end
            row.activity_source = f"baseline:{row.id}"
            row.updated_at = original_updated
            flag_modified(row, "updated_at")
        db.flush()
        state.cutover = {**state.cutover, "imported": [record_values(row) for _, _, row in intervals]}
        state.reporting_timezone = timezone
        reconciliation.initialize(db, state)
        state.enabled = True
        db.commit()
        return state.cutover


def pause(engine, paused=True):
    with admission(engine, exclusive=True) as connection, Session(connection) as db:
        state = db.get(ActivityState, 1)
        if not state or not state.cutover:
            raise ValueError("No completed cutover")
        state.cutover = {**state.cutover, "paused": paused}
        db.commit()


def rollback(engine):
    """Only a cutover with no subsequent canonical mutation may reopen legacy."""
    with admission(engine, exclusive=True) as connection, Session(connection) as db:
        state = db.get(ActivityState, 1)
        if not state or not state.cutover:
            raise ValueError("No completed cutover")
        current = [record_values(row) for row in db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual).order_by(TimeBlock.id))]
        if (current != state.cutover.get("imported") or state.cursor != state.cutover["baseline_cursor"]
                or db.scalar(select(ActivityOperation.operation_id).limit(1))):
            raise ValueError("New writes exist: keep legacy writers closed and repair forward")
        for source in state.cutover["source"]:
            row = db.get(TimeBlock, source["id"])
            if row is None:
                raise ValueError("Records changed: repair forward")
            for key, value in source.items():
                if key == "reporting_date":
                    continue
                if key in {"start_at", "end_at", "created_at", "updated_at"} and value:
                    value = dt.datetime.fromisoformat(value)
                setattr(row, key, value)
        state.enabled = False
        state.reconciliation = state.reporting_timezone = state.check_in = state.cutover = None
        db.commit()
