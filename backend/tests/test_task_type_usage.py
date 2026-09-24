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


def test_inactive_usage_and_restoration_after_type_deletion(client):
    from sqlalchemy.orm import Session
    from app.db.session import get_engine
    from app.models.battle_plan import Task
    from app.core.time import utc_now

    tid = _tid(client, "research")
    other = _tid(client, "writing")
    ids = []
    for title in ["Active", "Archived", "Trashed", "Trashed archive"]:
        response = client.post("/tasks", json={"title": title, "task_type_id": tid})
        assert response.status_code == 201
        ids.append(response.json()["id"])
    # Include the overlapping lifecycle timestamps that must count only as Trash.
    with Session(get_engine()) as db:
        db.get(Task, ids[1]).archived_at = utc_now()
        db.get(Task, ids[2]).deleted_at = utc_now()
        db.get(Task, ids[3]).archived_at = utc_now()
        db.get(Task, ids[3]).deleted_at = utc_now()
        db.commit()
    block = client.post("/days/2026-04-13/blocks", json={
        "lane": "planned", "task_type_id": other, "task_id": ids[0],
        "start_minute": 540, "end_minute": 600,
    })
    assert block.status_code == 200, block.text
    row = next(r for r in client.get("/task-types").json() if r["id"] == tid)
    assert row["task_usage_count"] == 4
    assert row["active_task_usage_count"] == 1
    assert row["archived_task_usage_count"] == 1
    assert row["trashed_task_usage_count"] == 2
    assert row["usage_count"] == 0

    assert client.delete(f"/task-types/{tid}").status_code == 409
    with Session(get_engine()) as db:
        assert all(db.get(Task, task_id).task_type_id == tid for task_id in ids)
    response = client.delete(f"/task-types/{tid}?clear_task_references=true")
    assert response.status_code == 204, response.text
    assert client.post(f"/tasks/{ids[1]}/unarchive").status_code == 204
    for task_id in ids[2:]:
        assert client.post(f"/tasks/{task_id}/restore").status_code == 204
    with Session(get_engine()) as db:
        assert all(db.get(Task, task_id).task_type_id is None for task_id in ids)
    from app.models.time_block import TimeBlock
    with Session(get_engine()) as db:
        assert db.get(TimeBlock, block.json()["id"]).task_type_id == other
