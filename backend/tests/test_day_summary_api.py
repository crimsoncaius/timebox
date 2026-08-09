from __future__ import annotations


def _tid(client, name: str) -> int:
    r = client.post("/task-types", json={"name": name})
    assert r.status_code == 200
    return r.json()["id"]


def _block(client, date: str, lane: str, tid: int, start: int, end: int) -> None:
    r = client.post(
        f"/days/{date}/blocks",
        json={
            "lane": lane,
            "task_type_id": tid,
            "start_minute": start,
            "end_minute": end,
        },
    )
    assert r.status_code == 200, r.text


def test_summary_of_untouched_day_is_empty_and_does_not_create_it(client):
    r = client.get("/days/2026-04-13/summary")
    assert r.status_code == 200
    data = r.json()
    assert data["date"] == "2026-04-13"
    assert data["planned_minutes"] == 0
    assert data["actual_minutes"] == 0
    assert data["rows"] == []
    assert data["meta"]["timezone"] == "UTC"

    # Viewing the review must not add the day to the archive.
    assert client.get("/days").json() == []


def test_summary_totals_and_rows(client):
    coding = _tid(client, "coding")
    writing = _tid(client, "writing")
    client.get("/days/2026-04-13")

    _block(client, "2026-04-13", "planned", coding, 540, 660)  # 120m planned
    _block(client, "2026-04-13", "actual", coding, 540, 630)  # 90m actual
    _block(client, "2026-04-13", "planned", writing, 660, 720)  # 60m planned

    data = client.get("/days/2026-04-13/summary").json()
    assert data["planned_minutes"] == 180
    assert data["actual_minutes"] == 90

    rows = data["rows"]
    assert len(rows) == 2
    # Busiest first: coding has 210 combined minutes, writing 60.
    assert rows[0]["task_type_name"] == "coding"
    assert rows[0]["planned_minutes"] == 120
    assert rows[0]["actual_minutes"] == 90
    assert rows[1]["task_type_name"] == "writing"
    assert rows[1]["planned_minutes"] == 60
    assert rows[1]["actual_minutes"] == 0


def test_summary_is_scoped_to_its_own_date(client):
    tid = _tid(client, "gym")
    _block(client, "2026-04-13", "actual", tid, 540, 600)

    other = client.get("/days/2026-04-14/summary").json()
    assert other["actual_minutes"] == 0
    assert other["rows"] == []


def test_summary_rejects_bad_date(client):
    r = client.get("/days/not-a-date/summary")
    assert r.status_code == 422
