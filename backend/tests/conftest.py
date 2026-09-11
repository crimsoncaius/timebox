"""Pytest fixtures: set env before app import, shared in-memory DB."""

from __future__ import annotations

import os

os.environ.setdefault("DATABASE_URL", "sqlite:///:memory:")
os.environ.setdefault("APP_TIMEZONE", "UTC")
os.environ.setdefault("CORS_ORIGINS", "*")

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import inspect, text

import app.models  # noqa: F401
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
    """Create the SQLite schema expected by a legacy migration fixture."""

    def prepare(engine, revision: str) -> None:
        Base.metadata.create_all(engine)
        with engine.begin() as connection:
            connection.execute(text("DROP TABLE IF EXISTS activity_operations"))
            connection.execute(text("DROP TABLE IF EXISTS activity_state"))
            connection.execute(text("DROP TABLE IF EXISTS recurring_planned_block_realizations"))
            connection.execute(text("DROP TABLE IF EXISTS recurring_preplanning_slots"))
            project_columns = {
                column["name"] for column in inspect(connection).get_columns("projects")
            }
            if "description" not in project_columns:
                connection.execute(
                    text("ALTER TABLE projects ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                )
            if "deadline_date" not in project_columns:
                connection.execute(text("ALTER TABLE projects ADD COLUMN deadline_date DATE"))
            if "deadline_at" not in project_columns:
                connection.execute(text("ALTER TABLE projects ADD COLUMN deadline_at DATETIME"))
            template_columns = {
                column["name"]
                for column in inspect(connection).get_columns("recurring_templates")
            }
            if "project_id" not in template_columns:
                connection.execute(text("ALTER TABLE recurring_templates ADD COLUMN project_id INTEGER"))
                connection.execute(
                    text(
                        "CREATE INDEX ix_recurring_templates_project_id "
                        "ON recurring_templates (project_id)"
                    )
                )
            if revision in {"014_recurrence_occurrence_protection", "015_definitive_legacy_cutover"}:
                connection.execute(text("DROP INDEX uq_task_types_name"))
                connection.execute(
                    text("ALTER TABLE recurring_templates DROP COLUMN keep_unfinished_overdue")
                )
                connection.execute(text("ALTER TABLE recurring_templates DROP COLUMN position"))
                connection.execute(text("ALTER TABLE recurrence_occurrences DROP COLUMN skipped"))
            if revision in {
                "014_recurrence_occurrence_protection",
                "015_definitive_legacy_cutover",
                "016_planned_block_type_resolution",
                "017_non_accumulating_recurrence",
            }:
                connection.execute(text("ALTER TABLE time_blocks DROP COLUMN name"))
            connection.execute(text("ALTER TABLE projects DROP COLUMN position"))
            connection.execute(text("DROP TABLE IF EXISTS alembic_version"))
            connection.execute(
                text("CREATE TABLE alembic_version (version_num VARCHAR(255) NOT NULL)")
            )
            connection.execute(
                text("INSERT INTO alembic_version (version_num) VALUES (:revision)"),
                {"revision": revision},
            )

    return prepare
