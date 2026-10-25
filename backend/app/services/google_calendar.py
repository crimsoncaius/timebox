"""One-way calendar reconciliation and atomic plan previews.

The caller holds exclusive writer admission. No function here commits, so a
configuration change or make-room action is one transaction, including notices.
"""
import datetime as dt
import hashlib
import json
from copy import deepcopy
from zoneinfo import ZoneInfo

from sqlalchemy import select

from app.models.battle_plan import Task
from app.models.google_calendar import GoogleCalendarConnection, GoogleCalendarOccurrence
from app.models.time_block import BlockLane, TimeBlock
from app.services import activity_selection, actual_block_service, task_type_service
from app.services import google_calendar_source as source
from app.services import planned_intervals as plans


def connection(db):
    row = db.get(GoogleCalendarConnection, 1)
    if row is None:
        row = GoogleCalendarConnection(id=1, data={})
        db.add(row)
        db.flush()
    return row


def occurrences(db):
    return list(db.scalars(select(GoogleCalendarOccurrence).order_by(GoogleCalendarOccurrence.id)))


def linked(db, block_id):
    return db.scalar(select(GoogleCalendarOccurrence).where(GoogleCalendarOccurrence.block_id == block_id))


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, default=str).encode()).hexdigest()


def remember_metadata(db, row):
    block = db.get(TimeBlock, row.block_id) if row.block_id else None
    if block:
        row.task_type_id, row.task_id, row.note = block.task_type_id, block.task_id, block.note
    return block


def remove_block(db, block):
    from app.models.battle_plan import RecurringPlannedBlockState
    from app.services.day_service import _mark_generated_planned_block, _touch_day
    from app.services.recurrence.protection import protect_task_occurrence
    if block.task_id:
        protect_task_occurrence(db, block.task_id)
    _mark_generated_planned_block(db, block, RecurringPlannedBlockState.deleted)
    activity_selection.detach_plan(db, block.id)
    for actual in db.scalars(select(TimeBlock).where(TimeBlock.planned_block_id == block.id)):
        actual_block_service.invalidate_record_actual_undo(db, actual.id)
        actual.planned_block_id = None
    _touch_day(block.day)
    row = linked(db, block.id)
    if row:
        remember_metadata(db, row)
        row.block_id = None
    db.delete(block)
    db.flush()


def log(row, kind, now, old=None):
    data = deepcopy(row.data)
    entry = dict(kind=kind, at=now.isoformat(), start=data.get("start"), end=data.get("end"), previous=old)
    history = data.setdefault("updates", [])
    if not history or {k: v for k, v in history[-1].items() if k != "at"} != {k: v for k, v in entry.items() if k != "at"}:
        history.append(entry)
    row.data = data


def freeze(db, now):
    for row in occurrences(db):
        block = remember_metadata(db, row)
        if block and plans.interval(block, plans.zone_name(db))[0] <= now:
            row.data = {**row.data, "frozen": True, "status": "frozen"}


def reader(settings, conn):
    credentials = source.unseal(settings, conn.credentials)
    access = source.token(settings, grant_type="refresh_token", refresh_token=credentials["refresh_token"])
    return source.GoogleReader(access["access_token"])


def synchronize(db, settings, now, google=None, additional=()):
    conn = connection(db)
    if not conn.credentials:
        return
    freeze(db, now)
    own_reader = google is None
    google = google or reader(settings, conn)
    try:
        calendars = google.calendars()
        available = {c["id"] for c in calendars}
        selected = list(dict.fromkeys([*conn.data.get("selected", []), *additional]))
        if any(c not in available for c in selected):
            raise source.GoogleUnavailable("A selected calendar is unavailable. Plans are retained; retry or deselect it.")
        existing = occurrences(db)
        zone = plans.zone_name(db)
        horizon = plans.bounds(now.astimezone(ZoneInfo(zone)).date() + dt.timedelta(days=90), zone)[1]
        fetched = {}
        for calendar in selected:
            known = {v["event_id"] for row in existing if row.account == conn.data.get("account") and not row.data.get("frozen")
                     for k, v in row.data.get("copies", {}).items() if k == calendar}
            fetched[calendar] = google.events(calendar, now, horizon, known)
        # No mutation until every selected calendar and every page was fetched.
        ingest(db, fetched, now)
        conn.data = {**conn.data, "calendars": calendars, "last_success": now.isoformat(), "error": None}
        reconcile(db, now)
    finally:
        if own_reader:
            google.close()


def ingest(db, fetched, now):
    conn = connection(db)
    account = conn.data["account"]
    existing = occurrences(db)
    aliases = {(calendar, copy["event_id"]): row for row in existing if row.account == account
               for calendar, copy in row.data.get("copies", {}).items()}
    for calendar, events in fetched.items():
        for raw in events:
            copy = source.normalize(raw, calendar)
            row = aliases.get((calendar, copy["event_id"]))
            key = digest([account, copy["identity"]])
            row = row or db.get(GoogleCalendarOccurrence, key)
            if row and row.data.get("frozen"):
                continue
            if row is None:
                if not copy["eligible"] or dt.datetime.fromisoformat(copy["start"]) <= now:
                    continue
                row = GoogleCalendarOccurrence(id=key, account=account, data={"status": "new", "copies": {}})
                db.add(row)
                db.flush()
            copies = deepcopy(row.data.get("copies", {}))
            if not copy["start"] and calendar in copies:
                copy = {**copies[calendar], "eligible": False, "etag": copy["etag"]}
            copies[calendar] = copy
            row.data = {**row.data, "copies": copies}
            aliases[(calendar, copy["event_id"])] = row


def reconcile(db, now):
    conn = connection(db)
    timezone = plans.zone_name(db)
    freeze(db, now)
    candidates = []
    for row in occurrences(db):
        if row.data.get("frozen"):
            continue
        block = remember_metadata(db, row)
        selected = conn.data.get("selected", []) if conn.credentials and row.account == conn.data.get("account") else []
        copy = next((row.data.get("copies", {}).get(c) for c in selected if c in row.data.get("copies", {})), None)
        old_interval = [row.data.get("start"), row.data.get("end")]
        old_status = row.data.get("status")
        if copy:
            row.data = {**row.data, **{k: copy[k] for k in ("start", "end", "name", "url", "calendar_id")}}
        current_interval = [row.data.get("start"), row.data.get("end")]
        changed = old_interval != current_interval
        if changed:
            row.data = {**row.data, "excluded": None, "previous": old_interval if old_interval[0] else None}
        start = dt.datetime.fromisoformat(row.data["start"]) if row.data.get("start") else None
        valid = copy and copy["eligible"] and start and start > now
        excluded = row.data.get("excluded") == current_interval
        if not valid or excluded:
            if block:
                remove_block(db, block)
                log(row, "Removed from plan", now, old_interval)
            status = "excluded" if excluded else "expired" if start and start <= now else "inactive"
            row.data = {**row.data, "status": status}
            if status == "expired" and old_status == "pending":
                log(row, "Conflict expired", now)
            continue
        end = dt.datetime.fromisoformat(row.data["end"])
        if block and plans.interval(block, timezone) != (start, end):
            # Preserve identity for a non-conflicting move. Remove stale placement
            # only when the requested interval is obstructed.
            try:
                plans.assert_no_overlap(db, start, end, timezone, {block.id})
            except ValueError:
                remove_block(db, block)
                block = None
            else:
                plans.set_interval(db, block, start, end, timezone)
            log(row, "Meeting moved", now, old_interval)
        if block:
            block.name = row.data["name"]
            row.data = {**row.data, "status": "imported"}
        else:
            candidates.append((row, start, end))
    # ponytail: bounded 90-day pair scan; interval index if calendar volume warrants it.
    for row, start, end in candidates:
        collides = any(other.id != row.id and a < end and b > start for other, a, b in candidates)
        try:
            plans.assert_no_overlap(db, start, end, timezone)
        except ValueError:
            collides = True
        if collides:
            if row.data.get("status") != "pending":
                log(row, "Calendar import conflict", now, row.data.get("previous"))
            row.data = {**row.data, "status": "pending"}
            continue
        task = db.get(Task, row.task_id) if row.task_id else None
        if task and task.deleted_at is not None:
            row.task_id = None
        type_id = task_type_service.resolve_task_type_id(db, row.task_type_id) or task_type_service.get_or_create_unspecified(db).id
        block = TimeBlock(lane=BlockLane.planned, task_type_id=type_id, task_id=row.task_id,
                          name=row.data["name"], note=row.note)
        plans.set_interval(db, block, start, end, timezone)
        if task and task.deleted_at is None:
            from app.services.recurrence.protection import protect_task_occurrence
            from app.services.recurrence.task_overrides import record_task_overrides
            task.ready_to_plan = False
            record_task_overrides(task, {"ready_to_plan"})
            protect_task_occurrence(db, task)
        row.block_id = block.id
        row.task_type_id = type_id
        row.data = {**row.data, "status": "imported"}
    db.flush()


def exclude(db, row, now):
    if row.data.get("frozen") or not row.data.get("start") or dt.datetime.fromisoformat(row.data["start"]) <= now:
        raise ValueError("This occurrence has started; refresh the plan.")
    row.data = {**row.data, "excluded": [row.data["start"], row.data["end"]], "status": "excluded"}
    block = remember_metadata(db, row)
    if block:
        remove_block(db, block)
    log(row, "Left out of plan", now)


def snapshot(db, now, date=None):
    conn = connection(db)
    timezone = plans.zone_name(db)
    block_rows = plans.rows(db)
    linked_rows = {r.block_id: r for r in occurrences(db) if r.block_id}
    blocks = [dict(id=b.id, name=b.name or (b.task.title if b.task else b.task_type.name),
                   start=plans.interval(b, timezone)[0].isoformat(), end=plans.interval(b, timezone)[1].isoformat(),
                   occurrence_id=linked_rows[b.id].id if b.id in linked_rows else None,
                   task_type_id=b.task_type_id, task_id=b.task_id, note=b.note) for b in block_rows]
    result = []
    for row in occurrences(db):
        data = row.data
        if not data.get("start"):
            continue
        start, end = dt.datetime.fromisoformat(data["start"]), dt.datetime.fromisoformat(data["end"])
        if date:
            a, b = plans.bounds(date, timezone)
            intervals = [[data["start"], data["end"]], data.get("previous")]
            intervals += [u.get("previous") for u in data.get("updates", [])]
            intervals += [[u.get("start"), u.get("end")] for u in data.get("updates", [])]
            if not any(i and i[0] and i[1] and dt.datetime.fromisoformat(i[0]) < b and dt.datetime.fromisoformat(i[1]) > a for i in intervals):
                continue
        elif data.get("status") not in {"pending", "excluded"} or start <= now:
            continue
        overlaps = [b for b in blocks if b["id"] != row.block_id and dt.datetime.fromisoformat(b["start"]) < end and dt.datetime.fromisoformat(b["end"]) > start]
        peers = [r.id for r in occurrences(db) if r.id != row.id and r.account == row.account and r.data.get("status") == "pending"
                 and r.data.get("start") and dt.datetime.fromisoformat(r.data["start"]) < end and dt.datetime.fromisoformat(r.data["end"]) > start]
        result.append(dict(id=row.id, block_id=row.block_id, name=data.get("name", "Busy"), start=data["start"], end=data["end"],
                           status=data.get("status"), previous=data.get("previous"), url=data.get("url"),
                           calendar_id=data.get("calendar_id"), task_type_id=row.task_type_id, task_id=row.task_id,
                           note=row.note, overlaps=overlaps, competing_ids=peers, updates=data.get("updates", [])))
    return dict(connected=bool(conn.credentials), account=conn.data.get("account"),
                calendars=conn.data.get("calendars", []), selected=conn.data.get("selected", []),
                last_success=conn.data.get("last_success"), error=conn.data.get("error"),
                timezone=timezone, occurrences=result)


def fingerprint(db, command):
    conn = connection(db)
    return digest([command, {k: v for k, v in conn.data.items() if k not in {"last_success", "error", "oauth"}}, bool(conn.credentials),
                   [(r.id, r.block_id, r.task_type_id, r.task_id, r.note, r.data) for r in occurrences(db)],
                   activity_selection.plans(db, plans.zone_name(db))])


def apply_command(db, command, now):
    from app.services.day_service import _active_task, _validate_recurrence_schedule
    conn = connection(db)
    freeze(db, now)
    kind = command["kind"]
    if kind == "select":
        ids = command.get("selected", [])
        if len(ids) != len(set(ids)) or not set(ids) <= {c["id"] for c in conn.data.get("calendars", [])}:
            raise ValueError("Choose available calendars without duplicates.")
        conn.data = {**conn.data, "selected": ids}
    elif kind in {"disconnect", "forget"}:
        conn.credentials = None
        conn.data = {**conn.data, "selected": [], "oauth": None}
        reconcile(db, now)
        if kind == "forget":
            for row in occurrences(db):
                if row.data.get("frozen"):
                    row.data = {"frozen": True, "status": "frozen"}
                    row.note, row.task_id, row.task_type_id = None, None, None
                else:
                    db.delete(row)
            conn.data = {}
    else:
        row = db.get(GoogleCalendarOccurrence, command.get("id"))
        if row is None or row.account != conn.data.get("account"):
            raise ValueError("Calendar occurrence not found.")
        if row.data.get("frozen") or not row.data.get("start") or dt.datetime.fromisoformat(row.data["start"]) <= now:
            raise ValueError("This occurrence has started. Refresh the plan.")
        if kind == "exclude":
            exclude(db, row, now)
        elif kind in {"include", "resolve"}:
            if not conn.credentials:
                raise ValueError("Reconnect Google Calendar first.")
            row.data = {**row.data, "excluded": None, "status": "pending"}
            edits = command.get("edits", [])
            if len({e["id"] for e in edits}) != len(edits):
                raise ValueError("Each block can be edited only once.")
            for edit in edits:
                block = db.get(TimeBlock, edit["id"])
                if block is None or block.lane != BlockLane.planned:
                    raise ValueError("An obstructing Planned Block changed. Refresh the preview.")
                imported = linked(db, block.id)
                if imported:
                    if edit["action"] != "remove":
                        raise ValueError("Change Google meeting times in Google Calendar.")
                    exclude(db, imported, now)
                elif edit["action"] == "remove":
                    remove_block(db, block)
                else:
                    a, b = dt.datetime.fromisoformat(edit["start"]), dt.datetime.fromisoformat(edit["end"])
                    if a.tzinfo is None or b.tzinfo is None or b <= a:
                        raise ValueError("Choose a positive interval with a time zone.")
                    if (b - a).total_seconds() < 60:
                        raise ValueError("Planned Blocks must be at least 1 minute.")
                    plans.set_interval(db, block, a, b, plans.zone_name(db))
                    _validate_recurrence_schedule(block.task, block.day)
            for peer_id in command.get("exclude_ids", []):
                peer = db.get(GoogleCalendarOccurrence, peer_id)
                if peer is None or peer.account != row.account or peer.id == row.id:
                    raise ValueError("Conflicting meeting changed. Refresh the preview.")
                exclude(db, peer, now)
            # Include again may remain pending; Make room must produce a placement.
        elif kind == "metadata":
            row.task_type_id = task_type_service.resolve_task_type_id(db, command.get("task_type_id")) or task_type_service.get_or_create_unspecified(db).id
            task = _active_task(db, command.get("task_id"), allow_completed=True)
            from types import SimpleNamespace

            from app.services.recurrence.protection import protect_task_occurrence
            _validate_recurrence_schedule(task, SimpleNamespace(date=dt.datetime.fromisoformat(row.data["start"]).astimezone(ZoneInfo(plans.zone_name(db))).date()))
            if row.task_id:
                protect_task_occurrence(db, row.task_id)
            if task:
                protect_task_occurrence(db, task)
            row.task_id = task.id if task else None
            row.note = command.get("note") or None
            if row.block_id:
                block = db.get(TimeBlock, row.block_id)
                if (block.task_type_id, block.task_id) != (row.task_type_id, row.task_id):
                    activity_selection.detach_plan(db, block.id)
                    for actual in db.scalars(select(TimeBlock).where(TimeBlock.planned_block_id == block.id)):
                        actual_block_service.invalidate_record_actual_undo(db, actual.id)
                        actual.planned_block_id = None
                block.task_type_id, block.task_id, block.note = row.task_type_id, row.task_id, row.note
        else:
            raise ValueError("Unknown calendar action.")
    db.flush()
    reconcile(db, now)
    if kind == "resolve" and db.get(GoogleCalendarOccurrence, command["id"]).data.get("status") != "imported":
        raise ValueError("The meeting still overlaps a plan or another pending meeting. Edit the preview.")
    all_plans = plans.rows(db)
    for block in all_plans:
        a, b = plans.interval(block, plans.zone_name(db))
        plans.assert_no_overlap(db, a, b, plans.zone_name(db), {block.id})
    db.flush()
