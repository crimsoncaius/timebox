import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.time import utc_now
from app.db.session import get_engine
from app.models.battle_plan import Task
from app.models.task_type import TaskType
from app.models.time_block import TimeBlock
from app.services import task_type_merge
from tests.test_activity_api import command
from tests.test_activity_api import tracking as tracking


def category(client, name):
    return client.post("/task-types", json={"name": name}).json()["id"]


def preview(client, source, target):
    r = client.post(f"/task-types/{source}/merge-preview", json={"target_id": target})
    assert r.status_code == 200, r.text
    return r.json()


def merge(client, plan):
    return client.post(
        f"/task-types/{plan['source_id']}/merge",
        json={"target_id": plan["target_id"], "preview_token": plan["preview_token"]},
    )


def test_branch_merge_preserves_work_and_independent_classifications(client):
    train = category(client, "travel/train")
    flight = category(client, "travel/flights")
    dest_train = category(client, "transportation/train")
    types = {r["name"]: r["id"] for r in client.get("/task-types").json()}
    source, target = types["travel"], types["transportation"]
    reading = category(client, "reading")
    for state in ["active", "archived", "trash"]:
        task = client.post("/tasks", json={"title": state, "task_type_id": source}).json()
        if state == "active":
            active_id = task["id"]
        with Session(get_engine()) as db:
            row = db.get(Task, task["id"])
            if state == "archived":
                row.archived_at = utc_now()
            if state == "trash":
                row.deleted_at = utc_now()
            db.commit()
    client.post(
        "/recurring-templates",
        json={
            "title": "commute",
            "task_type_id": source,
            "mode": "scheduled",
            "frequency": "daily",
            "start_date": "2099-01-01",
        },
    )
    client.post(
        "/days/2026-06-01/blocks",
        json={"lane": "planned", "task_type_id": train, "start_minute": 0, "end_minute": 30},
    ).json()
    unrelated = client.post(
        "/days/2026-06-01/blocks",
        json={
            "lane": "planned",
            "task_type_id": reading,
            "task_id": active_id,
            "start_minute": 60,
            "end_minute": 90,
        },
    )
    assert unrelated.status_code == 200, unrelated.text
    plan = preview(client, source, target)
    assert plan["task_count"] == 3 and plan["archived_task_count"] == plan["trashed_task_count"] == 1
    assert plan["recurring_series_count"] == 1 and plan["planned_block_count"] == 1
    assert sorted(c["action"] for c in plan["changes"]) == ["combine", "combine", "move"]
    assert merge(client, plan).status_code == 200
    after = {r["name"]: r["id"] for r in client.get("/task-types").json()}
    assert after["transportation/train"] == dest_train
    assert after["transportation/flights"] == flight
    assert not any(n.startswith("travel") or n.startswith("__merged") for n in after)
    with Session(get_engine()) as db:
        assert all(t.task_type_id == target for t in db.scalars(select(Task)))
        assert db.scalar(select(TimeBlock).where(TimeBlock.start_minute == 0)).task_type_id == dest_train
        assert db.scalar(select(TimeBlock).where(TimeBlock.start_minute == 60)).task_type_id == reading
    # A late assignment to the original identity follows the surviving category.
    late = client.post("/tasks", json={"title": "late offline task", "task_type_id": source})
    assert late.status_code == 201, late.text
    assert late.json()["task_type_id"] == target
    assert category(client, "travel") != source


def test_stale_structure_requires_confirmation_but_new_work_does_not(client):
    source, target = category(client, "a"), category(client, "b")
    p = preview(client, source, target)
    category(client, "b/train")
    assert merge(client, p).status_code == 409
    assert any(r["name"] == "a" for r in client.get("/task-types").json())
    p = preview(client, source, target)
    task = client.post("/tasks", json={"title": "new work", "task_type_id": source}).json()
    assert merge(client, p).status_code == 200
    with Session(get_engine()) as db:
        assert db.get(Task, task["id"]).task_type_id == target


@pytest.mark.parametrize(
    "a,b", [("a", "a"), ("a", "a/train"), ("a/train", "a"), ("unspecified", "a"), ("a", "unspecified")]
)
def test_reject_invalid_branches(client, a, b):
    for name in {a, b}:
        client.post("/task-types", json={"name": name})
    ids = {r["name"]: r["id"] for r in client.get("/task-types").json()}
    assert client.post(f"/task-types/{ids[a]}/merge-preview", json={"target_id": ids[b]}).status_code == 422


def test_failure_rolls_back_references_and_aliases(client, monkeypatch):
    source, target = category(client, "a"), category(client, "b")
    task = client.post("/tasks", json={"title": "keep", "task_type_id": source}).json()
    p = preview(client, source, target)

    def fail(*args):
        raise ValueError("injected failure")

    monkeypatch.setattr(task_type_merge, "_touch_days", fail)
    assert merge(client, p).status_code == 422
    with Session(get_engine()) as db:
        assert db.get(Task, task["id"]).task_type_id == source
        assert db.get(TaskType, source).is_merged is False


def test_running_activity_and_offline_replay_follow_merged_identity(tracking):
    source, target = category(tracking, "travel"), category(tracking, "transportation")
    task = tracking.post("/tasks", json={"title": "commute", "task_type_id": source}).json()
    block = tracking.post(
        "/days/2026-06-01/blocks",
        json={
            "lane": "planned",
            "task_type_id": source,
            "task_id": task["id"],
            "start_minute": 0,
            "end_minute": 30,
        },
    ).json()
    selection = dict(
        task_type_id=source, task_id=task["id"], planned_block_id=block["id"], selection_snapshot=True
    )
    first = tracking.get("/activity").json()
    start = command(first, "start", **selection)
    r = tracking.post("/activity/commands", json=start)
    assert r.status_code == 200, r.text
    running_snapshot = r.json()
    running = running_snapshot["current"]
    p = preview(tracking, source, target)
    assert merge(tracking, p).status_code == 200
    current = tracking.get("/activity").json()
    assert current["current"]["id"] == running["id"]
    assert current["current"]["start_at"] == running["start_at"]
    assert current["current"]["end_at"] is None
    assert current["current"]["task_type_id"] == target
    assert current["current"]["task_id"] == task["id"]
    assert current["current"]["planned_block_id"] == block["id"]
    # Retrying the original envelope remains valid; the receipt must not be rewritten.
    assert tracking.post("/activity/commands", json=start).status_code == 200
    delayed = command(running_snapshot, "switch", sequence=2, **selection)
    response = tracking.post("/activity/commands", json=delayed)
    assert response.status_code == 200, response.text
    assert all(r["task_type_id"] == target for r in response.json()["records"])
    assert all(
        r["planned_block_id"] == block["id"] and r["task_id"] == task["id"]
        for r in response.json()["records"]
    )
    assert source not in [r["id"] for r in response.json()["task_types"]]
    # Chains resolve even after the original destination itself merges.
    final = category(tracking, "commuting")
    assert merge(tracking, preview(tracking, target, final)).status_code == 200
    after = tracking.get("/activity").json()
    late = command(after, "switch", sequence=3, task_type_id=source, selection_snapshot=True)
    r = tracking.post("/activity/commands", json=late)
    assert r.status_code == 200, r.text
    assert all(row["task_type_id"] == final for row in r.json()["records"])
