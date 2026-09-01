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


def test_migration_consolidates_raced_task_types_before_enforcing_uniqueness(
    monkeypatch,
):
    with tempfile.TemporaryDirectory(prefix=".task-type-", dir=BACKEND_ROOT / "tests") as path:
        database_path = Path(path) / "before-resolution.sqlite3"
        engine = sa.create_engine(
            f"sqlite:///{database_path.as_posix()}", poolclass=sa.pool.NullPool
        )
        Base.metadata.create_all(engine)

        with engine.begin() as connection:
            connection.execute(sa.text("DROP INDEX uq_task_types_name"))
            connection.execute(
                sa.text("CREATE TABLE alembic_version (version_num VARCHAR(255) NOT NULL)")
            )
            connection.execute(
                sa.text(
                    "INSERT INTO alembic_version (version_num) "
                    "VALUES ('015_definitive_legacy_cutover')"
                )
            )
            connection.execute(
                Base.metadata.tables["task_types"].insert(),
                [{"id": 1, "name": "unspecified"}, {"id": 2, "name": "unspecified"}],
            )
            connection.execute(
                Base.metadata.tables["days"].insert().values(
                    id=1,
                    date=dt.date(2026, 9, 1),
                    start_hour=8,
                    end_hour=20,
                    show_full_day=False,
                )
            )
            connection.execute(
                Base.metadata.tables["tasks"].insert().values(
                    id=1,
                    title="Raced Task Type",
                    status="open",
                    ready_to_plan=False,
                    task_type_id=2,
                )
            )
            connection.execute(
                Base.metadata.tables["time_blocks"].insert().values(
                    id=1,
                    day_id=1,
                    lane="planned",
                    task_type_id=2,
                    task_id=1,
                    start_minute=540,
                    end_minute=570,
                )
            )

        monkeypatch.setattr(
            get_settings(), "database_url", f"sqlite:///{database_path.as_posix()}"
        )
        config = Config(str(BACKEND_ROOT / "alembic.ini"))
        command.upgrade(config, "head")

        with engine.connect() as connection:
            task_types = connection.execute(
                sa.text("SELECT id, name FROM task_types ORDER BY id")
            ).mappings().all()
            task_type_id = connection.execute(
                sa.text("SELECT task_type_id FROM tasks WHERE id = 1")
            ).scalar_one()
            block_type_id = connection.execute(
                sa.text("SELECT task_type_id FROM time_blocks WHERE id = 1")
            ).scalar_one()

        assert task_types == [{"id": 1, "name": "unspecified"}]
        assert task_type_id == 1
        assert block_type_id == 1
        assert next(
            index for index in sa.inspect(engine).get_indexes("task_types")
            if index["name"] == "uq_task_types_name"
        )["unique"] == 1
