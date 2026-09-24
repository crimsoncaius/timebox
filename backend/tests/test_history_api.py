"""Chronicle Calendar endpoint coverage."""


from __future__ import annotations


def test_chronicle_excludes_opened_dates_and_reports_saved_plans(client):
    client.get("/days/2026-05-02")
    task_type = client.post("/task-types", json={"name": "reading"}).json()
    saved = client.post(
        "/days/2026-05-03/blocks",
        json={"lane": "planned", "task_type_id": task_type["id"], "start_minute": 540, "end_minute": 600},
    )
    assert saved.status_code == 200, saved.text

    response = client.get("/days/chronicle?month=2026-05")
    assert response.status_code == 200
    assert response.json()["days"] == [{
        "date": "2026-05-03", "planned_count": 1, "actual_count": 0,
        "has_completion": False, "actual_blocks": [],
    }]


def test_chronicle_exposes_named_actual_without_day_row(client):
    created = client.post(
        "/actual-blocks",
        json={
            "name": "  Evening walk  ",
            "start_at": "2026-05-04T10:00:00Z",
            "end_at": "2026-05-04T11:00:00Z",
        },
    )
    assert created.status_code == 201, created.text

    response = client.get("/days/chronicle?month=2026-05")
    assert response.status_code == 200
    day = next(row for row in response.json()["days"] if row["date"] == "2026-05-04")
    assert day["actual_count"] == 1
    assert day["actual_blocks"][0]["actual_block"]["name"] == "Evening walk"
    assert day["actual_blocks"][0]["actual_block"]["task_type"]["name"] == "unspecified"
