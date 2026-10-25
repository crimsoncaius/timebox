import datetime as dt

import pytest
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.google_calendar import GoogleCalendarConnection
from app.models.time_block import BlockLane, TimeBlock
from app.services import google_calendar as calendar
from app.services import planned_intervals as plans
from app.services import task_type_service

NOW = dt.datetime(2030, 1, 1, tzinfo=dt.UTC)


def event(id="meeting", hour=10, uid=None, **extra):
    return dict(id=id, iCalUID=uid or id, summary=id, etag=f"{id}-{hour}",
                start={"dateTime": f"2030-01-02T{hour:02}:00:00Z"},
                end={"dateTime": f"2030-01-02T{hour+1:02}:00:00Z"}, **extra)


@pytest.fixture
def db():
    with Session(get_engine(), autoflush=False) as db:
        db.add(GoogleCalendarConnection(id=1, credentials="test", data={"account": "me", "selected": ["primary"],
               "calendars": [{"id": "primary", "name": "Primary"}, {"id": "shared", "name": "Shared"}]}))
        db.flush()
        yield db


def sync(db, events, now=NOW):
    calendar.freeze(db, now)
    calendar.ingest(db, {"primary": events}, now)
    calendar.reconcile(db, now)


def ordinary(db, hour=11):
    row = TimeBlock(lane=BlockLane.planned, task_type_id=task_type_service.get_or_create_unspecified(db).id, name="Focus")
    plans.set_interval(db, row, NOW + dt.timedelta(days=1, hours=hour), NOW + dt.timedelta(days=1, hours=hour+1), "UTC")
    return row


def test_move_conflict_removes_stale_plan_preserves_actual_and_metadata(db):
    sync(db, [event()])
    row = calendar.occurrences(db)[0]
    original = row.block_id
    block = db.get(TimeBlock, original)
    block.note = "Local context"
    actual = TimeBlock(lane=BlockLane.actual, task_type_id=block.task_type_id, planned_block_id=block.id,
                       start_at=NOW - dt.timedelta(hours=1), end_at=NOW)
    db.add(actual)
    obstruction = ordinary(db)
    sync(db, [event(hour=11)])
    assert row.block_id is None and row.data["status"] == "pending"
    assert row.note == "Local context" and actual.planned_block_id is None
    assert db.get(TimeBlock, actual.id) is actual
    calendar.remove_block(db, obstruction)
    sync(db, [event(hour=11)])
    assert row.block_id and db.get(TimeBlock, row.block_id).note == "Local context"
    assert actual.planned_block_id is None
    sync(db, [event(hour=11)])
    assert len(plans.rows(db)) == 1


def test_exclusions_only_reset_on_schedule_change_and_survive_disconnect(db):
    sync(db, [event()])
    row = calendar.occurrences(db)[0]
    calendar.exclude(db, row, NOW)
    renamed = event()
    renamed["summary"] = "Renamed"
    sync(db, [renamed])
    assert row.data["status"] == "excluded" and row.block_id is None
    calendar.apply_command(db, {"kind": "disconnect"}, NOW)
    conn = calendar.connection(db)
    conn.credentials = "reconnected"
    conn.data = {**conn.data, "selected": ["primary"]}
    sync(db, [renamed])
    assert row.block_id is None
    sync(db, [event(hour=12)])
    assert row.block_id is not None


def test_started_history_and_forget_keep_identity(db):
    sync(db, [event()])
    row = calendar.occurrences(db)[0]
    original = row.block_id
    later = NOW + dt.timedelta(days=1, hours=10)
    sync(db, [event(hour=13)], later)
    assert row.block_id == original and row.data["frozen"]
    assert plans.interval(db.get(TimeBlock, original), "UTC")[0].hour == 10
    calendar.apply_command(db, {"kind": "forget"}, later)
    assert row.data == {"frozen": True, "status": "frozen"}
    conn = calendar.connection(db)
    conn.credentials = "reconnected"
    conn.data = {"account": "me", "selected": ["primary"]}
    sync(db, [event(hour=13)], later)
    assert len(plans.rows(db)) == 1


def test_two_new_events_are_pending_and_resolution_is_atomic(db):
    sync(db, [event("a"), event("b")])
    a, b = sorted(calendar.occurrences(db), key=lambda r: r.data["name"])
    assert all(r.data["status"] == "pending" for r in (a, b))
    db.commit()
    with pytest.raises(ValueError), db.begin_nested():
        calendar.apply_command(db, {"kind": "resolve", "id": a.id}, NOW)
    assert not plans.rows(db)
    calendar.apply_command(db, {"kind": "resolve", "id": a.id, "exclude_ids": [b.id]}, NOW)
    assert a.block_id and b.data["status"] == "excluded"


def test_duplicate_precedence_cancellation_and_deselection(db):
    conn = calendar.connection(db)
    conn.data = {**conn.data, "selected": ["primary", "shared"]}
    calendar.ingest(db, {"primary": [event("a", uid="same")], "shared": [event("b", hour=12, uid="same")]}, NOW)
    calendar.reconcile(db, NOW)
    row = calendar.occurrences(db)[0]
    assert len(calendar.occurrences(db)) == 1 and row.data["start"].endswith("10:00:00+00:00")
    sync(db, [event("a", uid="same", status="cancelled")])
    assert row.block_id is None
    calendar.apply_command(db, {"kind": "select", "selected": ["shared"]}, NOW)
    assert row.block_id and row.data["calendar_id"] == "shared"


def test_recurring_exceptions_and_offsets_are_stable(db):
    first = event("first", uid="series", originalStartTime={"dateTime": "2030-01-02T10:00:00Z"})
    second = event("second", hour=12, uid="series", originalStartTime={"dateTime": "2030-01-02T12:00:00Z"})
    sync(db, [first, second])
    assert len(plans.rows(db)) == 2
    first["start"]["dateTime"] = "2030-01-02T18:00:00+08:00"
    first["end"]["dateTime"] = "2030-01-02T19:00:00+08:00"
    sync(db, [first, second])
    assert len(plans.rows(db)) == 2


def test_pending_expiry_and_rescheduled_recovery(db):
    ordinary(db, 10)
    sync(db, [event()])
    row = calendar.occurrences(db)[0]
    later = NOW + dt.timedelta(days=1, hours=10)
    sync(db, [event()], later)
    assert row.data["status"] == "expired"
    sync(db, [event(hour=14)], later)
    assert row.block_id and row.data["status"] == "imported"


def test_failed_full_fetch_does_not_remove_valid_plans(db):
    sync(db, [event()])
    conn = calendar.connection(db)
    conn.data = {**conn.data, "selected": ["primary", "shared"]}
    class Reader:
        def calendars(self):
            return [{"id": "primary"}, {"id": "shared"}]
        def events(self, calendar_id, *args):
            if calendar_id == "shared":
                raise calendar.source.GoogleUnavailable("Offline")
            return [event(status="cancelled")]
    with pytest.raises(calendar.source.GoogleUnavailable):
        calendar.synchronize(db, None, NOW, google=Reader())
    assert len(plans.rows(db)) == 1


def test_preview_rolls_back_and_stale_confirmation_is_rejected(db, client, monkeypatch):
    from app.api.routes import google_calendar as routes
    monkeypatch.setattr(routes, "utc_now", lambda: NOW)
    monkeypatch.setattr(routes, "sync", lambda *args: None)
    sync(db, [event()])
    row = calendar.occurrences(db)[0]
    db.commit()
    command = {"kind": "exclude", "id": row.id}
    preview = client.post("/google-calendar/action", json={"command": command})
    assert preview.status_code == 200, preview.text
    db.expire_all()
    assert row.block_id is not None
    ordinary(db, 14)
    db.commit()
    stale = client.post("/google-calendar/action", json={"command": command, "fingerprint": preview.json()["fingerprint"]})
    assert stale.status_code == 409
    preview = client.post("/google-calendar/action", json={"command": command}).json()
    done = client.post("/google-calendar/action", json={"command": command, "fingerprint": preview["fingerprint"]})
    assert done.status_code == 200, done.text
    db.expire_all()
    assert row.block_id is None and row.data["status"] == "excluded"


def test_source_ownership_and_delete_exclusion_on_existing_routes(db, client):
    from app.schemas.time_block import TimeBlockPatch
    from app.services import day_service
    sync(db, [event()])
    row = calendar.occurrences(db)[0]
    block = db.get(TimeBlock, row.block_id)
    db.commit()
    with pytest.raises(ValueError, match="Google"):
        day_service.patch_time_block(db, block.day, block.id, TimeBlockPatch(name="Local rename"))
    db.rollback()
    day_service.delete_time_block(db, block.day, block.id)
    assert row.data["status"] == "excluded" and row.block_id is None


def test_oauth_state_is_single_use_and_tokens_are_encrypted(client, monkeypatch):
    from urllib.parse import parse_qs, urlparse

    from cryptography.fernet import Fernet

    from app.api.routes import google_calendar as routes
    from app.core.config import Settings, get_settings
    from app.main import app
    settings = Settings(api_key="test-key", google_client_id="client", google_client_secret="secret",
                        google_redirect_uri="http://localhost/callback", google_token_key=Fernet.generate_key().decode())
    app.dependency_overrides[get_settings] = lambda: settings
    try:
        assert client.post("/google-calendar/connect").status_code == 401
        result = client.post("/google-calendar/connect", headers={"X-API-Key": "test-key"})
        assert result.status_code == 200
        nonce = parse_qs(urlparse(result.json()["url"]).query)["state"][0]
        assert client.get("/google-calendar/callback", params={"state": "forged", "error": "access_denied"}).status_code == 400
        assert client.get("/google-calendar/callback", params={"state": nonce, "error": "access_denied"}).status_code == 200
        assert client.get("/google-calendar/callback", params={"state": nonce, "error": "access_denied"}).status_code == 400
        sealed = routes.source.seal(settings, {"refresh_token": "private-refresh-token"})
        assert "private-refresh-token" not in sealed
        assert routes.source.unseal(settings, sealed)["refresh_token"] == "private-refresh-token"
    finally:
        app.dependency_overrides.pop(get_settings, None)
