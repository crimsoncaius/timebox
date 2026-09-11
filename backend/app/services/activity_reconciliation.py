"""Deterministic range reconciliation for the gated activity journal.

Action timestamps are already calibrated by the author. Arrival cursors select
what an author knew, but never choose a winner. Empty paint is durable stop/delete
intent, not the absence of a row. Replay retains it just like occupied time.
"""
import datetime as dt
import hashlib

from sqlalchemy import select

from app.models.activity import ActivityOperation
from app.models.time_block import BlockLane, TimeBlock
from app.schemas.time_block import ActualBlockRead
from app.services import actual_block_service as actuals

INF = dt.datetime.max.replace(tzinfo=dt.timezone.utc)


def instant(value):
    if isinstance(value, str):
        value = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    return value.replace(tzinfo=dt.timezone.utc) if value.tzinfo is None else value.astimezone(dt.timezone.utc)


def stamp(value):
    return instant(value).isoformat()


def order(operation):
    return (instant(operation.envelope["action_at"]), operation.device_id,
            operation.sequence, operation.operation_id)


def paint(pieces, start, end, data, owner):
    result = []
    for piece in pieces:
        a, b = instant(piece["start"]), instant(piece["end"]) if piece["end"] else INF
        if a >= end or b <= start:
            result.append(piece)
        else:
            if a < start:
                result.append({**piece, "end": stamp(start)})
            if b > end:
                result.append({**piece, "start": stamp(end)})
    result.append({"start": stamp(start), "end": None if end == INF else stamp(end),
                   "data": data, "owner": owner})
    return sorted(result, key=lambda p: p["start"])


def replay(baseline, operations):
    pieces = baseline
    for operation in sorted(operations, key=order):
        if not operation.intent:
            continue
        for part in operation.intent["ranges"]:
            pieces = paint(pieces, instant(part["start"]), instant(part["end"]) if part["end"] else INF,
                           part["data"], operation.operation_id)
    return pieces


def identity(piece):
    data = piece["data"]
    if piece["start"] == data["origin_start"]:
        return data["id"]
    return stable_id(data["source"] + ":" + piece["start"])


def stable_id(key):
    # Positive 31-bit IDs are supported by both native Int and existing APIs.
    # A collision aborts admission instead of silently merging identities.
    return 1_000_000 + int.from_bytes(hashlib.sha256(key.encode()).digest()[:4], "big") % 2_000_000_000


def initialize(db, state):
    if state.reconciliation is not None:
        return
    baseline = []
    for row in db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual)):
        data = ActualBlockRead.model_validate(row).model_dump(mode="json")
        data.update(source=f"baseline:{row.id}", origin_start=stamp(row.start_at), activity_source=row.activity_source)
        baseline.append({"start": stamp(row.start_at), "end": stamp(row.end_at) if row.end_at else None,
                         "data": data, "owner": f"baseline:{row.id}"})
    state.reconciliation = {"baseline": baseline, "cursor": state.cursor, "tombstones": [], "provenance": {}}


def prepare(db, state, body, operations, timezone="UTC"):
    if body.base_cursor > state.cursor:
        raise ValueError("Observed revision is ahead of the server")
    # Delivery may reorder a device's outbox. Validate against both neighbors,
    # without rejecting a lower sequence merely because it arrived later.
    for op in operations:
        if op.device_id == body.device_id:
            if op.sequence == body.sequence:
                raise ValueError("Device sequence already used")
            if (op.sequence < body.sequence and instant(op.envelope["action_at"]) > body.action_at) or (
                    op.sequence > body.sequence and instant(op.envelope["action_at"]) < body.action_at):
                raise ValueError("Device action order must be nondecreasing")
    if body.predecessor_id:
        predecessor = next((op for op in operations if op.operation_id == str(body.predecessor_id)), None)
        if predecessor and (predecessor.device_id != body.device_id or predecessor.sequence >= body.sequence):
            raise ValueError("Predecessor must be an earlier action from this installation")
    start = body.effective.at
    if body.effective.mode == "server_now":
        start = dt.datetime.now(dt.timezone.utc)
    if start is None:
        raise ValueError("An explicit effective instant is required")
    start = instant(start)
    if start > dt.datetime.now(dt.timezone.utc) + dt.timedelta(seconds=5):
        raise ValueError("Activity instant is in the future; check the device clock")
    known = replay(state.reconciliation["baseline"], [op for op in operations if op.cursor <= body.base_cursor])
    target = next((p for p in known if p["data"] and identity(p) == body.target_id), None)
    historical = body.kind in {"add", "edit", "delete"}
    if historical:
        if body.effective.mode != "range" or body.effective.end is None or body.effective.end <= start:
            raise ValueError("Historical commands require an explicit positive range")
        end = instant(body.effective.end)
        if end > dt.datetime.now(dt.timezone.utc):
            raise ValueError("Historical time cannot end in the future")
        if body.kind in {"edit", "delete"} and (target is None or target["end"] is None):
            raise ValueError("Historical commands require a known ended Actual Block")
        if body.kind == "delete" and (start != instant(target["start"]) or end != instant(target["end"])):
            raise ValueError("Delete must name the complete observed target range")
        if body.kind != "delete":
            for p in known:
                if p["data"] and p is not target and start < (instant(p["end"]) if p["end"] else INF) and instant(p["start"]) < end:
                    raise ValueError("Activity overlaps known time; adjust the other record first")
    else:
        end = INF
        if body.effective.mode == "range" or body.effective.end is not None:
            raise ValueError("Tracking transitions require an instant")
        if body.effective.mode == "instant" and start != body.action_at:
            raise ValueError("Immediate commands require their original action instant")
        current = next((p for p in known if p["data"] and p["end"] is None), None)
        if not body.predecessor_id:
            if (body.kind == "start") == (current is not None) or body.target_id != (identity(current) if current else None):
                raise ValueError("Transition does not match the observed activity")
            if current and start <= instant(current["start"]):
                raise ValueError("Transition must follow the observed activity start")
        if any(p["data"] and p["end"] and start < instant(p["end"]) for p in known):
            raise ValueError("Activity instant overlaps known history")
    data = None
    if body.kind not in {"stop", "delete"}:
        data = dict(target["data"]) if target and body.kind == "edit" else {}
        type_id = body.task_type_id or data.get("task_type_id")
        if not historical:
            from app.services.activity_selection import resolve
            data.update(resolve(db, body, start, timezone))
            type_id, task_id = data["task_type_id"], data["task_id"]
        elif body.kind == "add":
            type_id, task_id, planned_name = actuals._resolve_origin_item(
                db, task_type_id=type_id, task_id=body.task_id,
                planned_block_id=body.planned_block_id, retrospective_end=end)
            data.update(task_id=task_id, planned_block_id=body.planned_block_id, name=planned_name)
        else:
            type_id = type_id or actuals._get_or_create_unspecified_task_type(db).id
            task_id = body.task_id if "task_id" in body.model_fields_set else data.get("task_id")
            actuals._validate_item(db, type_id, task_id, retrospective_end=end if historical else None,
                                   allow_completed=body.kind == "edit" and task_id == data.get("task_id"))
            if data and (type_id != data.get("task_type_id") or task_id != data.get("task_id")):
                data["planned_block_id"] = None
            data["task_id"] = task_id
            if "planned_block_id" in body.model_fields_set:
                if body.planned_block_id is not None:
                    raise ValueError("Explicit relinking is not enabled by this correction command")
                data["planned_block_id"] = None
        if task_id is not None:
            actuals.protect_task_occurrence(db, task_id)
        source = data.get("source", str(body.operation_id))
        # Corrections carry the original provenance through moves and splits.
        # Disjoint concurrent moves cannot both claim the same fragment ID.
        data.update(id=data.get("id", stable_id(source)), source=source, origin_start=data.get("origin_start", stamp(start)),
                    task_type_id=type_id, created_at=data.get("created_at", stamp(body.action_at)),
                    updated_at=stamp(body.action_at))
        for field in ("name", "note"):
            if historical and field in body.model_fields_set or field not in data:
                data[field] = getattr(body, field)
    ranges = []
    if body.kind == "edit":
        ranges.append({"start": target["start"], "end": target["end"], "data": None})
    ranges.append({"start": stamp(start), "end": None if end == INF else stamp(end), "data": data})
    return {"ranges": ranges}, start


def materialize(db, state, operations):
    pieces = replay(state.reconciliation["baseline"], operations)
    devices = {op.operation_id: op.device_id for op in operations}
    desired = {}
    provenance = {}
    for p in pieces:
        if p["data"] is None:
            continue
        data = p["data"]
        key = identity(p)
        if key in desired:
            raise ValueError("Activity fragment identity collision")
        desired[key] = dict(lane=BlockLane.actual, start_at=instant(p["start"]),
                            activity_source=data.get("activity_source") if data["source"].startswith("baseline:") and key == data["id"] else data["source"],
                            end_at=instant(p["end"]) if p["end"] else None,
                            **{k: data.get(k) for k in ("task_type_id", "task_id", "planned_block_id", "name", "note")},
                            created_at=instant(data["created_at"]), updated_at=instant(data["updated_at"]))
        provenance[str(key)] = data["source"]
    existing = {row.id: row for row in db.scalars(select(TimeBlock).where(TimeBlock.lane == BlockLane.actual))}
    changed = set()
    for key, row in existing.items():
        values = desired.get(key)
        if values is None or any((instant(getattr(row, field)) if isinstance(getattr(row, field), dt.datetime) else getattr(row, field)) != value for field, value in values.items()):
            actuals.invalidate_record_actual_undo(db, key)
            db.delete(row)
            changed.add(key)
    # All conflicting ranges disappear inside the same transaction before insert;
    # untouched rows, including their metadata and identity, are never rewritten.
    db.flush()
    for key, values in desired.items():
        if key not in existing or key in changed:
            if db.get(TimeBlock, key) is not None:
                raise ValueError("Activity identity collides with an existing block")
            db.add(TimeBlock(id=key, **values))
    db.flush()
    # Include every possible retired fragment, even if delivery order meant it
    # was never briefly materialized. Tombstones themselves therefore converge.
    all_ranges = state.reconciliation["baseline"] + [p for op in operations if op.intent for p in op.intent["ranges"]]
    cuts = {p["end"] for p in all_ranges if p["end"]}
    removed = set()
    for p in all_ranges:
        if p["data"]:
            removed.add(p["data"]["id"])
            removed.add(identity(p))
            for cut in cuts:
                if instant(p["start"]) < instant(cut) < (instant(p["end"]) if p["end"] else INF):
                    removed.add(stable_id(p["data"]["source"] + ":" + cut))
    state.reconciliation = {**state.reconciliation, "tombstones": sorted(removed - desired.keys()), "provenance": provenance}
    for operation in operations:
        if not operation.intent:
            continue
        # Any replaced portion (including empty intent) is reported as superseded.
        own = replay([], [operation])
        operation.outcome = "applied"
        for p in own:
            start, end = instant(p["start"]), instant(p["end"]) if p["end"] else INF
            if any(devices.get(q["owner"], operation.device_id) != operation.device_id and instant(q["start"]) < end and
                   (instant(q["end"]) if q["end"] else INF) > start for q in pieces):
                operation.outcome = "superseded"
                break


def coverage(state, operations):
    if not state.reconciliation:
        return []
    by_id = {op.operation_id: op for op in operations}
    return [{"start": p["start"], "end": p["end"], "record_id": identity(p) if p["data"] else None,
             "order": [stamp(instant(by_id[p["owner"]].envelope["action_at"])),
                       by_id[p["owner"]].device_id, by_id[p["owner"]].sequence, p["owner"]]
             if p["owner"] in by_id else ["", "", 0, p["owner"]]}
            for p in replay(state.reconciliation["baseline"], operations)]
