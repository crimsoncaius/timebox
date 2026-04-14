"""Pytest fixtures: set env before app import, shared in-memory DB."""

from __future__ import annotations

import os

os.environ.setdefault("DATABASE_URL", "sqlite:///:memory:")
os.environ.setdefault("APP_TIMEZONE", "UTC")
os.environ.setdefault("CORS_ORIGINS", "*")

import pytest
from fastapi.testclient import TestClient

import app.models  # noqa: F401
from app.db.base import Base
from app.db.session import get_db, get_engine
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
