"""Request lifetime admission: cutover waits even across service commits.

PostgreSQL session advisory locks are held on a pinned connection. SQLite is
supported only in a single process (tests/offline rehearsal).
"""
from contextlib import contextmanager
from threading import Lock

from sqlalchemy import text

_local = Lock()
_key = 157144


@contextmanager
def admission(engine, *, exclusive=False):
    if engine.dialect.name != "postgresql":
        with _local, engine.connect() as connection:
            yield connection
        return
    suffix = "" if exclusive else "_shared"
    with engine.connect() as connection:
        connection.execute(text(f"SELECT pg_advisory_lock{suffix}(:key)"), {"key": _key})
        connection.commit()
        try:
            yield connection
        finally:
            connection.rollback()
            connection.execute(text(f"SELECT pg_advisory_unlock{suffix}(:key)"), {"key": _key})
            connection.commit()
