"""History list endpoint coverage."""


from __future__ import annotations


def test_list_days_returns_rows_without_summary(client):
    client.get("/days/2026-05-01")
    r = client.get("/days?limit=5")
    assert r.status_code == 200
    rows = r.json()
    assert len(rows) >= 1
    row = next(x for x in rows if x["date"] == "2026-05-01")
    assert "date" in row
    assert "updated_at" in row
    assert "summary" not in row


def test_list_days_counts_blocks(client):
    """Opening a date creates an empty day; only real blocks lift the count."""
    tt = client.post("/task-types", json={"name": "reading"}).json()
    client.get("/days/2026-05-02")
    client.get("/days/2026-05-03")
    client.post(
        "/days/2026-05-03/blocks",
        json={
            "lane": "planned",
            "task_type_id": tt["id"],
            "start_minute": 540,
            "end_minute": 600,
        },
    )

    rows = {row["date"]: row for row in client.get("/days?limit=50").json()}
    assert rows["2026-05-02"]["block_count"] == 0
    assert rows["2026-05-03"]["block_count"] == 1
