import uuid

import pytest

from app.core.config import Settings, get_settings
from app.main import app


@pytest.fixture
def tracking(client):
    app.dependency_overrides[get_settings] = lambda: Settings(activity_tracking_dev=True)
    yield client
    app.dependency_overrides.pop(get_settings, None)


def command(snapshot, kind, sequence=1, device="web", **payload):
    return {
        "operation_id": str(uuid.uuid4()), "device_id": device,
        "sequence": sequence, "kind": kind,
        "action_at": snapshot["server_at"],
        "calibration": {"server_at": snapshot["server_at"], "offset_ms": 0},
        "base_cursor": snapshot["cursor"],
        "effective": {"mode": "server_now"},
        "target_id": snapshot["current"]["id"] if snapshot["current"] else None,
        **payload,
    }


def test_start_is_immediate_durable_and_retry_does_not_restart(tracking):
    initial = tracking.get("/activity").json()
    operation = command(initial, "start")
    response = tracking.post("/activity/commands", json=operation)
    assert response.status_code == 200, response.text
    saved = response.json()
    assert saved["current"]["name"] is None
    assert saved["current"]["task_type"]["name"] == "unspecified"
    assert saved["current"]["start_at"] == saved["acknowledgement"]["effective_at"]
    retry = tracking.post("/activity/commands", json=operation).json()
    assert retry["current"] == saved["current"]
    assert retry["cursor"] == saved["cursor"]
    assert tracking.get("/actual-blocks/active").json()["id"] == saved["current"]["id"]
    assert tracking.get("/activity").json()["current"] == saved["current"]


def test_switch_is_contiguous_stop_leaves_gap_and_old_retry_cannot_revive(tracking):
    first = tracking.post("/activity/commands", json=command(tracking.get("/activity").json(), "start")).json()
    task_type = tracking.post("/task-types", json={"name": "reading"}).json()
    switch = command(first, "switch", device="android", task_type_id=task_type["id"])
    second = tracking.post("/activity/commands", json=switch).json()
    assert second["current"]["task_type"]["name"] == "reading"
    assert second["current"]["name"] is None
    assert second["records"][0]["end_at"] == second["current"]["start_at"]
    assert second["current"]["task_id"] is None
    assert second["current"]["planned_block_id"] is None
    stopped = tracking.post("/activity/commands", json=command(second, "stop", sequence=2)).json()
    assert stopped["current"] is None
    assert len(stopped["records"]) == 2
    retry = tracking.post("/activity/commands", json=switch).json()
    assert retry["cursor"] == stopped["cursor"]
    assert retry["current"] is None
    restarted = tracking.post("/activity/commands", json=command(stopped, "start", sequence=3)).json()
    assert restarted["current"]["start_at"] > stopped["records"][-1]["end_at"]


def test_stale_device_gets_explicit_conflict_and_canonical_state(tracking):
    original = tracking.get("/activity").json()
    saved = tracking.post("/activity/commands", json=command(original, "start")).json()
    conflict = tracking.post("/activity/commands", json=command(original, "start", device="android")).json()
    assert conflict["acknowledgement"]["outcome"] == "conflict"
    assert conflict["current"] == saved["current"]
    assert len(conflict["records"]) == 1


def test_failed_switch_changes_nothing_and_command_id_cannot_be_reused(tracking):
    operation = command(tracking.get("/activity").json(), "start")
    saved = tracking.post("/activity/commands", json=operation).json()
    invalid = command(saved, "switch", sequence=2)
    assert tracking.post("/activity/commands", json=invalid).status_code == 422
    assert tracking.get("/activity").json()["current"] == saved["current"]
    assert tracking.post("/activity/commands", json={**operation, "kind": "stop"}).status_code == 422


def test_default_server_does_not_expose_activity_protocol(client):
    assert client.get("/activity").status_code == 404


def test_development_gate_blocks_legacy_writers_even_after_flag_is_disabled(tracking):
    saved = tracking.post("/activity/commands", json=command(tracking.get("/activity").json(), "start")).json()
    assert tracking.post("/actual-blocks/start", json={}).status_code == 409
    app.dependency_overrides[get_settings] = lambda: Settings(activity_tracking_dev=False)
    assert tracking.post(f'/actual-blocks/{saved["current"]["id"]}/finish').status_code == 409


def test_task_type_cascade_and_migration_cannot_bypass_activity_protocol(tracking):
    saved = tracking.post("/activity/commands", json=command(tracking.get("/activity").json(), "start")).json()
    reading = tracking.post("/task-types", json={"name": "reading"}).json()
    other = tracking.post("/task-types", json={"name": "break"}).json()
    saved = tracking.post("/activity/commands", json=command(saved, "switch", sequence=2, task_type_id=reading["id"])).json()
    assert tracking.delete(f'/task-types/{reading["id"]}?cascade_blocks=true').status_code == 409
    assert tracking.delete(f'/task-types/{reading["id"]}?migrate_blocks_to={other["id"]}').status_code == 409
    assert tracking.get("/activity").json()["current"] == saved["current"]


def test_concurrent_postgres_writers_and_duplicate_switches(tracking):
    from concurrent.futures import ThreadPoolExecutor
    from threading import Barrier
    from app.db.session import get_engine

    if get_engine().dialect.name != "postgresql":
        pytest.skip("Requires real PostgreSQL; run with an isolated DATABASE_URL")
    initial = tracking.get("/activity").json()
    barrier = Barrier(2)
    def send(operation):
        barrier.wait(timeout=10)
        response = tracking.post("/activity/commands", json=operation)
        assert response.status_code == 200, response.text
        return response.json()
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(send, [command(initial, "start"), command(initial, "start", device="android")]))
    assert sorted(row["acknowledgement"]["outcome"] for row in results) == ["applied", "conflict"]
    latest = tracking.get("/activity").json()
    assert len(latest["records"]) == 1
    task_type = tracking.post("/task-types", json={"name": "break"}).json()
    operation = command(latest, "switch", sequence=2, task_type_id=task_type["id"])
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(send, [operation, operation]))
    assert results[0]["current"] == results[1]["current"]
    assert results[0]["cursor"] == results[1]["cursor"]
    assert len(results[0]["records"]) == 2
    assert results[0]["records"][0]["end_at"] == results[0]["current"]["start_at"]


def test_recording_crosses_midnight_and_plan_boundaries_without_completion(tracking, monkeypatch):
    import datetime as dt
    from app.services import activity_service

    class Clock(dt.datetime):
        instant = dt.datetime(2026, 9, 11, 23, 59, 10, tzinfo=dt.timezone.utc)
        @classmethod
        def now(cls, tz=None):
            return cls.instant
    monkeypatch.setattr(activity_service.dt, "datetime", Clock)
    initial = tracking.get("/activity").json()
    first = tracking.post("/activity/commands", json=command(initial, "start")).json()
    Clock.instant = dt.datetime(2026, 9, 12, 1, 10, tzinfo=dt.timezone.utc)
    later = tracking.get("/activity").json()
    assert later["current"]["id"] == first["current"]["id"]
    assert later["current"]["start_at"] == "2026-09-11T23:59:10Z"
    assert len(later["records"]) == 1
    assert tracking.get("/days/2026-09-11").json()["actual_blocks"][0]["actual_block"]["id"] == first["current"]["id"]
    assert tracking.get("/days/2026-09-12").json()["actual_blocks"][0]["actual_block"]["id"] == first["current"]["id"]
