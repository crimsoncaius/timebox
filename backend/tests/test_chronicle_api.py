from __future__ import annotations

import datetime as dt


def _today(client) -> dt.date:
    return dt.date.fromisoformat(client.get("/health").json()["today"])


def _month(client, date: dt.date) -> list[dict]:
    response = client.get(f"/days/chronicle?month={date:%Y-%m}")
    assert response.status_code == 200, response.text
    return response.json()["days"]


def test_opening_day_does_not_save_it_and_only_live_plans_appear(client):
    date = _today(client) - dt.timedelta(days=1)
    opened = client.get(f"/days/{date}")
    assert opened.status_code == 200
    assert opened.json()["id"] is None
    assert opened.json()["time_blocks"] == []
    assert client.get("/days").json() == []
    assert _month(client, date) == []

    task_type = client.post("/task-types", json={"name": "Planning"}).json()
    saved = client.post(
        f"/days/{date}/blocks",
        json={"lane": "planned", "task_type_id": task_type["id"], "start_minute": 540, "end_minute": 600},
    )
    assert saved.status_code == 200, saved.text
    assert [(row["date"], row["planned_count"]) for row in _month(client, date)] == [(str(date), 1)]

    block_id = saved.json()["planned_blocks"][0]["id"]
    assert client.delete(f"/days/{date}/blocks/{block_id}").status_code == 200
    assert _month(client, date) == []
    assert any(row["date"] == str(date) for row in client.get("/days").json())


def test_actual_without_day_row_appears_until_deleted(client):
    date = _today(client) - dt.timedelta(days=1)
    task_type = client.post("/task-types", json={"name": "Recorded"}).json()
    actual = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "start_at": f"{date}T10:00:00Z",
            "end_at": f"{date}T11:00:00Z",
        },
    )
    assert actual.status_code == 201, actual.text
    assert client.get("/days").json() == []
    assert [(row["date"], row["actual_count"]) for row in _month(client, date)] == [(str(date), 1)]

    assert client.delete(f"/actual-blocks/{actual.json()['id']}").status_code == 204
    assert _month(client, date) == []


def test_completion_only_date_survives_archive_but_not_trash(client):
    date = _today(client)
    task = client.post("/tasks", json={"title": "Finish without blocks"}).json()
    completed = client.post(f"/tasks/{task['id']}/complete")
    assert completed.status_code == 200, completed.text
    assert _month(client, date) == [{
        "date": str(date), "planned_count": 0, "actual_count": 0,
        "has_completion": True, "actual_blocks": [],
    }]

    assert client.post("/tasks/archive-completed", json={"task_ids": [task["id"]]}).status_code == 204
    assert _month(client, date)[0]["has_completion"] is True
    assert client.delete(f"/tasks/{task['id']}").status_code == 200
    assert _month(client, date) == []


def test_future_plan_does_not_appear_in_chronicle_yet(client):
    future = _today(client) + dt.timedelta(days=1)
    task_type = client.post("/task-types", json={"name": "Future"}).json()
    created = client.post(
        f"/days/{future}/blocks",
        json={"lane": "planned", "task_type_id": task_type["id"], "start_minute": 540, "end_minute": 600},
    )
    assert created.status_code == 200, created.text
    assert all(row["date"] != str(future) for row in _month(client, future))
