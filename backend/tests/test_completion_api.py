from __future__ import annotations

import datetime as dt


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
    return next(block for block in response.json()["time_blocks"] if block["lane"] == "planned")


def test_time_completion_is_independent_and_reversal_preserves_actual_work(client):
    task_type = _task_type(client)
    task = _task(client, status="in_progress", task_type_id=task_type["id"])
    planned = _planned_block(client, "2099-01-04", task, task_type)

    completed = client.post(
        f"/days/2099-01-04/blocks/{planned['id']}/complete-as-planned"
    )
    assert completed.status_code == 200, completed.text
    actual = next(
        block for block in completed.json()["time_blocks"] if block["lane"] == "actual"
    )
    assert actual["planned_block_id"] == planned["id"]
    assert actual["task"]["status"] == "in_progress"

    task_read = client.get("/tasks").json()["items"][0]
    assert task_read["status"] == "in_progress"
    assert task_read["allocation_total"] == 1
    assert task_read["allocation_completed"] == 1
    assert task_read["allocations"] == [
        {
            "block_id": planned["id"],
            "date": "2099-01-04",
            "start_minute": 540,
            "end_minute": 570,
            "time_completed": True,
        }
    ]

    reversed_response = client.delete(
        f"/days/2099-01-04/blocks/{planned['id']}/completion"
    )
    assert reversed_response.status_code == 200, reversed_response.text
    blocks = reversed_response.json()["time_blocks"]
    assert len(blocks) == 2
    detached_actual = next(block for block in blocks if block["lane"] == "actual")
    assert detached_actual["id"] == actual["id"]
    assert detached_actual["planned_block_id"] is None
    assert client.get("/tasks").json()["items"][0]["allocation_completed"] == 0

    # Deleting Planned intent later must not erase the recorded Actual work.
    deleted = client.delete(f"/days/2099-01-04/blocks/{planned['id']}")
    assert deleted.status_code == 200, deleted.text
    assert [block["id"] for block in deleted.json()["time_blocks"]] == [actual["id"]]


def test_deleting_paired_actual_makes_planned_incomplete_and_overlap_is_atomic(client):
    task_type = _task_type(client)
    task = _task(client, task_type_id=task_type["id"])
    planned = _planned_block(client, "2099-03-01", task, task_type)
    completed = client.post(
        f"/days/2099-03-01/blocks/{planned['id']}/complete-as-planned"
    )
    assert completed.status_code == 200, completed.text
    actual = next(
        block for block in completed.json()["time_blocks"] if block["lane"] == "actual"
    )

    deleted = client.delete(f"/days/2099-03-01/blocks/{actual['id']}")
    assert deleted.status_code == 200, deleted.text
    assert [block["id"] for block in deleted.json()["time_blocks"]] == [planned["id"]]
    assert client.get("/tasks").json()["items"][0]["allocation_completed"] == 0

    overlapping = client.post(
        "/days/2099-03-01/blocks",
        json={
            "lane": "actual",
            "task_type_id": task_type["id"],
            "start_minute": planned["start_minute"],
            "end_minute": planned["end_minute"],
        },
    )
    assert overlapping.status_code == 200, overlapping.text
    before = overlapping.json()["time_blocks"]
    rejected = client.post(
        f"/days/2099-03-01/blocks/{planned['id']}/complete-as-planned"
    )
    assert rejected.status_code == 422
    assert "overlap" in rejected.json()["detail"].lower()
    assert client.get("/days/2099-03-01").json()["time_blocks"] == before


def test_task_completion_is_independent_and_ordinary_reopen_uses_prior_work_status(client):
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
    planned = _planned_block(client, "2099-01-04", task, task_type)

    bypass = client.patch(f"/tasks/{task['id']}", json={"status": "completed"})
    assert bypass.status_code == 422
    assert bypass.json()["detail"] == "Use the Task completion or reopen command to change completion"

    response = client.post(
        f"/tasks/{task['id']}/complete",
        json={"planned_time": "keep"},
    )
    assert response.status_code == 200, response.text
    result = response.json()
    assert result["undo_token"]
    assert result["removed_planned_block_ids"] == []
    assert result["task"]["status"] == "completed"
    assert result["task"]["is_blocked"] is False
    assert result["task"]["blocking_reason"] is None
    assert result["task"]["ready_to_plan"] is False
    assert result["task"]["reminder_at"] is None

    # Task completion never manufactures recorded Actual time.
    day = client.get("/days/2099-01-04").json()
    assert [block["lane"] for block in day["time_blocks"]] == ["planned"]
    assert day["time_blocks"][0]["task"]["status"] == "completed"
    assert result["task"]["allocations"][0]["time_completed"] is False

    reopened = client.post(f"/tasks/{task['id']}/reopen")
    assert reopened.status_code == 200, reopened.text
    assert reopened.json()["status"] == "in_progress"
    assert reopened.json()["is_blocked"] is False
    assert reopened.json()["ready_to_plan"] is False
    assert reopened.json()["reminder_at"] is None

    client.delete(f"/tasks/{task['id']}")
    inactive = client.post(
        f"/tasks/{task['id']}/complete",
        json={"planned_time": "keep"},
    )
    assert inactive.status_code == 422
    assert inactive.json()["detail"] == "Inactive tasks are read-only"


def test_parent_completion_removes_future_incomplete_time_and_undo_restores_exact_state(client):
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
        status="open",
        task_type_id=task_type["id"],
        ready_to_plan=True,
    )
    second = _task(
        client,
        "Already done subtask",
        parent_id=parent["id"],
        status="completed",
        task_type_id=task_type["id"],
    )

    past = _planned_block(client, "2000-01-01", parent, task_type)
    parent_future = _planned_block(client, "2099-02-01", parent, task_type)
    child_future = _planned_block(client, "2099-02-02", first, task_type)
    completed_future = _planned_block(client, "2099-02-03", first, task_type)
    completed = client.post(
        f"/days/2099-02-03/blocks/{completed_future['id']}/complete-as-planned"
    )
    assert completed.status_code == 200, completed.text
    assert client.patch(
        f"/tasks/{parent['id']}", json={"ready_to_plan": True}
    ).status_code == 200
    assert client.patch(
        f"/tasks/{first['id']}", json={"ready_to_plan": True}
    ).status_code == 200

    response = client.post(
        f"/tasks/{parent['id']}/complete",
        json={"planned_time": "remove"},
    )
    assert response.status_code == 200, response.text
    result = response.json()
    assert set(result["removed_planned_block_ids"]) == {
        parent_future["id"],
        child_future["id"],
    }
    assert result["task"]["status"] == "completed"
    assert [child["status"] for child in result["task"]["subtasks"]] == [
        "completed",
        "completed",
    ]
    assert client.get("/days/2000-01-01").json()["time_blocks"][0]["id"] == past["id"]
    assert client.get("/days/2099-02-01").json()["time_blocks"] == []
    assert client.get("/days/2099-02-02").json()["time_blocks"] == []
    assert len(client.get("/days/2099-02-03").json()["time_blocks"]) == 2

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
    assert [child["status"] for child in restored["subtasks"]] == ["open", "completed"]
    assert restored["subtasks"][0]["ready_to_plan"] is True
    assert client.get("/days/2099-02-01").json()["time_blocks"][0]["id"] == parent_future["id"]
    assert client.get("/days/2099-02-02").json()["time_blocks"][0]["id"] == child_future["id"]

    repeated = client.post(
        f"/tasks/{parent['id']}/undo-completion",
        json={"undo_token": result["undo_token"]},
    )
    assert repeated.status_code == 422
    assert repeated.json()["detail"] == "Completion has already been undone"


def test_quota_tracker_completion_stays_derived_across_complete_reopen_and_undo(client):
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
    session = tracker["subtasks"][0]

    rejected = client.post(
        f"/tasks/{tracker['id']}/complete",
        json={"planned_time": "keep"},
    )
    assert rejected.status_code == 422
    assert rejected.json()["detail"] == "Quota Tracker completion is derived from Session Tasks"

    completed = client.post(
        f"/tasks/{session['id']}/complete",
        json={"planned_time": "keep"},
    )
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

    completed_again = client.post(
        f"/tasks/{session['id']}/complete",
        json={"planned_time": "keep"},
    ).json()
    undone = client.post(
        f"/tasks/{session['id']}/undo-completion",
        json={"undo_token": completed_again["undo_token"]},
    )
    assert undone.status_code == 200, undone.text
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "open"
    assert refreshed["quota_completed"] == 0
