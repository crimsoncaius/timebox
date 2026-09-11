import datetime as dt
import pytest

from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import TimeBlock, BlockLane
from app.services import activity_cutover
from test_activity_api import tracking, command


def test_import_preserves_grid_history_running_identity_and_reporting_zone(tracking):
    with Session(get_engine()) as db:
        type_ = TaskType(name="writing")
        day = Day(date=dt.date(2026, 9, 10))
        db.add_all([type_, day]); db.flush()
        history = TimeBlock(lane=BlockLane.actual, day_id=day.id, start_minute=1380,
                            end_minute=1440, task_type_id=type_.id, name="Draft", note="Keep me")
        running = TimeBlock(lane=BlockLane.actual, start_at=dt.datetime(2026, 9, 11, tzinfo=dt.timezone.utc), task_type_id=type_.id)
        db.add_all([history, running]); db.commit()
        ids = (history.id, running.id)
    activity_cutover.apply(get_engine(), "Asia/Singapore")
    snapshot = tracking.get("/activity").json()
    assert [r["id"] for r in snapshot["records"]] == list(ids)
    assert snapshot["records"][0]["start_at"] == "2026-09-10T15:00:00Z"
    assert snapshot["records"][0]["end_at"] == "2026-09-10T16:00:00Z"
    assert snapshot["records"][0]["note"] == "Keep me"
    assert snapshot["current"]["start_at"] == "2026-09-11T00:00:00Z"
    assert snapshot["reporting_timezone"] == "Asia/Singapore"
    activity_cutover.apply(get_engine(), "UTC")
    assert tracking.get("/activity").json()["records"] == snapshot["records"]
    assert tracking.post("/actual-blocks/start", json={}).status_code == 426
    stopped = tracking.post("/activity/commands", json=command(snapshot, "stop")).json()
    assert stopped["current"] is None


@pytest.mark.parametrize("day,minute", [(dt.date(2026, 3, 8), 150), (dt.date(2026, 11, 1), 90)])
def test_ambiguous_or_missing_grid_time_fails_without_enabling(tracking, day, minute):
    with Session(get_engine()) as db:
        type_ = TaskType(name="writing")
        date = Day(date=day)
        db.add_all([type_, date]); db.flush()
        db.add(TimeBlock(lane=BlockLane.actual, day_id=date.id, start_minute=minute,
                         end_minute=minute + 30, task_type_id=type_.id))
        db.commit()
    with pytest.raises(ValueError, match="Ambiguous or nonexistent"):
        activity_cutover.apply(get_engine(), "America/New_York")
    # Bootstrap still refuses the intact legacy dataset.
    assert tracking.get("/activity").status_code == 409


def test_prewrite_rollback_and_postwrite_forward_recovery(tracking):
    activity_cutover.apply(get_engine(), "UTC")
    assert tracking.post("/actual-blocks/start", json={}, headers={"X-Timebox-Protocol": "activity-online-v1"}).status_code == 409
    activity_cutover.rollback(get_engine())
    activity_cutover.apply(get_engine(), "UTC")
    snapshot = tracking.get("/activity").json()
    operation = command(snapshot, "start")
    saved = tracking.post("/activity/commands", json=operation).json()
    activity_cutover.pause(get_engine())
    assert tracking.post("/activity/commands", json=operation).status_code == 503
    assert tracking.get("/activity").json()["current"] == saved["current"]
    with pytest.raises(ValueError, match="repair forward"):
        activity_cutover.rollback(get_engine())
    activity_cutover.pause(get_engine(), False)
    assert tracking.post("/activity/commands", json=operation).json()["current"] == saved["current"]


def test_all_old_client_routes_rejected_after_cutover(tracking):
    activity_cutover.apply(get_engine(), "UTC")
    for method, path in [("post", "/actual-blocks/start"), ("post", "/planned-blocks/1/record-actual-as-planned"),
                         ("delete", "/projects/1"), ("delete", "/task-types/1?cascade_blocks=true"),
                         ("patch", "/days/2026-09-11/blocks/1"), ("get", "/tasks")]:
        response = getattr(tracking, method)(path)
        assert response.status_code == 426, response.text


def test_postgres_cutover_drains_an_admitted_legacy_request(client, monkeypatch):
    from concurrent.futures import ThreadPoolExecutor, TimeoutError
    from threading import Event
    from app.services import actual_block_service
    from app.core.config import Settings, get_settings
    from app.main import app
    if get_engine().dialect.name != "postgresql":
        pytest.skip("Requires restored PostgreSQL test database")
    admitted, release = Event(), Event()
    original = actual_block_service.start_actual_block
    def delayed(*args, **kwargs):
        admitted.set()
        assert release.wait(10)
        return original(*args, **kwargs)
    monkeypatch.setattr(actual_block_service, "start_actual_block", delayed)
    with ThreadPoolExecutor(max_workers=2) as pool:
        writer = pool.submit(client.post, "/actual-blocks/start", json={})
        assert admitted.wait(10)
        migration = pool.submit(activity_cutover.apply, get_engine(), "UTC")
        try:
            with pytest.raises(TimeoutError):
                migration.result(timeout=0.2)
        finally:
            release.set()
        legacy = writer.result(timeout=10)
        assert legacy.status_code == 201, legacy.text
        migration.result(timeout=10)
    app.dependency_overrides[get_settings] = lambda: Settings(activity_tracking_dev=True)
    try:
        current = client.get("/activity").json()["current"]
        assert current["id"] == legacy.json()["id"]
        assert current["start_at"] == legacy.json()["start_at"]
        assert client.post(f'/actual-blocks/{current["id"]}/finish').status_code == 426
    finally:
        app.dependency_overrides.pop(get_settings, None)
