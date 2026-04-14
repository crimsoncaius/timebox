from __future__ import annotations


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    data = r.json()
    assert data["status"] == "ok"
    assert "today" in data
    assert data["timezone"] == "UTC"


def test_get_day_creates_empty_blocks(client):
    r = client.get("/days/2026-04-13")
    assert r.status_code == 200
    data = r.json()
    assert data["date"] == "2026-04-13"
    assert data["time_blocks"] == []
    assert "summary" not in data
    assert data["meta"]["timezone"] == "UTC"


def test_create_and_patch_block(client):
    client.get("/days/2026-04-13")
    r = client.post(
        "/days/2026-04-13/blocks",
        json={"lane": "planned", "title": "Deep work", "start_minute": 540, "end_minute": 600},
    )
    assert r.status_code == 200
    data = r.json()
    assert len(data["time_blocks"]) == 1
    b = data["time_blocks"][0]
    assert b["lane"] == "planned"
    assert b["title"] == "Deep work"
    assert b["start_minute"] == 540
    assert b["end_minute"] == 600
    bid = b["id"]

    r2 = client.patch(
        f"/days/2026-04-13/blocks/{bid}",
        json={"end_minute": 630},
    )
    assert r2.status_code == 200
    data2 = r2.json()
    b2 = next(x for x in data2["time_blocks"] if x["id"] == bid)
    assert b2["end_minute"] == 630


def test_patch_day_window_invalid(client):
    client.get("/days/2026-04-13")
    r = client.patch("/days/2026-04-13", json={"start_hour": 12, "end_hour": 10})
    assert r.status_code == 422


def test_list_days(client):
    client.get("/days/2026-04-10")
    client.get("/days/2026-04-11")
    r = client.get("/days?limit=10")
    assert r.status_code == 200
    rows = r.json()
    assert len(rows) >= 2
    dates = [row["date"] for row in rows]
    assert "2026-04-11" in dates


def test_reject_non_snap_minutes(client):
    client.get("/days/2026-04-14")
    r = client.post(
        "/days/2026-04-14/blocks",
        json={"lane": "actual", "title": "x", "start_minute": 541, "end_minute": 600},
    )
    assert r.status_code == 422


def test_reject_overlap_same_lane(client):
    client.get("/days/2026-04-15")
    client.post(
        "/days/2026-04-15/blocks",
        json={"lane": "planned", "title": "a", "start_minute": 480, "end_minute": 540},
    )
    r = client.post(
        "/days/2026-04-15/blocks",
        json={"lane": "planned", "title": "b", "start_minute": 510, "end_minute": 570},
    )
    assert r.status_code == 422


def test_delete_block(client):
    client.get("/days/2026-04-16")
    r = client.post(
        "/days/2026-04-16/blocks",
        json={"lane": "actual", "title": "t", "start_minute": 0, "end_minute": 30},
    )
    bid = r.json()["time_blocks"][0]["id"]
    r2 = client.delete(f"/days/2026-04-16/blocks/{bid}")
    assert r2.status_code == 200
    assert r2.json()["time_blocks"] == []
