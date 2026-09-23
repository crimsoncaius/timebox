from pathlib import Path

from alembic.config import Config
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import URL

from alembic import command
from app.core.config import get_settings


def test_monday_week_migration_drops_saved_sunday_preference(tmp_path: Path, monkeypatch):
    database_url = str(URL.create("sqlite", database=str(tmp_path / "weeks.sqlite")))
    engine = create_engine(database_url)
    with engine.begin() as connection:
        connection.execute(text(
            "CREATE TABLE app_settings (id INTEGER PRIMARY KEY, start_hour INTEGER NOT NULL, "
            "week_start TEXT NOT NULL)"
        ))
        connection.execute(text(
            "INSERT INTO app_settings (id, start_hour, week_start) VALUES (1, 8, 'sunday')"
        ))
        connection.execute(text("CREATE TABLE alembic_version (version_num VARCHAR(255) NOT NULL)"))
        connection.execute(text(
            "INSERT INTO alembic_version (version_num) VALUES ('029_planned_recording_undo')"
        ))
    monkeypatch.setattr(get_settings(), "database_url", database_url)
    config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
    command.upgrade(config, "head")

    assert "week_start" not in {column["name"] for column in inspect(engine).get_columns("app_settings")}
    with engine.connect() as connection:
        assert connection.execute(text("SELECT start_hour FROM app_settings WHERE id = 1")).scalar_one() == 8
    engine.dispose()
