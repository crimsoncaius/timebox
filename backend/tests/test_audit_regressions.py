"""Persisted calendar, archive-history, and pre-planning regressions from the audit."""
import datetime as dt
from zoneinfo import ZoneInfo

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.routes import battle_plan
from app.core.config import Settings, get_settings
from app.db.session import get_engine
from app.main import app
from app.models.battle_plan import RecurrenceOccurrence, Task
from app.services import task_completion_service
from app.services.recurrence import templates

NOW = dt.datetime(2026, 11, 5, 18, tzinfo=dt.UTC)


def request(client, method, path, body=None, status=200):
    result = client.request(method, path, json=body)
    assert result.status_code == status, result.text
    return result.json() if result.content else None


@pytest.fixture
def reporting(client, monkeypatch):
    settings = Settings(app_timezone="UTC", activity_tracking_dev=True)
    app.dependency_overrides[get_settings] = lambda: settings
    def local_now(zone):
        return NOW.astimezone(ZoneInfo(zone))
    for module in ["app.services.battle_plan.tasks", "app.services.battle_plan._shared",
                   "app.api.routes.battle_plan"]:
        monkeypatch.setattr(module + ".now_in_tz", local_now)
    for module in ["app.services.battle_plan.tasks", "app.services.recurrence.templates",
                   "app.services.recurrence.synchronization", "app.services.recurrence.calendar",
                   "app.api.routes.recurring"]:
        monkeypatch.setattr(module + ".today_in_tz", lambda zone: local_now(zone).date())
    app.dependency_overrides[battle_plan.capture_utc_now] = lambda: NOW
    yield client, settings
    app.dependency_overrides.pop(get_settings, None)
    app.dependency_overrides.pop(battle_plan.capture_utc_now, None)


@pytest.mark.parametrize("zone,past_date,past_start,future_date,future_start", [
    ("Asia/Singapore", "2026-11-05", 1260, "2026-11-06", 180),
    ("America/New_York", "2026-11-05", 720, "2026-11-05", 840),
])
@pytest.mark.parametrize("via_api", [True, False])
def test_completion_uses_shared_zone_for_past_and_future_plans(
    reporting, zone, past_date, past_start, future_date, future_start, via_api
):
    client, deployment = reporting
    request(client, "PUT", "/activity/reporting-timezone", {"timezone": zone})
    task = request(client, "POST", "/tasks", {"title": "Complete across zones"}, 201)
    ids = []
    for date, start in [(past_date, past_start), (future_date, future_start)]:
        day = request(client, "POST", f"/days/{date}/blocks", {
            "task_id": task["id"], "start_minute": start, "end_minute": start + 30,
        })
        ids.append(next(b["id"] for b in day["planned_blocks"] if b["start_minute"] == start))
    if via_api:
        removed = request(client, "POST", f'/tasks/{task["id"]}/complete')["removed_planned_block_ids"]
    else:
        with Session(get_engine()) as db:
            _, _, removed = task_completion_service.complete_task(db, task["id"], NOW, deployment)
    assert removed == [ids[1]]
    assert ids[0] in [b["id"] for b in request(client, "GET", f"/days/{past_date}")["planned_blocks"]]


def test_task_deadlines_and_recurring_views_share_saved_today(reporting):
    client, deployment = reporting
    request(client, "PUT", "/activity/reporting-timezone", {"timezone": "Asia/Singapore"})
    created = request(client, "POST", "/tasks", {"title": "Due yesterday", "deadline_date": "2026-11-05"}, 201)
    assert created["overdue"] is True
    listed = request(client, "GET", "/tasks")
    assert listed["timezone"] == "Asia/Singapore"
    assert listed["items"][0]["overdue"] is True
    series = request(client, "POST", "/recurring-templates", {
        "title": "Daily", "mode": "scheduled", "frequency": "daily",
        "start_date": "2026-11-05", "confirm_backfill": True,
    }, 201)
    calendar = request(client, "GET", f'/recurring-templates/{series["id"]}/calendar?month=2026-11-01')
    assert series["next_occurrence"] == calendar["today"] == calendar["upcoming"][0] == "2026-11-06"
    with Session(get_engine()) as db:
        # Background/service callers must not revive yesterday's occurrence either.
        assert templates.get_template(db, series["id"], deployment).next_occurrence == dt.date(2026, 11, 6)
    preview = request(client, "POST", "/recurring-templates/preview", {
        "mode": "scheduled", "frequency": "daily", "start_date": "2026-11-05",
    })
    assert preview["past_cycles"] == 1
    assert preview["upcoming"][0]["start"] == "2026-11-06"


@pytest.mark.parametrize("mode", ["scheduled", "quota"])
def test_archiving_completed_occurrence_preserves_habit_history_but_blocks_edits(client, mode):
    day = (dt.datetime.now(dt.UTC).date() - dt.timedelta(days=2)).isoformat()
    body = {"title": "Keep history", "mode": mode, "frequency": "daily", "start_date": day,
            "confirm_backfill": True, "track_as_habit": True}
    body.update({"checklist_titles": ["Checkpoint"]} if mode == "scheduled" else {"quota_count": 1})
    series = request(client, "POST", "/recurring-templates", body, 201)
    key = series["id"]
    if mode == "scheduled":
        item = series["checklist_items"][0]["id"]
        request(client, "POST", f"/habits/{key}/items/{item}/days/{day}")
    request(client, "POST", f"/habits/{key}/days/{day}")
    with Session(get_engine()) as db:
        task_id = db.scalar(select(RecurrenceOccurrence.task_id).where(
            RecurrenceOccurrence.template_id == key,
            RecurrenceOccurrence.cycle_start == dt.date.fromisoformat(day),
        ))
    request(client, "POST", "/tasks/archive-completed", {"task_ids": [task_id]}, 204)
    habit = request(client, "GET", f"/habits?week={day}")["habits"][0]
    cell = next(d for d in habit["days"] if d["date"] == day)
    assert cell["state"] == "met" and cell["tickable"] is False
    if mode == "scheduled":
        item_cell = next(d for d in habit["items"][0]["days"] if d["date"] == day)
        assert item_cell["state"] == "met" and item_cell["tickable"] is False
        request(client, "DELETE", f"/habits/{key}/items/{item}/days/{day}", status=422)
    request(client, "DELETE", f"/habits/{key}/days/{day}", status=422)
    with Session(get_engine()) as db:
        assert db.get(Task, task_id).status.value == "completed"
    request(client, "POST", f"/tasks/{task_id}/unarchive", status=204)
    restored = request(client, "GET", f"/habits?week={day}")["habits"][0]
    assert next(d for d in restored["days"] if d["date"] == day)["tickable"] is True


@pytest.mark.parametrize("action", ["complete", "trash", "pause", "remove_schedule"])
def test_preplanning_notices_are_durable_actionable_and_stop_when_no_longer_relevant(client, action):
    today = request(client, "GET", "/health")["today"]
    request(client, "POST", f"/days/{today}/blocks", {"start_minute": 540, "end_minute": 600})
    series = request(client, "POST", "/recurring-templates", {
        "title": "Blocked slot", "mode": "scheduled", "frequency": "daily", "start_date": today,
        "cycle_limit": 1, "preplanning_mode": "planned_time",
        "preplanning_schedule": {"slots": [{"start_minute": 540, "end_minute": 600}]},
    }, 201)
    path = f'/recurring-templates/{series["id"]}'
    notice = series["preplanning_schedule"]["unavailable_slots"][0]
    assert notice["task_title"] == "Blocked slot" and notice["reason"] == "planned_block_overlap"
    assert notice["can_add_to_ready"] is True
    assert request(client, "GET", path)["preplanning_schedule"]["unavailable_slots"] == [notice]
    # The advertised recovery is an explicit Task mutation; noticing never sets readiness.
    task = next(t for t in request(client, "GET", "/tasks")["items"] if t["id"] == notice["task_id"])
    assert task["ready_to_plan"] is False
    request(client, "PATCH", f'/tasks/{notice["task_id"]}', {"ready_to_plan": True})
    assert request(client, "GET", path)["preplanning_schedule"]["unavailable_slots"][0]["can_add_to_ready"] is False
    assert len(request(client, "GET", f"/days/{today}")["planned_blocks"]) == 1
    if action == "complete":
        request(client, "POST", f'/tasks/{notice["task_id"]}/complete')
    elif action == "trash":
        request(client, "DELETE", f'/tasks/{notice["task_id"]}')
    elif action == "pause":
        request(client, "POST", path + "/pause")
    else:
        request(client, "PATCH", path, {"preplanning_mode": "none"})
    schedule = request(client, "GET", path)["preplanning_schedule"]
    assert schedule is None or schedule["unavailable_slots"] == []


def test_future_preplanning_notice_does_not_offer_current_queue_action(reporting):
    client, _ = reporting
    request(client, "PUT", "/activity/reporting-timezone", {"timezone": "Asia/Singapore"})
    request(client, "POST", "/days/2026-11-07/blocks", {"start_minute": 540, "end_minute": 600})
    series = request(client, "POST", "/recurring-templates", {
        "title": "Tomorrow", "mode": "scheduled", "frequency": "daily", "start_date": "2026-11-07",
        "cycle_limit": 1, "preplanning_mode": "planned_time",
        "preplanning_schedule": {"slots": [{"start_minute": 540, "end_minute": 600}]},
    }, 201)
    notice = series["preplanning_schedule"]["unavailable_slots"][0]
    assert notice["date"] == "2026-11-07" and notice["can_add_to_ready"] is False
