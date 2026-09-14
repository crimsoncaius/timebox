"""Online, previewed range replacement with journal-backed atomic Undo."""
import datetime as dt
import hashlib
import json
import uuid
from types import SimpleNamespace

from sqlalchemy import select, update
from sqlalchemy.dialects import postgresql, sqlite

from app.models.activity import ActivityOperation, ActivityState, PlannedRecordingUndo
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.time_block import ActualBlockRead
from app.services import actual_block_service as actuals
from app.services import activity_reconciliation as rec


def lock(db):
    insert = postgresql.insert if db.bind.dialect.name == "postgresql" else sqlite.insert
    db.execute(insert(ActivityState).values(id=1, enabled=False, cursor=0).on_conflict_do_nothing())
    db.execute(update(ActivityState).where(ActivityState.id == 1).values(cursor=ActivityState.cursor))
    return db.get(ActivityState, 1, populate_existing=True)


def rows(db, start, end):
    return list(db.scalars(select(TimeBlock).where(
        TimeBlock.lane == BlockLane.actual, TimeBlock.start_at < end,
        (TimeBlock.end_at.is_(None)) | (TimeBlock.end_at > start),
    ).order_by(TimeBlock.start_at, TimeBlock.id).with_for_update(of=TimeBlock)))


def facts(row):
    return ActualBlockRead.model_validate(row).model_dump(mode="json")


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def context(db, state):
    if state.enabled:
        rec.initialize(db, state)
        operations = list(db.scalars(select(ActivityOperation).where(ActivityOperation.intent.is_not(None))))
        return state, operations
    # Legacy recording still uses the same range engine, but never enables tracking
    # or makes an old journal authoritative over independent legacy corrections.
    temporary = SimpleNamespace(reconciliation=None, cursor=state.cursor)
    rec.initialize(db, temporary)
    return temporary, []


def apply(db, state, working, operations, ranges, now, extra):
    token = str(uuid.uuid4())
    # Confirmation replaces the observed timeline, even if a calibrated device's
    # last admitted timestamp was slightly ahead of the server clock.
    now = max([now, *[rec.instant(op.envelope["action_at"]) + dt.timedelta(microseconds=1) for op in operations]])
    state.cursor += 1
    operation = ActivityOperation(operation_id=token, device_id="planned-recording",
        sequence=state.cursor, cursor=state.cursor, outcome="applied", effective_at=now,
        envelope={"action_at": rec.stamp(now), **extra}, intent={"ranges": ranges})
    if state.enabled:
        db.add(operation)
        db.flush()
    rec.materialize(db, working, [*operations, operation])
    if not state.enabled:
        operation.intent = None
    return operation


def record(db, planned_id, settings, now, body):
    state = lock(db)
    if settings.activity_tracking_dev and not state.enabled:
        from app.services import activity_service
        state = activity_service._lock(db)
    plan = actuals._planned_row(db, planned_id, for_update=True)
    zone = actuals.get_zone(state.reporting_timezone or settings.app_timezone)
    midnight = dt.datetime.combine(plan.day.date, dt.time.min, tzinfo=zone)
    start = rec.instant(midnight + dt.timedelta(minutes=plan.start_minute))
    planned_end = rec.instant(midnight + dt.timedelta(minutes=plan.end_minute))
    end = min(planned_end, rec.instant(body.until) if body.until else now)
    if end > now or end <= start:
        raise ValueError("Recording is available after the Planned Block starts and cannot include future time")
    actuals._validate_item(db, plan.task_type_id, plan.task_id, retrospective_end=end)
    conflicts = rows(db, start, end)
    copied = {key: getattr(plan, key) for key in ("task_type_id", "task_id", "name", "note")}
    fingerprint = digest([plan.id, plan.day.date.isoformat(), plan.start_minute, plan.end_minute,
                          copied, rec.stamp(start), rec.stamp(end), [facts(r) for r in conflicts]])
    result = dict(start_at=rec.stamp(start), end_at=rec.stamp(end), fingerprint=fingerprint,
                  conflicts=[facts(r) for r in conflicts], actual_block=None, undo_token=None,
                  replacement=copied, stale=body.fingerprint is not None and body.fingerprint != fingerprint)
    if len(conflicts) == 1:
        row = conflicts[0]
        if row.planned_block_id == plan.id and rec.instant(row.start_at) == start and row.end_at and rec.instant(row.end_at) == end and all(getattr(row, k) == v for k, v in copied.items()):
            db.commit()
            return {**result, "status": "already_recorded", "actual_block": facts(row)}
    if result["stale"] or (conflicts and body.fingerprint != fingerprint):
        db.commit()
        return {**result, "status": "confirmation_required"}
    working, operations = context(db, state)
    before = rec.replay(working.reconciliation["baseline"], operations)
    scope_start = min([start, *[rec.instant(r.start_at) for r in conflicts]])
    scope_end = max([end, *[rec.instant(r.end_at) if r.end_at else rec.INF for r in conflicts]])
    restore = [dict(start=rec.stamp(scope_start), end=None if scope_end == rec.INF else rec.stamp(scope_end), data=None)]
    restore += [dict(start=rec.stamp(max(scope_start, rec.instant(p["start"]))),
                     end=None if min(scope_end, rec.instant(p["end"]) if p["end"] else rec.INF) == rec.INF
                     else rec.stamp(min(scope_end, rec.instant(p["end"]) if p["end"] else rec.INF)), data=p["data"]) for p in before
                if rec.instant(p["start"]) < scope_end and (rec.instant(p["end"]) if p["end"] else rec.INF) > scope_start]
    source = "record:" + str(uuid.uuid4())
    data = {**copied, "planned_block_id": plan.id, "id": rec.stable_id(source), "source": source,
            "origin_start": rec.stamp(start), "created_at": rec.stamp(now), "updated_at": rec.stamp(now)}
    operation = apply(db, state, working, operations,
        [dict(start=rec.stamp(start), end=rec.stamp(end), data=data)], now,
        {"planned_id": plan.id, "restore": restore, "scope_start": rec.stamp(scope_start),
         "scope_end": None if scope_end == rec.INF else rec.stamp(scope_end)})
    db.add(PlannedRecordingUndo(token=operation.operation_id,
        payload={**operation.envelope, "after": digest([facts(r) for r in rows(db, scope_start, scope_end)])}))
    if plan.task_id:
        actuals.protect_task_occurrence(db, plan.task_id)
    actual = facts(db.get(TimeBlock, data["id"]))
    db.commit()
    return {**result, "status": "recorded", "actual_block": actual, "undo_token": operation.operation_id}


def undo(db, planned_id, token, now):
    state = lock(db)
    operation = db.get(PlannedRecordingUndo, token)
    if operation is None or operation.payload.get("planned_id") != planned_id:
        return False  # Allow existing legacy Undo tokens to use their original path.
    saved = operation.payload
    start, end = rec.instant(saved["scope_start"]), rec.instant(saved["scope_end"]) if saved["scope_end"] else rec.INF
    if saved.get("undone") or digest([facts(r) for r in rows(db, start, end)]) != saved["after"]:
        raise ValueError("Recorded activity changed; Undo is no longer available. Edit or delete the records to correct them.")
    # Foreign-key deletion or detachment outside the range must never resurrect a link.
    for part in saved["restore"]:
        data = part["data"]
        if data and data.get("planned_block_id"):
            plan = db.get(TimeBlock, data["planned_block_id"])
            if plan is None or (plan.task_type_id, plan.task_id) != (data["task_type_id"], data.get("task_id")):
                raise ValueError("A linked plan changed; Undo is no longer available")
    working, operations = context(db, state)
    apply(db, state, working, operations, saved["restore"], now, {"undo_of": token})
    operation.payload = {**saved, "undone": True}
    db.commit()
    return True
