from __future__ import annotations

import datetime as dt

import pytest
from sqlalchemy import select, text as sql_text
from sqlalchemy.orm import Session
from fastapi.testclient import TestClient

from app.api.routes import actual_blocks, battle_plan
from app.core.config import Settings, get_settings
from app.db.session import get_engine
from app.main import app
from app.models.battle_plan import Task
from app.models.time_block import BlockLane, TimeBlock


UTC = dt.timezone.utc


@pytest.fixture
def captured_instants():
    values: list[dt.datetime] = []

    def capture() -> dt.datetime:
        return values.pop(0)

    app.dependency_overrides[battle_plan.capture_utc_now] = capture
    try:
        yield values
    finally:
        app.dependency_overrides.pop(battle_plan.capture_utc_now, None)


@pytest.fixture
def actual_instants():
    values: list[dt.datetime] = []

    def capture() -> dt.datetime:
        return values.pop(0)

    app.dependency_overrides[actual_blocks.capture_utc_now] = capture
    try:
        yield values
    finally:
        app.dependency_overrides.pop(actual_blocks.capture_utc_now, None)


def _task_type(client):
    response = client.post("/task-types", json={"name": "Completion work"})
    assert response.status_code == 200, response.text
    return response.json()


def _task(client, title="Finish the brief", **extra):
    response = client.post("/tasks", json={"title": title, **extra})
    assert response.status_code == 201, response.text
    return response.json()


def _planned_block(client, date, task, task_type, start=540):
    response = client.post(
        f"/days/{date}/blocks",
        json={
            "lane": "planned",
            "task_id": task["id"],
            "task_type_id": task_type["id"],
            "start_minute": start,
            "end_minute": start + 30,
        },
    )
    assert response.status_code == 200, response.text
    return next(
        block
        for block in response.json()["time_blocks"]
        if block["lane"] == "planned" and block["start_minute"] == start
    )


def test_subtask_check_and_uncheck_are_explicit_and_never_complete_parent(client):
    parent = _task(client, "Parent")
    first = _task(client, "First", parent_id=parent["id"])
    second = _task(client, "Second", parent_id=parent["id"])

    first_checked = client.post(f"/subtasks/{first['id']}/check")
    second_checked = client.post(f"/subtasks/{second['id']}/check")

    assert first_checked.status_code == 200, first_checked.text
    assert second_checked.status_code == 200, second_checked.text
    assert first_checked.json()["checked"] is True
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "open"
    assert [subtask["checked"] for subtask in refreshed["subtasks"]] == [True, True]

    unchecked = client.post(f"/subtasks/{first['id']}/uncheck")
    assert unchecked.status_code == 200, unchecked.text
    assert unchecked.json()["checked"] is False

    rejected = client.post(f"/tasks/{second['id']}/complete")
    assert rejected.status_code == 422
    assert rejected.json()["detail"] == "Subtasks use check and uncheck actions"


def test_actual_correspondence_is_independent_and_detach_preserves_actual_work(client):
    task_type = _task_type(client)
    task = _task(client, status="in_progress", task_type_id=task_type["id"])
    planned = _planned_block(client, "2099-01-04", task, task_type)

    recorded = client.post(
        f"/planned-blocks/{planned['id']}/record-actual-as-planned"
    )
    assert recorded.status_code == 201, recorded.text
    actual = recorded.json()["actual_block"]
    assert actual["planned_block_id"] == planned["id"]
    assert actual["task"]["status"] == "in_progress"

    task_read = client.get("/tasks").json()["items"][0]
    assert task_read["status"] == "in_progress"
    assert "allocation_total" not in task_read
    assert "allocation_completed" not in task_read
    assert "allocations" not in task_read

    detached_response = client.post(f"/actual-blocks/{actual['id']}/detach")
    assert detached_response.status_code == 200, detached_response.text
    detached_actual = detached_response.json()
    assert detached_actual["id"] == actual["id"]
    assert detached_actual["planned_block_id"] is None
    assert "allocation_completed" not in client.get("/tasks").json()["items"][0]

    # Deleting Planned intent later must not erase the recorded Actual work.
    deleted = client.delete(f"/days/2099-01-04/blocks/{planned['id']}")
    assert deleted.status_code == 200, deleted.text
    assert deleted.json()["time_blocks"] == []
    assert deleted.json()["actual_blocks"][0]["actual_block"]["id"] == actual["id"]


def test_deleting_paired_actual_leaves_planned_and_record_overlap_is_atomic(client):
    task_type = _task_type(client)
    task = _task(client, task_type_id=task_type["id"])
    planned = _planned_block(client, "2099-03-01", task, task_type)
    recorded = client.post(
        f"/planned-blocks/{planned['id']}/record-actual-as-planned"
    )
    assert recorded.status_code == 201, recorded.text
    actual = recorded.json()["actual_block"]

    deleted = client.delete(f"/actual-blocks/{actual['id']}")
    assert deleted.status_code == 204, deleted.text
    assert [block["id"] for block in client.get("/days/2099-03-01").json()["time_blocks"]] == [planned["id"]]
    assert "allocation_completed" not in client.get("/tasks").json()["items"][0]

    overlapping = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "start_at": "2099-03-01T09:00:00Z",
            "end_at": "2099-03-01T09:30:00Z",
        },
    )
    assert overlapping.status_code == 201, overlapping.text
    before = client.get("/days/2099-03-01").json()
    rejected = client.post(
        f"/planned-blocks/{planned['id']}/record-actual-as-planned"
    )
    assert rejected.status_code == 422
    assert "overlap" in rejected.json()["detail"].lower()
    assert client.get("/days/2099-03-01").json()["actual_blocks"] == before["actual_blocks"]


def test_completion_captures_one_instant_cleans_lifecycle_and_reopen_is_forward_only(
    client, captured_instants
):
    task_type = _task_type(client)
    task = _task(
        client,
        status="in_progress",
        task_type_id=task_type["id"],
        ready_to_plan=True,
        is_blocked=True,
        blocking_reason="Waiting for review",
        deadline_at="2099-01-05T12:00:00Z",
        reminder_at="2099-01-05T11:00:00Z",
    )
    planned = _planned_block(client, "2026-08-31", task, task_type)
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 34, 56, 789, tzinfo=UTC))

    bypass = client.patch(f"/tasks/{task['id']}", json={"status": "completed"})
    assert bypass.status_code == 422
    assert bypass.json()["detail"] == "Use the Task completion or reopen command to change completion"

    response = client.post(f"/tasks/{task['id']}/complete")
    assert response.status_code == 200, response.text
    result = response.json()
    assert result["undo_token"]
    assert result["removed_planned_block_ids"] == [planned["id"]]
    assert result["task"]["status"] == "completed"
    assert result["task"]["completed_at"] == "2026-08-30T12:34:56.000789Z"
    assert result["task"]["is_blocked"] is False
    assert result["task"]["blocking_reason"] is None
    assert result["task"]["ready_to_plan"] is False
    assert result["task"]["reminder_at"] is None

    # Task completion never manufactures recorded Actual time, and cleanup is automatic.
    day = client.get("/days/2026-08-31").json()
    assert day["time_blocks"] == []
    assert day["actual_blocks"] == []
    assert "allocations" not in result["task"]

    reopened = client.post(f"/tasks/{task['id']}/reopen")
    assert reopened.status_code == 200, reopened.text
    assert reopened.json()["status"] == "open"
    assert reopened.json()["is_blocked"] is False
    assert reopened.json()["ready_to_plan"] is False
    assert reopened.json()["reminder_at"] is None
    assert reopened.json()["completed_at"] is None
    assert client.get("/days/2026-08-31").json()["time_blocks"] == []

    client.delete(f"/tasks/{task['id']}")
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 35, tzinfo=UTC))
    inactive = client.post(f"/tasks/{task['id']}/complete")
    assert inactive.status_code == 422
    assert inactive.json()["detail"] == "Inactive tasks are read-only"


def test_completion_removes_only_selected_tasks_strictly_future_plans_and_undo_is_exact(
    client, captured_instants
):
    task_type = _task_type(client)
    parent = _task(
        client,
        "Parent task",
        status="in_progress",
        task_type_id=task_type["id"],
        ready_to_plan=True,
        is_blocked=True,
        blocking_reason="External dependency",
    )
    first = _task(
        client,
        "First subtask",
        parent_id=parent["id"],
    )
    second = _task(
        client,
        "Checked subtask",
        parent_id=parent["id"],
    )
    assert client.post(f"/subtasks/{second['id']}/check").status_code == 200
    other = _task(client, "Other task", task_type_id=task_type["id"])

    past = _planned_block(client, "2026-08-29", parent, task_type)
    exact = _planned_block(client, "2026-08-30", parent, task_type, start=600)
    parent_future = _planned_block(client, "2026-08-31", parent, task_type)
    future_with_actual = _planned_block(client, "2026-09-01", parent, task_type)
    other_future = _planned_block(client, "2026-09-02", other, task_type)
    recorded = client.post(
        f"/planned-blocks/{future_with_actual['id']}/record-actual-as-planned"
    )
    assert recorded.status_code == 201, recorded.text
    ended_actual = recorded.json()["actual_block"]
    assert client.patch(
        f"/tasks/{parent['id']}", json={"ready_to_plan": True}
    ).status_code == 200
    captured_instants.append(dt.datetime(2026, 8, 30, 10, 0, tzinfo=UTC))

    response = client.post(f"/tasks/{parent['id']}/complete")
    assert response.status_code == 200, response.text
    result = response.json()
    assert set(result["removed_planned_block_ids"]) == {
        parent_future["id"],
        future_with_actual["id"],
    }
    assert result["task"]["status"] == "completed"
    assert [child["checked"] for child in result["task"]["subtasks"]] == [False, True]
    assert all(child["effectively_resolved"] for child in result["task"]["subtasks"])
    assert client.get("/days/2026-08-29").json()["time_blocks"][0]["id"] == past["id"]
    assert client.get("/days/2026-08-30").json()["time_blocks"][0]["id"] == exact["id"]
    assert client.get("/days/2026-08-31").json()["time_blocks"] == []
    assert client.get("/days/2026-09-02").json()["time_blocks"][0]["id"] == other_future["id"]
    assert client.get(f"/actual-blocks/{ended_actual['id']}").json()["planned_block_id"] is None

    undone = client.post(
        f"/tasks/{parent['id']}/undo-completion",
        json={"undo_token": result["undo_token"]},
    )
    assert undone.status_code == 200, undone.text
    restored = undone.json()
    assert restored["status"] == "in_progress"
    assert restored["ready_to_plan"] is True
    assert restored["is_blocked"] is True
    assert restored["blocking_reason"] == "External dependency"
    assert [child["checked"] for child in restored["subtasks"]] == [False, True]
    assert [child["effectively_resolved"] for child in restored["subtasks"]] == [False, True]
    assert client.get("/days/2026-08-31").json()["time_blocks"][0]["id"] == parent_future["id"]
    assert client.get("/days/2026-09-01").json()["time_blocks"][0]["id"] == future_with_actual["id"]
    assert client.get(f"/actual-blocks/{ended_actual['id']}").json()["planned_block_id"] == future_with_actual["id"]

    repeated = client.post(
        f"/tasks/{parent['id']}/undo-completion",
        json={"undo_token": result["undo_token"]},
    )
    assert repeated.status_code == 422
    assert repeated.json()["detail"] == "Completion has already been undone"


def test_cleanup_compares_planned_local_time_as_an_instant(
    client, captured_instants
):
    singapore = Settings(
        database_url="sqlite:///:memory:",
        app_timezone="Asia/Singapore",
        cors_origins="*",
    )
    app.dependency_overrides[get_settings] = lambda: singapore
    try:
        task_type = _task_type(client)
        task = _task(client, task_type_id=task_type["id"])
        exact = _planned_block(client, "2026-08-30", task, task_type, start=1200)
        later = _planned_block(client, "2026-08-30", task, task_type, start=1230)
        captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))

        completed = client.post(f"/tasks/{task['id']}/complete")

        assert completed.status_code == 200, completed.text
        assert completed.json()["removed_planned_block_ids"] == [later["id"]]
        assert [
            plan["id"]
            for plan in client.get("/days/2026-08-30").json()["planned_blocks"]
        ] == [exact["id"]]
    finally:
        app.dependency_overrides.pop(get_settings, None)


def test_finish_and_complete_uses_one_instant_preserves_active_plan_and_undo_keeps_actual_ended(
    client, captured_instants, actual_instants
):
    task_type = _task_type(client)
    task = _task(client, task_type_id=task_type["id"])
    active_plan = _planned_block(client, "2026-08-30", task, task_type, start=720)
    removable = _planned_block(client, "2026-08-31", task, task_type)
    actual_instants.append(dt.datetime(2026, 8, 30, 9, 0, tzinfo=UTC))
    started = client.post(
        "/actual-blocks/start", json={"planned_block_id": active_plan["id"]}
    )
    assert started.status_code == 201, started.text

    captured_instants.append(dt.datetime(2026, 8, 30, 10, 0, tzinfo=UTC))
    completed = client.post(f"/tasks/{task['id']}/complete")

    assert completed.status_code == 200, completed.text
    result = completed.json()
    assert result["removed_planned_block_ids"] == [removable["id"]]
    actual = client.get(f"/actual-blocks/{started.json()['id']}").json()
    assert actual["end_at"] == "2026-08-30T10:00:00Z"
    assert actual["planned_block_id"] == active_plan["id"]
    assert client.get("/days/2026-08-30").json()["planned_blocks"][0]["id"] == active_plan["id"]

    undone = client.post(
        f"/tasks/{task['id']}/undo-completion",
        json={"undo_token": result["undo_token"]},
    )
    assert undone.status_code == 200, undone.text
    assert undone.json()["status"] == "open"
    preserved = client.get(f"/actual-blocks/{started.json()['id']}").json()
    assert preserved["end_at"] == "2026-08-30T10:00:00Z"
    assert client.get("/actual-blocks/active").json() is None


def test_completion_leaves_another_tasks_active_actual_untouched(
    client, captured_instants, actual_instants
):
    task_type = _task_type(client)
    selected = _task(client, "Selected", task_type_id=task_type["id"])
    other = _task(client, "Other", task_type_id=task_type["id"])
    actual_instants.append(dt.datetime(2026, 8, 30, 9, 0, tzinfo=UTC))
    started = client.post(
        "/actual-blocks/start",
        json={"task_type_id": task_type["id"], "task_id": other["id"]},
    ).json()
    captured_instants.append(dt.datetime(2026, 8, 30, 10, 0, tzinfo=UTC))

    completed = client.post(f"/tasks/{selected['id']}/complete")

    assert completed.status_code == 200, completed.text
    assert client.get("/actual-blocks/active").json()["id"] == started["id"]
    assert client.get(f"/actual-blocks/{started['id']}").json()["end_at"] is None


def test_finish_and_complete_rolls_back_actual_task_and_plan_on_failure(
    client, captured_instants, actual_instants
):
    task_type = _task_type(client)
    task = _task(
        client,
        task_type_id=task_type["id"],
        ready_to_plan=True,
        is_blocked=True,
        blocking_reason="Keep all state",
    )
    future = _planned_block(client, "2026-08-31", task, task_type)
    assert client.patch(
        f"/tasks/{task['id']}", json={"ready_to_plan": True}
    ).status_code == 200
    actual_instants.append(dt.datetime(2026, 8, 30, 10, 0, tzinfo=UTC))
    active = client.post(
        "/actual-blocks/start",
        json={"task_type_id": task_type["id"], "task_id": task["id"]},
    ).json()
    captured_instants.append(dt.datetime(2026, 8, 30, 9, 59, tzinfo=UTC))

    rejected = client.post(f"/tasks/{task['id']}/complete")

    assert rejected.status_code == 422, rejected.text
    assert rejected.json()["detail"] == "Actual Block end must be after its start"
    unchanged = client.get("/tasks").json()["items"][0]
    assert unchanged["status"] == "open"
    assert unchanged["ready_to_plan"] is True
    assert unchanged["is_blocked"] is True
    assert unchanged["blocking_reason"] == "Keep all state"
    assert client.get("/days/2026-08-31").json()["planned_blocks"][0]["id"] == future["id"]
    assert client.get("/actual-blocks/active").json()["id"] == active["id"]


def test_database_failure_rolls_back_finished_actual_task_cleanup_and_undo_record(
    client, captured_instants, actual_instants
):
    task_type = _task_type(client)
    task = _task(client, task_type_id=task_type["id"], ready_to_plan=True)
    future = _planned_block(client, "2026-08-31", task, task_type)
    assert client.patch(
        f"/tasks/{task['id']}", json={"ready_to_plan": True}
    ).status_code == 200
    actual_instants.append(dt.datetime(2026, 8, 30, 9, 0, tzinfo=UTC))
    active = client.post(
        "/actual-blocks/start",
        json={"task_type_id": task_type["id"], "task_id": task["id"]},
    ).json()
    with get_engine().begin() as connection:
        connection.execute(
            sql_text(
                """
                CREATE TRIGGER reject_completion_operation
                BEFORE INSERT ON task_completion_operations
                BEGIN
                  SELECT RAISE(ABORT, 'simulated completion persistence failure');
                END
                """
            )
        )
    captured_instants.append(dt.datetime(2026, 8, 30, 10, 0, tzinfo=UTC))
    failing_client = TestClient(app, raise_server_exceptions=False)

    failed = failing_client.post(f"/tasks/{task['id']}/complete")

    assert failed.status_code == 500
    assert client.get("/tasks").json()["items"][0]["status"] == "open"
    assert client.get("/tasks").json()["items"][0]["ready_to_plan"] is True
    assert client.get("/days/2026-08-31").json()["planned_blocks"][0]["id"] == future["id"]
    assert client.get("/actual-blocks/active").json()["id"] == active["id"]
    with Session(get_engine()) as db:
        assert db.query(Task).filter(Task.id == task["id"]).one().completed_at is None
        assert db.query(TimeBlock).filter(
            TimeBlock.lane == BlockLane.actual,
            TimeBlock.id == active["id"],
            TimeBlock.end_at.is_(None),
        ).count() == 1


def test_subtasks_reject_task_lifecycle_planning_reminders_and_actual(client):
    task_type = _task_type(client)
    parent = _task(client, "Parent", task_type_id=task_type["id"])
    subtask = _task(client, "Checkpoint", parent_id=parent["id"])

    invalid_create = client.post(
        "/tasks",
        json={
            "title": "Lifecycle checkpoint",
            "parent_id": parent["id"],
            "ready_to_plan": True,
        },
    )
    assert invalid_create.status_code == 422

    for payload in (
        {"ready_to_plan": True},
        {"is_blocked": True},
        {"status": "in_progress"},
        {"task_type_id": task_type["id"]},
        {"deadline_at": "2026-09-01T12:00:00Z"},
        {"reminder_at": "2026-09-01T11:00:00Z"},
    ):
        rejected = client.patch(f"/tasks/{subtask['id']}", json=payload)
        assert rejected.status_code == 422, (payload, rejected.text)

    assert client.post(f"/tasks/{subtask['id']}/complete").status_code == 422
    assert client.post(f"/tasks/{subtask['id']}/reopen").status_code == 422
    planned = client.post(
        "/days/2026-08-30/blocks",
        json={
            "lane": "planned",
            "task_type_id": task_type["id"],
            "task_id": subtask["id"],
            "start_minute": 540,
            "end_minute": 570,
        },
    )
    assert planned.status_code == 422
    retrospective = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "task_id": subtask["id"],
            "start_at": "2026-08-30T09:00:00Z",
            "end_at": "2026-08-30T09:30:00Z",
        },
    )
    assert retrospective.status_code == 422


def test_completed_task_and_subtasks_freeze_but_historical_correction_and_earlier_actual_remain(
    client, captured_instants, actual_instants
):
    task_type = _task_type(client)
    task = _task(
        client,
        task_type_id=task_type["id"],
        deadline_at="2026-09-01T12:00:00Z",
        reminder_at="2026-09-01T11:00:00Z",
    )
    subtask = _task(client, "Checkpoint", parent_id=task["id"])
    trashed_subtask = _task(client, "Trashed checkpoint", parent_id=task["id"])
    assert client.delete(f"/tasks/{trashed_subtask['id']}").status_code == 200
    historical_plan = _planned_block(client, "2026-08-29", task, task_type)
    historical_actual = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "task_id": task["id"],
            "start_at": "2026-08-29T09:00:00Z",
            "end_at": "2026-08-29T09:30:00Z",
        },
    ).json()
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))
    assert client.post(f"/tasks/{task['id']}/complete").status_code == 200

    assert client.patch(
        f"/tasks/{task['id']}", json={"title": "No edit"}
    ).status_code == 422
    assert client.patch(
        f"/tasks/{task['id']}", json={"ready_to_plan": True}
    ).status_code == 422
    assert client.post(
        "/tasks", json={"title": "No addition", "parent_id": task["id"]}
    ).status_code == 422
    assert client.patch(
        f"/tasks/{subtask['id']}", json={"title": "No child edit"}
    ).status_code == 422
    assert client.post(f"/subtasks/{subtask['id']}/check").status_code == 422
    assert client.delete(f"/tasks/{subtask['id']}").status_code == 422
    assert client.post(f"/tasks/{trashed_subtask['id']}/restore").status_code == 422
    assert client.delete(
        f"/tasks/{trashed_subtask['id']}/permanent"
    ).status_code == 422
    assert client.post(
        "/tasks/reorder",
        json={
            "placements": [
                {"task_id": task["id"], "status": "completed", "position": 5}
            ]
        },
    ).status_code == 422

    new_plan = client.post(
        "/days/2026-08-28/blocks",
        json={
            "lane": "planned",
            "task_type_id": task_type["id"],
            "task_id": task["id"],
            "start_minute": 540,
            "end_minute": 570,
        },
    )
    assert new_plan.status_code == 422
    corrected_plan = client.patch(
        f"/days/2026-08-29/blocks/{historical_plan['id']}",
        json={"note": "Historical correction"},
    )
    assert corrected_plan.status_code == 200, corrected_plan.text
    corrected_actual = client.patch(
        f"/actual-blocks/{historical_actual['id']}",
        json={"note": "Historical correction"},
    )
    assert corrected_actual.status_code == 200, corrected_actual.text

    earlier = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "task_id": task["id"],
            "start_at": "2026-08-30T11:30:00Z",
            "end_at": "2026-08-30T12:00:00Z",
        },
    )
    assert earlier.status_code == 201, earlier.text
    later = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "task_id": task["id"],
            "start_at": "2026-08-30T12:00:00Z",
            "end_at": "2026-08-30T12:30:00Z",
        },
    )
    assert later.status_code == 422
    actual_instants.append(dt.datetime(2026, 8, 30, 13, 0, tzinfo=UTC))
    live = client.post(
        "/actual-blocks/start",
        json={"task_type_id": task_type["id"], "task_id": task["id"]},
    )
    assert live.status_code == 422


def test_completed_actual_reassignment_and_relink_obey_completion_boundary(
    client, captured_instants, actual_instants
):
    task_type = _task_type(client)
    target = _task(client, "Completed target", task_type_id=task_type["id"])
    source = _task(client, "Open source", task_type_id=task_type["id"])
    earlier_plan = _planned_block(
        client, "2026-08-30", target, task_type, start=600
    )
    target_plan = _planned_block(
        client, "2026-08-30", target, task_type, start=660
    )
    existing = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "task_id": target["id"],
            "start_at": "2026-08-30T09:00:00Z",
            "end_at": "2026-08-30T10:00:00Z",
        },
    ).json()
    earlier_source = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "task_id": source["id"],
            "start_at": "2026-08-30T10:00:00Z",
            "end_at": "2026-08-30T11:00:00Z",
        },
    ).json()
    later_target = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "task_id": target["id"],
            "start_at": "2026-08-30T13:00:00Z",
            "end_at": "2026-08-30T14:00:00Z",
        },
    ).json()
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))
    assert client.post(f"/tasks/{target['id']}/complete").status_code == 200

    corrected = client.patch(
        f"/actual-blocks/{existing['id']}",
        json={
            "start_at": "2026-08-30T09:01:00Z",
            "end_at": "2026-08-30T09:59:00Z",
        },
    )
    assert corrected.status_code == 200, corrected.text
    reactivated = client.patch(
        f"/actual-blocks/{existing['id']}", json={"end_at": None}
    )
    assert reactivated.status_code == 422
    assert reactivated.json()["detail"] == "Completed Task Actual Blocks cannot be reactivated"

    reassigned_earlier = client.patch(
        f"/actual-blocks/{earlier_source['id']}", json={"task_id": target["id"]}
    )
    assert reassigned_earlier.status_code == 200, reassigned_earlier.text
    assert reassigned_earlier.json()["task_id"] == target["id"]
    earlier_relink = client.post(
        f"/actual-blocks/{earlier_source['id']}/relink",
        json={"planned_block_id": earlier_plan["id"]},
    )
    assert earlier_relink.status_code == 200, earlier_relink.text

    later_relink = client.post(
        f"/actual-blocks/{later_target['id']}/relink",
        json={"planned_block_id": target_plan["id"]},
    )
    assert later_relink.status_code == 422
    assert later_relink.json()["detail"] == (
        "Actual work after Task Completion requires ordinary reopen"
    )
    assert client.get(f"/actual-blocks/{later_target['id']}").json()["planned_block_id"] is None

    actual_instants.append(dt.datetime(2026, 8, 30, 15, 0, tzinfo=UTC))
    active = client.post(
        "/actual-blocks/start",
        json={"task_type_id": task_type["id"], "task_id": source["id"]},
    ).json()
    active_reassignment = client.patch(
        f"/actual-blocks/{active['id']}", json={"task_id": target["id"]}
    )
    assert active_reassignment.status_code == 422
    assert active_reassignment.json()["detail"] == (
        "Actual work after Task Completion requires ordinary reopen"
    )
    assert client.get("/actual-blocks/active").json()["task_id"] == source["id"]


def test_completion_undo_rejects_ordinary_reopen_as_newer_intent_without_restoring_plans(
    client, captured_instants
):
    task_type = _task_type(client)
    task = _task(client, task_type_id=task_type["id"])
    future = _planned_block(client, "2026-08-31", task, task_type)
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))
    completion = client.post(f"/tasks/{task['id']}/complete").json()
    assert client.post(f"/tasks/{task['id']}/reopen").status_code == 200

    rejected = client.post(
        f"/tasks/{task['id']}/undo-completion",
        json={"undo_token": completion["undo_token"]},
    )

    assert rejected.status_code == 422, rejected.text
    assert rejected.json()["detail"] == "Completion changed; Undo is no longer available"
    assert client.get("/tasks").json()["items"][0]["status"] == "open"
    assert client.get("/days/2026-08-31").json()["time_blocks"] == []
    assert future["id"] not in completion["task"]["planned_dates"]


def test_completion_undo_rejects_plan_overlap_without_partial_restore(
    client, captured_instants
):
    task_type = _task_type(client)
    task = _task(client, task_type_id=task_type["id"])
    exact = _planned_block(client, "2026-08-30", task, task_type, start=540)
    future = _planned_block(client, "2026-08-30", task, task_type, start=600)
    captured_instants.append(dt.datetime(2026, 8, 30, 9, 0, tzinfo=UTC))
    completion = client.post(f"/tasks/{task['id']}/complete").json()
    assert completion["removed_planned_block_ids"] == [future["id"]]
    changed = client.patch(
        f"/days/2026-08-30/blocks/{exact['id']}", json={"end_minute": 630}
    )
    assert changed.status_code == 200, changed.text

    rejected = client.post(
        f"/tasks/{task['id']}/undo-completion",
        json={"undo_token": completion["undo_token"]},
    )

    assert rejected.status_code == 422, rejected.text
    assert rejected.json()["detail"] == "Planned time changed; Undo is no longer available"
    assert client.get("/tasks").json()["items"][0]["status"] == "completed"
    assert [
        plan["id"] for plan in client.get("/days/2026-08-30").json()["planned_blocks"]
    ] == [exact["id"]]


def test_completion_undo_relinks_edited_ended_actual_when_primary_item_is_unchanged(
    client, captured_instants
):
    task_type = _task_type(client)
    task = _task(client, task_type_id=task_type["id"])
    future = _planned_block(client, "2026-09-01", task, task_type)
    actual = client.post(
        f"/planned-blocks/{future['id']}/record-actual-as-planned"
    ).json()["actual_block"]
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))
    completion = client.post(f"/tasks/{task['id']}/complete").json()
    changed = client.patch(
        f"/actual-blocks/{actual['id']}", json={"note": "Corrected note"}
    )
    assert changed.status_code == 200, changed.text

    undone = client.post(
        f"/tasks/{task['id']}/undo-completion",
        json={"undo_token": completion["undo_token"]},
    )

    assert undone.status_code == 200, undone.text
    restored_actual = client.get(f"/actual-blocks/{actual['id']}").json()
    assert restored_actual["note"] == "Corrected note"
    assert restored_actual["planned_block_id"] == future["id"]


def test_completion_undo_rejects_actual_reassociation_without_partial_restore(
    client, captured_instants
):
    first_type = _task_type(client)
    second_type = client.post("/task-types", json={"name": "Other work"}).json()
    task = _task(client, task_type_id=first_type["id"])
    other = _task(client, "Other", task_type_id=second_type["id"])
    future = _planned_block(client, "2026-09-01", task, first_type)
    actual = client.post(
        f"/planned-blocks/{future['id']}/record-actual-as-planned"
    ).json()["actual_block"]
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))
    completion = client.post(f"/tasks/{task['id']}/complete").json()
    changed = client.patch(
        f"/actual-blocks/{actual['id']}",
        json={"task_type_id": second_type["id"], "task_id": other["id"]},
    )
    assert changed.status_code == 200, changed.text

    rejected = client.post(
        f"/tasks/{task['id']}/undo-completion",
        json={"undo_token": completion["undo_token"]},
    )

    assert rejected.status_code == 422, rejected.text
    assert rejected.json()["detail"] == "Actual correspondence changed; Undo is no longer available"
    assert client.get("/tasks").json()["items"][0]["status"] == "completed"
    assert client.get("/days/2026-09-01").json()["planned_blocks"] == []
    assert client.get(f"/actual-blocks/{actual['id']}").json()["task_id"] == other["id"]


def test_completion_is_scoped_to_one_recurring_task_occurrence(
    client, captured_instants
):
    task_type = _task_type(client)
    today = client.get("/health").json()["today"]
    created = client.post(
        "/recurring-templates",
        json={
            "title": "Daily review",
            "mode": "scheduled",
            "frequency": "daily",
            "interval": 1,
            "start_date": today,
            "task_type_id": task_type["id"],
            "checklist_titles": [],
        },
    )
    assert created.status_code == 201, created.text
    tomorrow = (dt.date.fromisoformat(today) + dt.timedelta(days=1)).isoformat()
    first_occurrence = client.get(
        "/tasks", params={"planning_date": today}
    ).json()["items"][0]
    second_occurrence = client.get(
        "/tasks", params={"planning_date": tomorrow}
    ).json()["items"][0]
    first_plan = _planned_block(client, today, first_occurrence, task_type)
    second_plan = _planned_block(client, tomorrow, second_occurrence, task_type)
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))

    completed = client.post(f"/tasks/{first_occurrence['id']}/complete")

    assert completed.status_code == 200, completed.text
    assert completed.json()["removed_planned_block_ids"] == [first_plan["id"]]
    assert completed.json()["task"]["status"] == "completed"
    refreshed = client.get(
        "/tasks", params={"planning_date": tomorrow}
    ).json()["items"]
    assert refreshed[0]["id"] == second_occurrence["id"]
    assert refreshed[0]["status"] == "open"
    assert client.get(f"/days/{today}").json()["planned_blocks"] == []
    assert client.get(f"/days/{tomorrow}").json()["planned_blocks"][0]["id"] == second_plan["id"]


def test_completion_eligibility_rejects_direct_completed_create_replay_and_inactive_tasks(
    client, captured_instants
):
    direct = client.post("/tasks", json={"title": "Bypass", "status": "completed"})
    assert direct.status_code == 422
    assert direct.json()["detail"] == "Use the Task completion command to complete a Task"

    task = _task(client)
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))
    assert client.post(f"/tasks/{task['id']}/complete").status_code == 200
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 1, tzinfo=UTC))
    replay = client.post(f"/tasks/{task['id']}/complete")
    assert replay.status_code == 422
    assert replay.json()["detail"] == "Task is already completed"

    assert client.post(
        "/tasks/archive-completed", json={"task_ids": [task["id"]]}
    ).status_code == 204
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 2, tzinfo=UTC))
    assert client.post(f"/tasks/{task['id']}/complete").status_code == 422
    assert client.post(f"/tasks/{task['id']}/reopen").status_code == 422

    trashed = _task(client, "Trashed")
    assert client.delete(f"/tasks/{trashed['id']}").status_code == 200
    captured_instants.append(dt.datetime(2026, 8, 30, 12, 3, tzinfo=UTC))
    assert client.post(f"/tasks/{trashed['id']}/complete").status_code == 422
    assert client.post(f"/tasks/{trashed['id']}/reopen").status_code == 422


def test_completion_openapi_exposes_commands_without_legacy_cleanup_choice(client):
    schema = client.get("/openapi.json").json()
    completion = schema["paths"]["/tasks/{task_id}/complete"]["post"]
    assert "requestBody" not in completion
    assert "TaskCompletion" + "Create" not in schema["components"]["schemas"]
    assert "planned" + "_time" not in str(completion)
    assert "/subtasks/{subtask_id}/check" in schema["paths"]
    assert "/subtasks/{subtask_id}/uncheck" in schema["paths"]


def test_quota_tracker_completion_stays_derived_across_complete_reopen_and_undo(
    client, captured_instants
):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    created = client.post(
        "/recurring-templates",
        json={
            "title": "Practice",
            "mode": "quota",
            "frequency": "weekly",
            "interval": 1,
            "quota_count": 2,
            "start_date": today.isoformat(),
        },
    )
    assert created.status_code == 201, created.text
    tracker = client.get("/tasks").json()["items"][0]
    session = tracker["session_tasks"][0]

    captured_instants.append(dt.datetime(2026, 8, 30, 12, 0, tzinfo=UTC))
    rejected = client.post(f"/tasks/{tracker['id']}/complete")
    assert rejected.status_code == 422
    assert rejected.json()["detail"] == "Quota Tracker completion is derived from Session Tasks"

    captured_instants.append(dt.datetime(2026, 8, 30, 12, 1, tzinfo=UTC))
    completed = client.post(f"/tasks/{session['id']}/complete")
    assert completed.status_code == 200, completed.text
    result = completed.json()
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "in_progress"
    assert refreshed["quota_completed"] == 1

    reopened = client.post(f"/tasks/{session['id']}/reopen")
    assert reopened.status_code == 200, reopened.text
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "open"
    assert refreshed["quota_completed"] == 0

    captured_instants.append(dt.datetime(2026, 8, 30, 12, 2, tzinfo=UTC))
    completed_again = client.post(f"/tasks/{session['id']}/complete").json()
    undone = client.post(
        f"/tasks/{session['id']}/undo-completion",
        json={"undo_token": completed_again["undo_token"]},
    )
    assert undone.status_code == 200, undone.text
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "open"
    assert refreshed["quota_completed"] == 0
