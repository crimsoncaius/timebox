"""Isolated backend audit probes. Run with backend/.venv Python from any cwd.

No network, external database, model calls, or application source writes.
Each check uses fresh in-memory SQLite and raises if the reported bug disappears.
"""
import datetime as dt
import os
from pathlib import Path
import sys
from unittest.mock import patch
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "backend"))
os.environ["DATABASE_URL"] = "sqlite:///:memory:"
os.environ["APP_TIMEZONE"] = "UTC"
os.environ["API_KEY"] = ""

from fastapi.testclient import TestClient
from app.main import app
from app.db.base import Base
from app.db.session import get_engine
from app.core.config import Settings, get_settings
from app.api.routes import battle_plan

client = TestClient(app)
NOW = dt.datetime(2026, 11, 5, 18, tzinfo=dt.UTC)


def reset():
    app.dependency_overrides.clear()
    app.dependency_overrides[get_settings] = lambda: Settings(
        database_url="sqlite:///:memory:", app_timezone="UTC", api_key=None,
        activity_tracking_dev=True,
    )
    Base.metadata.drop_all(get_engine())
    Base.metadata.create_all(get_engine())


def req(method, path, body=None, status=200):
    result = client.request(method, path, json=body)
    assert result.status_code == status, (path, result.status_code, result.text)
    return result.json() if result.content else None


def timezone_completion():
    reset()
    req("PUT", "/activity/reporting-timezone", {"timezone": "Asia/Singapore"})
    task = req("POST", "/tasks", {"title": "Audit historical plan"}, 201)
    before = req("POST", "/days/2026-11-05/blocks", {
        "task_id": task["id"], "start_minute": 1260, "end_minute": 1290,
    })
    block = before["time_blocks"][0]
    app.dependency_overrides[battle_plan.capture_utc_now] = lambda: NOW
    completed = req("POST", f'/tasks/{task["id"]}/complete')
    after = req("GET", "/days/2026-11-05")
    assert block["id"] in completed["removed_planned_block_ids"]
    assert not after["time_blocks"]
    print("B1 CONFIRMED: completion at 2026-11-06 02:00 Singapore deletes the already-past Nov 5 21:00 plan")


def timezone_overdue():
    reset()
    req("PUT", "/activity/reporting-timezone", {"timezone": "Asia/Singapore"})
    local_now = lambda zone: NOW.astimezone(ZoneInfo(zone))
    with patch("app.services.battle_plan._shared.now_in_tz", local_now), patch(
        "app.services.battle_plan.tasks.now_in_tz", local_now
    ), patch("app.api.routes.battle_plan.now_in_tz", local_now):
        task = req("POST", "/tasks", {"title": "Due yesterday", "deadline_date": "2026-11-05"}, 201)
        result = req("GET", "/tasks")
    assert result["timezone"] == "UTC"
    assert task["overdue"] is False
    print("B2 CONFIRMED: shared zone Singapore, /tasks timezone UTC, yesterday's deadline overdue=false")


def timezone_recurrence():
    reset()
    req("PUT", "/activity/reporting-timezone", {"timezone": "Asia/Singapore"})
    local_day = lambda zone: NOW.astimezone(ZoneInfo(zone)).date()
    with patch("app.services.recurrence.templates.today_in_tz", local_day), patch(
        "app.services.recurrence.synchronization.today_in_tz", local_day
    ), patch("app.services.recurrence.calendar.today_in_tz", local_day):
        series = req("POST", "/recurring-templates", {
            "title": "Daily audit", "mode": "scheduled", "frequency": "daily",
            "start_date": "2026-11-05", "confirm_backfill": True,
        }, 201)
        calendar = req("GET", f'/recurring-templates/{series["id"]}/calendar?month=2026-11-01')
    assert series["next_occurrence"] == "2026-11-05"
    assert calendar["today"] == "2026-11-06"
    assert calendar["upcoming"][0] == "2026-11-06"
    print("B2 CONFIRMED: same series next_occurrence=Nov 5 but its calendar today/upcoming=Nov 6")


def merged_task_assignment_control():
    reset()
    old = req("POST", "/task-types", {"name": "travel"})["id"]
    new = req("POST", "/task-types", {"name": "transport"})["id"]
    plan = req("POST", f"/task-types/{old}/merge-preview", {"target_id": new})
    req("POST", f"/task-types/{old}/merge", {"target_id": new, "preview_token": plan["preview_token"]})
    created = req("POST", "/tasks", {"title": "Stale draft", "task_type_id": old}, 201)
    assert created["task_type_id"] == new
    series = req("POST", "/recurring-templates", {
        "title": "Stale series", "task_type_id": old,
        "mode": "scheduled", "frequency": "daily", "start_date": "2099-01-01",
    }, 201)
    assert series["task_type_id"] == new
    print("CONTROL PASSED: delayed task/series assignments resolve merged category IDs correctly")


def archive_habit_progress():
    reset()
    day = (dt.datetime.now(dt.UTC).date() - dt.timedelta(days=2)).isoformat()
    series = req("POST", "/recurring-templates", {
        "title": "Audit habit", "mode": "scheduled", "frequency": "daily",
        "start_date": day, "confirm_backfill": True, "track_as_habit": True,
    }, 201)
    before = req("POST", f'/habits/{series["id"]}/days/{day}')
    cell = lambda result: next(d for h in result["habits"] if h["template_id"] == series["id"] for d in h["days"] if d["date"] == day)
    assert cell(before)["state"] == "met"
    completed = req("GET", f'/recurring-templates/{series["id"]}/calendar?month={day[:7]}-01')["completed"][0]
    req("POST", "/tasks/archive-completed", {"task_ids": [completed["id"]]}, 204)
    after = req("GET", f"/habits?week={day}")
    assert cell(after)["state"] == "missed"
    assert req("GET", "/tasks?state=archived")["items"][0]["status"] == "completed"
    print("B3 CONFIRMED: archiving a completed Task Occurrence changes its Habit day from met to missed")


if __name__ == "__main__":
    for check in [timezone_completion, timezone_overdue, timezone_recurrence, archive_habit_progress, merged_task_assignment_control]:
        check()
    app.dependency_overrides.clear()
