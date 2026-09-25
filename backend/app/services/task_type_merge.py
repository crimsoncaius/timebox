"""Preview and atomically consolidate separate Task Type branches.

The merge route takes exclusive request admission. Preview tokens capture identities,
paths and versions, never usage counts: work created after a preview is included.
"""

from __future__ import annotations

import hashlib
import json

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.core.time import utc_now
from app.models.activity import ActivityOperation, ActivityState, PlannedRecordingUndo
from app.models.battle_plan import RecurringTemplate, Task, TaskCompletionOperation, TaskStatus
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.task_type import TaskTypeMergeChange, TaskTypeMergePreview
from app.services.task_type_service import _touch_days, list_task_types


class StaleMergePreview(ValueError):
    pass


def preview(db: Session, source_id: int, target_id: int) -> TaskTypeMergePreview:
    rows = list_task_types(db)
    by_id = {r.id: r for r in rows}
    source, target = by_id.get(source_id), by_id.get(target_id)
    if source is None or target is None:
        raise ValueError("Task type not found")
    if "unspecified" in (source.name, target.name):
        raise ValueError("The unspecified task type cannot be merged")
    if (
        source.id == target.id
        or source.name.startswith(target.name + "/")
        or target.name.startswith(source.name + "/")
    ):
        raise ValueError(
            "Choose separate branches; a type cannot merge with itself, an ancestor or a descendant"
        )
    branch = [r for r in rows if r.id == source.id or r.name.startswith(source.name + "/")]
    destination = [r for r in rows if r.id == target.id or r.name.startswith(target.name + "/")]
    fingerprint = [(r.id, r.name, r.updated_at.isoformat()) for r in branch + destination]
    token = hashlib.sha256(json.dumps(fingerprint).encode()).hexdigest()
    names = {r.name for r in destination}
    ids = [r.id for r in branch]
    tasks = list(db.scalars(select(Task).where(Task.task_type_id.in_(ids))))
    blocks = list(db.scalars(select(TimeBlock).where(TimeBlock.task_type_id.in_(ids))))
    series = list(db.scalars(select(RecurringTemplate.id).where(RecurringTemplate.task_type_id.in_(ids))))
    return TaskTypeMergePreview(
        source_id=source.id,
        source_name=source.name,
        target_id=target.id,
        target_name=target.name,
        preview_token=token,
        changes=[
            TaskTypeMergeChange(
                source_id=r.id,
                source_name=r.name,
                target_name=target.name + r.name[len(source.name) :],
                action="combine" if target.name + r.name[len(source.name) :] in names else "move",
            )
            for r in branch
        ],
        task_count=len(tasks),
        completed_task_count=sum(t.status == TaskStatus.completed for t in tasks),
        archived_task_count=sum(t.archived_at is not None and t.deleted_at is None for t in tasks),
        trashed_task_count=sum(t.deleted_at is not None for t in tasks),
        planned_block_count=sum(b.lane == BlockLane.planned for b in blocks),
        actual_block_count=sum(b.lane == BlockLane.actual for b in blocks),
        recurring_series_count=len(series),
    )


def _remap(value, mapping):
    """Remap classification only in mutable replay/undo data, never command receipts."""
    if isinstance(value, list):
        return [_remap(v, mapping) for v in value]
    if isinstance(value, dict):
        return {
            k: mapping.get(v, v) if k == "task_type_id" and isinstance(v, int) else _remap(v, mapping)
            for k, v in value.items()
        }
    return value


def merge(db: Session, source_id: int, target_id: int, token: str | None) -> TaskTypeMergePreview:
    plan = preview(db, source_id, target_id)
    if not token or token != plan.preview_token:
        raise StaleMergePreview(
            "Task Type branches changed. Review the refreshed merge before confirming again."
        )
    rows = {r.name: r for r in list_task_types(db)}
    now = utc_now()
    mapping = {}
    for change in plan.changes:
        source = db.get(TaskType, change.source_id)
        if change.action == "move":
            source.name = change.target_name
            source.updated_at = now
        else:
            target = rows[change.target_name]
            mapping[source.id] = target.id
            source.is_merged = True
            source.merged_into_id = target.id
            # Keep the ID reserved for delayed devices, while freeing its public path.
            source.name = f"__merged_{source.id}_{plan.preview_token}"
            source.updated_at = now
            target.updated_at = now
    day_ids = list(
        db.scalars(
            select(TimeBlock.day_id)
            .where(
                TimeBlock.task_type_id.in_([c.source_id for c in plan.changes]), TimeBlock.day_id.is_not(None)
            )
            .distinct()
        )
    )
    for old, new in mapping.items():
        for model in (Task, RecurringTemplate, TimeBlock):
            db.execute(
                update(model).where(model.task_type_id == old).values(task_type_id=new, updated_at=now)
            )
        db.execute(update(TaskType).where(TaskType.merged_into_id == old).values(merged_into_id=new))
    _touch_days(db, day_ids)
    state = db.get(ActivityState, 1)
    if state:
        state.cursor += 1
        if state.reconciliation:
            state.reconciliation = _remap(state.reconciliation, mapping)
    for op in db.scalars(select(ActivityOperation).where(ActivityOperation.intent.is_not(None))):
        op.intent = _remap(op.intent, mapping)
    for undo in db.scalars(select(PlannedRecordingUndo)):
        undo.payload = _remap(undo.payload, mapping)
    for op in db.scalars(select(TaskCompletionOperation)):
        op.snapshot_json = json.dumps(_remap(json.loads(op.snapshot_json), mapping))
    db.flush()
    return plan
