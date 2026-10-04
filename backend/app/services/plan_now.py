"""Online, idempotent plan-and-track transaction using the ordinary range journal."""
import datetime as dt
from copy import deepcopy

from sqlalchemy import select

from app.core.time import utc_now
from app.models.activity import PlanNowOperation
from app.models.battle_plan import RecurringPlannedBlockRealization, RecurringPlannedBlockState, Task
from app.models.time_block import BlockLane, TimeBlock
from app.services import activity_reconciliation as rec
from app.services import activity_selection, activity_service, day_service, task_type_service
from app.services import planned_intervals as intervals
from app.services import planned_recording as recording
from app.services.recurrence.protection import protect_task_occurrence


def revision(db, state, timezone, *, undo=False):
    current = activity_service.actuals.get_active_actual_block(db)
    facts = [activity_selection.plans(db, timezone), current.model_dump(mode="json") if current else None, timezone]
    if undo:
        facts.append([recording.facts(r) for r in db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual).order_by(TimeBlock.id))])
        facts.append(state.cursor)
    return recording.digest(facts)


FIELDS = ("id", "day_id", "task_type_id", "task_id", "name", "note", "start_minute", "end_minute", "start_at", "end_at")


def saved_plan(row):
    return {key: rec.stamp(value) if isinstance(value := getattr(row, key), dt.datetime) else value for key in FIELDS}


def result(db, state, timezone, undo_id=None):
    db.flush()
    snapshot = activity_service._snapshot(db, state, timezone)
    snapshot.plan_now_undo = undo_id
    db.commit()
    return snapshot


def execute(db, body, timezone):
    state = activity_service._lock(db)
    timezone = state.reporting_timezone or timezone
    request = body.model_dump(mode="json")
    receipt = db.get(PlanNowOperation, str(body.operation_id))
    if receipt:
        if receipt.request != request:
            raise ValueError("Save ID already used with different content")
        return result(db, state, timezone, None if receipt.payload.get("undone") else receipt.operation_id)
    if body.revision != revision(db, state, timezone):
        raise ValueError("The plan or Current Activity changed. Review the updated preview and save again.")
    now = rec.instant(body.effective_at)
    if not -5 <= (utc_now() - now).total_seconds() <= 30:
        raise ValueError("Save time is stale. Review the preview and save again.")
    current = activity_service.actuals.get_active_actual_block(db)
    if body.current_id != (current.id if current else None):
        raise ValueError("Current Activity changed. Reopen the editor.")
    name = (body.name or "").strip() or None
    type_id = task_type_service.resolve_task_type_id(db, body.task_type_id)
    activity_service.actuals.validate_item(db, type_id, body.task_id)
    selected = (type_id, body.task_id, name)
    same = current is not None and (current.task_type_id, current.task_id, current.name) == selected
    task = day_service._active_task(db, body.task_id, for_update=True, allow_completed=same)
    task_before = dict(id=task.id, ready=task.ready_to_plan, overrides=task.recurrence_overrides_json) if task else None
    end = now + dt.timedelta(minutes=body.minutes)
    plans = intervals.rows(db)
    times = {p.id: intervals.interval(p, timezone) for p in plans}
    matching = next((p for p in plans if (p.task_type_id, p.task_id, p.name) == selected and times[p.id][0] <= now
                     and (times[p.id][1] > now or (same and current.planned_block_id == p.id))), None)
    affected = [p for p in plans if times[p.id][0] < end and times[p.id][1] > now and p is not matching]
    if {p.id for p in affected} != set(body.replace_plan_ids):
        raise ValueError("The affected plans changed as time passed. Review the preview and save again.")
    # Initialize before changing links: Undo retains the true preceding timeline.
    rec.initialize(db, state)
    operations = recording.context(db, state)[1]
    before = deepcopy(rec.replay(state.reconciliation["baseline"], operations))
    scope = min(now, rec.instant(current.start_at)) if current else now
    restore = [dict(start=rec.stamp(scope), end=None, data=None)]
    restore += [dict(start=rec.stamp(max(scope, rec.instant(p["start"]))), end=p["end"], data=p["data"])
                for p in before if (rec.instant(p["end"]) if p["end"] else rec.INF) > scope]
    originals = [saved_plan(p) for p in [*affected, *([matching] if matching else [])]]
    realizations = list(db.scalars(select(RecurringPlannedBlockRealization).where(
        RecurringPlannedBlockRealization.planned_block_id.in_([p["id"] for p in originals]))))
    saved_realizations = [dict(id=r.id, state=r.state.value, planned_block_id=r.planned_block_id) for r in realizations]
    added = []
    deleted = []
    for p in affected:
        a, b = times[p.id]
        if p.task_id:
            protect_task_occurrence(db, p.task_id)
        day_service._mark_generated_planned_block(db, p, RecurringPlannedBlockState.customized if a < now or b > end else RecurringPlannedBlockState.deleted)
        if a < now:
            intervals.set_interval(db, p, a, now, timezone)
            if b > end:
                tail = TimeBlock(lane=BlockLane.planned, task_type_id=p.task_type_id, task_id=p.task_id, name=p.name, note=p.note)
                intervals.set_interval(db, tail, end, b, timezone)
                added.append(tail.id)
        elif b > end:
            intervals.set_interval(db, p, end, b, timezone)
        else:
            activity_selection.detach_plan(db, p.id)
            for actual in db.scalars(select(TimeBlock).where(TimeBlock.planned_block_id == p.id)):
                actual.planned_block_id = None
            deleted.append(p)
    if matching:
        plan = matching
        day_service._mark_generated_planned_block(db, plan, RecurringPlannedBlockState.customized)
    else:
        plan = TimeBlock(lane=BlockLane.planned, task_type_id=type_id, task_id=body.task_id, name=name)
    intervals.set_interval(db, plan, times[matching.id][0] if matching else now, end, timezone)
    if task:
        day_service._validate_recurrence_schedule(task, plan.day)
        task.ready_to_plan = False
        day_service.record_task_overrides(task, {"ready_to_plan"})
        protect_task_occurrence(db, task)
    db.flush()
    if not matching:
        added.append(plan.id)
    # Keep replaced rows until new identities are allocated. SQLite may otherwise
    # reuse a deleted maximum ID, making Undo confuse an original with an addition.
    for old in deleted:
        db.delete(old)
    db.flush()
    # Existing Actual identity and origin are preserved for an adjustment.
    if same:
        active = next(p for p in before if p["data"] and p["end"] is None)
        data = {**active["data"], "planned_block_id": plan.id}
        start = rec.instant(active["start"])
    else:
        source = "plan-now:" + str(body.operation_id)
        data = dict(id=rec.stable_id(source), source=source, origin_start=rec.stamp(now),
                    task_type_id=type_id, task_id=body.task_id, name=name, note=plan.note,
                    planned_block_id=plan.id, created_at=rec.stamp(now), updated_at=rec.stamp(now))
        start = now
    # Re-read intents after detach_plan has removed references to replaced plans.
    operations = recording.context(db, state)[1]
    recording.apply(db, state, state, operations, [dict(start=rec.stamp(start), end=None, data=data)], utc_now(), {"plan_now": str(body.operation_id)})
    db.flush()
    payload = dict(plans=originals, added=added, realizations=saved_realizations, restore=restore,
                   after=revision(db, state, timezone, undo=True), task=task_before, task_version=task.version if task else None)
    db.add(PlanNowOperation(operation_id=str(body.operation_id), request=request, payload=payload))
    return result(db, state, timezone, str(body.operation_id))


def undo(db, operation_id, timezone):
    state = activity_service._lock(db)
    timezone = state.reporting_timezone or timezone
    receipt = db.get(PlanNowOperation, str(operation_id))
    if receipt is None:
        raise ValueError("Plan change not found")
    saved = receipt.payload
    if saved.get("undone"):
        return result(db, state, timezone)
    if revision(db, state, timezone, undo=True) != saved["after"]:
        raise ValueError("The plan or recorded activity changed. Undo is no longer available.")
    if saved.get("task"):
        previous = saved["task"]
        task = db.get(Task, previous["id"])
        if task is None or task.version != saved["task_version"]:
            raise ValueError("The Task changed. Undo is no longer available.")
        task.ready_to_plan = previous["ready"]
        task.recurrence_overrides_json = previous["overrides"]
    for values in saved["plans"]:
        row = db.get(TimeBlock, values["id"])
        if row is None:
            row = TimeBlock(lane=BlockLane.planned)
        for key, value in values.items():
            setattr(row, key, rec.instant(value) if key in {"start_at", "end_at"} and value else value)
        db.add(row)
    db.flush()
    for values in saved["realizations"]:
        row = db.get(RecurringPlannedBlockRealization, values["id"])
        if row is None:
            raise ValueError("Recurring plan changed. Undo is no longer available.")
        row.state = RecurringPlannedBlockState(values["state"])
        row.planned_block_id = values["planned_block_id"]
    for plan_id in saved["added"]:
        activity_selection.detach_plan(db, plan_id)
    working, operations = recording.context(db, state)
    recording.apply(db, state, working, operations, saved["restore"], utc_now(), {"undo_plan_now": str(operation_id)})
    for plan_id in saved["added"]:
        row = db.get(TimeBlock, plan_id)
        if row:
            db.delete(row)
    receipt.payload = {**saved, "undone": True}
    return result(db, state, timezone)
