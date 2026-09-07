from __future__ import annotations

import pytest
from sqlalchemy import text

from app.db.session import get_engine
from app.readiness import expected_alembic_head


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


@pytest.fixture
def stamp_database():
    def stamp(revision: str) -> None:
        with get_engine().begin() as connection:
            connection.execute(text("DROP TABLE IF EXISTS alembic_version"))
            connection.execute(
                text("CREATE TABLE alembic_version (version_num VARCHAR(255) NOT NULL)")
            )
            connection.execute(
                text("INSERT INTO alembic_version (version_num) VALUES (:revision)"),
                {"revision": revision},
            )

    yield stamp
    with get_engine().begin() as connection:
        connection.execute(text("DROP TABLE IF EXISTS alembic_version"))


def test_ready_reports_database_and_matching_schema(client, stamp_database):
    head = expected_alembic_head()
    stamp_database(head)

    response = client.get("/ready")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ready",
        "database": "ok",
        "alembic": {"current": head, "head": head},
    }


def test_ready_rejects_schema_mismatch(client, stamp_database):
    stamp_database("outdated_revision")

    response = client.get("/ready")

    assert response.status_code == 503
    assert response.json() == {
        "status": "not_ready",
        "database": "ok",
        "alembic": {
            "current": "outdated_revision",
            "head": expected_alembic_head(),
        },
    }


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
        json={"start_minute": 547, "end_minute": 607},
    )
    assert r2.status_code == 200
    data2 = r2.json()
    b2 = next(x for x in data2["time_blocks"] if x["id"] == bid)
    assert (b2["start_minute"], b2["end_minute"]) == (547, 607)


def test_taskless_planned_block_name_round_trips_and_defaults_to_unspecified(client):
    created = client.post(
        "/days/2026-04-19/blocks",
        json={
            "lane": "planned",
            "name": "  Dinner   with Alex  ",
            "note": "Bring the invitation",
            "start_minute": 1080,
            "end_minute": 1140,
        },
    )

    assert created.status_code == 200
    block = created.json()["time_blocks"][0]
    assert block["name"] == "Dinner   with Alex"
    assert block["note"] == "Bring the invitation"
    assert block["task_type"]["name"] == "unspecified"

    changed = client.patch(
        f"/days/2026-04-19/blocks/{block['id']}",
        json={"name": "  Dinner with Sam  "},
    )
    assert changed.status_code == 200
    assert changed.json()["planned_blocks"][0]["name"] == "Dinner with Sam"

    cleared = client.patch(
        f"/days/2026-04-19/blocks/{block['id']}",
        json={"name": " \t "},
    )
    assert cleared.status_code == 200
    assert cleared.json()["time_blocks"][0]["name"] is None
    assert client.get("/days/2026-04-19").json()["time_blocks"][0]["name"] is None


def test_planned_block_name_contract_allows_duplicates_and_rejects_more_than_500_characters(client):
    task_type_id = _tid(client, "named sessions")
    first = client.post(
        "/days/2026-04-20/blocks",
        json={
            "lane": "planned",
            "task_type_id": task_type_id,
            "name": "Focus",
            "start_minute": 480,
            "end_minute": 510,
        },
    )
    second = client.post(
        "/days/2026-04-20/blocks",
        json={
            "lane": "planned",
            "task_type_id": task_type_id,
            "name": "Focus",
            "start_minute": 510,
            "end_minute": 540,
        },
    )

    assert first.status_code == 200
    assert second.status_code == 200
    assert [block["name"] for block in second.json()["planned_blocks"]] == ["Focus", "Focus"]

    too_long = client.post(
        "/days/2026-04-21/blocks",
        json={
            "lane": "planned",
            "name": "x" * 501,
            "start_minute": 480,
            "end_minute": 510,
        },
    )
    assert too_long.status_code == 422


def test_task_backed_planned_block_contract_accepts_name_without_deriving_one(client):
    task_type_id = _tid(client, "task sessions")
    task = client.post(
        "/tasks",
        json={"title": "Prepare launch", "task_type_id": task_type_id},
    ).json()

    unnamed = client.post(
        "/days/2026-04-22/blocks",
        json={
            "lane": "planned",
            "task_id": task["id"],
            "start_minute": 480,
            "end_minute": 510,
        },
    )
    named = client.post(
        "/days/2026-04-22/blocks",
        json={
            "lane": "planned",
            "task_id": task["id"],
            "name": "Outline session",
            "start_minute": 510,
            "end_minute": 540,
        },
    )

    assert unnamed.status_code == 200
    assert named.status_code == 200
    blocks = named.json()["planned_blocks"]
    assert blocks[0]["name"] is None
    assert blocks[1]["name"] == "Outline session"


def test_list_days(client):
    client.get("/days/2026-04-10")
    client.get("/days/2026-04-11")
    r = client.get("/days?limit=10")
    assert r.status_code == 200
    rows = r.json()
    assert len(rows) >= 2
    dates = [row["date"] for row in rows]
    assert "2026-04-11" in dates


def test_create_planned_block_accepts_any_whole_minute(client):
    tid = _tid(client, "minute-precise")

    response = client.post(
        "/days/2026-04-14/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "start_minute": 547,
            "end_minute": 577,
        },
    )

    assert response.status_code == 200
    block = response.json()["planned_blocks"][0]
    assert (block["start_minute"], block["end_minute"]) == (547, 577)


def test_create_planned_block_requires_thirty_minutes(client):
    tid = _tid(client, "too-short")

    response = client.post(
        "/days/2026-04-14/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "start_minute": 547,
            "end_minute": 576,
        },
    )

    assert response.status_code == 422
    assert response.json()["detail"] == "Planned Blocks must be at least 30 minutes"


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


def test_delete_actual_block(client):
    tid = _tid(client, "t")
    client.get("/days/2026-04-16")
    r = client.post(
        "/actual-blocks",
        json={
            "task_type_id": tid,
            "start_at": "2026-04-16T00:00:00Z",
            "end_at": "2026-04-16T00:30:00Z",
        },
    )
    bid = r.json()["id"]
    r2 = client.delete(f"/actual-blocks/{bid}")
    assert r2.status_code == 204
    assert client.get("/days/2026-04-16").json()["actual_blocks"] == []


def test_commit_plan_is_atomic_across_days(client):
    task_type_id = _tid(client, "planning-session")
    first = client.post("/tasks", json={"title": "First", "ready_to_plan": True}).json()
    second = client.post("/tasks", json={"title": "Second", "ready_to_plan": True}).json()

    response = client.post(
        "/days/plan",
        json={
            "placements": [
                {
                    "date": "2026-08-24",
                    "task_id": first["id"],
                    "task_type_id": task_type_id,
                    "start_minute": 547,
                    "end_minute": 577,
                },
                {
                    "date": "2026-08-25",
                    "task_id": second["id"],
                    "task_type_id": task_type_id,
                    "start_minute": 603,
                    "end_minute": 663,
                },
            ]
        },
    )

    assert response.status_code == 200
    days = response.json()["days"]
    assert [day["date"] for day in days] == ["2026-08-24", "2026-08-25"]
    assert [day["time_blocks"][0]["task_id"] for day in days] == [first["id"], second["id"]]
    tasks = {task["id"]: task for task in client.get("/tasks").json()["items"]}
    assert tasks[first["id"]]["ready_to_plan"] is False
    assert tasks[second["id"]]["ready_to_plan"] is False


def test_linked_planned_block_creation_resolves_task_type_in_backend(client):
    typed_id = _tid(client, "focused")
    typed_task = client.post(
        "/tasks",
        json={"title": "Typed", "task_type_id": typed_id, "ready_to_plan": True},
    ).json()
    untyped_task = client.post(
        "/tasks", json={"title": "Untyped", "ready_to_plan": True}
    ).json()

    typed = client.post(
        "/days/2026-08-29/blocks",
        json={
            "lane": "planned",
            "task_id": typed_task["id"],
            "start_minute": 540,
            "end_minute": 570,
        },
    )
    untyped = client.post(
        "/days/2026-08-29/blocks",
        json={
            "lane": "planned",
            "task_id": untyped_task["id"],
            "start_minute": 600,
            "end_minute": 630,
        },
    )

    assert typed.status_code == 200
    assert untyped.status_code == 200
    blocks = untyped.json()["time_blocks"]
    typed_block = next(block for block in blocks if block["task_id"] == typed_task["id"])
    untyped_block = next(block for block in blocks if block["task_id"] == untyped_task["id"])
    assert typed_block["task_type_id"] == typed_id
    assert untyped_block["task_type"]["name"] == "unspecified"
    assert [row["name"] for row in client.get("/task-types").json()].count("unspecified") == 1


def test_taskless_planned_block_without_task_type_uses_unspecified(client):
    response = client.post(
        "/days/2026-08-30/blocks",
        json={"lane": "planned", "start_minute": 540, "end_minute": 570},
    )

    assert response.status_code == 200
    assert response.json()["planned_blocks"][0]["task_type_id"] > 0
    assert response.json()["time_blocks"][0]["task_type"]["name"] == "unspecified"
    assert [row["name"] for row in client.get("/task-types").json()] == ["unspecified"]


def test_commit_plan_resolves_one_fallback_for_untyped_tasks(client):
    first = client.post("/tasks", json={"title": "First", "ready_to_plan": True}).json()
    second = client.post("/tasks", json={"title": "Second", "ready_to_plan": True}).json()

    response = client.post(
        "/days/plan",
        json={
            "placements": [
                {
                    "date": "2026-08-31",
                    "task_id": first["id"],
                    "start_minute": 540,
                    "end_minute": 570,
                },
                {
                    "date": "2026-09-01",
                    "task_id": second["id"],
                    "start_minute": 600,
                    "end_minute": 630,
                },
            ]
        },
    )

    assert response.status_code == 200
    block_types = {
        day["time_blocks"][0]["task_type_id"] for day in response.json()["days"]
    }
    assert len(block_types) == 1
    assert [row["name"] for row in client.get("/task-types").json()].count("unspecified") == 1


def test_commit_plan_rolls_back_every_placement_when_one_overlaps(client):
    task_type_id = _tid(client, "planning-rollback")
    existing = client.post(
        "/days/2026-08-27/blocks",
        json={
            "lane": "planned",
            "task_type_id": task_type_id,
            "start_minute": 600,
            "end_minute": 630,
        },
    )
    assert existing.status_code == 200
    first = client.post("/tasks", json={"title": "First", "ready_to_plan": True}).json()
    second = client.post("/tasks", json={"title": "Second", "ready_to_plan": True}).json()

    response = client.post(
        "/days/plan",
        json={
            "placements": [
                {
                    "date": "2026-08-26",
                    "task_id": first["id"],
                    "task_type_id": task_type_id,
                    "start_minute": 540,
                    "end_minute": 570,
                },
                {
                    "date": "2026-08-27",
                    "task_id": second["id"],
                    "task_type_id": task_type_id,
                    "start_minute": 600,
                    "end_minute": 630,
                },
            ]
        },
    )

    assert response.status_code == 422
    assert client.get("/days/2026-08-26/preview").json()["time_blocks"] == []
    tasks = {task["id"]: task for task in client.get("/tasks").json()["items"]}
    assert tasks[first["id"]]["ready_to_plan"] is True
    assert tasks[second["id"]]["ready_to_plan"] is True


def test_commit_plan_rolls_back_materialized_fallback_when_a_placement_fails(client):
    occupied_type_id = _tid(client, "occupied")
    client.post(
        "/days/2026-09-03/blocks",
        json={
            "lane": "planned",
            "task_type_id": occupied_type_id,
            "start_minute": 600,
            "end_minute": 630,
        },
    )
    first = client.post("/tasks", json={"title": "First", "ready_to_plan": True}).json()
    second = client.post("/tasks", json={"title": "Second", "ready_to_plan": True}).json()

    response = client.post(
        "/days/plan",
        json={
            "placements": [
                {
                    "date": "2026-09-02",
                    "task_id": first["id"],
                    "start_minute": 540,
                    "end_minute": 570,
                },
                {
                    "date": "2026-09-03",
                    "task_id": second["id"],
                    "start_minute": 600,
                    "end_minute": 630,
                },
            ]
        },
    )

    assert response.status_code == 422
    assert client.get("/days/2026-09-02/preview").json()["time_blocks"] == []
    assert [row["name"] for row in client.get("/task-types").json()] == ["occupied"]


def test_commit_plan_rejects_duplicate_tasks_without_writing(client):
    task_type_id = _tid(client, "planning-duplicate")
    task = client.post("/tasks", json={"title": "Only once", "ready_to_plan": True}).json()
    placement = {
        "date": "2026-08-28",
        "task_id": task["id"],
        "task_type_id": task_type_id,
        "start_minute": 540,
        "end_minute": 570,
    }

    response = client.post("/days/plan", json={"placements": [placement, placement]})

    assert response.status_code == 422
    assert client.get("/days/2026-08-28/preview").json()["time_blocks"] == []


def test_create_block_unknown_task_type(client):
    client.get("/days/2026-04-20")
    r = client.post(
        "/days/2026-04-20/blocks",
        json={"lane": "planned", "task_type_id": 99999, "start_minute": 0, "end_minute": 30},
    )
    assert r.status_code == 422


def test_record_actual_as_planned_is_one_shot(client):
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

    r2 = client.post(f"/planned-blocks/{planned_id}/record-actual-as-planned")
    assert r2.status_code == 201
    actual = r2.json()["actual_block"]
    assert actual["planned_block_id"] == planned_id
    assert actual["start_at"] == "2026-04-21T08:00:00Z"
    assert actual["end_at"] == "2026-04-21T08:30:00Z"
    assert actual["note"] == "do it"

    r3 = client.post(f"/planned-blocks/{planned_id}/record-actual-as-planned")
    assert r3.status_code == 422
    day3 = client.get("/days/2026-04-21").json()
    assert len(day3["planned_blocks"]) == 1
    assert len(day3["actual_blocks"]) == 1


def test_linked_planned_block_clears_readiness_and_preserves_task_on_actual(client):
    tid = _tid(client, "linked-work")
    task_response = client.post(
        "/tasks",
        json={
            "title": "Draft launch brief",
            "task_type_id": tid,
            "ready_to_plan": True,
            "status": "in_progress",
        },
    )
    assert task_response.status_code == 201
    task = task_response.json()

    created = client.post(
        "/days/2026-05-06/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "task_id": task["id"],
            "start_minute": 540,
            "end_minute": 600,
        },
    )
    assert created.status_code == 200
    planned = created.json()["time_blocks"][0]
    assert planned["task_id"] == task["id"]
    assert planned["task"] == {
        "id": task["id"],
        "title": "Draft launch brief",
        "status": "in_progress",
        "task_type_id": tid,
        "archived_at": None,
        "deleted_at": None,
    }

    refreshed_task = client.get("/tasks").json()["items"][0]
    assert refreshed_task["ready_to_plan"] is False
    assert refreshed_task["status"] == "in_progress"

    recorded = client.post(
        f"/planned-blocks/{planned['id']}/record-actual-as-planned"
    )
    assert recorded.status_code == 201
    actual = recorded.json()["actual_block"]
    assert actual["task_id"] == task["id"]
    assert actual["task"]["title"] == "Draft launch brief"
    assert client.get("/tasks").json()["items"][0]["status"] == "in_progress"


def test_patch_block_accepts_and_clears_task_link(client):
    tid = _tid(client, "patch-link")
    task = client.post("/tasks", json={"title": "Link later", "ready_to_plan": True}).json()
    created = client.post(
        "/days/2026-05-07/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "start_minute": 600,
            "end_minute": 630,
        },
    ).json()
    block_id = created["time_blocks"][0]["id"]

    linked = client.patch(
        f"/days/2026-05-07/blocks/{block_id}", json={"task_id": task["id"]}
    )
    assert linked.status_code == 200
    assert linked.json()["time_blocks"][0]["task_id"] == task["id"]
    assert client.get("/tasks").json()["items"][0]["ready_to_plan"] is False

    unlinked = client.patch(
        f"/days/2026-05-07/blocks/{block_id}", json={"task_id": None}
    )
    assert unlinked.status_code == 200
    assert unlinked.json()["time_blocks"][0]["task"] is None


def test_planned_block_name_survives_task_link_name_edits_and_unlink(client):
    tid = _tid(client, "named-link")
    task = client.post(
        "/tasks",
        json={
            "title": "Prepare launch",
            "task_type_id": tid,
            "ready_to_plan": True,
            "status": "in_progress",
        },
    ).json()
    created = client.post(
        "/days/2026-05-08/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "name": "Outline session",
            "start_minute": 600,
            "end_minute": 630,
        },
    ).json()["time_blocks"][0]

    linked = client.patch(
        f"/days/2026-05-08/blocks/{created['id']}",
        json={"task_id": task["id"]},
    )
    assert linked.status_code == 200, linked.text
    linked_block = linked.json()["time_blocks"][0]
    assert linked_block["name"] == "Outline session"
    assert linked_block["task_id"] == task["id"]
    assert linked_block["task_type_id"] == tid

    renamed = client.patch(
        f"/days/2026-05-08/blocks/{created['id']}",
        json={"name": "  Review session  "},
    )
    assert renamed.status_code == 200, renamed.text
    renamed_block = renamed.json()["time_blocks"][0]
    assert renamed_block["name"] == "Review session"
    assert renamed_block["task_id"] == task["id"]
    assert renamed_block["task_type_id"] == tid
    task_after_name_edit = client.get("/tasks").json()["items"][0]
    assert task_after_name_edit["ready_to_plan"] is False
    assert task_after_name_edit["status"] == "in_progress"

    unlinked = client.patch(
        f"/days/2026-05-08/blocks/{created['id']}",
        json={"task_id": None},
    )
    assert unlinked.status_code == 200, unlinked.text
    unlinked_block = unlinked.json()["time_blocks"][0]
    assert unlinked_block["name"] == "Review session"
    assert unlinked_block["task"] is None
    assert unlinked_block["task_type_id"] == tid

    cleared = client.patch(
        f"/days/2026-05-08/blocks/{created['id']}",
        json={"name": "   "},
    )
    assert cleared.status_code == 200, cleared.text
    assert cleared.json()["time_blocks"][0]["name"] is None
    assert client.get("/days/2026-05-08").json()["time_blocks"][0]["name"] is None


def test_unlinking_unnamed_planned_block_does_not_copy_task_title(client):
    tid = _tid(client, "unnamed-unlink")
    task = client.post("/tasks", json={"title": "Keep this as task context"}).json()
    created = client.post(
        "/days/2026-05-09/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "task_id": task["id"],
            "start_minute": 600,
            "end_minute": 630,
        },
    ).json()["time_blocks"][0]

    assert created["name"] is None
    unlinked = client.patch(
        f"/days/2026-05-09/blocks/{created['id']}",
        json={"task_id": None},
    )

    assert unlinked.status_code == 200, unlinked.text
    block = unlinked.json()["time_blocks"][0]
    assert block["task"] is None
    assert block["name"] is None
    assert block["task_type_id"] == tid


def test_day_block_create_rejects_actual_lane(client):
    tid = _tid(client, "actual-only")
    client.get("/days/2026-04-22")
    r = client.post(
        "/days/2026-04-22/blocks",
        json={"lane": "actual", "task_type_id": tid, "start_minute": 0, "end_minute": 30},
    )
    assert r.status_code == 422
