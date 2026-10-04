"""Request lifetime admission: cutover waits even across service commits.

PostgreSQL session advisory locks are held on a pinned connection. SQLite is
supported only in a single process (tests/offline rehearsal).
"""

from __future__ import annotations

import time
from contextlib import contextmanager
from threading import Lock

from sqlalchemy import text

_local = Lock()
_key = 157144


@contextmanager
def admission(engine, *, exclusive=False, timeout=None):
    if engine.dialect.name != "postgresql":
        acquired = _local.acquire() if timeout is None else _local.acquire(timeout=timeout)
        if not acquired:
            raise TimeoutError("Writer admission timed out")
        try:
            with engine.connect() as connection:
                yield connection
        finally:
            _local.release()
        return
    suffix = "" if exclusive else "_shared"
    with engine.connect() as connection:
        if timeout is None:
            connection.execute(text(f"SELECT pg_advisory_lock{suffix}(:key)"), {"key": _key})
        else:
            deadline = time.monotonic() + timeout
            while not connection.scalar(text(f"SELECT pg_try_advisory_lock{suffix}(:key)"), {"key": _key}):
                if time.monotonic() >= deadline:
                    raise TimeoutError("Writer admission timed out")
                time.sleep(min(0.025, max(0, deadline - time.monotonic())))
        connection.commit()
        try:
            yield connection
        finally:
            connection.rollback()
            # A disconnected session has already released its advisory lock.
            # Reconnecting here would not restore admission for recovery work.
            if not connection.invalidated:
                connection.execute(text(f"SELECT pg_advisory_unlock{suffix}(:key)"), {"key": _key})
                connection.commit()
