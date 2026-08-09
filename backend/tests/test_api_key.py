from __future__ import annotations

import pytest

from app.core.config import Settings, get_settings
from app.main import app

PROTECTED = ["/days", "/settings", "/task-types", "/days/2026-04-13"]


@pytest.fixture
def with_api_key():
    """Run the app as if API_KEY=secret-key were set in the environment."""

    def _override() -> Settings:
        return Settings(app_timezone="UTC", api_key="secret-key")

    app.dependency_overrides[get_settings] = _override
    yield
    app.dependency_overrides.pop(get_settings, None)


def test_open_by_default(client):
    for path in PROTECTED:
        assert client.get(path).status_code == 200, path


@pytest.mark.usefixtures("with_api_key")
def test_missing_key_is_rejected(client):
    for path in PROTECTED:
        r = client.get(path)
        assert r.status_code == 401, path
        assert r.json()["detail"] == "Missing API key"


@pytest.mark.usefixtures("with_api_key")
def test_wrong_key_is_rejected(client):
    r = client.get("/days", headers={"X-API-Key": "nope"})
    assert r.status_code == 403
    assert r.json()["detail"] == "Invalid API key"


@pytest.mark.usefixtures("with_api_key")
def test_correct_key_is_accepted(client):
    for path in PROTECTED:
        r = client.get(path, headers={"X-API-Key": "secret-key"})
        assert r.status_code == 200, path


@pytest.mark.usefixtures("with_api_key")
def test_health_stays_open(client):
    assert client.get("/health").status_code == 200


@pytest.mark.usefixtures("with_api_key")
def test_writes_are_protected(client):
    r = client.post("/task-types", json={"name": "coding"})
    assert r.status_code == 401

    r = client.post(
        "/task-types", json={"name": "coding"}, headers={"X-API-Key": "secret-key"}
    )
    assert r.status_code == 200
