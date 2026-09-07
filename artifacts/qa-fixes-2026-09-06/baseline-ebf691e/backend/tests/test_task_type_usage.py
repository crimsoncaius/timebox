from __future__ import annotations


def _tid(client, name: str) -> int:
    r = client.post("/task-types", json={"name": name})
    assert r.status_code == 200
    return r.json()["id"]


def test_usage_count_is_zero_for_unused_types(client):
    _tid(client, "coding")
    rows = client.get("/task-types").json()
    assert [r["usage_count"] for r in rows] == [0]


def test_usage_count_tracks_blocks(client):
    coding = _tid(client, "coding")
    _tid(client, "writing")

    planned = client.post(
        "/days/2026-04-13/blocks",
        json={
            "lane": "planned",
            "task_type_id": coding,
            "start_minute": 540,
            "end_minute": 600,
        },
    )
    assert planned.status_code == 200, planned.text
    actual = client.post(
        "/actual-blocks",
        json={
            "task_type_id": coding,
            "start_at": "2026-04-13T10:00:00Z",
            "end_at": "2026-04-13T11:00:00Z",
        },
    )
    assert actual.status_code == 201, actual.text

    by_name = {r["name"]: r["usage_count"] for r in client.get("/task-types").json()}
    assert by_name == {"coding": 2, "writing": 0}


def test_parent_path_counts_only_its_own_blocks(client):
    # Creating "coding/ai" also creates the "coding" parent.
    leaf = _tid(client, "coding/ai")
    r = client.post(
        "/days/2026-04-13/blocks",
        json={
            "lane": "planned",
            "task_type_id": leaf,
            "start_minute": 540,
            "end_minute": 600,
        },
    )
    assert r.status_code == 200

    by_name = {r["name"]: r["usage_count"] for r in client.get("/task-types").json()}
    assert by_name == {"coding": 0, "coding/ai": 1}
