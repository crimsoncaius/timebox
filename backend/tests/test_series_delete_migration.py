from __future__ import annotations

import datetime as dt
from pathlib import Path

from alembic import command
from alembic.config import Config
import pytest
import sqlalchemy as sa

from app.core.config import get_settings
from app.db.base import Base
from app.db.session import _create_engine


def test_series_delete_migration_retains_history_and_refuses_lossy_downgrade(tmp_path, monkeypatch):
    url = f"sqlite:///{(tmp_path / 'series-history.sqlite3').as_posix()}"
    engine = _create_engine(url)
    Base.metadata.create_all(engine)
    date = dt.date(2026, 9, 6)
    with engine.begin() as connection:
        connection.execute(sa.text("CREATE TABLE alembic_version (version_num VARCHAR(255) NOT NULL)"))
        connection.execute(sa.text("INSERT INTO alembic_version VALUES ('020_detach_series_history')"))
        connection.execute(Base.metadata.tables["recurring_templates"].insert().values(
            id=1, title="History", mode="scheduled", status="ended", frequency="daily",
            start_date=date, generation_start_date=date,
        ))
        connection.execute(Base.metadata.tables["tasks"].insert().values(
            id=1, title="Skipped history", recurring_template_id=1,
        ))
        connection.execute(Base.metadata.tables["recurrence_occurrences"].insert().values(
            id=1, template_id=1, task_id=1, occurrence_key="scheduled:2026-09-06",
            cycle_start=date, cycle_end=date, skipped=True,
        ))
    monkeypatch.setattr(get_settings(), "database_url", url)
    config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
    command.downgrade(config, "019_project_order")
    column = next(c for c in sa.inspect(engine).get_columns("recurrence_occurrences") if c["name"] == "template_id")
    assert column["nullable"] is False

    command.upgrade(config, "020_detach_series_history")

    with engine.begin() as connection:
        assert connection.execute(sa.text("SELECT task_id, skipped FROM recurrence_occurrences")).one() == (1, 1)
        connection.execute(sa.text("DELETE FROM recurring_templates WHERE id = 1"))
        assert connection.execute(sa.text("SELECT template_id, task_id, skipped FROM recurrence_occurrences")).one() == (None, 1, 1)
        assert connection.execute(sa.text("SELECT title, recurring_template_id FROM tasks")).one() == ("Skipped history", None)
    with pytest.raises(RuntimeError, match="detached occurrence history"):
        command.downgrade(config, "019_project_order")
    with engine.connect() as connection:
        assert connection.execute(sa.text("SELECT task_id, skipped FROM recurrence_occurrences")).one() == (1, 1)
    engine.dispose()
