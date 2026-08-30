from __future__ import annotations

from io import StringIO
from pathlib import Path

from alembic import command
from alembic.config import Config

from app.core.config import get_settings


BACKEND_ROOT = Path(__file__).resolve().parents[1]


def test_definitive_contract_migration_widens_alembic_version_before_long_revision(
    monkeypatch,
):
    output = StringIO()
    monkeypatch.setattr(
        get_settings(),
        "database_url",
        "postgresql://offline:offline@localhost/offline",
    )
    config = Config(str(BACKEND_ROOT / "alembic.ini"), output_buffer=output)

    command.upgrade(
        config,
        "011_task_completion_lifecycle:012_definitive_timeboxing_contracts",
        sql=True,
    )

    sql = output.getvalue()
    widen = "ALTER TABLE alembic_version ALTER COLUMN version_num TYPE VARCHAR(255)"
    record_revision = (
        "UPDATE alembic_version SET version_num='012_definitive_timeboxing_contracts'"
    )

    assert widen in sql
    assert sql.index(widen) < sql.index(record_revision)
