from __future__ import annotations

import datetime as dt

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.battle_plan import Task


def create_task(client, title="Task", **extra):
    response = client.post("/tasks", json={"title": title, **extra})
    assert response.status_code == 201, response.text
    return response.json()


def create_block(client, date, task_id, task_type_id, lane="planned", start_minute=540):
    if lane == "actual":
        start = dt.datetime.combine(
            dt.date.fromisoformat(date), dt.time.min, tzinfo=dt.timezone.utc
        ) + dt.timedelta(minutes=start_minute)
        response = client.post(
            "/actual-blocks",
            json={
                "task_id": task_id,
                "task_type_id": task_type_id,
                "start_at": start.isoformat(),
                "end_at": (start + dt.timedelta(minutes=30)).isoformat(),
            },
        )
        assert response.status_code == 201, response.text
        return
    response = client.post(
        f"/days/{date}/blocks",
        json={
            "lane": lane,
            "task_id": task_id,
            "task_type_id": task_type_id,
            "start_minute": start_minute,
            "end_minute": start_minute + 30,
        },
    )
    assert response.status_code == 200, response.text


def test_task_responses_include_planned_dates_for_tasks_but_not_subtask_lifecycle(client):
    task_type = client.post("/task-types", json={"name": "Focus"}).json()
    parent = create_task(client, "Parent", task_type_id=task_type["id"])
    child = create_task(
        client,
        "Child",
        parent_id=parent["id"],
    )

    create_block(client, "2026-08-24", parent["id"], task_type["id"])
    create_block(client, "2026-08-22", parent["id"], task_type["id"])
    create_block(client, "2026-08-22", parent["id"], task_type["id"], start_minute=600)
    create_block(client, "2026-08-21", parent["id"], task_type["id"], lane="actual")
    rejected_subtask_plan = client.post(
        "/days/2026-08-23/blocks",
        json={
            "lane": "planned",
            "task_id": child["id"],
            "task_type_id": task_type["id"],
            "start_minute": 540,
            "end_minute": 570,
        },
    )
    assert rejected_subtask_plan.status_code == 422

    rows = client.get("/tasks").json()["items"]
    assert rows[0]["planned_dates"] == ["2026-08-22", "2026-08-24"]
    assert "planned_dates" not in rows[0]["subtasks"][0]

    patched = client.patch(f"/tasks/{parent['id']}", json={"description": "Updated"})
    assert patched.json()["planned_dates"] == ["2026-08-22", "2026-08-24"]
    assert "planned_dates" not in patched.json()["subtasks"][0]

    trashed = client.delete(f"/tasks/{parent['id']}")
    assert trashed.json()["planned_dates"] == ["2026-08-22", "2026-08-24"]
    assert trashed.json()["subtasks"] == []

    created_without_blocks = create_task(client, "Empty")
    assert created_without_blocks["planned_dates"] == []


def test_project_crud_and_permanent_cascade(client):
    created = client.post(
        "/projects",
        json={"name": "Launch"},
    )
    assert created.status_code == 201
    project = created.json()
    task = create_task(client, project_id=project["id"])

    patched = client.patch(
        f"/projects/{project['id']}",
        json={"name": "Launch v2"},
    )
    assert patched.status_code == 200
    assert patched.json()["name"] == "Launch v2"

    assert client.delete(f"/projects/{project['id']}").status_code == 204
    assert client.get("/projects").json() == []
    assert client.get("/tasks").json()["items"] == []
    assert client.patch(f"/tasks/{task['id']}", json={"title": "Gone"}).status_code == 404


def test_project_names_are_trimmed_and_case_insensitively_unique(client):
    assert client.post("/projects", json={"name": "  Atlas  "}).json()["name"] == "Atlas"
    duplicate = client.post("/projects", json={"name": "atlas"})
    assert duplicate.status_code == 422


def test_subtasks_inherit_project_and_cannot_nest(client):
    project = client.post("/projects", json={"name": "Atlas"}).json()
    parent = create_task(client, "Parent", project_id=project["id"])
    child = create_task(client, "Child", parent_id=parent["id"], project_id=None)
    assert child["project_id"] == project["id"]

    nested = client.post("/tasks", json={"title": "Nested", "parent_id": child["id"]})
    assert nested.status_code == 422
    assert nested.json()["detail"] == "Subtasks cannot contain subtasks"

    moved = client.patch(f"/tasks/{parent['id']}", json={"project_id": None})
    assert moved.status_code == 200
    rows = client.get("/tasks").json()["items"]
    assert rows[0]["project_id"] is None
    assert "project_id" not in rows[0]["subtasks"][0]


def test_subtask_check_does_not_complete_parent(client):
    parent = create_task(client, "Parent")
    child = create_task(client, "Child", parent_id=parent["id"])
    assert client.post(f"/subtasks/{child['id']}/check").status_code == 200
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "open"
    assert "status" not in refreshed["subtasks"][0]
    assert refreshed["subtasks"][0]["checked"] is True


def test_planning_readiness_is_persistent_and_separate_from_work_status(client):
    task = create_task(client, "Plan me", status="in_progress", ready_to_plan=True)
    assert task["ready_to_plan"] is True
    assert task["status"] == "in_progress"

    patched = client.patch(
        f"/tasks/{task['id']}",
        json={"ready_to_plan": False},
    )
    assert patched.status_code == 200
    assert patched.json()["ready_to_plan"] is False
    assert patched.json()["status"] == "in_progress"


def test_reorder_changes_status_and_position(client):
    first = create_task(client, "First")
    second = create_task(client, "Second")
    response = client.post(
        "/tasks/reorder",
        json={
            "placements": [
                {"task_id": second["id"], "status": "blocked", "position": 0},
                {"task_id": first["id"], "status": "open", "position": 4},
            ]
        },
    )
    assert response.status_code == 204
    by_id = {row["id"]: row for row in client.get("/tasks").json()["items"]}
    assert by_id[second["id"]]["status"] == "open"
    assert by_id[second["id"]]["is_blocked"] is True
    assert by_id[first["id"]]["position"] == 4


def test_blocked_is_an_open_task_condition_and_completed_clears_it(client):
    task = create_task(client, "Waiting on approval")

    blocked = client.patch(
        f"/tasks/{task['id']}",
        json={"is_blocked": True, "blocking_reason": "Legal review"},
    )
    assert blocked.status_code == 200
    assert blocked.json()["status"] == "open"
    assert blocked.json()["is_blocked"] is True
    assert blocked.json()["blocking_reason"] == "Legal review"

    completed = client.post(f"/tasks/{task['id']}/complete")
    assert completed.status_code == 200
    assert completed.json()["task"]["is_blocked"] is False
    assert completed.json()["task"]["blocking_reason"] is None


def test_archive_restore_trash_and_permanent_delete(client):
    task = create_task(client)
    assert client.post(f"/tasks/{task['id']}/complete").status_code == 200
    assert client.post("/tasks/archive-completed", json={"task_ids": [task["id"]]}).status_code == 204
    assert client.get("/tasks").json()["items"] == []
    assert len(client.get("/tasks?state=archived").json()["items"]) == 1
    assert client.post(f"/tasks/{task['id']}/unarchive").status_code == 204

    assert client.delete(f"/tasks/{task['id']}").status_code == 200
    assert len(client.get("/tasks?state=trash").json()["items"]) == 1
    assert client.post(f"/tasks/{task['id']}/restore").status_code == 204
    assert len(client.get("/tasks").json()["items"]) == 1

    client.delete(f"/tasks/{task['id']}")
    assert client.delete(f"/tasks/{task['id']}/permanent").status_code == 204
    assert client.get("/tasks?state=trash").json()["items"] == []


def test_expired_trash_is_purged(client):
    task = create_task(client)
    client.delete(f"/tasks/{task['id']}")
    with Session(get_engine()) as db:
        row = db.execute(select(Task).where(Task.id == task["id"])).scalar_one()
        row.deleted_at = dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=31)
        db.commit()
    assert client.get("/tasks?state=trash").json()["items"] == []
    assert client.patch(f"/tasks/{task['id']}", json={"title": "Gone"}).status_code == 404


def test_task_type_removal_warns_then_clears_task_reference(client):
    task_type = client.post("/task-types", json={"name": "battle/work"}).json()
    task = create_task(client, task_type_id=task_type["id"])
    rows = client.get("/task-types").json()
    selected = next(row for row in rows if row["id"] == task_type["id"])
    assert selected["usage_count"] == 0
    assert selected["task_usage_count"] == 1

    warned = client.delete(f"/task-types/{task_type['id']}")
    assert warned.status_code == 409
    assert "Battle Plan" in warned.json()["detail"]
    assert client.delete(
        f"/task-types/{task_type['id']}?clear_task_references=true"
    ).status_code == 204
    refreshed = client.get("/tasks").json()["items"]
    assert refreshed[0]["id"] == task["id"]
    assert refreshed[0]["task_type_id"] is None


def test_deadlines_overdue_and_reminders_deliver_once(client):
    today = client.get("/health").json()["today"]
    due_today = create_task(client, "Due today", deadline_date=today)
    assert due_today["overdue"] is False

    overdue = create_task(client, "Overdue", deadline_date="2000-01-01")
    assert overdue["overdue"] is True
    future = create_task(
        client,
        "Reminder",
        deadline_at="2099-01-02T12:00:00Z",
        reminder_at="2020-01-01T12:00:00Z",
    )
    due = client.get("/reminders/due").json()
    assert [row["id"] for row in due] == [future["id"]]
    assert client.post(f"/reminders/{future['id']}/delivered").status_code == 204
    assert client.get("/reminders/due").json() == []


def test_reminder_requires_deadline_and_precedes_it(client):
    no_deadline = client.post(
        "/tasks", json={"title": "No due", "reminder_at": "2099-01-01T12:00:00Z"}
    )
    assert no_deadline.status_code == 422
    too_late = client.post(
        "/tasks",
        json={
            "title": "Late reminder",
            "deadline_at": "2099-01-01T12:00:00Z",
            "reminder_at": "2099-01-01T12:00:00Z",
        },
    )
    assert too_late.status_code == 422


def test_move_project_preserves_task_subtasks_and_blocks(client):
    from app.models.time_block import TimeBlock
    from app.models.time_block import BlockLane

    first = client.post("/projects", json={"name": "First"}).json()["id"]
    second = client.post("/projects", json={"name": "Second"}).json()["id"]
    task_type = client.post("/task-types", json={"name": "focus"}).json()["id"]
    parent = create_task(client, "Move me", description="Keep content", ready_to_plan=True, task_type_id=task_type)
    child = create_task(client, "Checkpoint", parent_id=parent["id"])
    client.post(f"/subtasks/{child['id']}/check")
    create_block(client, "2026-08-24", parent["id"], task_type)
    create_block(client, "2026-08-24", parent["id"], task_type, lane="actual")

    def block_ids():
        with Session(get_engine()) as db:
            return (list(db.scalars(select(TimeBlock.id).where(TimeBlock.task_id == parent["id"], TimeBlock.lane == BlockLane.planned))),
                    list(db.scalars(select(TimeBlock.id).where(TimeBlock.task_id == parent["id"], TimeBlock.lane == BlockLane.actual))))

    original_blocks = block_ids()
    assert all(original_blocks)
    baseline = next(row for row in client.get("/tasks").json()["items"] if row["id"] == parent["id"])
    for destination in (first, second, None):
        moved = client.patch(f"/tasks/{parent['id']}", json={"project_id": destination})
        assert moved.status_code == 200, moved.text
        saved = next(row for row in client.get("/tasks").json()["items"] if row["id"] == parent["id"])
        assert saved["project_id"] == destination
        for field in ("title", "description", "status", "ready_to_plan", "planned_dates"):
            assert saved[field] == baseline[field]
        assert [{k: v for k, v in child.items() if k != "updated_at"} for child in saved["subtasks"]] == [{k: v for k, v in child.items() if k != "updated_at"} for child in baseline["subtasks"]]
        assert block_ids() == original_blocks
        with Session(get_engine()) as db:
            assert db.get(Task, child["id"]).project_id == destination
    failed = client.patch(f"/tasks/{parent['id']}", json={"project_id": 999999})
    assert failed.status_code == 404
    assert next(row for row in client.get("/tasks").json()["items"] if row["id"] == parent["id"])["project_id"] is None
    assert block_ids() == original_blocks
