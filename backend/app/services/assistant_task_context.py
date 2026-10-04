"""Deterministic current evidence, separate from immutable historical snapshots."""
import time

from sqlalchemy import select

from app.core.config import get_settings
from app.core.time import utc_now
from app.models.battle_plan import Task
from app.services.activity_service import reporting_settings
from app.services.assistant_limits import MAX_REFRESH_BYTES, MAX_REFRESH_IDENTITIES, TASK_READ_TIMEOUT
from app.services.assistant_tasks import encoded, project_ids, read_session


def known_tasks(snapshots):
    known = {}
    children = {}
    recency = {}
    for index, snapshot in enumerate(snapshots.values()):
        if snapshot.get("kind") != "tasks":
            continue
        for row in snapshot["rows"]:
            known.pop(row["id"], None)
            known[row["id"]] = row
            recency[row["id"]] = index
            for child in row.get("subtasks") or []:
                children[child["id"]] = row["id"]
            for session in row.get("sessions") or []:
                known.pop(session["id"], None)
                known[session["id"]] = session
                recency[session["id"]] = index
    return {key: known[key] for key in sorted(known, key=lambda key: (-recency[key], key))}, children


def comparable(row):
    return {k: v for k, v in row.items() if k not in {"description", "projection_hash", "field_coverage", "matched_subtask_ids"}
            and not k.endswith("_cursor")}


def unverified(snapshots, now, reason):
    known, children = known_tasks(snapshots)
    result = {"read_at": now.isoformat(), "requested_ids": list(known), "refreshed_ids": [],
              "projections": [], "changed_fields": {}, "unavailable_ids": [], "unverified_ids": list(known),
              "omitted_child_count": len(children), "status": "unverified", "reason": reason,
              "description_coverage": "historical_only_not_refreshed"}
    if len(encoded(result)) > MAX_REFRESH_BYTES:
        result.update(requested_ids=[], unverified_ids=[], omitted_identity_count=len(known))
    return result


def refresh(snapshots, *, now=None, priority_ids=(), priority_children=None):
    now = now or utc_now()
    known, children = known_tasks(snapshots)
    children.update(priority_children or {})
    order = list(dict.fromkeys([*priority_ids, *known]))
    result = {"read_at": now.isoformat(), "requested_ids": order, "refreshed_ids": [], "projections": [],
              "changed_fields": {}, "unavailable_ids": [], "unverified_ids": [], "children": {},
              "description_coverage": "historical_only_not_refreshed", "status": "complete"}
    started = time.monotonic()
    covered_identities = set()
    try:
        with read_session() as db:
            zone = reporting_settings(db, get_settings()).app_timezone
            result["reporting_timezone"] = zone
            for task_id in order:
                if (len(covered_identities) >= MAX_REFRESH_IDENTITIES and task_id not in covered_identities) or time.monotonic() - started >= TASK_READ_TIMEOUT:
                    result["unverified_ids"].append(task_id)
                    continue
                row = project_ids(db, [{"id": task_id, "kind": known.get(task_id, {}).get("kind", "ordinary")}], zone, now, [])[0]
                nested_ids = {c["id"] for c in (row.get("subtasks") or []) + (row.get("sessions") or [])}
                prospective_ids = covered_identities | nested_ids | {task_id}
                if len(prospective_ids) > MAX_REFRESH_IDENTITIES:
                    result["unverified_ids"].append(task_id)
                    continue
                # No fresh cursors: this preflight does not issue discovery reads.
                row = {k: v for k, v in row.items() if not k.endswith("_cursor")}
                candidate = [*result["projections"], row]
                if len(encoded({**result, "projections": candidate})) > MAX_REFRESH_BYTES - 4096:
                    result["unverified_ids"].append(task_id)
                    continue
                result["projections"].append(row)
                covered_identities = prospective_ids
                if row["availability"] == "unavailable":
                    result["unavailable_ids"].append(task_id)
                else:
                    result["refreshed_ids"].append(task_id)
                    old, current = comparable(known.get(task_id, {})), comparable(row)
                    result["changed_fields"][str(task_id)] = sorted(k for k in old.keys() | current.keys() if old.get(k) != current.get(k))
                # Each explicitly seen child consumes an identity slot, even when
                # it is outside today's first nested page. Never verify by omission.
                for child_id, parent_id in children.items():
                    if parent_id != task_id:
                        continue
                    state = {"parent_id": parent_id, "availability": "unverified"}
                    if (child_id in covered_identities or len(covered_identities) < MAX_REFRESH_IDENTITIES) and time.monotonic() - started < TASK_READ_TIMEOUT:
                        covered_identities.add(child_id)
                        child = db.execute(select(Task.id, Task.title, Task.checked).where(Task.id == child_id,
                            Task.parent_id == parent_id, Task.deleted_at.is_(None), Task.archived_at.is_(None))).mappings().first() if row["availability"] == "available" else None
                        state = {"parent_id": parent_id, "availability": "available", **dict(child)} if child else {"parent_id": parent_id, "availability": "unavailable"}
                    result["children"][str(child_id)] = state
    except Exception:
        result["status"] = "unverified"
        covered = set(result["refreshed_ids"] + result["unavailable_ids"])
        result["unverified_ids"] = [i for i in order if i not in covered]
    for child_id, parent_id in children.items():
        result["children"].setdefault(str(child_id), {"parent_id": parent_id, "availability": "unverified"})
    if result["unverified_ids"] or any(c["availability"] == "unverified" for c in result["children"].values()):
        result["status"] = "partial" if result["projections"] else "unverified"
    if len(encoded(result)) > MAX_REFRESH_BYTES:
        # Keep compact coverage rather than assert that an oversized projection
        # (or its child collection) was verified.
        result.update(projections=[], refreshed_ids=[], unavailable_ids=[], unverified_ids=order,
                      children={}, changed_fields={}, status="unverified", omitted_child_count=len(children))
        if len(encoded(result)) > MAX_REFRESH_BYTES:
            result.update(requested_ids=[], unverified_ids=[], omitted_identity_count=len(order))
    return result
