from __future__ import annotations

import datetime as dt

import pytest
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.battle_plan import Task, TaskCompletionOperation
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock


UTC = dt.timezone.utc


def _type_and_day(db: Session, date: dt.date = dt.date(2026, 8, 30)) -> tuple[TaskType, Day]:
    task_type = TaskType(name="contract")
    day = Day(date=date, start_hour=8, end_hour=20, show_full_day=False)
    db.add_all([task_type, day])
    db.commit()
    return task_type, day


def _actual(
    task_type_id: int,
    start: dt.datetime,
    end: dt.datetime | None,
    *,
    planned_block_id: int | None = None,
) -> TimeBlock:
    return TimeBlock(
        lane=BlockLane.actual,
        task_type_id=task_type_id,
        task_id=None,
        planned_block_id=planned_block_id,
        day_id=None,
        start_minute=None,
        end_minute=None,
        start_at=start,
        end_at=end,
    )


def test_parent_completion_preserves_explicit_subtask_checks_and_exposes_resolution(client):
    parent = client.post("/tasks", json={"title": "Parent"}).json()
    unchecked = client.post(
        "/tasks", json={"title": "Unchecked", "parent_id": parent["id"]}
    ).json()
    checked = client.post(
        "/tasks",
        json={"title": "Checked", "parent_id": parent["id"], "status": "completed"},
    ).json()
    assert checked["subtasks"] == []

    completed = client.post(
        f"/tasks/{parent['id']}/complete", json={"planned_time": "keep"}
    )
    assert completed.status_code == 200, completed.text
    task = completed.json()["task"]
    assert task["completed_at"] is not None
    assert task["version"] > parent["version"]
    assert [item["checked"] for item in task["subtasks"]] == [False, True]
    assert all(item["effectively_resolved"] for item in task["subtasks"])
    assert all("status" not in item for item in task["subtasks"])

    with Session(get_engine()) as db:
        stored = {row.id: row for row in db.query(Task).filter(Task.id.in_([unchecked["id"], checked["id"]]))}
        assert stored[unchecked["id"]].checked is False
        assert stored[checked["id"]].checked is True
        operation = db.get(TaskCompletionOperation, completed.json()["undo_token"])
        assert operation is not None
        assert operation.completed_task_version == task["version"]


def test_database_rejects_second_active_actual_and_actual_overlap():
    with Session(get_engine()) as db:
        task_type, _ = _type_and_day(db)
        db.add(_actual(task_type.id, dt.datetime(2026, 8, 30, 9, tzinfo=UTC), None))
        db.commit()

        db.add(_actual(task_type.id, dt.datetime(2026, 8, 30, 10, tzinfo=UTC), None))
        with pytest.raises(IntegrityError):
            db.commit()
        db.rollback()

        active = db.query(TimeBlock).filter(TimeBlock.end_at.is_(None)).one()
        active.end_at = dt.datetime(2026, 8, 30, 10, tzinfo=UTC)
        db.commit()
        db.add(
            _actual(
                task_type.id,
                dt.datetime(2026, 8, 30, 9, 30, tzinfo=UTC),
                dt.datetime(2026, 8, 30, 10, 30, tzinfo=UTC),
            )
        )
        with pytest.raises(IntegrityError):
            db.commit()


def test_database_rejects_invalid_or_duplicate_correspondence():
    with Session(get_engine()) as db:
        task_type, day = _type_and_day(db)
        planned = TimeBlock(
            lane=BlockLane.planned,
            task_type_id=task_type.id,
            day_id=day.id,
            start_minute=480,
            end_minute=540,
        )
        unrelated_actual = _actual(
            task_type.id,
            dt.datetime(2026, 8, 30, 6, tzinfo=UTC),
            dt.datetime(2026, 8, 30, 7, tzinfo=UTC),
        )
        db.add_all([planned, unrelated_actual])
        db.commit()

        db.add(
            _actual(
                task_type.id,
                dt.datetime(2026, 8, 30, 7, tzinfo=UTC),
                dt.datetime(2026, 8, 30, 8, tzinfo=UTC),
                planned_block_id=unrelated_actual.id,
            )
        )
        with pytest.raises(IntegrityError):
            db.commit()
        db.rollback()

        first = _actual(
            task_type.id,
            dt.datetime(2026, 8, 30, 8, tzinfo=UTC),
            dt.datetime(2026, 8, 30, 9, tzinfo=UTC),
            planned_block_id=planned.id,
        )
        db.add(first)
        db.commit()
        db.add(
            _actual(
                task_type.id,
                dt.datetime(2026, 8, 30, 10, tzinfo=UTC),
                dt.datetime(2026, 8, 30, 11, tzinfo=UTC),
                planned_block_id=planned.id,
            )
        )
        with pytest.raises(IntegrityError):
            db.commit()


def test_cross_midnight_actual_projects_without_splitting_and_totals_intersections(client):
    task_type = client.post("/task-types", json={"name": "Night work"}).json()
    with Session(get_engine()) as db:
        block = _actual(
            task_type["id"],
            dt.datetime(2026, 8, 30, 23, 30, tzinfo=UTC),
            dt.datetime(2026, 8, 31, 0, 45, tzinfo=UTC),
        )
        db.add(block)
        db.commit()
        block_id = block.id

    first = client.get("/days/2026-08-30/preview").json()
    second = client.get("/days/2026-08-31/preview").json()
    assert first["actual_minutes"] == 30
    assert second["actual_minutes"] == 45
    assert first["actual_blocks"][0]["actual_block"]["id"] == block_id
    assert second["actual_blocks"][0]["actual_block"]["id"] == block_id
    assert (first["actual_blocks"][0]["start_minute"], first["actual_blocks"][0]["end_minute"]) == (1410, 1440)
    assert (second["actual_blocks"][0]["start_minute"], second["actual_blocks"][0]["end_minute"]) == (0, 45)
    assert client.get("/days/2026-08-30/summary").json()["actual_minutes"] == 30
    assert client.get("/days/2026-08-31/summary").json()["actual_minutes"] == 45

    with Session(get_engine()) as db:
        assert db.query(TimeBlock).filter(TimeBlock.id == block_id).count() == 1


def test_active_actual_and_explicit_correspondence_are_readable(client):
    task_type = client.post("/task-types", json={"name": "Active work"}).json()
    planned_day = client.post(
        "/days/2026-08-30/blocks",
        json={
            "lane": "planned",
            "task_type_id": task_type["id"],
            "start_minute": 600,
            "end_minute": 660,
        },
    ).json()
    planned_id = planned_day["planned_blocks"][0]["id"]
    with Session(get_engine()) as db:
        actual = _actual(
            task_type["id"],
            dt.datetime(2026, 8, 30, 10, tzinfo=UTC),
            None,
            planned_block_id=planned_id,
        )
        db.add(actual)
        db.commit()
        actual_id = actual.id

    active = client.get("/actual-blocks/active")
    assert active.status_code == 200
    assert active.json()["id"] == actual_id
    assert active.json()["end_at"] is None
    assert active.json()["planned_block_id"] == planned_id

    day = client.get("/days/2026-08-30").json()
    assert day["planned_blocks"][0]["actual_block_id"] == actual_id
    assert day["planned_blocks"][0]["start_minute"] == 600
    assert day["planned_blocks"][0]["end_minute"] == 660
    assert active.json()["start_at"] == "2026-08-30T10:00:00"


def test_definitive_openapi_contract_has_no_allocation_or_time_completion_truth(client):
    schemas = client.get("/openapi.json").json()["components"]["schemas"]
    task_properties = schemas["TaskRead"]["properties"]
    subtask_properties = schemas["SubtaskRead"]["properties"]
    time_block_properties = schemas["TimeBlockRead"]["properties"]

    assert {"allocation_total", "allocation_completed", "allocations"}.isdisjoint(
        task_properties
    )
    assert "time_completed" not in time_block_properties
    assert {"status", "ready_to_plan", "completed_at", "deadline_at"}.isdisjoint(
        subtask_properties
    )
    assert {"checked", "effectively_resolved"} <= set(subtask_properties)


def test_task_occurrence_identity_and_quota_session_task_contracts_remain_distinct(client):
    today = client.get("/health").json()["today"]
    scheduled = client.post(
        "/recurring-templates",
        json={
            "title": "Daily",
            "mode": "scheduled",
            "frequency": "daily",
            "interval": 1,
            "start_date": today,
        },
    )
    assert scheduled.status_code == 201, scheduled.text
    occurrence = next(
        item for item in client.get("/tasks").json()["items"] if item["title"] == "Daily"
    )
    assert occurrence["occurrence"] == {
        "id": occurrence["occurrence"]["id"],
        "recurring_task_series_id": scheduled.json()["id"],
        "occurrence_key": occurrence["occurrence_key"],
    }

    quota = client.post(
        "/recurring-templates",
        json={
            "title": "Quota",
            "mode": "quota",
            "frequency": "weekly",
            "interval": 1,
            "quota_count": 2,
            "start_date": today,
        },
    )
    assert quota.status_code == 201, quota.text
    tracker = next(
        item for item in client.get("/tasks").json()["items"] if item["title"] == "Quota"
    )
    assert tracker["subtasks"] == []
    assert len(tracker["session_tasks"]) == 2
    assert all("status" in session for session in tracker["session_tasks"])
