from __future__ import annotations

from collections.abc import Generator
from functools import lru_cache

from fastapi import Request
from sqlalchemy import create_engine, event, inspect, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.config import get_settings


def _enable_sqlite_foreign_keys(dbapi_connection, _connection_record) -> None:
    """Make SQLite enforce the same declared FK actions as PostgreSQL."""

    cursor = dbapi_connection.cursor()
    try:
        cursor.execute("PRAGMA foreign_keys=ON")
    finally:
        cursor.close()


def repair_sqlite_actual_record_operation_references(engine: Engine) -> None:
    """Repair operation references created before SQLite FK enforcement was enabled."""

    if engine.dialect.name != "sqlite":
        return
    with engine.begin() as connection:
        tables = set(inspect(connection).get_table_names())
        if not {"actual_block_record_operations", "time_blocks"}.issubset(tables):
            return
        connection.execute(
            text(
                """
                UPDATE actual_block_record_operations
                SET actual_block_id = NULL
                WHERE actual_block_id IS NOT NULL
                  AND (
                    invalidated_at IS NOT NULL
                    OR undone_at IS NOT NULL
                    OR NOT EXISTS (
                      SELECT 1 FROM time_blocks
                      WHERE time_blocks.id = actual_block_record_operations.actual_block_id
                        AND time_blocks.lane = 'actual'
                    )
                  )
                """
            )
        )
        connection.execute(
            text(
                """
                UPDATE actual_block_record_operations
                SET planned_block_id = NULL
                WHERE planned_block_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM time_blocks
                    WHERE time_blocks.id = actual_block_record_operations.planned_block_id
                      AND time_blocks.lane = 'planned'
                  )
                """
            )
        )


@lru_cache
def get_engine():
    settings = get_settings()
    return _create_engine(settings.database_url)


def _create_engine(url: str) -> Engine:
    if url.startswith("sqlite"):
        engine_options = {"connect_args": {"check_same_thread": False}}
        # In-memory SQLite databases need one shared connection so every test
        # session sees the same schema. File-backed databases must instead use
        # SQLite's regular pool: sharing one connection across FastAPI worker
        # threads can corrupt concurrent reminder and page-load requests.
        if ":memory:" in url or url.rstrip("/") == "sqlite:":
            engine_options["poolclass"] = StaticPool
        engine = create_engine(url, **engine_options)
        event.listen(engine, "connect", _enable_sqlite_foreign_keys)
        return engine
    return create_engine(url, pool_pre_ping=True)


@lru_cache
def _session_factory():
    return sessionmaker(autocommit=False, autoflush=False, bind=get_engine())


def get_db(request: Request) -> Generator[Session, None, None]:
    from app.db.activity_admission import admission
    with admission(get_engine(), exclusive=request.method == "POST" and request.url.path.startswith("/task-types/") and request.url.path.endswith("/merge")) as connection, Session(bind=connection, autoflush=False) as db:
        yield db


@event.listens_for(Session, "before_flush")
def _resolve_merged_task_type_references(db, _flush_context, _instances):
    from app.models.battle_plan import RecurringTemplate, Task
    from app.models.time_block import TimeBlock
    from app.services.task_type_service import get_task_type
    for row in db.new | db.dirty:
        if isinstance(row, (Task, RecurringTemplate, TimeBlock)) and row.task_type_id is not None:
            target = get_task_type(db, row.task_type_id)
            if target is None:
                raise ValueError("Task type not found")
            if target.id != row.task_type_id:
                row.task_type_id = target.id
                row.task_type = target
