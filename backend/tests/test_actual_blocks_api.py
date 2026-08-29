from __future__ import annotations

import datetime as dt

import pytest

from app.api.routes import actual_blocks
from app.main import app


UTC = dt.timezone.utc


@pytest.fixture
def captured_instants():
    values: list[dt.datetime] = []

    def capture() -> dt.datetime:
        return values.pop(0)

    app.dependency_overrides[actual_blocks.capture_utc_now] = capture
    try:
        yield values
    finally:
        app.dependency_overrides.pop(actual_blocks.capture_utc_now, None)


def _task_type(client, name: str = "Actual work") -> dict:
    response = client.post("/task-types", json={"name": name})
    assert response.status_code == 200, response.text
    return response.json()


def _planned_block(
    client,
    task_type_id: int,
    *,
    date: str = "2026-08-30",
    task_id: int | None = None,
    note: str = "Planned note",
    start_minute: int = 540,
    end_minute: int = 600,
) -> dict:
    response = client.post(
        f"/days/{date}/blocks",
        json={
            "lane": "planned",
            "task_type_id": task_type_id,
            "task_id": task_id,
            "note": note,
            "start_minute": start_minute,
            "end_minute": end_minute,
        },
    )
    assert response.status_code == 200, response.text
    return next(
        block
        for block in response.json()["planned_blocks"]
        if block["start_minute"] == start_minute and block["end_minute"] == end_minute
    )


def test_live_actual_start_and_finish_capture_authoritative_instants(
    client, captured_instants
):
    task_type = _task_type(client)
    captured_instants.extend(
        [
            dt.datetime(2026, 8, 30, 10, 12, tzinfo=UTC),
            dt.datetime(2026, 8, 30, 11, 18, tzinfo=UTC),
        ]
    )

    started = client.post(
        "/actual-blocks/start",
        json={"task_type_id": task_type["id"], "note": "Focused work"},
    )

    assert started.status_code == 201, started.text
    active = started.json()
    assert active["start_at"] == "2026-08-30T10:12:00Z"
    assert active["end_at"] is None
    assert client.get("/actual-blocks/active").json()["id"] == active["id"]

    finished = client.post(f"/actual-blocks/{active['id']}/finish")

    assert finished.status_code == 200, finished.text
    assert finished.json()["id"] == active["id"]
    assert finished.json()["end_at"] == "2026-08-30T11:18:00Z"
    assert client.get("/actual-blocks/active").json() is None
    day = client.get("/days/2026-08-30/preview").json()
    assert day["actual_minutes"] == 66
    assert day["actual_blocks"][0]["actual_block"]["id"] == active["id"]


def test_retrospective_actual_correction_preserves_planned_data_and_correspondence(client):
    task_type = _task_type(client, "Client work")
    task = client.post(
        "/tasks", json={"title": "Prepare briefing", "task_type_id": task_type["id"]}
    ).json()
    planned = _planned_block(client, task_type["id"], task_id=task["id"])

    created = client.post(
        "/actual-blocks",
        json={
            "planned_block_id": planned["id"],
            "start_at": "2026-08-30T10:12:00Z",
            "end_at": "2026-08-30T11:18:00Z",
            "note": "Actual note",
        },
    )

    assert created.status_code == 201, created.text
    actual = created.json()
    assert actual["planned_block_id"] == planned["id"]
    assert actual["task_id"] == task["id"]
    assert actual["task_type_id"] == task_type["id"]
    assert client.get(f"/actual-blocks/{actual['id']}").json() == actual

    corrected = client.patch(
        f"/actual-blocks/{actual['id']}",
        json={
            "start_at": "2026-08-30T10:07:00Z",
            "end_at": "2026-08-30T11:23:00Z",
            "note": "Corrected actual note",
        },
    )

    assert corrected.status_code == 200, corrected.text
    assert corrected.json()["planned_block_id"] == planned["id"]
    assert corrected.json()["note"] == "Corrected actual note"
    day = client.get("/days/2026-08-30").json()
    assert day["actual_minutes"] == 76
    assert day["planned_blocks"][0] == {
        **planned,
        "actual_block_id": actual["id"],
    }


def test_planned_time_and_note_edits_preserve_link_but_primary_item_change_detaches(client):
    first_type = _task_type(client, "First kind")
    second_type = _task_type(client, "Second kind")
    first_task = client.post("/tasks", json={"title": "First task"}).json()
    second_task = client.post("/tasks", json={"title": "Second task"}).json()
    planned = _planned_block(client, first_type["id"], task_id=first_task["id"])
    actual = client.post(
        "/actual-blocks",
        json={
            "planned_block_id": planned["id"],
            "start_at": "2026-08-30T10:12:00Z",
            "end_at": "2026-08-30T11:18:00Z",
        },
    ).json()

    corrected_plan = client.patch(
        f"/days/2026-08-30/blocks/{planned['id']}",
        json={"start_minute": 510, "end_minute": 570, "note": "Revised plan"},
    )

    assert corrected_plan.status_code == 200, corrected_plan.text
    corrected = corrected_plan.json()
    assert corrected["planned_blocks"][0]["actual_block_id"] == actual["id"]
    linked_actual = corrected["actual_blocks"][0]["actual_block"]
    assert linked_actual["planned_block_id"] == planned["id"]
    assert linked_actual["start_at"] == "2026-08-30T10:12:00Z"

    changed_item = client.patch(
        f"/days/2026-08-30/blocks/{planned['id']}",
        json={"task_type_id": second_type["id"], "task_id": second_task["id"]},
    )

    assert changed_item.status_code == 200, changed_item.text
    day = changed_item.json()
    assert day["planned_blocks"][0]["actual_block_id"] is None
    detached = day["actual_blocks"][0]["actual_block"]
    assert detached["id"] == actual["id"]
    assert detached["planned_block_id"] is None
    assert detached["task_type_id"] == first_type["id"]
    assert detached["task_id"] == first_task["id"]


def test_detach_and_relink_preserve_blocks_and_reject_matched_target_atomically(client):
    task_type = _task_type(client, "Correspondence")
    task = client.post("/tasks", json={"title": "One primary item"}).json()
    first_plan = _planned_block(client, task_type["id"], task_id=task["id"])
    second_plan = _planned_block(
        client,
        task_type["id"],
        task_id=task["id"],
        start_minute=600,
        end_minute=660,
    )
    other_task = client.post("/tasks", json={"title": "Different item"}).json()
    mismatched_plan = _planned_block(
        client,
        task_type["id"],
        task_id=other_task["id"],
        start_minute=660,
        end_minute=720,
    )
    linked = client.post(
        "/actual-blocks",
        json={
            "planned_block_id": first_plan["id"],
            "start_at": "2026-08-30T12:00:00Z",
            "end_at": "2026-08-30T12:30:00Z",
        },
    ).json()

    detached = client.post(f"/actual-blocks/{linked['id']}/detach")

    assert detached.status_code == 200, detached.text
    assert detached.json()["planned_block_id"] is None
    day = client.get("/days/2026-08-30").json()
    assert all(plan["actual_block_id"] is None for plan in day["planned_blocks"])

    mismatched = client.post(
        f"/actual-blocks/{linked['id']}/relink",
        json={"planned_block_id": mismatched_plan["id"]},
    )
    assert mismatched.status_code == 422, mismatched.text
    assert mismatched.json()["detail"] == (
        "Actual and Planned Blocks must have the same primary item"
    )
    assert client.get(f"/actual-blocks/{linked['id']}").json()["planned_block_id"] is None

    relinked = client.post(
        f"/actual-blocks/{linked['id']}/relink",
        json={"planned_block_id": second_plan["id"]},
    )

    assert relinked.status_code == 200, relinked.text
    assert relinked.json()["planned_block_id"] == second_plan["id"]
    other_actual = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "task_id": task["id"],
            "start_at": "2026-08-30T13:00:00Z",
            "end_at": "2026-08-30T13:30:00Z",
        },
    ).json()

    rejected = client.post(
        f"/actual-blocks/{other_actual['id']}/relink",
        json={"planned_block_id": second_plan["id"]},
    )

    assert rejected.status_code == 422, rejected.text
    assert rejected.json()["detail"] == "Planned Block already has corresponding Actual"
    day = client.get("/days/2026-08-30").json()
    actuals = {
        projection["actual_block"]["id"]: projection["actual_block"]
        for projection in day["actual_blocks"]
    }
    assert actuals[linked["id"]]["planned_block_id"] == second_plan["id"]
    assert actuals[other_actual["id"]]["planned_block_id"] is None


def test_record_actual_as_planned_has_one_shot_exact_undo(client):
    task_type = _task_type(client, "Shortcut")
    task = client.post("/tasks", json={"title": "Shortcut task"}).json()
    planned = _planned_block(
        client,
        task_type["id"],
        task_id=task["id"],
        note="Copy this note",
        start_minute=570,
        end_minute=630,
    )

    recorded = client.post(
        f"/planned-blocks/{planned['id']}/record-actual-as-planned"
    )

    assert recorded.status_code == 201, recorded.text
    result = recorded.json()
    assert result["undo_token"]
    actual = result["actual_block"]
    assert actual["planned_block_id"] == planned["id"]
    assert actual["task_type_id"] == task_type["id"]
    assert actual["task_id"] == task["id"]
    assert actual["note"] == "Copy this note"
    assert actual["start_at"] == "2026-08-30T09:30:00Z"
    assert actual["end_at"] == "2026-08-30T10:30:00Z"

    repeated = client.post(
        f"/planned-blocks/{planned['id']}/record-actual-as-planned"
    )
    assert repeated.status_code == 422
    assert repeated.json()["detail"] == "Planned Block already has corresponding Actual"

    revised_plan = client.patch(
        f"/days/2026-08-30/blocks/{planned['id']}",
        json={"start_minute": 540, "end_minute": 600, "note": "New plan only"},
    )
    assert revised_plan.status_code == 200
    assert revised_plan.json()["planned_blocks"][0]["actual_block_id"] == actual["id"]

    undone = client.post(
        f"/planned-blocks/{planned['id']}/undo-record-actual-as-planned",
        json={"undo_token": result["undo_token"]},
    )

    assert undone.status_code == 204, undone.text
    day = client.get("/days/2026-08-30").json()
    assert day["actual_blocks"] == []
    assert day["planned_blocks"][0]["id"] == planned["id"]
    assert day["planned_blocks"][0]["start_minute"] == 540
    repeated_undo = client.post(
        f"/planned-blocks/{planned['id']}/undo-record-actual-as-planned",
        json={"undo_token": result["undo_token"]},
    )
    assert repeated_undo.status_code == 422
    assert repeated_undo.json()["detail"] == "Record Actual Undo has already been used"


def test_actual_and_planned_deletion_preserve_the_other_side(client):
    task_type = _task_type(client, "Deletion")
    first_plan = _planned_block(client, task_type["id"])
    first_actual = client.post(
        "/actual-blocks",
        json={
            "planned_block_id": first_plan["id"],
            "start_at": "2026-08-30T12:00:00Z",
            "end_at": "2026-08-30T12:30:00Z",
        },
    ).json()

    deleted_actual = client.delete(f"/actual-blocks/{first_actual['id']}")

    assert deleted_actual.status_code == 204, deleted_actual.text
    day = client.get("/days/2026-08-30").json()
    assert day["actual_blocks"] == []
    assert day["planned_blocks"][0]["id"] == first_plan["id"]
    assert day["planned_blocks"][0]["actual_block_id"] is None

    second_plan = _planned_block(
        client, task_type["id"], start_minute=600, end_minute=660
    )
    second_actual = client.post(
        "/actual-blocks",
        json={
            "planned_block_id": second_plan["id"],
            "start_at": "2026-08-30T13:00:00Z",
            "end_at": "2026-08-30T13:30:00Z",
        },
    ).json()

    deleted_plan = client.delete(f"/days/2026-08-30/blocks/{second_plan['id']}")

    assert deleted_plan.status_code == 200, deleted_plan.text
    day = deleted_plan.json()
    assert [plan["id"] for plan in day["planned_blocks"]] == [first_plan["id"]]
    assert len(day["actual_blocks"]) == 1
    preserved = day["actual_blocks"][0]["actual_block"]
    assert preserved["id"] == second_actual["id"]
    assert preserved["planned_block_id"] is None


def test_second_live_start_and_overlapping_actual_are_rejected_without_partial_write(
    client, captured_instants
):
    task_type = _task_type(client, "Atomic")
    captured_instants.extend(
        [
            dt.datetime(2026, 8, 30, 10, 0, tzinfo=UTC),
            dt.datetime(2026, 8, 30, 11, 0, tzinfo=UTC),
        ]
    )
    first = client.post(
        "/actual-blocks/start", json={"task_type_id": task_type["id"]}
    )
    assert first.status_code == 201, first.text

    second = client.post(
        "/actual-blocks/start", json={"task_type_id": task_type["id"]}
    )

    assert second.status_code == 422, second.text
    assert second.json()["detail"] == "An Actual Block is already active"
    assert client.get("/actual-blocks/active").json()["id"] == first.json()["id"]

    overlapping = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "start_at": "2026-08-30T10:30:00Z",
            "end_at": "2026-08-30T11:30:00Z",
        },
    )

    assert overlapping.status_code == 422, overlapping.text
    assert overlapping.json()["detail"] == "Actual Blocks cannot overlap"
    assert client.get("/actual-blocks/active").json()["id"] == first.json()["id"]


def test_standalone_non_task_actual_projects_minute_accurate_cross_midnight_totals(client):
    task_type = _task_type(client, "Dinner")

    created = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "note": "Dinner with Alex",
            "start_at": "2026-08-30T23:47:00Z",
            "end_at": "2026-08-31T00:13:00Z",
        },
    )

    assert created.status_code == 201, created.text
    assert created.json()["task_id"] is None
    assert created.json()["planned_block_id"] is None
    first = client.get("/days/2026-08-30/preview").json()
    second = client.get("/days/2026-08-31/preview").json()
    assert first["actual_minutes"] == 13
    assert second["actual_minutes"] == 13
    assert (first["actual_blocks"][0]["start_minute"], first["actual_blocks"][0]["end_minute"]) == (
        1427,
        1440,
    )
    assert (second["actual_blocks"][0]["start_minute"], second["actual_blocks"][0]["end_minute"]) == (
        0,
        13,
    )
    assert client.get("/days/2026-08-30/summary").json()["actual_minutes"] == 13
    assert client.get("/days/2026-08-31/summary").json()["actual_minutes"] == 13


def test_openapi_and_day_commands_expose_no_legacy_time_completion(client):
    paths = client.get("/openapi.json").json()["paths"]
    assert not any("complete-as-planned" in path for path in paths)
    assert not any(path.endswith("/completion") for path in paths)

    task_type = _task_type(client, "Definitive only")
    legacy_actual = client.post(
        "/days/2026-08-30/blocks",
        json={
            "lane": "actual",
            "task_type_id": task_type["id"],
            "start_minute": 540,
            "end_minute": 600,
        },
    )
    assert legacy_actual.status_code == 422


def test_actual_primary_item_change_detaches_without_changing_planned(client):
    first_type = _task_type(client, "Actual first")
    second_type = _task_type(client, "Actual second")
    first_task = client.post("/tasks", json={"title": "First"}).json()
    second_task = client.post("/tasks", json={"title": "Second"}).json()
    planned = _planned_block(client, first_type["id"], task_id=first_task["id"])
    actual = client.post(
        "/actual-blocks",
        json={
            "planned_block_id": planned["id"],
            "start_at": "2026-08-30T12:00:00Z",
            "end_at": "2026-08-30T12:30:00Z",
        },
    ).json()

    changed = client.patch(
        f"/actual-blocks/{actual['id']}",
        json={"task_type_id": second_type["id"], "task_id": second_task["id"]},
    )

    assert changed.status_code == 200, changed.text
    assert changed.json()["planned_block_id"] is None
    assert changed.json()["task_type_id"] == second_type["id"]
    assert changed.json()["task_id"] == second_task["id"]
    day = client.get("/days/2026-08-30").json()
    assert day["planned_blocks"][0]["actual_block_id"] is None
    assert day["planned_blocks"][0]["task_type_id"] == first_type["id"]
    assert day["planned_blocks"][0]["task_id"] == first_task["id"]


@pytest.mark.parametrize("mutation", ["patch", "detach", "relink", "planned-item"])
def test_record_actual_undo_is_invalidated_by_newer_actual_intent(client, mutation):
    task_type = _task_type(client, f"Undo {mutation}")
    task = client.post("/tasks", json={"title": "Undo task"}).json()
    planned = _planned_block(client, task_type["id"], task_id=task["id"])
    recorded = client.post(
        f"/planned-blocks/{planned['id']}/record-actual-as-planned"
    ).json()
    actual = recorded["actual_block"]

    if mutation == "patch":
        response = client.patch(
            f"/actual-blocks/{actual['id']}", json={"note": "Newer correction"}
        )
    elif mutation == "detach":
        response = client.post(f"/actual-blocks/{actual['id']}/detach")
    elif mutation == "relink":
        target = _planned_block(
            client,
            task_type["id"],
            task_id=task["id"],
            start_minute=600,
            end_minute=660,
        )
        assert client.post(f"/actual-blocks/{actual['id']}/detach").status_code == 200
        response = client.post(
            f"/actual-blocks/{actual['id']}/relink",
            json={"planned_block_id": target["id"]},
        )
    else:
        replacement_type = _task_type(client, "Replacement item")
        replacement_task = client.post(
            "/tasks", json={"title": "Replacement task"}
        ).json()
        response = client.patch(
            f"/days/2026-08-30/blocks/{planned['id']}",
            json={
                "task_type_id": replacement_type["id"],
                "task_id": replacement_task["id"],
            },
        )
    assert response.status_code == 200, response.text

    undo = client.post(
        f"/planned-blocks/{planned['id']}/undo-record-actual-as-planned",
        json={"undo_token": recorded["undo_token"]},
    )

    assert undo.status_code == 422, undo.text
    assert undo.json()["detail"] == (
        "Actual Block changed; Record Actual Undo is no longer available"
    )
    assert client.get(f"/actual-blocks/{actual['id']}").status_code == 200


def test_overlapping_actual_correction_is_rejected_without_partial_write(client):
    task_type = _task_type(client, "Correction atomicity")
    first = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "start_at": "2026-08-30T09:00:00Z",
            "end_at": "2026-08-30T10:00:00Z",
        },
    ).json()
    second = client.post(
        "/actual-blocks",
        json={
            "task_type_id": task_type["id"],
            "note": "Keep me",
            "start_at": "2026-08-30T10:00:00Z",
            "end_at": "2026-08-30T11:00:00Z",
        },
    ).json()

    rejected = client.patch(
        f"/actual-blocks/{second['id']}",
        json={
            "note": "Must roll back too",
            "start_at": "2026-08-30T09:30:00Z",
            "end_at": "2026-08-30T10:30:00Z",
        },
    )

    assert rejected.status_code == 422, rejected.text
    assert rejected.json()["detail"] == "Actual Blocks cannot overlap"
    assert client.get(f"/actual-blocks/{first['id']}").json() == first
    assert client.get(f"/actual-blocks/{second['id']}").json() == second
