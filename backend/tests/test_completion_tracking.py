"""Task lifecycle changes must survive the same replay used by device commands."""

import datetime as dt
import json
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.routes import battle_plan
from app.core.config import Settings, get_settings
from app.db.session import get_engine
from app.main import app
from app.models.activity import ActivityOperation, ActivityState
from app.models.battle_plan import TaskCompletionOperation
from app.services import activity_reconciliation as rec
from app.services import task_completion_service
from tests.test_activity_api import command


def instant(hour):
    return dt.datetime(2026, 9, 10, hour, tzinfo=dt.UTC)


@pytest.fixture
def tracking(client):
    app.dependency_overrides[get_settings] = lambda: Settings(
        activity_tracking_dev=True
    )
    app.dependency_overrides[battle_plan.capture_utc_now] = lambda: instant(11)
    yield client
    app.dependency_overrides.pop(get_settings, None)
    app.dependency_overrides.pop(battle_plan.capture_utc_now, None)


def send(client, snapshot, kind, hour, **fields):
    effective = fields.pop(
        "effective", {"mode": "instant", "at": instant(hour).isoformat()}
    )
    response = client.post(
        "/activity/commands",
        json=command(
            snapshot,
            kind,
            sequence=snapshot["cursor"] + 1,
            action_at=instant(hour).isoformat(),
            effective=effective,
            **fields,
        ),
    )
    assert response.status_code == 200, response.text
    return response.json()


def setup_activity(client):
    type_id = client.post("/task-types", json={"name": "Writing"}).json()["id"]
    task_id = client.post(
        "/tasks", json={"title": "Finish chapter", "task_type_id": type_id}
    ).json()["id"]
    snapshot = send(
        client,
        client.get("/activity").json(),
        "start",
        10,
        task_type_id=type_id,
        task_id=task_id,
        selection_snapshot=True,
    )
    return type_id, task_id, snapshot


def add_past(client, snapshot, type_id):
    return send(
        client,
        snapshot,
        "add",
        13,
        task_type_id=type_id,
        target_id=None,
        effective={
            "mode": "range",
            "at": instant(8).isoformat(),
            "end": instant(9).isoformat(),
        },
    )


def assert_replay_agrees(client):
    before = client.get("/activity").json()
    with Session(get_engine()) as db:
        state = db.get(ActivityState, 1)
        rec.materialize(db, state, list(db.scalars(select(ActivityOperation))))
        db.commit()
    after = client.get("/activity").json()
    assert after["records"] == before["records"]
    assert after["current"] == before["current"]
    for part in after["coverage"]:
        if part["record_id"] is not None:
            record = next(r for r in after["records"] if r["id"] == part["record_id"])
            assert rec.instant(part["start"]) == rec.instant(record["start_at"])
            assert (
                part["end"] is None
                if record["end_at"] is None
                else rec.instant(part["end"]) == rec.instant(record["end_at"])
            )
    return after


@pytest.mark.parametrize("followup", ["start", "add", "edit"])
def test_completion_stops_journal_and_allows_followup(tracking, followup):
    type_id, task_id, original = setup_activity(tracking)
    response = tracking.post(f"/tasks/{task_id}/complete")
    assert response.status_code == 200, response.text
    stopped = tracking.get("/activity").json()
    assert stopped["current"] is None
    if followup == "start":
        result = send(tracking, stopped, "start", 12, task_type_id=type_id)
        assert result["current"]["task_id"] is None
    else:
        result = add_past(tracking, stopped, type_id)
        if followup == "edit":
            past = next(
                r for r in result["records"] if rec.instant(r["start_at"]) == instant(8)
            )
            result = send(
                tracking,
                result,
                "edit",
                14,
                target_id=past["id"],
                note="Edited later",
                effective={
                    "mode": "range",
                    "at": past["start_at"],
                    "end": past["end_at"],
                },
            )
        assert result["current"] is None
    ended = next(r for r in result["records"] if r["id"] == original["current"]["id"])
    assert rec.instant(ended["end_at"]) == instant(11)
    assert stopped["cursor"] == original["cursor"] + 1
    assert_replay_agrees(tracking)


def test_completion_undo_and_repeat_do_not_resume(tracking):
    type_id, task_id, _ = setup_activity(tracking)
    completed = tracking.post(f"/tasks/{task_id}/complete").json()
    before = tracking.get("/activity").json()
    assert tracking.post(f"/tasks/{task_id}/complete").status_code == 422
    assert tracking.get("/activity").json()["cursor"] == before["cursor"]
    undone = tracking.post(
        f"/tasks/{task_id}/undo-completion",
        json={"undo_token": completed["undo_token"]},
    )
    assert undone.status_code == 200, undone.text
    assert (
        add_past(tracking, tracking.get("/activity").json(), type_id)["current"] is None
    )
    assert_replay_agrees(tracking)


def test_unrelated_completion_preserves_current_and_cursor(tracking):
    _, _, original = setup_activity(tracking)
    other = tracking.post("/tasks", json={"title": "Other task"}).json()["id"]
    assert tracking.post(f"/tasks/{other}/complete").status_code == 200
    after = assert_replay_agrees(tracking)
    assert after["current"] == original["current"]
    assert after["cursor"] == original["cursor"]


def test_completion_failure_rolls_back_journal_and_records(tracking, monkeypatch):
    _, task_id, original = setup_activity(tracking)

    def fail(*args):
        raise RuntimeError("Injected failure after activity change")

    monkeypatch.setattr(task_completion_service, "_derive_quota", fail)
    with pytest.raises(RuntimeError, match="Injected failure"):
        tracking.post(f"/tasks/{task_id}/complete")
    after = assert_replay_agrees(tracking)
    assert after["records"] == original["records"]
    assert after["cursor"] == original["cursor"]
    with Session(get_engine()) as db:
        assert list(db.scalars(select(TaskCompletionOperation))) == []


@pytest.mark.parametrize("link_count,legacy_token", [(1, True), (2, False)])
def test_completion_and_undo_preserve_plan_links_through_replay(
    tracking, link_count, legacy_token
):
    type_id, task_id, snapshot = setup_activity(tracking)
    plan_response = tracking.post(
        "/days/2026-09-11/blocks",
        json={
            "lane": "planned",
            "task_type_id": type_id,
            "task_id": task_id,
            "start_minute": 600,
            "end_minute": 660,
        },
    )
    assert plan_response.status_code == 200, plan_response.text
    plan_id = plan_response.json()["planned_blocks"][0]["id"]
    # Two historical Actuals may link to one future plan independently of its time.
    for hour in (6, 8)[:link_count]:
        snapshot = send(
            tracking,
            snapshot,
            "add",
            10,
            task_type_id=type_id,
            planned_block_id=plan_id,
            target_id=None,
            effective={
                "mode": "range",
                "at": instant(hour).isoformat(),
                "end": instant(hour + 1).isoformat(),
            },
        )
    linked_ids = {
        r["id"] for r in snapshot["records"] if r["planned_block_id"] == plan_id
    }
    assert len(linked_ids) == link_count
    completed = tracking.post(f"/tasks/{task_id}/complete")
    assert completed.status_code == 200, completed.text
    assert completed.json()["removed_planned_block_ids"] == [plan_id]
    after = assert_replay_agrees(tracking)
    assert all(
        r["planned_block_id"] is None for r in after["records"] if r["id"] in linked_ids
    )
    if legacy_token:
        with Session(get_engine()) as db:
            saved = db.get(TaskCompletionOperation, completed.json()["undo_token"])
            snapshot = json.loads(saved.snapshot_json)
            for plan in snapshot["removed_planned_blocks"]:
                plan["corresponding_actual_id"] = plan.pop("corresponding_actual_ids")[
                    0
                ]
            saved.snapshot_json = json.dumps(snapshot)
            db.commit()
    undone = tracking.post(
        f"/tasks/{task_id}/undo-completion",
        json={"undo_token": completed.json()["undo_token"]},
    )
    assert undone.status_code == 200, undone.text
    restored = assert_replay_agrees(tracking)
    assert restored["current"] is None
    assert {
        r["id"] for r in restored["records"] if r["planned_block_id"] == plan_id
    } == linked_ids


@pytest.mark.parametrize("kind", ["switch", "stop"])
def test_postgres_completion_serializes_with_tracking(tracking, kind):
    if get_engine().dialect.name != "postgresql":
        pytest.skip("Real PostgreSQL row locks required")
    type_id, task_id, before = setup_activity(tracking)
    barrier = Barrier(2)
    operation = command(
        before,
        kind,
        sequence=2,
        task_type_id=type_id,
        action_at=instant(12).isoformat(),
        effective={"mode": "instant", "at": instant(12).isoformat()},
    )

    def complete():
        barrier.wait(timeout=10)
        return tracking.post(f"/tasks/{task_id}/complete")

    def transition():
        barrier.wait(timeout=10)
        return tracking.post("/activity/commands", json=operation)

    with ThreadPoolExecutor(2) as pool:
        a, b = pool.submit(complete), pool.submit(transition)
        assert a.result(timeout=15).status_code == 200
        transition_result = b.result(timeout=15)
        assert transition_result.status_code in (200, 422)
        if transition_result.status_code == 422:
            assert transition_result.json()["detail"] == "Transition does not match the observed activity"
    after = assert_replay_agrees(tracking)
    assert before["cursor"] < after["cursor"] <= before["cursor"] + 2
    if kind == "stop":
        assert after["current"] is None
    elif transition_result.status_code == 200:
        assert after["current"] is not None
        assert after["current"]["task_id"] is None
    else:
        assert after["current"] is None
    assert all(
        r["end_at"] is not None for r in after["records"] if r["task_id"] == task_id
    )
