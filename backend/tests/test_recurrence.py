from __future__ import annotations

import datetime as dt

from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.battle_plan import RecurringTemplate
from app.models.battle_plan import RecurrenceFrequency, RecurrenceMode
from app.schemas.battle_plan import RecurrencePreviewRequest
from app.services.recurrence_service import iter_windows


def rule(**changes):
    values = {
        "mode": "scheduled",
        "frequency": "daily",
        "interval": 1,
        "weekdays": [],
        "month_day": None,
        "quota_count": None,
        "start_date": dt.date(2025, 1, 1),
        "end_date": None,
        "cycle_limit": None,
    }
    values.update(changes)
    return RecurrencePreviewRequest(**values)


def test_calendar_rules_cover_intervals_weekdays_month_fallback_and_limits():
    monthly = iter_windows(rule(
        frequency=RecurrenceFrequency.monthly,
        month_day=31,
        start_date=dt.date(2025, 1, 31),
    ), dt.date(2025, 4, 30))
    assert [item.start for item in monthly] == [
        dt.date(2025, 1, 31), dt.date(2025, 2, 28),
        dt.date(2025, 3, 31), dt.date(2025, 4, 30),
    ]

    weekly = iter_windows(rule(
        frequency=RecurrenceFrequency.weekly,
        interval=2,
        weekdays=[0, 4],
        start_date=dt.date(2025, 1, 1),
        cycle_limit=3,
    ), dt.date(2025, 2, 28))
    assert [item.start for item in weekly] == [
        dt.date(2025, 1, 3), dt.date(2025, 1, 13), dt.date(2025, 1, 17),
    ]


def test_quota_week_boundaries_follow_setting():
    quota = rule(
        mode=RecurrenceMode.quota,
        frequency=RecurrenceFrequency.weekly,
        quota_count=3,
        start_date=dt.date(2025, 1, 8),
    )
    monday = iter_windows(quota, dt.date(2025, 1, 20), "monday")
    sunday = iter_windows(quota, dt.date(2025, 1, 20), "sunday")
    assert monday[0].start == dt.date(2025, 1, 8)
    assert monday[0].end == dt.date(2025, 1, 12)
    assert sunday[0].end == dt.date(2025, 1, 11)
    assert monday[1].start == dt.date(2025, 1, 13)
    assert sunday[1].start == dt.date(2025, 1, 12)


def _daily_body(today: str, **changes):
    body = {
        "title": "Daily review",
        "mode": "scheduled",
        "frequency": "daily",
        "interval": 1,
        "start_date": today,
        "checklist_titles": ["Inbox", "Calendar"],
    }
    body.update(changes)
    return body


def test_generation_is_idempotent_and_copies_checklist(client):
    today = client.get("/health").json()["today"]
    created = client.post("/recurring-templates", json=_daily_body(today))
    assert created.status_code == 201, created.text
    first = client.get("/tasks").json()["items"]
    second = client.get("/tasks").json()["items"]
    assert len(first) == len(second) == 8
    assert all(task["ready_to_plan"] for task in first)
    assert [child["title"] for child in first[0]["subtasks"]] == ["Inbox", "Calendar"]
    assert all(not child["ready_to_plan"] for child in first[0]["subtasks"])
    assert first[0]["recurring_template_title"] == "Daily review"


def test_past_start_requires_confirmation_then_backfills(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    body = _daily_body((today - dt.timedelta(days=3)).isoformat(), checklist_titles=[])
    response = client.post("/recurring-templates", json=body)
    assert response.status_code == 409
    assert response.json()["detail"]["past_cycles"] == 3
    body["confirm_backfill"] = True
    assert client.post("/recurring-templates", json=body).status_code == 201
    assert len(client.get("/tasks").json()["items"]) == 11


def test_quota_sessions_drive_parent_and_cannot_be_scheduled_early(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "exercise"}).json()
    response = client.post("/recurring-templates", json={
        "title": "Gym", "mode": "quota", "frequency": "weekly", "interval": 1,
        "quota_count": 3, "start_date": today.isoformat(),
        "task_type_id": task_type["id"],
    })
    assert response.status_code == 201, response.text
    parent = client.get("/tasks").json()["items"][0]
    assert parent["recurrence_kind"] == "quota_parent"
    assert parent["quota_completed"] == 0
    assert [child["title"] for child in parent["subtasks"]] == ["Session 1", "Session 2", "Session 3"]
    assert all(child["ready_to_plan"] for child in parent["subtasks"])

    session = parent["subtasks"][0]
    too_early = dt.date.fromisoformat(session["quota_period_start"]) - dt.timedelta(days=1)
    blocked = client.post(f"/days/{too_early.isoformat()}/blocks", json={
        "lane": "planned", "task_type_id": task_type["id"], "task_id": session["id"],
        "start_minute": 600, "end_minute": 630,
    })
    assert blocked.status_code == 422

    assert client.patch(f"/tasks/{session['id']}", json={"status": "completed"}).status_code == 200
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "in_progress"
    assert refreshed["quota_completed"] == 1
    for child in refreshed["subtasks"][1:]:
        client.patch(f"/tasks/{child['id']}", json={"status": "completed"})
    assert client.get("/tasks").json()["items"][0]["status"] == "completed"


def test_template_edit_honors_task_field_override(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today, checklist_titles=[])).json()
    tasks = client.get("/tasks").json()["items"]
    custom = tasks[0]
    client.patch(f"/tasks/{custom['id']}", json={"title": "My custom title"})
    updated = client.patch(f"/recurring-templates/{template['id']}", json={"title": "New title"})
    assert updated.status_code == 200, updated.text
    refreshed = client.get("/tasks").json()["items"]
    by_id = {item["id"]: item for item in refreshed}
    assert by_id[custom["id"]]["title"] == "My custom title"
    assert all(item["title"] == "New title" for item in refreshed if item["id"] != custom["id"])


def test_schedule_edit_starts_after_preserved_history_without_new_backfill(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    start = today - dt.timedelta(days=28)
    template = client.post("/recurring-templates", json={
        "title": "Weekly review", "mode": "scheduled", "frequency": "weekly",
        "interval": 1, "weekdays": [start.weekday()], "start_date": start.isoformat(),
        "confirm_backfill": True,
    }).json()
    before = [task for task in client.get("/tasks").json()["items"] if task["recurring_template_id"] == template["id"]]
    assert len(before) == 6

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "daily", "weekdays": [], "interval": 1,
    })
    assert changed.status_code == 200, changed.text
    after = [task for task in client.get("/tasks").json()["items"] if task["recurring_template_id"] == template["id"]]
    # Four historical weekly occurrences plus today and the seven-day daily horizon.
    assert len(after) == 12


def test_checklist_edit_rebuilds_only_pristine_future_occurrences(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today)).json()
    response = client.patch(f"/recurring-templates/{template['id']}", json={
        "checklist_titles": ["New first", "New second"],
    })
    assert response.status_code == 200, response.text
    tasks = client.get("/tasks").json()["items"]
    assert len(tasks) == 8
    assert all([child["title"] for child in task["subtasks"]] == ["New first", "New second"] for task in tasks)


def test_occurrence_tombstone_prevents_regeneration(client):
    today = client.get("/health").json()["today"]
    client.post("/recurring-templates", json=_daily_body(today, checklist_titles=[]))
    task_id = client.get("/tasks").json()["items"][0]["id"]
    client.delete(f"/tasks/{task_id}")
    client.delete(f"/tasks/{task_id}/permanent")
    remaining = client.get("/tasks").json()["items"]
    assert len(remaining) == 7
    assert task_id not in {task["id"] for task in remaining}


def test_lifecycle_and_permanent_template_delete_preserve_tasks(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today, checklist_titles=[])).json()
    first_task = client.get("/tasks").json()["items"][0]
    client.patch(f"/tasks/{first_task['id']}", json={"description": "keep me"})
    assert client.post(f"/recurring-templates/{template['id']}/pause").status_code == 200
    assert client.get("/recurring-templates?status=paused").json()[0]["status"] == "paused"
    assert client.post(f"/recurring-templates/{template['id']}/resume").status_code == 200
    assert client.post(f"/recurring-templates/{template['id']}/end").status_code == 200
    assert client.delete(f"/recurring-templates/{template['id']}").status_code == 204
    preserved = client.get("/tasks").json()["items"]
    assert len(preserved) == 1
    assert preserved[0]["recurring_template_id"] is None
    assert preserved[0]["description"] == "keep me"


def test_same_day_pause_resume_rematerializes_today(client):
    today = client.get("/health").json()["today"]
    template = client.post(
        "/recurring-templates",
        json=_daily_body(today, checklist_titles=[]),
    ).json()

    assert client.post(f"/recurring-templates/{template['id']}/pause").status_code == 200
    resumed = client.post(f"/recurring-templates/{template['id']}/resume")

    assert resumed.status_code == 200, resumed.text
    tasks = [
        task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    assert len(tasks) == 8
    assert {task["deadline_date"] for task in tasks} >= {today}
    assert resumed.json()["next_occurrence"] == today


def test_resume_after_longer_pause_still_suppresses_through_resume_day(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    yesterday = today - dt.timedelta(days=1)
    template = client.post(
        "/recurring-templates",
        json=_daily_body(yesterday.isoformat(), checklist_titles=[], confirm_backfill=True),
    ).json()
    assert client.post(f"/recurring-templates/{template['id']}/pause").status_code == 200
    with Session(get_engine()) as db:
        row = db.get(RecurringTemplate, template["id"])
        row.paused_at = dt.datetime.combine(
            yesterday,
            dt.time(hour=12),
            tzinfo=dt.timezone.utc,
        )
        db.commit()

    resumed = client.post(f"/recurring-templates/{template['id']}/resume")

    assert resumed.status_code == 200, resumed.text
    dates = {
        task["deadline_date"] for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    }
    assert yesterday.isoformat() in dates
    assert today.isoformat() not in dates
    assert resumed.json()["next_occurrence"] == (today + dt.timedelta(days=1)).isoformat()
