"""Pytest fixtures: set env before app import, shared in-memory DB."""

from __future__ import annotations

import os
from pathlib import Path
from unittest.mock import patch

os.environ.setdefault("DATABASE_URL", "sqlite:///:memory:")
os.environ.setdefault("APP_TIMEZONE", "UTC")
os.environ.setdefault("CORS_ORIGINS", "*")

import pytest
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import text

import app.models  # noqa: F401
from alembic import command
from app.core.config import get_settings
from app.db.base import Base
from app.db.session import get_engine
from app.main import app


@pytest.fixture(autouse=True)
def reset_db() -> None:
    engine = get_engine()
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def prepare_legacy_schema():
    """Start from frozen revision 014, then apply real migrations to the requested era.

    Using current metadata and dropping selected new columns silently left later
    tables behind. The immutable snapshot keeps upgrades meaningful as models grow.
    """
    backend = Path(__file__).resolve().parents[1]
    schema = (backend / "tests/fixtures/schema_014.sql").read_text()

    def prepare(engine, revision: str) -> None:
        with engine.connect() as connection:
            connection.connection.driver_connection.executescript(schema)
            connection.execute(text("CREATE TABLE alembic_version (version_num VARCHAR(255) NOT NULL)"))
            connection.execute(text("INSERT INTO alembic_version VALUES ('014_recurrence_occurrence_protection')"))
            connection.commit()
        if revision != "014_recurrence_occurrence_protection":
            with patch.object(get_settings(), "database_url", str(engine.url)):
                command.upgrade(Config(str(backend / "alembic.ini")), revision)

    return prepare
