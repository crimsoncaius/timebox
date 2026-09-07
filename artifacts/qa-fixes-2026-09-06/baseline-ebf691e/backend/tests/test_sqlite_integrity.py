from __future__ import annotations

from sqlalchemy import text

import app.models  # noqa: F401
from app.db.base import Base
from app.db.session import (
    _create_engine,
    repair_sqlite_actual_record_operation_references,
)


def test_sqlite_engine_enforces_foreign_keys_and_repairs_legacy_operation_orphans(
    tmp_path,
):
    database = tmp_path / "operation-orphans.sqlite3"
    engine = _create_engine(f"sqlite:///{database.as_posix()}")
    Base.metadata.create_all(engine)

    with engine.begin() as connection:
        assert connection.exec_driver_sql("PRAGMA foreign_keys").scalar_one() == 1
        connection.execute(text("INSERT INTO task_types (id, name) VALUES (1, 'work')"))
        connection.execute(
            text(
                """
                INSERT INTO days (id, date, start_hour, end_hour, show_full_day)
                VALUES (1, '2026-08-30', 8, 20, 0)
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO time_blocks (
                  id, day_id, lane, task_type_id, start_minute, end_minute
                ) VALUES (5, 1, 'planned', 1, 600, 660)
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO time_blocks (
                  id, lane, task_type_id, planned_block_id, start_at, end_at
                ) VALUES (
                  6, 'actual', 1, 5, '2026-08-30 10:00:00', '2026-08-30 11:00:00'
                )
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO actual_block_record_operations (
                  token, actual_block_id, planned_block_id
                ) VALUES ('current-token', 6, 5)
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO time_blocks (
                  id, day_id, lane, task_type_id, start_minute, end_minute
                ) VALUES (3, 1, 'planned', 1, 540, 600)
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO time_blocks (
                  id, lane, task_type_id, planned_block_id, start_at, end_at
                ) VALUES (
                  4, 'actual', 1, 3, '2026-08-30 09:00:00', '2026-08-30 10:00:00'
                )
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO actual_block_record_operations (
                  token, actual_block_id, planned_block_id, invalidated_at
                ) VALUES ('old-token', 4, 3, '2026-08-30 10:01:00')
                """
            )
        )

    # Reproduce a database written before the connection hook existed.
    engine.dispose()
    legacy_engine = _create_engine(f"sqlite:///{database.as_posix()}")
    with legacy_engine.connect() as connection:
        connection.exec_driver_sql("PRAGMA foreign_keys=OFF")
        connection.execute(text("DELETE FROM time_blocks WHERE id IN (3, 4)"))
        connection.commit()
    legacy_engine.dispose()

    repair_engine = _create_engine(f"sqlite:///{database.as_posix()}")
    repair_sqlite_actual_record_operation_references(repair_engine)
    with repair_engine.connect() as connection:
        references = connection.execute(
            text(
                """
                SELECT actual_block_id, planned_block_id
                FROM actual_block_record_operations
                WHERE token = 'old-token'
                """
            )
        ).one()
        assert references == (None, None)
        current_references = connection.execute(
            text(
                """
                SELECT actual_block_id, planned_block_id
                FROM actual_block_record_operations
                WHERE token = 'current-token'
                """
            )
        ).one()
        assert current_references == (6, 5)
