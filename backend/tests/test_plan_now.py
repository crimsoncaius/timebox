import datetime as dt
import uuid

import pytest

from tests.test_activity_api import command
from tests.test_activity_api import tracking as tracking


def freeze(monkeypatch, instant):
    from app.services import activity_service, plan_now
    at = dt.datetime.fromisoformat(instant)
    monkeypatch.setattr(activity_service, "utc_now", lambda: at)
    monkeypatch.setattr(plan_now, "utc_now", lambda: at)
    return at


def request(client, minutes=15, name="Writing", task_type_id=None, **changes):
    state = client.get("/activity").json()
    if task_type_id is None:
        task_type_id = client.post("/task-types", json={"name": name}).json()["id"]
        state = client.get("/activity").json()
    at = dt.datetime.fromisoformat(state["server_at"].replace("Z", "+00:00"))
    end = at + dt.timedelta(minutes=minutes)
    conflicts = [p["id"] for p in state["plans"] if dt.datetime.fromisoformat(p["start_at"]) < end and dt.datetime.fromisoformat(p["end_at"]) > at
                 and (p["task_type_id"], p["task_id"], p["name"]) != (task_type_id, changes.get("task_id"), name)]
    return dict(operation_id=str(uuid.uuid4()), revision=state["plan_now_revision"],
                current_id=state["current"]["id"] if state["current"] else None,
                effective_at=at.isoformat(), minutes=minutes, task_type_id=task_type_id, name=name, replace_plan_ids=conflicts, **changes)


def save(client, body):
    response = client.post("/activity/plan-now", json=body)
    assert response.status_code == 200, response.text
    return response.json()


def test_start_retry_adjust_and_atomic_undo(tracking):
    body = request(tracking)
    first = save(tracking, body)
    current = first["current"]
    plan = next(p for p in first["plans"] if p["id"] == current["planned_block_id"])
    assert dt.datetime.fromisoformat(current["start_at"]) == dt.datetime.fromisoformat(plan["start_at"])
    assert dt.datetime.fromisoformat(plan["end_at"]) - dt.datetime.fromisoformat(plan["start_at"]) == dt.timedelta(minutes=15)
    assert save(tracking, body)["current"] == current
    adjustment = request(tracking, minutes=30, task_type_id=body["task_type_id"])
    adjusted = save(tracking, adjustment)
    assert adjusted["current"]["id"] == current["id"]
    assert adjusted["current"]["start_at"] == current["start_at"]
    assert adjusted["plans"][0]["id"] == plan["id"]
    undone = tracking.post(f'/activity/plan-now/{adjustment["operation_id"]}/undo')
    assert undone.status_code == 200, undone.text
    assert undone.json()["plans"] == first["plans"]
    assert undone.json()["current"]["id"] == current["id"]
    assert tracking.post(f'/activity/plan-now/{adjustment["operation_id"]}/undo').json()["plans"] == first["plans"]


def test_interruption_resumes_plan_and_undo_restores_both(tracking):
    first_body = request(tracking, minutes=60)
    first = save(tracking, first_body)
    interrupt = request(tracking, minutes=15, name="Email")
    interrupted = save(tracking, interrupt)
    assert interrupted["current"]["task_type_id"] == interrupt["task_type_id"]
    assert len(interrupted["plans"]) == 3
    writing = [p for p in interrupted["plans"] if p["task_type_id"] == first_body["task_type_id"]]
    assert len(writing) == 2
    assert writing[-1]["end_at"] == first["plans"][0]["end_at"]
    undo = tracking.post(f'/activity/plan-now/{interrupt["operation_id"]}/undo')
    assert undo.status_code == 200, undo.text
    assert undo.json()["plans"] == first["plans"]
    assert undo.json()["current"]["id"] == first["current"]["id"]


def test_stale_save_and_changed_undo_do_not_mutate(tracking):
    body = request(tracking)
    stale = {**body, "operation_id": str(uuid.uuid4())}
    saved = save(tracking, body)
    rejected = tracking.post("/activity/plan-now", json=stale)
    assert rejected.status_code == 409
    assert tracking.get("/activity").json()["plans"] == saved["plans"]
    tracking.post("/activity/commands", json=command(saved, "stop", device="other"))
    assert tracking.post(f'/activity/plan-now/{body["operation_id"]}/undo').status_code == 409


def test_confirmation_cannot_silently_replace_unreviewed_plans(tracking):
    save(tracking, request(tracking, minutes=60))
    body = request(tracking, name="Email")
    body["replace_plan_ids"] = []
    before = tracking.get("/activity").json()
    assert tracking.post("/activity/plan-now", json=body).status_code == 409
    assert tracking.get("/activity").json()["plans"] == before["plans"]


@pytest.mark.parametrize("minutes", [0, 91])
def test_invalid_duration_changes_nothing(tracking, minutes):
    body = request(tracking)
    body["minutes"] = minutes
    assert tracking.post("/activity/plan-now", json=body).status_code == 422
    assert tracking.get("/activity").json()["current"] is None


def test_unlinked_matching_plan_keeps_start_note_and_actual_identity(tracking, monkeypatch):
    freeze(monkeypatch, "2026-10-04T10:20:59.732+00:00")
    kind = tracking.post("/task-types", json={"name": "Writing"}).json()["id"]
    plan = tracking.post("/days/2026-10-04/blocks", json=dict(lane="planned", task_type_id=kind,
        name="Writing", note="Keep this note", start_minute=600, end_minute=660)).json()
    current = tracking.post("/activity/commands", json=command(tracking.get("/activity").json(), "start",
        task_type_id=kind, name="Writing", selection_snapshot=True)).json()["current"]
    assert current["planned_block_id"] is None
    body = request(tracking, task_type_id=kind)
    saved = save(tracking, body)
    assert len(saved["plans"]) == 1
    assert saved["plans"][0]["id"] == plan["id"]
    assert saved["plans"][0]["note"] == "Keep this note"
    assert saved["plans"][0]["start_at"].startswith("2026-10-04T10:00:00")
    assert saved["plans"][0]["end_at"].startswith("2026-10-04T10:35:59.732")
    assert saved["current"]["id"] == current["id"]
    assert saved["current"]["start_at"] == current["start_at"]


def test_delayed_save_midnight_projection_edit_and_reporting(tracking, monkeypatch):
    from app.services import plan_now
    at = freeze(monkeypatch, "2026-10-04T23:59:59.732+00:00")
    body = request(tracking)
    monkeypatch.setattr(plan_now, "utc_now", lambda: at + dt.timedelta(seconds=3))
    saved = save(tracking, body)
    plan = saved["plans"][0]
    assert dt.datetime.fromisoformat(plan["end_at"]) - dt.datetime.fromisoformat(plan["start_at"]) == dt.timedelta(minutes=15)
    portions = []
    for date in ("2026-10-04", "2026-10-05"):
        response = tracking.get(f"/days/{date}")
        assert response.status_code == 200, response.text
        block = next(p for p in response.json()["time_blocks"] if p["id"] == plan["id"])
        assert block["duration_minutes"] == 15
        portions.append(block["minutes_in_day"])
        edited = tracking.patch(f'/days/{date}/blocks/{plan["id"]}', json=dict(note="Edited", start_minute=block["start_minute"], end_minute=block["end_minute"]))
        assert edited.status_code == 200, edited.text
    assert sum(portions) == pytest.approx(15)
    after = tracking.get("/activity").json()["plans"][0]
    assert after["start_at"] == plan["start_at"] and after["end_at"] == plan["end_at"]
    wrong_day = tracking.patch(f'/days/2026-10-06/blocks/{plan["id"]}', json={"note": "Wrong day"})
    assert wrong_day.status_code in (404, 409)


@pytest.mark.parametrize("at", ["2026-03-08T06:59:59.732+00:00", "2026-11-01T05:59:59.732+00:00"])
def test_dst_plan_duration_is_elapsed_time(tracking, monkeypatch, at):
    from app.core.config import Settings, get_settings
    from app.main import app
    app.dependency_overrides[get_settings] = lambda: Settings(activity_tracking_dev=True, app_timezone="America/New_York")
    freeze(monkeypatch, at)
    saved = save(tracking, request(tracking, minutes=90))
    plan = saved["plans"][0]
    assert dt.datetime.fromisoformat(plan["end_at"]) - dt.datetime.fromisoformat(plan["start_at"]) == dt.timedelta(minutes=90)
    date = at[:10]
    day = tracking.get(f"/days/{date}").json()
    assert day["planned_minutes"] == 90
    assert day["time_blocks"][0]["duration_minutes"] == 90


def test_failure_after_plan_mutation_rolls_back_everything(tracking, monkeypatch):
    from app.services import planned_recording
    first = save(tracking, request(tracking, minutes=60))
    body = request(tracking, name="Email")
    def fail(*args, **kwargs):
        raise ValueError("Injected recording failure")
    monkeypatch.setattr(planned_recording, "apply", fail)
    response = tracking.post("/activity/plan-now", json=body)
    assert response.status_code == 409
    after = tracking.get("/activity").json()
    assert after["plans"] == first["plans"] and after["records"] == first["records"]


def test_plan_edit_invalidates_preview_and_undo(tracking):
    body = request(tracking)
    first = save(tracking, body)
    stale = request(tracking, task_type_id=body["task_type_id"])
    date = first["plans"][0]["start_at"][:10]
    response = tracking.patch(f'/days/{date}/blocks/{first["plans"][0]["id"]}', json={"note": "Changed elsewhere"})
    assert response.status_code == 200, response.text
    assert tracking.post("/activity/plan-now", json=stale).status_code == 409
    assert tracking.post(f'/activity/plan-now/{body["operation_id"]}/undo').status_code == 409


def test_task_link_and_ready_to_plan_restore_on_undo(tracking):
    kind = tracking.post("/task-types", json={"name": "Writing"}).json()["id"]
    response = tracking.post("/tasks", json={"title": "Draft", "task_type_id": kind, "ready_to_plan": True})
    assert response.status_code == 201, response.text
    task = response.json()
    body = request(tracking, task_type_id=kind, name="Draft", task_id=task["id"])
    saved = save(tracking, body)
    assert saved["current"]["task_id"] == task["id"]
    assert saved["plans"][0]["task_id"] == task["id"]
    assert next(t for t in tracking.get('/tasks').json()["items"] if t["id"] == task["id"])["ready_to_plan"] is False
    undone = tracking.post(f'/activity/plan-now/{body["operation_id"]}/undo')
    assert undone.status_code == 200, undone.text
    assert next(t for t in tracking.get('/tasks').json()["items"] if t["id"] == task["id"])["ready_to_plan"] is True


def test_generated_plan_replacement_survives_sync_and_undo(tracking, monkeypatch):
    from tests.test_recurrence import _daily_body
    today = tracking.get("/health").json()["today"]
    freeze(monkeypatch, today + "T08:59:59.732+00:00")
    kind = tracking.post("/task-types", json={"name": "Writing"}).json()["id"]
    template = tracking.post("/recurring-templates", json=_daily_body(today, task_type_id=kind,
        checklist_titles=[], preplanning_schedule={"slots": [{"start_minute": 540, "end_minute": 570}]}))
    assert template.status_code == 201, template.text
    original = tracking.get("/activity").json()["plans"]
    body = request(tracking, name="Email", minutes=60)
    saved = save(tracking, body)
    tracking.get(f'/recurring-templates/{template.json()["id"]}')
    assert tracking.get("/activity").json()["plans"] == saved["plans"]
    undone = tracking.post(f'/activity/plan-now/{body["operation_id"]}/undo')
    assert undone.status_code == 200, undone.text
    assert undone.json()["plans"] == original
    tracking.get(f'/recurring-templates/{template.json()["id"]}')
    assert tracking.get("/activity").json()["plans"] == original


def test_cross_midnight_replacement_allocates_new_identity_and_restores_original(tracking, monkeypatch):
    freeze(monkeypatch, "2026-10-04T23:59:59.732+00:00")
    kind = tracking.post("/task-types", json={"name": "Meeting"}).json()["id"]
    response = tracking.post("/days/2026-10-05/blocks", json=dict(lane="planned", task_type_id=kind,
        name="Meeting", note="Original", start_minute=0, end_minute=30))
    assert response.status_code == 200, response.text
    original = tracking.get("/activity").json()["plans"]
    body = request(tracking, minutes=60, name="Writing")
    saved = save(tracking, body)
    assert saved["current"]["planned_block_id"] != original[0]["id"]
    undone = tracking.post(f'/activity/plan-now/{body["operation_id"]}/undo')
    assert undone.status_code == 200, undone.text
    assert undone.json()["plans"] == original
