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
