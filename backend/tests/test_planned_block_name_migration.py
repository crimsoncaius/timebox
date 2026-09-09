from __future__ import annotations

import datetime as dt
from pathlib import Path
import tempfile

from alembic import command
from alembic.config import Config
import sqlalchemy as sa

import app.models  # noqa: F401
from app.core.config import get_settings
from app.db.base import Base


BACKEND_ROOT = Path(__file__).resolve().parents[1]


def test_migration_adds_nullable_name_without_backfilling_existing_blocks(
    monkeypatch, prepare_legacy_schema
):
    with tempfile.TemporaryDirectory(prefix=".block-name-", dir=BACKEND_ROOT / "tests") as path:
        database_path = Path(path) / "before-block-name.sqlite3"
        engine = sa.create_engine(
            f"sqlite:///{database_path.as_posix()}", poolclass=sa.pool.NullPool
        )
        prepare_legacy_schema(engine, "017_non_accumulating_recurrence")

        with engine.begin() as connection:
            connection.execute(
                Base.metadata.tables["task_types"].insert().values(id=1, name="meetings")
            )
            connection.execute(
                Base.metadata.tables["days"].insert().values(
                    id=1,
                    date=dt.date(2026, 9, 4),
                    start_hour=8,
                    end_hour=20,
                    show_full_day=False,
                )
            )
            connection.execute(
                sa.text(
                    "INSERT INTO time_blocks "
                    "(id, day_id, lane, task_type_id, note, start_minute, end_minute) "
                    "VALUES (1, 1, 'planned', 1, 'Dinner with Alex', 1080, 1140)"
                )
            )

        monkeypatch.setattr(
            get_settings(), "database_url", f"sqlite:///{database_path.as_posix()}"
        )
        command.upgrade(Config(str(BACKEND_ROOT / "alembic.ini")), "head")

        with engine.connect() as connection:
            row = connection.execute(
                sa.text("SELECT name, note FROM time_blocks WHERE id = 1")
            ).mappings().one()

        assert row == {"name": None, "note": "Dinner with Alex"}
        name_column = next(
            column
            for column in sa.inspect(engine).get_columns("time_blocks")
            if column["name"] == "name"
        )
        assert name_column["nullable"] is True
