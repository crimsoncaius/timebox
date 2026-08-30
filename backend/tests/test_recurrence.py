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


def _planned_block(client, date: dt.date, task: dict, task_type_id: int) -> dict:
    response = client.post(f"/days/{date.isoformat()}/blocks", json={
        "lane": "planned",
        "task_type_id": task_type_id,
        "task_id": task["id"],
        "start_minute": 600,
        "end_minute": 660,
    })
    assert response.status_code == 200, response.text
    return response.json()["planned_blocks"][-1]


def test_generation_is_idempotent_and_copies_checklist(client):
    today = client.get("/health").json()["today"]
    created = client.post("/recurring-templates", json=_daily_body(today))
    assert created.status_code == 201, created.text
    first = client.get("/tasks").json()["items"]
    second = client.get("/tasks").json()["items"]
    assert len(first) == len(second) == 8
    assert all(task["ready_to_plan"] for task in first)
    assert [child["title"] for child in first[0]["subtasks"]] == ["Inbox", "Calendar"]
    assert all("ready_to_plan" not in child for child in first[0]["subtasks"])
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
    assert [child["title"] for child in parent["session_tasks"]] == ["Session 1", "Session 2", "Session 3"]
    assert all(child["ready_to_plan"] for child in parent["session_tasks"])

    session = parent["session_tasks"][0]
    too_early = dt.date.fromisoformat(session["quota_period_start"]) - dt.timedelta(days=1)
    blocked = client.post(f"/days/{too_early.isoformat()}/blocks", json={
        "lane": "planned", "task_type_id": task_type["id"], "task_id": session["id"],
        "start_minute": 600, "end_minute": 630,
    })
    assert blocked.status_code == 422

    assert client.post(f"/tasks/{session['id']}/complete").status_code == 200
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "in_progress"
    assert refreshed["quota_completed"] == 1
    for child in refreshed["session_tasks"][1:]:
        client.post(f"/tasks/{child['id']}/complete")
    assert client.get("/tasks").json()["items"][0]["status"] == "completed"


def test_quota_series_edit_still_propagates_fields_to_unoverridden_sessions(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json={
        "title": "Practice",
        "description": "Original guidance",
        "mode": "quota",
        "frequency": "weekly",
        "interval": 1,
        "quota_count": 2,
        "start_date": today,
    }).json()
    tracker = client.get("/tasks").json()["items"][0]
    customized, inherited = tracker["session_tasks"]
    assert client.patch(
        f"/tasks/{customized['id']}", json={"description": "My session note"}
    ).status_code == 200

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "description": "Updated guidance", "urgency": "high",
    })

    assert changed.status_code == 200, changed.text
    refreshed = client.get("/tasks").json()["items"][0]
    sessions = {session["id"]: session for session in refreshed["session_tasks"]}
    assert refreshed["description"] == "Updated guidance"
    assert sessions[customized["id"]]["description"] == "My session note"
    assert sessions[customized["id"]]["urgency"] == "high"
    assert sessions[inherited["id"]]["description"] == "Updated guidance"
    assert sessions[inherited["id"]]["urgency"] == "high"


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


def test_cadence_edit_starts_today_while_protected_future_occurrence_survives(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post("/recurring-templates", json={
        "title": "Weekly review", "mode": "scheduled", "frequency": "weekly",
        "interval": 1, "weekdays": [today.weekday()], "start_date": today.isoformat(),
        "checklist_titles": [],
    }).json()
    before = [
        task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    protected = next(task for task in before if task["deadline_date"] == (today + dt.timedelta(days=7)).isoformat())
    assert client.patch(
        f"/tasks/{protected['id']}", json={"description": "future exception"}
    ).status_code == 200

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "daily", "weekdays": [], "interval": 1,
    })

    assert changed.status_code == 200, changed.text
    after = [
        task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    assert {task["deadline_date"] for task in after} == {
        (today + dt.timedelta(days=offset)).isoformat() for offset in range(8)
    }
    preserved = next(task for task in after if task["id"] == protected["id"])
    assert preserved["description"] == "future exception"


def test_new_cadence_is_anchored_to_application_local_today(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    original_start = today - dt.timedelta(days=27)
    template = client.post("/recurring-templates", json={
        "title": "Weekly review", "mode": "scheduled", "frequency": "weekly",
        "interval": 1, "weekdays": [today.weekday()],
        "start_date": original_start.isoformat(), "confirm_backfill": True,
        "checklist_titles": [],
    }).json()

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "daily", "weekdays": [], "interval": 2,
    })

    assert changed.status_code == 200, changed.text
    current_and_future = sorted(
        task["deadline_date"] for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
        and task["deadline_date"] >= today.isoformat()
    )
    assert current_and_future == [
        (today + dt.timedelta(days=offset)).isoformat() for offset in (0, 2, 4, 6)
    ]


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


def test_checklist_edit_keeps_protected_snapshot_and_rebuilds_unprotected_occurrence(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today)).json()
    before = [
        task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    protected, rebuildable = before[:2]
    assert client.post(f"/subtasks/{protected['subtasks'][0]['id']}/check").status_code == 200
    assert client.post(f"/subtasks/{protected['subtasks'][0]['id']}/uncheck").status_code == 200

    response = client.patch(f"/recurring-templates/{template['id']}", json={
        "checklist_titles": ["Inbox zero", "Plan tomorrow"],
    })

    assert response.status_code == 200, response.text
    after = {
        task["id"]: task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    }
    assert [child["title"] for child in after[protected["id"]]["subtasks"]] == ["Inbox", "Calendar"]
    assert after[protected["id"]]["subtasks"][0]["checked"] is False
    assert rebuildable["id"] in after
    assert [child["title"] for child in after[rebuildable["id"]]["subtasks"]] == [
        "Inbox zero", "Plan tomorrow",
    ]
    assert {child["id"] for child in after[rebuildable["id"]]["subtasks"]}.isdisjoint(
        {child["id"] for child in rebuildable["subtasks"]}
    )


def test_field_propagation_uses_stable_origin_not_moved_deadline(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post(
        "/recurring-templates", json=_daily_body(today.isoformat(), checklist_titles=[])
    ).json()
    occurrence = next(
        task for task in client.get("/tasks").json()["items"]
        if task["deadline_date"] == (today + dt.timedelta(days=2)).isoformat()
    )
    moved = client.patch(
        f"/tasks/{occurrence['id']}",
        json={"deadline_date": (today - dt.timedelta(days=20)).isoformat()},
    )
    assert moved.status_code == 200, moved.text

    updated = client.patch(
        f"/recurring-templates/{template['id']}", json={"description": "series description"}
    )

    assert updated.status_code == 200, updated.text
    refreshed = next(
        task for task in client.get("/tasks").json()["items"]
        if task["id"] == occurrence["id"]
    )
    assert refreshed["deadline_date"] == (today - dt.timedelta(days=20)).isoformat()
    assert refreshed["description"] == "series description"


def test_planned_and_actual_state_survive_cadence_replacement(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Focus"}).json()
    template = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(), checklist_titles=[], task_type_id=task_type["id"]
    )).json()
    before = [
        task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    planned_task = next(task for task in before if task["deadline_date"] == (today + dt.timedelta(days=1)).isoformat())
    actual_task = next(task for task in before if task["deadline_date"] == (today + dt.timedelta(days=2)).isoformat())
    planned = _planned_block(client, today + dt.timedelta(days=1), planned_task, task_type["id"])
    actual = client.post("/actual-blocks", json={
        "task_type_id": task_type["id"],
        "task_id": actual_task["id"],
        "start_at": f"{today.isoformat()}T01:00:00Z",
        "end_at": f"{today.isoformat()}T02:00:00Z",
    })
    assert actual.status_code == 201, actual.text
    assert client.delete(f"/actual-blocks/{actual.json()['id']}").status_code == 204

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "weekly", "weekdays": [today.weekday()], "interval": 1,
    })

    assert changed.status_code == 200, changed.text
    after = {task["id"]: task for task in client.get("/tasks").json()["items"]}
    assert planned_task["id"] in after
    assert actual_task["id"] in after
    assert client.get(f"/days/{(today + dt.timedelta(days=1)).isoformat()}").json()["planned_blocks"][0]["id"] == planned["id"]
    assert client.get(f"/actual-blocks/{actual.json()['id']}").status_code == 404


def test_completion_reopen_and_undo_retain_each_occurrence_snapshot(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post(
        "/recurring-templates", json=_daily_body(today.isoformat())
    ).json()
    first, second = client.get("/tasks").json()["items"][:2]
    assert client.post(f"/subtasks/{second['subtasks'][0]['id']}/check").status_code == 200

    first_completion = client.post(f"/tasks/{first['id']}/complete")
    assert first_completion.status_code == 200, first_completion.text
    assert client.patch(f"/recurring-templates/{template['id']}", json={
        "title": "Later series title", "checklist_titles": ["Later template"],
    }).status_code == 200
    reopened = client.post(f"/tasks/{first['id']}/reopen")
    assert reopened.status_code == 200, reopened.text
    assert reopened.json()["title"] == "Daily review"
    assert [subtask["title"] for subtask in reopened.json()["subtasks"]] == ["Inbox", "Calendar"]

    second_completion = client.post(f"/tasks/{second['id']}/complete")
    assert second_completion.status_code == 200, second_completion.text
    assert client.patch(
        f"/recurring-templates/{template['id']}", json={"description": "later description"}
    ).status_code == 200
    undone = client.post(f"/tasks/{second['id']}/undo-completion", json={
        "undo_token": second_completion.json()["undo_token"],
    })
    assert undone.status_code == 200, undone.text
    assert undone.json()["description"] == ""
    assert undone.json()["subtasks"][0]["checked"] is True

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "weekly", "weekdays": [today.weekday()], "interval": 1,
    })
    assert changed.status_code == 200, changed.text
    after = {task["id"]: task for task in client.get("/tasks").json()["items"]}
    assert first["id"] in after
    assert second["id"] in after


def test_completing_one_occurrence_leaves_another_snapshot_and_plan_untouched(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Focus"}).json()
    client.post("/recurring-templates", json=_daily_body(
        today.isoformat(), task_type_id=task_type["id"]
    ))
    first, second = client.get("/tasks").json()["items"][:2]
    assert client.post(f"/subtasks/{second['subtasks'][1]['id']}/check").status_code == 200
    planned = _planned_block(client, today + dt.timedelta(days=2), second, task_type["id"])

    completed = client.post(f"/tasks/{first['id']}/complete")

    assert completed.status_code == 200, completed.text
    after = {task["id"]: task for task in client.get("/tasks").json()["items"]}
    assert after[first["id"]]["status"] == "completed"
    assert after[first["id"]]["completed_at"] is not None
    assert after[second["id"]]["status"] == "open"
    assert after[second["id"]]["completed_at"] is None
    assert [subtask["checked"] for subtask in after[second["id"]]["subtasks"]] == [False, True]
    assert client.get(f"/days/{(today + dt.timedelta(days=2)).isoformat()}").json()["planned_blocks"][0]["id"] == planned["id"]


def test_occurrence_tombstone_prevents_regeneration(client):
    today = client.get("/health").json()["today"]
    template = client.post(
        "/recurring-templates", json=_daily_body(today, checklist_titles=[])
    ).json()
    task_id = client.get("/tasks").json()["items"][0]["id"]
    client.delete(f"/tasks/{task_id}")
    client.delete(f"/tasks/{task_id}/permanent")
    remaining = client.get("/tasks").json()["items"]
    assert len(remaining) == 7
    assert task_id not in {task["id"] for task in remaining}
    series = client.get(f"/recurring-templates/{template['id']}").json()
    assert f"scheduled:{today}" not in {window["key"] for window in series["upcoming"]}


def test_lifecycle_preserves_protected_occurrences_and_defers_permanent_series_delete(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today, checklist_titles=[])).json()
    first_task = client.get("/tasks").json()["items"][0]
    client.patch(f"/tasks/{first_task['id']}", json={"description": "keep me"})
    assert client.post(f"/recurring-templates/{template['id']}/pause").status_code == 200
    assert client.get("/recurring-templates?status=paused").json()[0]["status"] == "paused"
    assert client.post(f"/recurring-templates/{template['id']}/resume").status_code == 200
    assert client.post(f"/recurring-templates/{template['id']}/end").status_code == 200
    assert client.delete(f"/recurring-templates/{template['id']}").status_code == 405
    preserved = client.get("/tasks").json()["items"]
    assert len(preserved) == 1
    assert preserved[0]["recurring_template_id"] == template["id"]
    assert preserved[0]["description"] == "keep me"


def test_pause_and_end_remove_only_untouched_future_occurrences(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Focus"}).json()
    template = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(), checklist_titles=[], task_type_id=task_type["id"]
    )).json()
    protected = next(
        task for task in client.get("/tasks").json()["items"]
        if task["deadline_date"] == (today + dt.timedelta(days=3)).isoformat()
    )
    planned = _planned_block(client, today + dt.timedelta(days=3), protected, task_type["id"])

    paused = client.post(f"/recurring-templates/{template['id']}/pause")

    assert paused.status_code == 200, paused.text
    paused_tasks = [
        task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    assert [task["id"] for task in paused_tasks] == [protected["id"]]
    assert client.post(f"/recurring-templates/{template['id']}/resume").status_code == 200
    assert client.post(f"/recurring-templates/{template['id']}/end").status_code == 200
    ended_tasks = [
        task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    assert [task["id"] for task in ended_tasks] == [protected["id"]]
    assert client.get(f"/days/{(today + dt.timedelta(days=3)).isoformat()}").json()["planned_blocks"][0]["id"] == planned["id"]


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
