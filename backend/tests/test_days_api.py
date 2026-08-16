from __future__ import annotations


def _tid(client, name: str) -> int:
    r = client.post("/task-types", json={"name": name})
    assert r.status_code == 200
    return r.json()["id"]


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


def test_preview_missing_day_does_not_create_archive_row(client):
    client.patch(
        "/settings",
        json={"start_hour": 7, "end_hour": 19, "show_full_day": False},
    )

    r = client.get("/days/2026-04-30/preview")

    assert r.status_code == 200
    assert r.json()["date"] == "2026-04-30"
    assert r.json()["start_hour"] == 7
    assert r.json()["end_hour"] == 19
    assert r.json()["time_blocks"] == []
    assert all(row["date"] != "2026-04-30" for row in client.get("/days").json())


def test_preview_existing_day_includes_its_blocks(client):
    tid = _tid(client, "previewed")
    created = client.post(
        "/days/2026-05-01/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "start_minute": 540,
            "end_minute": 600,
        },
    )
    assert created.status_code == 200

    r = client.get("/days/2026-05-01/preview")

    assert r.status_code == 200
    assert r.json()["date"] == "2026-05-01"
    assert [block["task_type"]["name"] for block in r.json()["time_blocks"]] == ["previewed"]


def test_create_and_patch_block(client):
    tid = _tid(client, "Deep work")
    client.get("/days/2026-04-13")
    r = client.post(
        "/days/2026-04-13/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "note": "focus",
            "start_minute": 540,
            "end_minute": 600,
        },
    )
    assert r.status_code == 200
    data = r.json()
    assert len(data["time_blocks"]) == 1
    b = data["time_blocks"][0]
    assert b["lane"] == "planned"
    assert b["task_type_id"] == tid
    assert b["task_type"]["name"] == "deep work"
    assert b["note"] == "focus"
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
    tid = _tid(client, "x")
    client.get("/days/2026-04-14")
    r = client.post(
        "/days/2026-04-14/blocks",
        json={"lane": "actual", "task_type_id": tid, "start_minute": 541, "end_minute": 600},
    )
    assert r.status_code == 422


def test_reject_overlap_same_lane(client):
    a = _tid(client, "a")
    b = _tid(client, "b")
    client.get("/days/2026-04-15")
    client.post(
        "/days/2026-04-15/blocks",
        json={"lane": "planned", "task_type_id": a, "start_minute": 480, "end_minute": 540},
    )
    r = client.post(
        "/days/2026-04-15/blocks",
        json={"lane": "planned", "task_type_id": b, "start_minute": 510, "end_minute": 570},
    )
    assert r.status_code == 422


def test_reject_overlap_same_lane_on_patch(client):
    """PATCH cannot extend a block into another block in the same lane."""
    first = _tid(client, "first")
    second = _tid(client, "second")
    client.get("/days/2026-04-17")
    r1 = client.post(
        "/days/2026-04-17/blocks",
        json={"lane": "planned", "task_type_id": first, "start_minute": 480, "end_minute": 510},
    )
    assert r1.status_code == 200
    bid_first = r1.json()["time_blocks"][0]["id"]
    r2 = client.post(
        "/days/2026-04-17/blocks",
        json={"lane": "planned", "task_type_id": second, "start_minute": 540, "end_minute": 600},
    )
    assert r2.status_code == 200

    r3 = client.patch(
        f"/days/2026-04-17/blocks/{bid_first}",
        json={"end_minute": 570},
    )
    assert r3.status_code == 422


def test_delete_block(client):
    tid = _tid(client, "t")
    client.get("/days/2026-04-16")
    r = client.post(
        "/days/2026-04-16/blocks",
        json={"lane": "actual", "task_type_id": tid, "start_minute": 0, "end_minute": 30},
    )
    bid = r.json()["time_blocks"][0]["id"]
    r2 = client.delete(f"/days/2026-04-16/blocks/{bid}")
    assert r2.status_code == 200
    assert r2.json()["time_blocks"] == []


def test_create_block_unknown_task_type(client):
    client.get("/days/2026-04-20")
    r = client.post(
        "/days/2026-04-20/blocks",
        json={"lane": "planned", "task_type_id": 99999, "start_minute": 0, "end_minute": 30},
    )
    assert r.status_code == 422


def test_complete_planned_as_actual(client):
    tid = _tid(client, "planned-complete")
    client.get("/days/2026-04-21")
    r = client.post(
        "/days/2026-04-21/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "note": "do it",
            "start_minute": 480,
            "end_minute": 510,
        },
    )
    assert r.status_code == 200
    planned_id = r.json()["time_blocks"][0]["id"]

    r2 = client.post(f"/days/2026-04-21/blocks/{planned_id}/complete-as-planned")
    assert r2.status_code == 200
    day = r2.json()
    assert len(day["time_blocks"]) == 2
    actual = next(b for b in day["time_blocks"] if b["lane"] == "actual")
    assert actual["planned_block_id"] == planned_id
    assert actual["start_minute"] == 480
    assert actual["end_minute"] == 510
    assert actual["note"] == "do it"

    r3 = client.post(f"/days/2026-04-21/blocks/{planned_id}/complete-as-planned")
    assert r3.status_code == 200
    day3 = r3.json()
    assert len(day3["time_blocks"]) == 2


def test_complete_non_planned_block_rejected(client):
    tid = _tid(client, "actual-only")
    client.get("/days/2026-04-22")
    r = client.post(
        "/days/2026-04-22/blocks",
        json={"lane": "actual", "task_type_id": tid, "start_minute": 0, "end_minute": 30},
    )
    assert r.status_code == 200
    aid = r.json()["time_blocks"][0]["id"]
    r2 = client.post(f"/days/2026-04-22/blocks/{aid}/complete-as-planned")
    assert r2.status_code == 422
