from __future__ import annotations


def test_list_task_types_empty(client):
    r = client.get("/task-types")
    assert r.status_code == 200
    assert r.json() == []


def test_create_and_list_task_types(client):
    r = client.post("/task-types", json={"name": "work"})
    assert r.status_code == 200
    row = r.json()
    assert row["name"] == "work"
    assert "id" in row

    r2 = client.get("/task-types")
    assert r2.status_code == 200
    names = [x["name"] for x in r2.json()]
    assert "work" in names


def test_duplicate_task_type_name_case_insensitive(client):
    assert client.post("/task-types", json={"name": "Work"}).status_code == 200
    r = client.post("/task-types", json={"name": "WORK"})
    assert r.status_code == 422


def test_patch_task_type(client):
    tid = client.post("/task-types", json={"name": "coding"}).json()["id"]
    r = client.patch(f"/task-types/{tid}", json={"name": "coding v2"})
    assert r.status_code == 200
    assert r.json()["name"] == "coding v2"


def test_delete_unused_task_type(client):
    tid = client.post("/task-types", json={"name": "temp"}).json()["id"]
    r = client.delete(f"/task-types/{tid}")
    assert r.status_code == 204


def test_delete_task_type_in_use(client):
    tid = client.post("/task-types", json={"name": "in-use"}).json()["id"]
    client.get("/days/2026-05-01")
    client.post(
        "/days/2026-05-01/blocks",
        json={"lane": "planned", "task_type_id": tid, "start_minute": 0, "end_minute": 30},
    )
    r = client.delete(f"/task-types/{tid}")
    assert r.status_code == 409
    assert "still used" in r.json()["detail"].lower()


def test_patch_task_type_not_found(client):
    r = client.patch("/task-types/99999", json={"name": "nope"})
    assert r.status_code == 404


def test_delete_task_type_not_found(client):
    r = client.delete("/task-types/99999")
    assert r.status_code == 404


def test_create_task_type_canonicalizes_and_creates_missing_ancestors(client):
    r = client.post("/task-types", json={"name": " Coding / AI / Agents "})
    assert r.status_code == 200
    assert r.json()["name"] == "coding/ai/agents"

    names = [row["name"] for row in client.get("/task-types").json()]
    assert names == ["coding", "coding/ai", "coding/ai/agents"]


def test_patch_task_type_renames_descendant_branch(client):
    client.post("/task-types", json={"name": "coding/ai"})
    rows = client.get("/task-types").json()
    root_id = next(row["id"] for row in rows if row["name"] == "coding")

    r = client.patch(f"/task-types/{root_id}", json={"name": "development"})
    assert r.status_code == 200
    assert r.json()["name"] == "development"

    names = [row["name"] for row in client.get("/task-types").json()]
    assert "development/ai" in names
    assert "coding/ai" not in names


def test_delete_task_type_with_descendants_returns_conflict(client):
    client.post("/task-types", json={"name": "exercise/cardio"})
    rows = client.get("/task-types").json()
    parent_id = next(row["id"] for row in rows if row["name"] == "exercise")
    r = client.delete(f"/task-types/{parent_id}")
    assert r.status_code == 409
    assert "subpaths" in r.json()["detail"].lower()


def test_delete_task_type_cascade_removes_blocks(client):
    tid = client.post("/task-types", json={"name": "cascade-me"}).json()["id"]
    client.get("/days/2026-06-01")
    client.post(
        "/days/2026-06-01/blocks",
        json={"lane": "planned", "task_type_id": tid, "start_minute": 0, "end_minute": 30},
    )
    r = client.delete(f"/task-types/{tid}?cascade_blocks=true")
    assert r.status_code == 204
    day = client.get("/days/2026-06-01").json()
    assert day["time_blocks"] == []
    ids = [x["id"] for x in client.get("/task-types").json()]
    assert tid not in ids


def test_delete_task_type_migrate_then_remove(client):
    tid_a = client.post("/task-types", json={"name": "migrate-from"}).json()["id"]
    tid_b = client.post("/task-types", json={"name": "migrate-to"}).json()["id"]
    client.get("/days/2026-06-02")
    client.post(
        "/days/2026-06-02/blocks",
        json={"lane": "planned", "task_type_id": tid_a, "start_minute": 0, "end_minute": 30},
    )
    r = client.delete(f"/task-types/{tid_a}?migrate_blocks_to={tid_b}")
    assert r.status_code == 204
    day = client.get("/days/2026-06-02").json()
    assert len(day["time_blocks"]) == 1
    assert day["time_blocks"][0]["task_type_id"] == tid_b
    ids = [x["id"] for x in client.get("/task-types").json()]
    assert tid_a not in ids


def test_delete_task_type_both_cascade_and_migrate_422(client):
    tid = client.post("/task-types", json={"name": "both-modes"}).json()["id"]
    other = client.post("/task-types", json={"name": "other-for-query"}).json()["id"]
    r = client.delete(f"/task-types/{tid}?cascade_blocks=true&migrate_blocks_to={other}")
    assert r.status_code == 422


def test_delete_task_type_migrate_same_id_422(client):
    tid = client.post("/task-types", json={"name": "self-migrate"}).json()["id"]
    r = client.delete(f"/task-types/{tid}?migrate_blocks_to={tid}")
    assert r.status_code == 422


def test_delete_task_type_migrate_missing_target_422(client):
    tid = client.post("/task-types", json={"name": "orphan-migrate"}).json()["id"]
    r = client.delete(f"/task-types/{tid}?migrate_blocks_to=99999")
    assert r.status_code == 422


def test_delete_task_type_cascade_removes_planned_and_actual_pair(client):
    tid = client.post("/task-types", json={"name": "planned-pair"}).json()["id"]
    client.get("/days/2026-06-03")
    planned = client.post(
        "/days/2026-06-03/blocks",
        json={"lane": "planned", "task_type_id": tid, "start_minute": 60, "end_minute": 90},
    ).json()["time_blocks"][0]
    pid = planned["id"]
    client.post(f"/days/2026-06-03/blocks/{pid}/complete-as-planned")
    day_before = client.get("/days/2026-06-03").json()
    assert len(day_before["time_blocks"]) == 2
    actual = next(b for b in day_before["time_blocks"] if b["lane"] == "actual")
    assert actual.get("planned_block_id") == pid

    r = client.delete(f"/task-types/{tid}?cascade_blocks=true")
    assert r.status_code == 204
    day_after = client.get("/days/2026-06-03").json()
    assert day_after["time_blocks"] == []


def test_delete_task_type_migrate_keeps_planned_block_link(client):
    tid_a = client.post("/task-types", json={"name": "m-from"}).json()["id"]
    tid_b = client.post("/task-types", json={"name": "m-to"}).json()["id"]
    client.get("/days/2026-06-04")
    planned = client.post(
        "/days/2026-06-04/blocks",
        json={"lane": "planned", "task_type_id": tid_a, "start_minute": 120, "end_minute": 150},
    ).json()["time_blocks"][0]
    pid = planned["id"]
    client.post(f"/days/2026-06-04/blocks/{pid}/complete-as-planned")
    r = client.delete(f"/task-types/{tid_a}?migrate_blocks_to={tid_b}")
    assert r.status_code == 204
    day = client.get("/days/2026-06-04").json()
    assert len(day["time_blocks"]) == 2
    actual = next(b for b in day["time_blocks"] if b["lane"] == "actual")
    assert actual["task_type_id"] == tid_b
    assert actual.get("planned_block_id") == pid
    planned2 = next(b for b in day["time_blocks"] if b["lane"] == "planned")
    assert planned2["task_type_id"] == tid_b


def test_patch_task_type_creates_missing_target_ancestors(client):
    leaf_id = client.post("/task-types", json={"name": "coding/ai"}).json()["id"]
    r = client.patch(f"/task-types/{leaf_id}", json={"name": "development/ml"})
    assert r.status_code == 200
    names = [row["name"] for row in client.get("/task-types").json()]
    assert "development" in names
    assert "development/ml" in names


def test_post_invalid_task_type_path_double_slash(client):
    r = client.post("/task-types", json={"name": "coding//ai"})
    assert r.status_code == 422
    assert "invalid" in r.json()["detail"].lower()


def test_patch_task_type_renames_descendants_when_path_has_underscores(client):
    """Underscores must not act as SQL LIKE wildcards when matching descendant paths."""
    client.post("/task-types", json={"name": "e2e_hier_root_20260604/subleaf"})
    rows = client.get("/task-types").json()
    root_id = next(r["id"] for r in rows if r["name"] == "e2e_hier_root_20260604")
    r = client.patch(f"/task-types/{root_id}", json={"name": "e2e_hier_renamed_20260604"})
    assert r.status_code == 200
    names = [x["name"] for x in client.get("/task-types").json()]
    assert "e2e_hier_renamed_20260604/subleaf" in names
    assert "e2e_hier_root_20260604/subleaf" not in names
