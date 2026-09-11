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
    recorded = client.post(f"/planned-blocks/{pid}/record-actual-as-planned")
    assert recorded.status_code == 201
    day_before = client.get("/days/2026-06-03").json()
    assert len(day_before["planned_blocks"]) == 1
    assert len(day_before["actual_blocks"]) == 1
    actual = day_before["actual_blocks"][0]["actual_block"]
    assert actual["planned_block_id"] == pid

    r = client.delete(f"/task-types/{tid}?cascade_blocks=true")
    assert r.status_code == 204
    day_after = client.get("/days/2026-06-03").json()
    assert day_after["time_blocks"] == []
    assert day_after["actual_blocks"] == []


def test_delete_task_type_migrate_detaches_correspondence_on_item_change(client):
    tid_a = client.post("/task-types", json={"name": "m-from"}).json()["id"]
    tid_b = client.post("/task-types", json={"name": "m-to"}).json()["id"]
    client.get("/days/2026-06-04")
    planned = client.post(
        "/days/2026-06-04/blocks",
        json={"lane": "planned", "task_type_id": tid_a, "start_minute": 120, "end_minute": 150},
    ).json()["time_blocks"][0]
    pid = planned["id"]
    recorded = client.post(f"/planned-blocks/{pid}/record-actual-as-planned")
    assert recorded.status_code == 201
    r = client.delete(f"/task-types/{tid_a}?migrate_blocks_to={tid_b}")
    assert r.status_code == 204
    day = client.get("/days/2026-06-04").json()
    assert len(day["planned_blocks"]) == 1
    assert len(day["actual_blocks"]) == 1
    actual = day["actual_blocks"][0]["actual_block"]
    assert actual["task_type_id"] == tid_b
    assert actual["planned_block_id"] is None
    planned2 = day["planned_blocks"][0]
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


def test_unspecified_cannot_be_renamed_or_deleted_in_any_mode(client):
    tid = client.post('/task-types', json={'name': 'unspecified'}).json()['id']
    other = client.post('/task-types', json={'name': 'other'}).json()['id']
    for query in ['', '?cascade_blocks=true', f'?migrate_blocks_to={other}', '?clear_task_references=true&cascade_blocks=true']:
        response = client.delete(f'/task-types/{tid}{query}')
        assert response.status_code == 422
        assert 'cannot be deleted' in response.json()['detail']
    assert client.patch(f'/task-types/{tid}', json={'name': 'neutral'}).status_code == 422
    assert client.patch(f'/task-types/{other}', json={'name': ' UNSPECIFIED '}).status_code == 422
    assert next(r for r in client.get('/task-types').json() if r['id'] == tid)['name'] == 'unspecified'


def test_rename_preserves_historical_blocks_and_correspondence(client):
    tid = client.post('/task-types', json={'name': 'coding/ai'}).json()['id']
    root = next(r for r in client.get('/task-types').json() if r['name'] == 'coding')
    planned = client.post('/days/2026-06-03/blocks', json={'lane': 'planned', 'task_type_id': tid, 'start_minute': 60, 'end_minute': 90}).json()['time_blocks'][0]
    assert client.post(f"/planned-blocks/{planned['id']}/record-actual-as-planned").status_code == 201
    before = client.get('/days/2026-06-03').json()
    assert client.patch(f"/task-types/{root['id']}", json={'name': ' Learning '}).status_code == 200
    after = client.get('/days/2026-06-03').json()
    assert after['planned_blocks'][0]['id'] == planned['id']
    assert after['planned_blocks'][0]['task_type_id'] == tid
    assert after['time_blocks'][0]['task_type']['name'] == 'learning/ai'
    actual = after['actual_blocks'][0]['actual_block']
    assert actual['id'] == before['actual_blocks'][0]['actual_block']['id']
    assert actual['planned_block_id'] == planned['id']
    assert actual['task_type_id'] == tid
    assert actual['task_type']['name'] == 'learning/ai'


def test_rename_into_own_branch_preserves_ids_and_creates_parent(client):
    leaf = client.post('/task-types', json={'name': 'coding/ai'}).json()
    root = next(r for r in client.get('/task-types').json() if r['name'] == 'coding')
    response = client.patch(f"/task-types/{root['id']}", json={'name': 'coding/ai'})
    assert response.status_code == 200
    rows = {r['id']: r['name'] for r in client.get('/task-types').json()}
    assert rows[root['id']] == 'coding/ai'
    assert rows[leaf['id']] == 'coding/ai/ai'
    assert 'coding' in rows.values()


def test_branch_collision_leaves_every_name_unchanged(client):
    client.post('/task-types', json={'name': 'coding/ai'})
    client.post('/task-types', json={'name': 'learning/ai'})
    before = client.get('/task-types').json()
    root = next(r for r in before if r['name'] == 'coding')
    assert client.patch(f"/task-types/{root['id']}", json={'name': 'learning'}).status_code == 422
    assert client.get('/task-types').json() == before


def test_rename_preserves_task_reference_and_protected_delete_cannot_clear_it(client):
    tid = client.post('/task-types', json={'name': 'coding'}).json()['id']
    task = client.post('/tasks', json={'title': 'Keep this task', 'task_type_id': tid}).json()
    assert client.patch(f'/task-types/{tid}', json={'name': 'learning'}).status_code == 200
    assert next(r for r in client.get('/tasks').json()['items'] if r['id'] == task['id'])['task_type_id'] == tid
    neutral = client.post('/task-types', json={'name': 'unspecified'}).json()['id']
    neutral_task = client.post('/tasks', json={'title': 'Neutral task', 'task_type_id': neutral}).json()
    assert client.delete(f'/task-types/{neutral}?clear_task_references=true').status_code == 422
    assert next(r for r in client.get('/tasks').json()['items'] if r['id'] == neutral_task['id'])['task_type_id'] == neutral
