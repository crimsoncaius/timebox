import importlib.util
from pathlib import Path

from alembic.migration import MigrationContext
from alembic.operations import Operations
from sqlalchemy import create_engine, text


def test_destination_migration_preserves_only_active_timed_schedules():
    migration_path = Path(__file__).parents[1] / "alembic/versions/032_preplanning_destination.py"
    spec = importlib.util.spec_from_file_location("preplanning_migration", migration_path)
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    engine = create_engine("sqlite:///:memory:")
    with engine.begin() as connection:
        connection.execute(text("CREATE TABLE recurring_templates (id INTEGER PRIMARY KEY)"))
        connection.execute(text("CREATE TABLE recurring_preplanning_slots (template_id INTEGER, removed_at TEXT)"))
        connection.execute(text("INSERT INTO recurring_templates VALUES (1), (2), (3)"))
        connection.execute(text("INSERT INTO recurring_preplanning_slots VALUES (1, NULL), (2, '2026-09-01')"))
        with Operations.context(MigrationContext.configure(connection)):
            migration.upgrade()
            assert connection.execute(text(
                "SELECT id, preplanning_mode FROM recurring_templates ORDER BY id"
            )).all() == [(1, "planned_time"), (2, "none"), (3, "none")]
            migration.downgrade()
        assert connection.execute(text("SELECT id FROM recurring_templates ORDER BY id")).scalars().all() == [1, 2, 3]
    engine.dispose()
