"""Server-authored timeline changes inside the caller's transaction."""

import datetime as dt
import uuid

from sqlalchemy import select, update
from sqlalchemy.dialects import postgresql, sqlite

from app.models.activity import ActivityOperation, ActivityState
from app.services import activity_reconciliation as rec


def lock(db):
    """Serialize with device admission without enabling legacy databases."""
    insert = (
        postgresql.insert if db.bind.dialect.name == "postgresql" else sqlite.insert
    )
    db.execute(
        insert(ActivityState)
        .values(id=1, enabled=False, cursor=0)
        .on_conflict_do_nothing()
    )
    db.execute(
        update(ActivityState)
        .where(ActivityState.id == 1)
        .values(cursor=ActivityState.cursor)
    )
    return db.get(ActivityState, 1, populate_existing=True)


def pieces(db, state):
    rec.initialize(db, state)
    operations = list(db.scalars(select(ActivityOperation)))
    return rec.replay(state.reconciliation["baseline"], operations)


def plan_link_ranges(db, state, links):
    """Keep fragment identities and all details while changing selected links."""
    if not links:
        return []
    ranges = []
    found = set()
    for part in pieces(db, state):
        if part["data"] and rec.identity(part) in links:
            found.add(rec.identity(part))
            ranges.append(
                {
                    "start": part["start"],
                    "end": part["end"],
                    "data": {
                        **part["data"],
                        "planned_block_id": links[rec.identity(part)],
                    },
                }
            )
    if found != set(links):
        raise ValueError(
            "Recorded activity changed; plan correspondence cannot be updated safely"
        )
    return ranges


def append(db, state, ranges, at, *, source, metadata):
    """Append and materialize atomically; the caller owns commit/rollback."""
    if not ranges:
        return
    rec.initialize(db, state)
    operations = list(db.scalars(select(ActivityOperation)))
    # The server action must follow everything observed under the lock. Its
    # authored order can differ from the exact effective interval boundary.
    authored_at = max(
        [
            rec.instant(at),
            *[
                rec.instant(op.envelope["action_at"]) + dt.timedelta(microseconds=1)
                for op in operations
            ],
        ]
    )
    state.cursor += 1
    operation = ActivityOperation(
        operation_id=str(uuid.uuid4()),
        device_id=source,
        sequence=state.cursor,
        cursor=state.cursor,
        outcome="applied",
        effective_at=at,
        envelope={"action_at": rec.stamp(authored_at), **metadata},
        intent={"ranges": ranges},
    )
    db.add(operation)
    db.flush()
    rec.materialize(db, state, [*operations, operation])
    return operation
