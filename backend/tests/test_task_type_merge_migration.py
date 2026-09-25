import importlib.util
from pathlib import Path

import pytest
from alembic.migration import MigrationContext
from alembic.operations import Operations
from sqlalchemy import create_engine, text


def test_merge_migration_preserves_existing_references_and_guards_downgrade():
    path = Path(__file__).parents[1] / "alembic/versions/033_task_type_merge.py"
    spec = importlib.util.spec_from_file_location("merge_migration", path)
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    # Alembic's SQLite connection has foreign-key enforcement disabled during batch DDL.
    engine = create_engine("sqlite:///:memory:")
    with engine.begin() as connection:
        connection.execute(
            text("CREATE TABLE task_types (id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL)")
        )
        connection.execute(
            text(
                "CREATE TABLE tasks (id INTEGER PRIMARY KEY, task_type_id INTEGER REFERENCES task_types(id))"
            )
        )
        connection.execute(text("INSERT INTO task_types VALUES (1, 'travel'), (2, 'transportation')"))
        connection.execute(text("INSERT INTO tasks VALUES (1, 1)"))
        with Operations.context(MigrationContext.configure(connection)):
            migration.upgrade()
            assert connection.execute(
                text("SELECT id, is_merged, merged_into_id FROM task_types ORDER BY id")
            ).all() == [(1, 0, None), (2, 0, None)]
            assert connection.execute(text("SELECT task_type_id FROM tasks")).scalar_one() == 1
            assert connection.execute(text("PRAGMA foreign_key_check")).all() == []
            migration.downgrade()
            migration.upgrade()
            connection.execute(text("UPDATE task_types SET is_merged = 1, merged_into_id = 2 WHERE id = 1"))
            with pytest.raises(RuntimeError, match="backup"):
                migration.downgrade()
    engine.dispose()
