"""Snapshot planning context without giving a plan ownership of recording."""
import datetime as dt
from zoneinfo import ZoneInfo

from sqlalchemy import select

from app.models.time_block import TimeBlock, BlockLane
from app.models.battle_plan import Task
from app.services import actual_block_service as actuals


def plans(db, timezone):
    result = []
    for row in db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.planned)):
        midnight = dt.datetime.combine(row.day.date, dt.time(), ZoneInfo(timezone))
        result.append(dict(id=row.id, task_type_id=row.task_type_id, task_id=row.task_id,
                           name=row.name, note=row.note,
                           start_at=(midnight + dt.timedelta(minutes=row.start_minute)).isoformat(),
                           end_at=(midnight + dt.timedelta(minutes=row.end_minute)).isoformat()))
    return result


def resolve(db, body, start, timezone):
    plan_id = body.planned_block_id
    type_id, task_id, name, note = body.task_type_id, body.task_id, body.name, body.note
    if body.kind == "start" and not body.selection_snapshot and all(
            value is None for value in (plan_id, type_id, task_id, name)):
        plan = next((p for p in plans(db, timezone)
                     if dt.datetime.fromisoformat(p["start_at"]) <= start < dt.datetime.fromisoformat(p["end_at"])), None)
        if plan:
            plan_id, type_id, task_id, name, note = (plan[k] for k in ("id", "task_type_id", "task_id", "name", "note"))
    if plan_id is not None:
        plan = db.get(TimeBlock, plan_id)
        if body.selection_snapshot:
            # A delayed command retains its captured facts. A reclassified or
            # deleted plan cannot receive a stale correspondence on reconnect.
            if plan is None or plan.lane != BlockLane.planned or (plan.task_type_id, plan.task_id) != (type_id, task_id):
                plan_id = None
        else:
            type_id, task_id, name = actuals._resolve_origin_item(
                db, task_type_id=type_id, task_id=task_id, planned_block_id=plan_id)
            note = plan.note
    elif task_id is not None and not body.selection_snapshot:
        task = db.get(Task, task_id)
        if task is None:
            raise ValueError("Task not found")
        type_id, name = task.task_type_id, task.title
    if body.kind == "switch" and type_id is None and task_id is None:
        raise ValueError("Task Type is required when switching")
    type_id = type_id or actuals._get_or_create_unspecified_task_type(db).id
    actuals._validate_item(db, type_id, task_id)
    return dict(task_type_id=type_id, task_id=task_id, planned_block_id=plan_id, name=name, note=note)


def lock_if_enabled(db):
    from app.models.activity import ActivityState
    from sqlalchemy import update
    db.execute(update(ActivityState).where(ActivityState.id == 1, ActivityState.enabled.is_(True))
               .values(cursor=ActivityState.cursor))


def detach_plan(db, plan_id):
    """Carry explicit unlink through future replay without changing recorded facts."""
    from copy import deepcopy
    from app.models.activity import ActivityState, ActivityOperation
    state = db.get(ActivityState, 1)
    if state and state.enabled:
        state.cursor += 1
        if state.reconciliation:
            value = deepcopy(state.reconciliation)
            for part in value["baseline"]:
                if part["data"] and part["data"].get("planned_block_id") == plan_id:
                    part["data"]["planned_block_id"] = None
            state.reconciliation = value
            for op in db.scalars(select(ActivityOperation)):
                if op.intent:
                    intent = deepcopy(op.intent)
                    for part in intent["ranges"]:
                        if part["data"] and part["data"].get("planned_block_id") == plan_id:
                            part["data"]["planned_block_id"] = None
                    op.intent = intent
