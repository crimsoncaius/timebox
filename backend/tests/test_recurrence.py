from __future__ import annotations

import datetime as dt

import pytest
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.core.config import get_settings
from app.models.battle_plan import (
    RecurrenceOccurrence,
    RecurringPlannedBlockRealization,
    RecurringPreplanningSlot,
    RecurringTemplate,
    Task,
    TaskStatus,
)
from app.models.battle_plan import RecurrenceFrequency, RecurrenceMode
from app.schemas.battle_plan import RecurrencePreviewRequest
from app.services.recurrence_service import iter_windows
from app.services.recurrence_service import synchronize


def rule(**changes):
    values = {
        "mode": "scheduled",
        "frequency": "daily",
        "interval": 1,
        "weekdays": [],
        "month_day": None,
        "quota_count": None,
        "start_date": dt.date(2025, 1, 1),
        "end_date": None,
        "cycle_limit": None,
    }
    values.update(changes)
    return RecurrencePreviewRequest(**values)


def test_calendar_rules_cover_intervals_weekdays_month_fallback_and_limits():
    monthly = iter_windows(rule(
        frequency=RecurrenceFrequency.monthly,
        month_day=31,
        start_date=dt.date(2025, 1, 31),
    ), dt.date(2025, 4, 30))
    assert [item.start for item in monthly] == [
        dt.date(2025, 1, 31), dt.date(2025, 2, 28),
        dt.date(2025, 3, 31), dt.date(2025, 4, 30),
    ]

    weekly = iter_windows(rule(
        frequency=RecurrenceFrequency.weekly,
        interval=2,
        weekdays=[0, 4],
        start_date=dt.date(2025, 1, 1),
        cycle_limit=3,
    ), dt.date(2025, 2, 28))
    assert [item.start for item in weekly] == [
        dt.date(2025, 1, 3), dt.date(2025, 1, 13), dt.date(2025, 1, 17),
    ]


def test_quota_week_boundaries_follow_setting():
    quota = rule(
        mode=RecurrenceMode.quota,
        frequency=RecurrenceFrequency.weekly,
        quota_count=3,
        start_date=dt.date(2025, 1, 8),
    )
    monday = iter_windows(quota, dt.date(2025, 1, 20), "monday")
    sunday = iter_windows(quota, dt.date(2025, 1, 20), "sunday")
    assert monday[0].start == dt.date(2025, 1, 8)
    assert monday[0].end == dt.date(2025, 1, 12)
    assert sunday[0].end == dt.date(2025, 1, 11)
    assert monday[1].start == dt.date(2025, 1, 13)
    assert sunday[1].start == dt.date(2025, 1, 12)


def _daily_body(today: str, **changes):
    body = {
        "title": "Daily review",
        "mode": "scheduled",
        "frequency": "daily",
        "interval": 1,
        "start_date": today,
        "checklist_titles": ["Inbox", "Calendar"],
    }
    body.update(changes)
    return body


def _planned_block(client, date: dt.date, task: dict, task_type_id: int) -> dict:
    response = client.post(f"/days/{date.isoformat()}/blocks", json={
        "lane": "planned",
        "task_type_id": task_type_id,
        "task_id": task["id"],
        "start_minute": 600,
        "end_minute": 660,
    })
    assert response.status_code == 200, response.text
    return response.json()["planned_blocks"][-1]


def _generated_root_count(template_id: int) -> int:
    with Session(get_engine()) as db:
        return db.execute(
            select(func.count(Task.id)).where(
                Task.recurring_template_id == template_id,
                Task.parent_id.is_(None),
            )
        ).scalar_one()


def _task_for_planning_date(client, date: dt.date, template_id: int) -> dict:
    return next(
        task for task in client.get(
            "/tasks", params={"planning_date": date.isoformat()}
        ).json()["items"]
        if task["recurring_template_id"] == template_id
    )


def _generated_preplanning_case(client) -> tuple[dt.date, dict, dict]:
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Generated routine"}).json()
    template = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(),
        checklist_titles=["Keep this checkpoint"],
        task_type_id=task_type["id"],
        preplanning_schedule={
            "slots": [{"start_minute": 540, "end_minute": 600}],
        },
    )).json()
    block = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"][0]
    return today, template, block


def _edit_preplanning_slot(client, template: dict) -> dict:
    slot = template["preplanning_schedule"]["slots"][0]
    response = client.patch(f"/recurring-templates/{template['id']}", json={
        "checklist_titles": ["Replacement checkpoint"],
        "preplanning_schedule": {"slots": [{
            "key": slot["key"],
            "start_minute": 720,
            "end_minute": 780,
        }]},
    })
    assert response.status_code == 200, response.text
    return response.json()


def test_time_edit_customizes_one_generated_planned_block_while_untouched_peer_reconciles(
    client,
):
    today, template, block = _generated_preplanning_case(client)

    edited_block = client.patch(
        f"/days/{today.isoformat()}/blocks/{block['id']}",
        json={"start_minute": 630, "end_minute": 690},
    )
    assert edited_block.status_code == 200, edited_block.text
    _edit_preplanning_slot(client, template)

    customized = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]
    untouched = client.get(
        f"/days/{(today + dt.timedelta(days=1)).isoformat()}"
    ).json()["planned_blocks"]
    assert [(item["id"], item["start_minute"], item["end_minute"]) for item in customized] == [
        (block["id"], 630, 690)
    ]
    assert [(item["start_minute"], item["end_minute"]) for item in untouched] == [(720, 780)]
    customized_task = _task_for_planning_date(client, today, template["id"])
    untouched_task = _task_for_planning_date(
        client, today + dt.timedelta(days=1), template["id"]
    )
    assert [item["title"] for item in customized_task["subtasks"]] == [
        "Keep this checkpoint"
    ]
    assert [item["title"] for item in untouched_task["subtasks"]] == [
        "Replacement checkpoint"
    ]


@pytest.mark.parametrize(
    ("patch", "field", "expected"),
    [
        pytest.param({"name": "My exception"}, "name", "My exception", id="Block Name"),
        pytest.param({"note": "Bring the draft"}, "note", "Bring the draft", id="note"),
    ],
)
def test_text_edit_customizes_generated_planned_block(client, patch, field, expected):
    today, template, block = _generated_preplanning_case(client)

    changed = client.patch(
        f"/days/{today.isoformat()}/blocks/{block['id']}", json=patch
    )
    assert changed.status_code == 200, changed.text
    _edit_preplanning_slot(client, template)
    for _ in range(2):
        assert client.get(f"/recurring-templates/{template['id']}").status_code == 200

    planned = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]
    assert len(planned) == 1
    assert planned[0]["id"] == block["id"]
    assert planned[0][field] == expected
    assert (planned[0]["start_minute"], planned[0]["end_minute"]) == (540, 600)


def test_task_type_reassignment_customizes_generated_planned_block(client):
    today, template, block = _generated_preplanning_case(client)
    reassigned = client.post("/task-types", json={"name": "Reassigned work"}).json()

    changed = client.patch(
        f"/days/{today.isoformat()}/blocks/{block['id']}",
        json={"task_type_id": reassigned["id"]},
    )
    assert changed.status_code == 200, changed.text
    _edit_preplanning_slot(client, template)

    planned = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]
    assert len(planned) == 1
    assert planned[0]["id"] == block["id"]
    assert planned[0]["task_type_id"] == reassigned["id"]
    assert (planned[0]["start_minute"], planned[0]["end_minute"]) == (540, 600)


def test_battle_plan_task_reassignment_customizes_generated_planned_block(client):
    today, template, block = _generated_preplanning_case(client)
    reassigned = client.post("/tasks", json={"title": "One-off alternative"}).json()

    changed = client.patch(
        f"/days/{today.isoformat()}/blocks/{block['id']}",
        json={"task_id": reassigned["id"]},
    )
    assert changed.status_code == 200, changed.text
    _edit_preplanning_slot(client, template)

    planned = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]
    assert len(planned) == 1
    assert planned[0]["id"] == block["id"]
    assert planned[0]["task_id"] == reassigned["id"]
    assert (planned[0]["start_minute"], planned[0]["end_minute"]) == (540, 600)


def test_deleting_generated_planned_block_leaves_durable_tombstone_on_synchronization(
    client,
):
    today, template, block = _generated_preplanning_case(client)

    deleted = client.delete(f"/days/{today.isoformat()}/blocks/{block['id']}")
    assert deleted.status_code == 200, deleted.text
    for _ in range(2):
        assert client.get(f"/recurring-templates/{template['id']}").status_code == 200

    assert client.get(f"/days/{today.isoformat()}").json()["planned_blocks"] == []
    untouched = client.get(
        f"/days/{(today + dt.timedelta(days=1)).isoformat()}"
    ).json()["planned_blocks"]
    assert [(item["start_minute"], item["end_minute"]) for item in untouched] == [(540, 600)]


def test_removed_and_readded_slot_does_not_revive_deleted_generated_planned_block(client):
    today, template, block = _generated_preplanning_case(client)
    original_slot_key = template["preplanning_schedule"]["slots"][0]["key"]
    assert client.delete(
        f"/days/{today.isoformat()}/blocks/{block['id']}"
    ).status_code == 200

    removed = client.patch(
        f"/recurring-templates/{template['id']}",
        json={"preplanning_schedule": None},
    )
    assert removed.status_code == 200, removed.text
    readded = client.patch(
        f"/recurring-templates/{template['id']}",
        json={
            "preplanning_schedule": {
                "slots": [{"start_minute": 540, "end_minute": 600}]
            }
        },
    )
    assert readded.status_code == 200, readded.text
    assert readded.json()["preplanning_schedule"]["slots"][0]["key"] == original_slot_key
    for _ in range(2):
        assert client.get(f"/recurring-templates/{template['id']}").status_code == 200

    assert client.get(f"/days/{today.isoformat()}").json()["planned_blocks"] == []
    untouched = client.get(
        f"/days/{(today + dt.timedelta(days=1)).isoformat()}"
    ).json()["planned_blocks"]
    assert [(item["start_minute"], item["end_minute"]) for item in untouched] == [(540, 600)]


def test_keyless_readd_rejects_ambiguous_removed_slot_exception_histories(client):
    today, template, first_block = _generated_preplanning_case(client)
    first_slot = template["preplanning_schedule"]["slots"][0]
    assert client.delete(
        f"/days/{today.isoformat()}/blocks/{first_block['id']}"
    ).status_code == 200
    assert client.patch(
        f"/recurring-templates/{template['id']}",
        json={"preplanning_schedule": None},
    ).status_code == 200

    second = client.patch(
        f"/recurring-templates/{template['id']}",
        json={
            "preplanning_schedule": {
                "slots": [{"start_minute": 720, "end_minute": 780}]
            }
        },
    )
    assert second.status_code == 200, second.text
    second_slot = second.json()["preplanning_schedule"]["slots"][0]
    assert second_slot["key"] != first_slot["key"]
    converged = client.patch(
        f"/recurring-templates/{template['id']}",
        json={
            "preplanning_schedule": {
                "slots": [{
                    "key": second_slot["key"],
                    "start_minute": 540,
                    "end_minute": 600,
                }]
            }
        },
    )
    assert converged.status_code == 200, converged.text
    assert client.patch(
        f"/recurring-templates/{template['id']}",
        json={"preplanning_schedule": None},
    ).status_code == 200

    ambiguous = client.patch(
        f"/recurring-templates/{template['id']}",
        json={
            "preplanning_schedule": {
                "slots": [{"start_minute": 540, "end_minute": 600}]
            }
        },
    )

    assert ambiguous.status_code == 422, ambiguous.text
    assert "ambiguous" in ambiguous.json()["detail"].lower()
    assert client.get(f"/recurring-templates/{template['id']}").json()[
        "preplanning_schedule"
    ] is None
    assert client.get(f"/days/{today.isoformat()}").json()["planned_blocks"] == []


def test_moving_generated_planned_block_to_another_day_customizes_only_its_realization(
    client,
):
    today, template, block = _generated_preplanning_case(client)
    target_date = today + dt.timedelta(days=1)

    moved = client.patch(
        f"/days/{today.isoformat()}/blocks/{block['id']}",
        json={
            "date": target_date.isoformat(),
            "start_minute": 630,
            "end_minute": 690,
        },
    )
    assert moved.status_code == 200, moved.text
    _edit_preplanning_slot(client, template)
    for _ in range(2):
        assert client.get(f"/recurring-templates/{template['id']}").status_code == 200

    assert client.get(f"/days/{today.isoformat()}").json()["planned_blocks"] == []
    target_blocks = client.get(
        f"/days/{target_date.isoformat()}"
    ).json()["planned_blocks"]
    assert len(target_blocks) == 2
    assert (target_blocks[0]["id"], target_blocks[0]["start_minute"], target_blocks[0]["end_minute"]) == (
        block["id"], 630, 690,
    )
    assert (target_blocks[1]["start_minute"], target_blocks[1]["end_minute"]) == (720, 780)
    assert target_blocks[1]["id"] != block["id"]


def test_scheduled_series_preplanning_schedule_materializes_attached_planned_blocks(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Writing"}).json()
    body = _daily_body(
        today.isoformat(),
        checklist_titles=[],
        task_type_id=task_type["id"],
        preplanning_schedule={
            "slots": [{"start_minute": 540, "end_minute": 600}],
        },
    )

    created = client.post("/recurring-templates", json=body)

    assert created.status_code == 201, created.text
    schedule = created.json()["preplanning_schedule"]
    assert len(schedule["slots"]) == 1
    assert schedule["slots"][0]["start_minute"] == 540
    assert schedule["slots"][0]["end_minute"] == 600
    assert schedule["slots"][0]["weekday"] is None
    assert client.get(f"/recurring-templates/{created.json()['id']}").json()[
        "preplanning_schedule"
    ] == schedule

    planned_block_ids = []
    task_ids = []
    for offset in range(8):
        day_date = today + dt.timedelta(days=offset)
        occurrence = _task_for_planning_date(client, day_date, created.json()["id"])
        day = client.get(f"/days/{day_date.isoformat()}").json()
        planned = day["planned_blocks"]
        assert len(planned) == 1
        assert planned[0]["task_id"] == occurrence["id"]
        assert planned[0]["task_type_id"] == task_type["id"]
        assert (planned[0]["start_minute"], planned[0]["end_minute"]) == (540, 600)
        assert day["actual_blocks"] == []
        assert occurrence["status"] == "open"
        assert occurrence["completed_at"] is None
        assert occurrence["ready_to_plan"] is False
        planned_block_ids.append(planned[0]["id"])
        task_ids.append(occurrence["id"])

    with Session(get_engine()) as db:
        realizations = list(
            db.execute(select(RecurringPlannedBlockRealization)).scalars()
        )
        realization_task_ids = set(db.execute(
            select(RecurrenceOccurrence.task_id)
            .join(
                RecurringPlannedBlockRealization,
                RecurringPlannedBlockRealization.occurrence_id == RecurrenceOccurrence.id,
            )
        ).scalars())
        assert len(realizations) == 8
        assert {item.planned_block_id for item in realizations} == set(planned_block_ids)
        assert {item.state.value for item in realizations} == {"untouched"}
        assert realization_task_ids == set(task_ids)


def test_confirmed_historical_backfill_does_not_preplan_past_task_occurrences(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Reflection"}).json()
    created = client.post("/recurring-templates", json=_daily_body(
        (today - dt.timedelta(days=2)).isoformat(),
        task_type_id=task_type["id"],
        checklist_titles=[],
        confirm_backfill=True,
        preplanning_schedule={"slots": [{"start_minute": 1260, "end_minute": 1320}]},
    ))

    assert created.status_code == 201, created.text
    for offset in (-2, -1):
        historical = today + dt.timedelta(days=offset)
        assert client.get(f"/days/{historical.isoformat()}").json()["planned_blocks"] == []
    assert len(client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]) == 1


def test_replacing_a_tombstoned_slot_retains_its_logical_realization_identity(client):
    today = client.get("/health").json()["today"]
    created = client.post("/recurring-templates", json=_daily_body(
        today,
        checklist_titles=[],
        preplanning_schedule={"slots": [{"start_minute": 540, "end_minute": 600}]},
    ))
    assert created.status_code == 201, created.text

    with Session(get_engine()) as db:
        slot = db.execute(select(RecurringPreplanningSlot)).scalar_one()
        realization = db.execute(
            select(RecurringPlannedBlockRealization).where(
                RecurringPlannedBlockRealization.slot_id == slot.id
            )
        ).scalars().first()
        assert realization is not None
        logical_slot_key = slot.slot_key
        realization_id = realization.id
        slot.removed_at = dt.datetime.now(dt.timezone.utc)
        db.commit()
        db.add(RecurringPreplanningSlot(
            template_id=slot.template_id,
            slot_key="replacement-slot-key",
            position=slot.position,
            weekday=slot.weekday,
            start_minute=slot.start_minute,
            end_minute=slot.end_minute,
        ))
        db.commit()
        db.delete(slot)
        db.commit()

    with Session(get_engine()) as db:
        retained = db.get(RecurringPlannedBlockRealization, realization_id)
        assert retained is not None
        assert retained.slot_id is None
        assert retained.slot_key == logical_slot_key


@pytest.mark.parametrize("frequency", ["daily", "weekly", "monthly"])
def test_one_preplanning_slot_uses_each_scheduled_recurrence_date(client, frequency):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Routine"}).json()
    rule_fields = {
        "weekdays": [today.weekday()] if frequency == "weekly" else [],
        "month_day": today.day if frequency == "monthly" else None,
    }
    slot = {
        "start_minute": 720,
        "end_minute": 750,
        "weekday": today.weekday() if frequency == "weekly" else None,
    }

    created = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(),
        frequency=frequency,
        task_type_id=task_type["id"],
        checklist_titles=[],
        preplanning_schedule={"slots": [slot]},
        **rule_fields,
    ))

    assert created.status_code == 201, created.text
    occurrence = _task_for_planning_date(client, today, created.json()["id"])
    planned = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]
    assert [(item["task_id"], item["start_minute"], item["end_minute"]) for item in planned] == [
        (occurrence["id"], 720, 750)
    ]


def test_editing_a_preplanning_slot_updates_current_and_future_planned_blocks(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    created = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(),
        checklist_titles=[],
        preplanning_schedule={"slots": [{"start_minute": 540, "end_minute": 600}]},
    ))
    assert created.status_code == 201, created.text
    slot = created.json()["preplanning_schedule"]["slots"][0]

    edited = client.patch(f"/recurring-templates/{created.json()['id']}", json={
        "preplanning_schedule": {"slots": [{
            "key": slot["key"],
            "start_minute": 630,
            "end_minute": 690,
        }]},
    })

    assert edited.status_code == 200, edited.text
    assert edited.json()["preplanning_schedule"]["slots"] == [{
        **slot,
        "start_minute": 630,
        "end_minute": 690,
    }]
    for offset in (0, 1):
        planned = client.get(f"/days/{(today + dt.timedelta(days=offset)).isoformat()}").json()[
            "planned_blocks"
        ]
        assert [(item["start_minute"], item["end_minute"]) for item in planned] == [(630, 690)]


def test_swapping_two_keyed_slot_times_reconciles_atomically_and_idempotently(client):
    today = client.get("/health").json()["today"]
    created = client.post("/recurring-templates", json=_daily_body(
        today,
        checklist_titles=[],
        preplanning_schedule={"slots": [
            {"start_minute": 480, "end_minute": 540},
            {"start_minute": 960, "end_minute": 1020},
        ]},
    )).json()
    first, second = created["preplanning_schedule"]["slots"]

    edited = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": {"slots": [
            {"key": first["key"], "start_minute": 960, "end_minute": 1020},
            {"key": second["key"], "start_minute": 480, "end_minute": 540},
        ]},
    })

    assert edited.status_code == 200, edited.text
    expected = [(480, 540), (960, 1020)]
    assert [
        (item["start_minute"], item["end_minute"])
        for item in client.get(f"/days/{today}").json()["planned_blocks"]
    ] == expected
    for _ in range(2):
        assert client.get(f"/recurring-templates/{created['id']}").status_code == 200
        assert [
            (item["start_minute"], item["end_minute"])
            for item in client.get(f"/days/{today}").json()["planned_blocks"]
        ] == expected


def test_reordering_two_keyed_slots_persists_requested_order(client):
    today = client.get("/health").json()["today"]
    created = client.post("/recurring-templates", json=_daily_body(
        today,
        checklist_titles=[],
        preplanning_schedule={"slots": [
            {"start_minute": 480, "end_minute": 540},
            {"start_minute": 960, "end_minute": 1020},
        ]},
    )).json()
    first, second = created["preplanning_schedule"]["slots"]

    edited = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": {"slots": [
            {"key": second["key"], "start_minute": 960, "end_minute": 1020},
            {"key": first["key"], "start_minute": 480, "end_minute": 540},
        ]},
    })

    assert edited.status_code == 200, edited.text
    assert [slot["key"] for slot in edited.json()["preplanning_schedule"]["slots"]] == [
        second["key"], first["key"]
    ]
    assert [slot["position"] for slot in edited.json()["preplanning_schedule"]["slots"]] == [0, 1]


def test_duplicate_keyed_slots_are_rejected_without_changing_schedule_or_blocks(client):
    today = client.get("/health").json()["today"]
    created = client.post("/recurring-templates", json=_daily_body(
        today,
        checklist_titles=[],
        preplanning_schedule={"slots": [
            {"start_minute": 480, "end_minute": 540},
            {"start_minute": 960, "end_minute": 1020},
        ]},
    )).json()
    slots = created["preplanning_schedule"]["slots"]

    response = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": {"slots": [
            {"key": slots[0]["key"], "start_minute": 480, "end_minute": 540},
            {"key": slots[0]["key"], "start_minute": 960, "end_minute": 1020},
        ]},
    })

    assert response.status_code == 422, response.text
    assert client.get(f"/recurring-templates/{created['id']}").json()[
        "preplanning_schedule"
    ]["slots"] == slots
    assert [
        (item["start_minute"], item["end_minute"])
        for item in client.get(f"/days/{today}").json()["planned_blocks"]
    ] == [(480, 540), (960, 1020)]


def test_adding_a_second_preplanning_slot_is_idempotent_across_series_and_day_reads(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    created = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(),
        checklist_titles=[],
        preplanning_schedule={"slots": [{"start_minute": 480, "end_minute": 540}]},
    )).json()
    first = created["preplanning_schedule"]["slots"][0]

    edited = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": {"slots": [
            {"key": first["key"], "start_minute": 480, "end_minute": 540},
            {"start_minute": 960, "end_minute": 1020},
        ]},
    })

    assert edited.status_code == 200, edited.text
    slots = edited.json()["preplanning_schedule"]["slots"]
    assert [(slot["position"], slot["start_minute"], slot["end_minute"]) for slot in slots] == [
        (0, 480, 540),
        (1, 960, 1020),
    ]
    for _ in range(2):
        assert client.get(f"/recurring-templates/{created['id']}").json()[
            "preplanning_schedule"
        ]["slots"] == slots
        planned = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]
        assert [(item["start_minute"], item["end_minute"]) for item in planned] == [
            (480, 540),
            (960, 1020),
        ]


def test_removing_a_slot_keeps_past_planned_blocks_and_removes_current_and_future_ones(
    client, monkeypatch
):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    created = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(),
        checklist_titles=[],
        preplanning_schedule={"slots": [
            {"start_minute": 480, "end_minute": 540},
            {"start_minute": 960, "end_minute": 1020},
        ]},
    )).json()
    retained = created["preplanning_schedule"]["slots"][1]
    simulated_today = today + dt.timedelta(days=1)
    monkeypatch.setattr(
        "app.services.recurrence.templates.today_in_tz",
        lambda _timezone: simulated_today,
    )

    edited = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": {"slots": [{
            "key": retained["key"],
            "start_minute": retained["start_minute"],
            "end_minute": retained["end_minute"],
        }]},
    })

    assert edited.status_code == 200, edited.text
    assert [slot["key"] for slot in edited.json()["preplanning_schedule"]["slots"]] == [
        retained["key"]
    ]
    past = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]
    assert [(item["start_minute"], item["end_minute"]) for item in past] == [
        (480, 540),
        (960, 1020),
    ]
    for offset in (1, 2):
        planned = client.get(f"/days/{(today + dt.timedelta(days=offset)).isoformat()}").json()[
            "planned_blocks"
        ]
        assert [(item["start_minute"], item["end_minute"]) for item in planned] == [
            (960, 1020)
        ]

    cleared = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": None,
    })
    assert cleared.status_code == 200, cleared.text
    assert cleared.json()["preplanning_schedule"] is None
    assert len(client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]) == 2
    assert client.get(f"/days/{simulated_today.isoformat()}").json()["planned_blocks"] == []


def test_editing_a_weekly_slot_rejects_a_date_outside_the_recurrence_rule(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    selected_weekday = today.weekday()
    unselected_weekday = (selected_weekday + 1) % 7
    created = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(),
        frequency="weekly",
        weekdays=[selected_weekday],
        checklist_titles=[],
        preplanning_schedule={"slots": [{
            "weekday": selected_weekday,
            "start_minute": 540,
            "end_minute": 600,
        }]},
    )).json()
    slot = created["preplanning_schedule"]["slots"][0]

    response = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": {"slots": [{
            "key": slot["key"],
            "weekday": unselected_weekday,
            "start_minute": 540,
            "end_minute": 600,
        }]},
    })

    assert response.status_code == 422, response.text
    assert response.json()["detail"] == "A weekly pre-planning slot must use a selected recurrence weekday"
    persisted = client.get(f"/recurring-templates/{created['id']}").json()
    assert persisted["preplanning_schedule"]["slots"] == [slot]


def test_weekly_slots_place_planned_blocks_only_on_their_selected_recurrence_dates(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    next_date = today + dt.timedelta(days=1)
    created = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(),
        frequency="weekly",
        weekdays=sorted({today.weekday(), next_date.weekday()}),
        checklist_titles=[],
        preplanning_schedule={"slots": [{
            "weekday": today.weekday(),
            "start_minute": 480,
            "end_minute": 540,
        }]},
    )).json()
    first = created["preplanning_schedule"]["slots"][0]

    edited = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": {"slots": [
            {
                "key": first["key"],
                "weekday": today.weekday(),
                "start_minute": 480,
                "end_minute": 540,
            },
            {
                "weekday": next_date.weekday(),
                "start_minute": 960,
                "end_minute": 1020,
            },
        ]},
    })

    assert edited.status_code == 200, edited.text
    today_blocks = client.get(f"/days/{today.isoformat()}").json()["planned_blocks"]
    next_blocks = client.get(f"/days/{next_date.isoformat()}").json()["planned_blocks"]
    assert [(item["start_minute"], item["end_minute"]) for item in today_blocks] == [(480, 540)]
    assert [(item["start_minute"], item["end_minute"]) for item in next_blocks] == [(960, 1020)]


def test_editing_a_slot_never_overlaps_an_existing_planned_block(client):
    today = client.get("/health").json()["today"]
    task_type = client.post("/task-types", json={"name": "Occupied"}).json()
    created = client.post("/recurring-templates", json=_daily_body(
        today,
        checklist_titles=[],
        preplanning_schedule={"slots": [{"start_minute": 540, "end_minute": 600}]},
    )).json()
    slot = created["preplanning_schedule"]["slots"][0]
    occupied = client.post(f"/days/{today}/blocks", json={
        "lane": "planned",
        "task_type_id": task_type["id"],
        "start_minute": 630,
        "end_minute": 690,
    })
    assert occupied.status_code == 200, occupied.text

    edited = client.patch(f"/recurring-templates/{created['id']}", json={
        "preplanning_schedule": {"slots": [{
            "key": slot["key"],
            "start_minute": 630,
            "end_minute": 690,
        }]},
    })

    assert edited.status_code == 200, edited.text
    assert [(item["start_minute"], item["end_minute"]) for item in client.get(
        f"/days/{today}"
    ).json()["planned_blocks"]] == [(630, 690)]


@pytest.mark.parametrize(
    ("body_changes", "message"),
    [
        (
            {"preplanning_schedule": {"slots": [{"start_minute": 540, "end_minute": 560}]}},
            "Planned Blocks must be at least 30 minutes",
        ),
        (
            {"preplanning_schedule": {"slots": [{"start_minute": 600, "end_minute": 570}]}},
            "Invalid range",
        ),
        (
            {"preplanning_schedule": {"slots": [
                {"start_minute": 540, "end_minute": 600},
                {"start_minute": 570, "end_minute": 630},
            ]}},
            "cannot overlap",
        ),
        (
            {
                "frequency": "weekly",
                "weekdays": [0],
                "preplanning_schedule": {"slots": [
                    {"weekday": 1, "start_minute": 540, "end_minute": 600}
                ]},
            },
            "selected recurrence weekday",
        ),
        (
            {
                "mode": "quota",
                "frequency": "daily",
                "quota_count": 1,
                "preplanning_schedule": {"slots": [
                    {"start_minute": 540, "end_minute": 600}
                ]},
            },
            "scheduled Recurring Task Series",
        ),
    ],
)
def test_preplanning_schedule_rejects_invalid_planned_block_or_recurrence_position(
    client, body_changes, message
):
    today = client.get("/health").json()["today"]

    response = client.post(
        "/recurring-templates",
        json=_daily_body(today, checklist_titles=[], **body_changes),
    )

    assert response.status_code == 422
    assert message in response.text


def test_recurring_work_rejects_project_associations(client):
    today = client.get("/health").json()["today"]
    project = client.post("/projects", json={"name": "Launch"}).json()

    created = client.post("/recurring-templates", json=_daily_body(today, project_id=project["id"]))

    assert created.status_code == 422
    assert "project_id" in created.text

    template = client.post("/recurring-templates", json=_daily_body(today)).json()
    occurrence = _task_for_planning_date(client, dt.date.fromisoformat(today), template["id"])
    moved = client.patch(f"/tasks/{occurrence['id']}", json={"project_id": project["id"]})
    assert moved.status_code == 422, moved.text
    assert "Recurring work cannot belong to a Project" in moved.text


def test_generation_is_idempotent_and_copies_checklist(client):
    today = client.get("/health").json()["today"]
    created = client.post("/recurring-templates", json=_daily_body(today))
    assert created.status_code == 201, created.text
    assert created.json()["preplanning_schedule"] is None
    first = client.get("/tasks").json()["items"]
    second = client.get("/tasks").json()["items"]
    assert len(first) == len(second) == 1
    assert all(
        client.get(f"/days/{task['deadline_date']}").json()["planned_blocks"] == []
        for task in first
    )
    assert _generated_root_count(created.json()["id"]) == 8
    assert first[0]["ready_to_plan"] is False
    assert [child["title"] for child in first[0]["subtasks"]] == ["Inbox", "Calendar"]
    assert all("ready_to_plan" not in child for child in first[0]["subtasks"])
    assert first[0]["recurring_template_title"] == "Daily review"

    with Session(get_engine()) as db:
        legacy_implicit = db.get(Task, first[0]["id"])
        legacy_implicit.ready_to_plan = True
        db.commit()
    assert client.get("/tasks").json()["items"][0]["ready_to_plan"] is False

    opted_in = client.patch(f"/tasks/{first[0]['id']}", json={"ready_to_plan": True})
    assert opted_in.status_code == 200, opted_in.text
    assert client.get("/tasks").json()["items"][0]["ready_to_plan"] is True


def test_past_start_requires_confirmation_then_backfills(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    body = _daily_body((today - dt.timedelta(days=3)).isoformat(), checklist_titles=[])
    response = client.post("/recurring-templates", json=body)
    assert response.status_code == 409
    assert response.json()["detail"]["past_cycles"] == 3
    body["confirm_backfill"] = True
    assert client.post("/recurring-templates", json=body).status_code == 201
    template_id = client.get("/recurring-templates").json()[0]["id"]
    assert len(client.get("/tasks").json()["items"]) == 1
    assert _generated_root_count(template_id) == 11
    with Session(get_engine()) as db:
        skipped = db.execute(select(func.count(RecurrenceOccurrence.id)).where(
            RecurrenceOccurrence.template_id == template_id,
            RecurrenceOccurrence.skipped.is_(True),
        )).scalar_one()
    assert skipped == 3


def test_quota_sessions_drive_parent_and_cannot_be_scheduled_early(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "exercise"}).json()
    response = client.post("/recurring-templates", json={
        "title": "Gym", "mode": "quota", "frequency": "weekly", "interval": 1,
        "quota_count": 3, "start_date": today.isoformat(),
        "task_type_id": task_type["id"],
    })
    assert response.status_code == 201, response.text
    parent = client.get("/tasks").json()["items"][0]
    assert parent["recurrence_kind"] == "quota_parent"
    assert parent["quota_completed"] == 0
    assert [child["title"] for child in parent["session_tasks"]] == ["Session 1", "Session 2", "Session 3"]
    assert all(not child["ready_to_plan"] for child in parent["session_tasks"])

    session = parent["session_tasks"][0]
    too_early = dt.date.fromisoformat(session["quota_period_start"]) - dt.timedelta(days=1)
    blocked = client.post(f"/days/{too_early.isoformat()}/blocks", json={
        "lane": "planned", "task_type_id": task_type["id"], "task_id": session["id"],
        "start_minute": 600, "end_minute": 630,
    })
    assert blocked.status_code == 422

    assert client.post(f"/tasks/{session['id']}/complete").status_code == 200
    refreshed = client.get("/tasks").json()["items"][0]
    assert refreshed["status"] == "in_progress"
    assert refreshed["quota_completed"] == 1
    for child in refreshed["session_tasks"][1:]:
        client.post(f"/tasks/{child['id']}/complete")
    assert client.get("/tasks").json()["items"] == []
    with Session(get_engine()) as db:
        assert db.get(Task, parent["id"]).status == TaskStatus.completed


def test_quota_series_edit_still_propagates_fields_to_unoverridden_sessions(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json={
        "title": "Practice",
        "description": "Original guidance",
        "mode": "quota",
        "frequency": "weekly",
        "interval": 1,
        "quota_count": 2,
        "start_date": today,
    }).json()
    tracker = client.get("/tasks").json()["items"][0]
    customized, inherited = tracker["session_tasks"]
    assert client.patch(
        f"/tasks/{customized['id']}", json={"description": "My session note"}
    ).status_code == 200

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "description": "Updated guidance", "urgency": "high",
    })

    assert changed.status_code == 200, changed.text
    refreshed = client.get("/tasks").json()["items"][0]
    sessions = {session["id"]: session for session in refreshed["session_tasks"]}
    assert refreshed["description"] == "Updated guidance"
    assert sessions[customized["id"]]["description"] == "My session note"
    assert sessions[customized["id"]]["urgency"] == "high"
    assert sessions[inherited["id"]]["description"] == "Updated guidance"
    assert sessions[inherited["id"]]["urgency"] == "high"


def test_template_edit_honors_task_field_override(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today, checklist_titles=[])).json()
    tasks = client.get("/tasks").json()["items"]
    custom = tasks[0]
    client.patch(f"/tasks/{custom['id']}", json={"title": "My custom title"})
    updated = client.patch(f"/recurring-templates/{template['id']}", json={"title": "New title"})
    assert updated.status_code == 200, updated.text
    refreshed = client.get("/tasks").json()["items"]
    by_id = {item["id"]: item for item in refreshed}
    assert by_id[custom["id"]]["title"] == "My custom title"
    assert all(item["title"] == "New title" for item in refreshed if item["id"] != custom["id"])


def test_schedule_edit_starts_after_preserved_history_without_new_backfill(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    start = today - dt.timedelta(days=28)
    template = client.post("/recurring-templates", json={
        "title": "Weekly review", "mode": "scheduled", "frequency": "weekly",
        "interval": 1, "weekdays": [start.weekday()], "start_date": start.isoformat(),
        "confirm_backfill": True,
    }).json()
    before = [task for task in client.get("/tasks").json()["items"] if task["recurring_template_id"] == template["id"]]
    assert len(before) == 1
    assert _generated_root_count(template["id"]) == 6

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "daily", "weekdays": [], "interval": 1,
    })
    assert changed.status_code == 200, changed.text
    after = [task for task in client.get("/tasks").json()["items"] if task["recurring_template_id"] == template["id"]]
    # Four historical weekly occurrences plus today and the seven-day daily horizon.
    assert len(after) == 1
    assert _generated_root_count(template["id"]) == 12


def test_cadence_edit_starts_today_while_protected_future_occurrence_survives(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post("/recurring-templates", json={
        "title": "Weekly review", "mode": "scheduled", "frequency": "weekly",
        "interval": 1, "weekdays": [today.weekday()], "start_date": today.isoformat(),
        "checklist_titles": [],
    }).json()
    protected = _task_for_planning_date(client, today + dt.timedelta(days=7), template["id"])
    assert client.patch(
        f"/tasks/{protected['id']}", json={"description": "future exception"}
    ).status_code == 200

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "daily", "weekdays": [], "interval": 1,
    })

    assert changed.status_code == 200, changed.text
    with Session(get_engine()) as db:
        after = list(db.execute(select(Task).where(
            Task.recurring_template_id == template["id"], Task.parent_id.is_(None)
        )).scalars())
    assert {task.deadline_date for task in after} == {
        today + dt.timedelta(days=offset) for offset in range(8)
    }
    preserved = next(task for task in after if task.id == protected["id"])
    assert preserved.description == "future exception"


def test_new_cadence_is_anchored_to_application_local_today(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    original_start = today - dt.timedelta(days=27)
    template = client.post("/recurring-templates", json={
        "title": "Weekly review", "mode": "scheduled", "frequency": "weekly",
        "interval": 1, "weekdays": [today.weekday()],
        "start_date": original_start.isoformat(), "confirm_backfill": True,
        "checklist_titles": [],
    }).json()

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "daily", "weekdays": [], "interval": 2,
    })

    assert changed.status_code == 200, changed.text
    with Session(get_engine()) as db:
        current_and_future = sorted(db.execute(select(Task.deadline_date).where(
            Task.recurring_template_id == template["id"],
            Task.parent_id.is_(None),
            Task.deadline_date >= today,
        )).scalars())
    assert current_and_future == [
        today + dt.timedelta(days=offset) for offset in (0, 2, 4, 6)
    ]


def test_checklist_edit_rebuilds_only_pristine_future_occurrences(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today)).json()
    response = client.patch(f"/recurring-templates/{template['id']}", json={
        "checklist_titles": ["New first", "New second"],
    })
    assert response.status_code == 200, response.text
    tasks = client.get("/tasks").json()["items"]
    assert len(tasks) == 1
    assert [child["title"] for child in tasks[0]["subtasks"]] == ["New first", "New second"]
    for offset in range(1, 8):
        task = _task_for_planning_date(client, dt.date.fromisoformat(today) + dt.timedelta(days=offset), template["id"])
        assert [child["title"] for child in task["subtasks"]] == ["New first", "New second"]


def test_checklist_edit_keeps_protected_snapshot_and_rebuilds_unprotected_occurrence(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today)).json()
    protected = _task_for_planning_date(client, dt.date.fromisoformat(today), template["id"])
    rebuildable = _task_for_planning_date(client, dt.date.fromisoformat(today) + dt.timedelta(days=1), template["id"])
    assert client.post(f"/subtasks/{protected['subtasks'][0]['id']}/check").status_code == 200
    assert client.post(f"/subtasks/{protected['subtasks'][0]['id']}/uncheck").status_code == 200

    response = client.patch(f"/recurring-templates/{template['id']}", json={
        "checklist_titles": ["Inbox zero", "Plan tomorrow"],
    })

    assert response.status_code == 200, response.text
    after = {
        task["id"]: task for task in (
            _task_for_planning_date(client, dt.date.fromisoformat(today), template["id"]),
            _task_for_planning_date(client, dt.date.fromisoformat(today) + dt.timedelta(days=1), template["id"]),
        )
    }
    assert [child["title"] for child in after[protected["id"]]["subtasks"]] == ["Inbox", "Calendar"]
    assert after[protected["id"]]["subtasks"][0]["checked"] is False
    assert rebuildable["id"] in after
    assert [child["title"] for child in after[rebuildable["id"]]["subtasks"]] == [
        "Inbox zero", "Plan tomorrow",
    ]
    assert {child["id"] for child in after[rebuildable["id"]]["subtasks"]}.isdisjoint(
        {child["id"] for child in rebuildable["subtasks"]}
    )


def test_field_propagation_uses_stable_origin_not_moved_deadline(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post(
        "/recurring-templates", json=_daily_body(today.isoformat(), checklist_titles=[])
    ).json()
    occurrence = _task_for_planning_date(client, today + dt.timedelta(days=2), template["id"])
    moved = client.patch(
        f"/tasks/{occurrence['id']}",
        json={"deadline_date": (today - dt.timedelta(days=20)).isoformat()},
    )
    assert moved.status_code == 200, moved.text

    updated = client.patch(
        f"/recurring-templates/{template['id']}", json={"description": "series description"}
    )

    assert updated.status_code == 200, updated.text
    refreshed = _task_for_planning_date(client, today + dt.timedelta(days=2), template["id"])
    assert refreshed["deadline_date"] == (today - dt.timedelta(days=20)).isoformat()
    assert refreshed["description"] == "series description"


def test_planned_and_actual_state_survive_cadence_replacement(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Focus"}).json()
    template = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(), checklist_titles=[], task_type_id=task_type["id"]
    )).json()
    planned_task = _task_for_planning_date(client, today + dt.timedelta(days=1), template["id"])
    actual_task = _task_for_planning_date(client, today + dt.timedelta(days=2), template["id"])
    planned = _planned_block(client, today + dt.timedelta(days=1), planned_task, task_type["id"])
    actual = client.post("/actual-blocks", json={
        "task_type_id": task_type["id"],
        "task_id": actual_task["id"],
        "start_at": f"{today.isoformat()}T01:00:00Z",
        "end_at": f"{today.isoformat()}T02:00:00Z",
    })
    assert actual.status_code == 201, actual.text
    assert client.delete(f"/actual-blocks/{actual.json()['id']}").status_code == 204

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "weekly", "weekdays": [today.weekday()], "interval": 1,
    })

    assert changed.status_code == 200, changed.text
    with Session(get_engine()) as db:
        assert db.get(Task, planned_task["id"]) is not None
        assert db.get(Task, actual_task["id"]) is not None
    assert client.get(f"/days/{(today + dt.timedelta(days=1)).isoformat()}").json()["planned_blocks"][0]["id"] == planned["id"]
    assert client.get(f"/actual-blocks/{actual.json()['id']}").status_code == 404


def test_completion_reopen_and_undo_retain_each_occurrence_snapshot(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post(
        "/recurring-templates", json=_daily_body(today.isoformat())
    ).json()
    first = _task_for_planning_date(client, today, template["id"])
    second = _task_for_planning_date(client, today + dt.timedelta(days=1), template["id"])
    assert client.post(f"/subtasks/{second['subtasks'][0]['id']}/check").status_code == 200

    first_completion = client.post(f"/tasks/{first['id']}/complete")
    assert first_completion.status_code == 200, first_completion.text
    assert client.patch(f"/recurring-templates/{template['id']}", json={
        "title": "Later series title", "checklist_titles": ["Later template"],
    }).status_code == 200
    reopened = client.post(f"/tasks/{first['id']}/reopen")
    assert reopened.status_code == 200, reopened.text
    assert reopened.json()["title"] == "Daily review"
    assert [subtask["title"] for subtask in reopened.json()["subtasks"]] == ["Inbox", "Calendar"]

    second_completion = client.post(f"/tasks/{second['id']}/complete")
    assert second_completion.status_code == 200, second_completion.text
    assert client.patch(
        f"/recurring-templates/{template['id']}", json={"description": "later description"}
    ).status_code == 200
    undone = client.post(f"/tasks/{second['id']}/undo-completion", json={
        "undo_token": second_completion.json()["undo_token"],
    })
    assert undone.status_code == 200, undone.text
    assert undone.json()["description"] == ""
    assert undone.json()["subtasks"][0]["checked"] is True

    changed = client.patch(f"/recurring-templates/{template['id']}", json={
        "frequency": "weekly", "weekdays": [today.weekday()], "interval": 1,
    })
    assert changed.status_code == 200, changed.text
    with Session(get_engine()) as db:
        assert db.get(Task, first["id"]) is not None
        assert db.get(Task, second["id"]) is not None


def test_completing_one_occurrence_leaves_another_snapshot_and_plan_untouched(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Focus"}).json()
    template = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(), task_type_id=task_type["id"]
    )).json()
    first = _task_for_planning_date(client, today, template["id"])
    second = _task_for_planning_date(client, today + dt.timedelta(days=1), template["id"])
    assert client.post(f"/subtasks/{second['subtasks'][1]['id']}/check").status_code == 200
    planned = _planned_block(client, today + dt.timedelta(days=2), second, task_type["id"])

    completed = client.post(f"/tasks/{first['id']}/complete")

    assert completed.status_code == 200, completed.text
    assert client.get("/tasks").json()["items"] == []
    future = _task_for_planning_date(client, today + dt.timedelta(days=1), template["id"])
    assert future["status"] == "open"
    assert future["completed_at"] is None
    assert [subtask["checked"] for subtask in future["subtasks"]] == [False, True]
    assert client.get(f"/days/{(today + dt.timedelta(days=2)).isoformat()}").json()["planned_blocks"][0]["id"] == planned["id"]


def test_occurrence_tombstone_prevents_regeneration(client):
    today = client.get("/health").json()["today"]
    template = client.post(
        "/recurring-templates", json=_daily_body(today, checklist_titles=[])
    ).json()
    task_id = client.get("/tasks").json()["items"][0]["id"]
    client.delete(f"/tasks/{task_id}")
    client.delete(f"/tasks/{task_id}/permanent")
    remaining = client.get("/tasks").json()["items"]
    assert len(remaining) == 0
    assert _generated_root_count(template["id"]) == 7
    assert task_id not in {task["id"] for task in remaining}
    series = client.get(f"/recurring-templates/{template['id']}").json()
    assert f"scheduled:{today}" not in {window["key"] for window in series["upcoming"]}


def test_lifecycle_preserves_and_detaches_occurrences_on_permanent_series_delete(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today, checklist_titles=[])).json()
    first_task = client.get("/tasks").json()["items"][0]
    client.patch(f"/tasks/{first_task['id']}", json={"description": "keep me"})
    assert client.post(f"/recurring-templates/{template['id']}/pause").status_code == 200
    assert client.get("/recurring-templates?status=paused").json()[0]["status"] == "paused"
    assert client.post(f"/recurring-templates/{template['id']}/resume").status_code == 200
    assert client.post(f"/recurring-templates/{template['id']}/end").status_code == 200
    deleted = client.delete(f"/recurring-templates/{template['id']}")
    assert deleted.status_code == 204, deleted.text
    assert client.get(f"/recurring-templates/{template['id']}").status_code == 404
    assert client.get("/recurring-templates?status=ended").json() == []
    preserved = client.get("/tasks").json()["items"]
    assert len(preserved) == 1
    assert preserved[0]["id"] == first_task["id"]
    assert preserved[0]["recurring_template_id"] is None
    assert preserved[0]["description"] == "keep me"


@pytest.mark.parametrize("status", ["active", "paused"])
def test_permanent_series_delete_requires_ending_first(client, status):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json=_daily_body(today)).json()
    if status == "paused":
        assert client.post(f"/recurring-templates/{template['id']}/pause").status_code == 200
    response = client.delete(f"/recurring-templates/{template['id']}")
    assert response.status_code == 422, response.text
    assert client.get(f"/recurring-templates/{template['id']}").json()["status"] == status


def test_permanent_series_delete_missing_is_not_found(client):
    assert client.delete("/recurring-templates/99999").status_code == 404


def test_permanent_series_delete_preserves_task_history_and_skipped_visibility(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "History"}).json()
    template = client.post("/recurring-templates", json=_daily_body(
        (today - dt.timedelta(days=1)).isoformat(), confirm_backfill=True,
        task_type_id=task_type["id"],
    )).json()
    task = _task_for_planning_date(client, today, template["id"])
    assert client.post(f"/subtasks/{task['subtasks'][0]['id']}/check").status_code == 200
    future_date = today + dt.timedelta(days=2)
    future_task = _task_for_planning_date(client, future_date, template["id"])
    future_block = _planned_block(client, future_date, future_task, task_type["id"])
    actual = client.post("/actual-blocks", json={
        "task_type_id": task_type["id"], "task_id": task["id"],
        "start_at": f"{today.isoformat()}T01:00:00Z",
        "end_at": f"{today.isoformat()}T01:30:00Z", "note": "Durable actual history",
    })
    assert actual.status_code == 201, actual.text
    completed = client.post(f"/tasks/{task['id']}/complete")
    assert completed.status_code == 200, completed.text
    assert client.post(f"/recurring-templates/{template['id']}/end").status_code == 200
    actual_blocks = client.get(f"/days/{today.isoformat()}").json()["actual_blocks"]
    with Session(get_engine()) as db:
        task_ids = list(db.scalars(select(Task.id).where(Task.recurring_template_id == template["id"])))
        skipped_id = db.scalar(select(RecurrenceOccurrence.task_id).where(
            RecurrenceOccurrence.template_id == template["id"], RecurrenceOccurrence.skipped.is_(True),
        ))
        preserved_fields = (
            "id", "parent_id", "title", "description", "status", "completed_at", "checked",
            "archived_at", "deleted_at", "project_id", "task_type_id", "reminder_at",
            "deadline_date", "recurrence_kind", "occurrence_key", "ready_to_plan",
        )
        before = {
            task_id: {field: getattr(db.get(Task, task_id), field) for field in preserved_fields}
            for task_id in task_ids
        }
    assert skipped_id is not None

    response = client.delete(f"/recurring-templates/{template['id']}")

    assert response.status_code == 204, response.text
    with Session(get_engine()) as db:
        for task_id in task_ids:
            after = db.get(Task, task_id)
            assert after is not None
            assert after.recurring_template_id is None
            assert {field: getattr(after, field) for field in preserved_fields} == before[task_id]
    active_tasks = client.get("/tasks").json()["items"]
    assert all(item["occurrence"] is None for item in active_tasks)
    active_ids = {item["id"] for item in active_tasks}
    assert skipped_id not in active_ids
    assert future_task["id"] in active_ids
    assert client.get(f"/days/{future_date.isoformat()}").json()["planned_blocks"] == [future_block]
    assert client.get(f"/days/{today.isoformat()}").json()["actual_blocks"] == actual_blocks
    with Session(get_engine()) as db:
        assert db.get(RecurringTemplate, template["id"]) is None
        assert db.get(Task, skipped_id).occurrence.skipped is True
        assert db.get(Task, skipped_id).occurrence.template_id is None


def test_permanent_quota_series_delete_keeps_tracker_and_session_completion(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json={
        "title": "Practice", "mode": "quota", "frequency": "weekly", "interval": 1,
        "quota_count": 2, "start_date": today,
    }).json()
    tracker = client.get("/tasks").json()["items"][0]
    first, second = tracker["session_tasks"]
    assert client.post(f"/tasks/{first['id']}/complete").status_code == 200
    assert client.post(f"/recurring-templates/{template['id']}/end").status_code == 200
    assert client.delete(f"/recurring-templates/{template['id']}").status_code == 204
    detached = next(item for item in client.get("/tasks").json()["items"] if item["id"] == tracker["id"])
    assert detached["recurring_template_id"] is None
    assert detached["recurrence_kind"] == "quota_parent"
    assert detached["quota_completed"] == 1
    assert [session["id"] for session in detached["session_tasks"]] == [first["id"], second["id"]]
    assert all(session["recurring_template_id"] is None for session in detached["session_tasks"])
    assert client.post(f"/tasks/{second['id']}/complete").status_code == 200
    updated = next(item for item in client.get("/tasks").json()["items"] if item["id"] == tracker["id"])
    assert updated["quota_completed"] == 2


def test_pause_and_end_remove_only_untouched_future_occurrences(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Focus"}).json()
    template = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(), checklist_titles=[], task_type_id=task_type["id"]
    )).json()
    protected = _task_for_planning_date(client, today + dt.timedelta(days=3), template["id"])
    planned = _planned_block(client, today + dt.timedelta(days=3), protected, task_type["id"])

    paused = client.post(f"/recurring-templates/{template['id']}/pause")

    assert paused.status_code == 200, paused.text
    paused_tasks = [
        task for task in client.get("/tasks", params={
            "planning_date": (today + dt.timedelta(days=3)).isoformat(),
        }).json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    assert [task["id"] for task in paused_tasks] == [protected["id"]]
    assert client.post(f"/recurring-templates/{template['id']}/resume").status_code == 200
    assert client.post(f"/recurring-templates/{template['id']}/end").status_code == 200
    ended_tasks = [
        task for task in client.get("/tasks", params={
            "planning_date": (today + dt.timedelta(days=3)).isoformat(),
        }).json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    assert [task["id"] for task in ended_tasks] == [protected["id"]]
    assert client.get(f"/days/{(today + dt.timedelta(days=3)).isoformat()}").json()["planned_blocks"][0]["id"] == planned["id"]


def test_same_day_pause_resume_rematerializes_today(client):
    today = client.get("/health").json()["today"]
    template = client.post(
        "/recurring-templates",
        json=_daily_body(today, checklist_titles=[]),
    ).json()

    assert client.post(f"/recurring-templates/{template['id']}/pause").status_code == 200
    resumed = client.post(f"/recurring-templates/{template['id']}/resume")

    assert resumed.status_code == 200, resumed.text
    tasks = [
        task for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    ]
    assert len(tasks) == 1
    assert {task["deadline_date"] for task in tasks} >= {today}
    assert resumed.json()["next_occurrence"] == today


def test_resume_after_longer_pause_still_suppresses_through_resume_day(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    yesterday = today - dt.timedelta(days=1)
    template = client.post(
        "/recurring-templates",
        json=_daily_body(yesterday.isoformat(), checklist_titles=[], confirm_backfill=True),
    ).json()
    assert client.post(f"/recurring-templates/{template['id']}/pause").status_code == 200
    with Session(get_engine()) as db:
        row = db.get(RecurringTemplate, template["id"])
        row.paused_at = dt.datetime.combine(
            yesterday,
            dt.time(hour=12),
            tzinfo=dt.timezone.utc,
        )
        db.commit()

    resumed = client.post(f"/recurring-templates/{template['id']}/resume")

    assert resumed.status_code == 200, resumed.text
    dates = {
        task["deadline_date"] for task in client.get("/tasks").json()["items"]
        if task["recurring_template_id"] == template["id"]
    }
    assert yesterday.isoformat() not in dates
    assert today.isoformat() not in dates
    assert resumed.json()["next_occurrence"] == (today + dt.timedelta(days=1)).isoformat()


def test_default_series_skips_past_occurrences_but_keeps_one_current_row(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post("/recurring-templates", json=_daily_body(
        (today - dt.timedelta(days=2)).isoformat(),
        checklist_titles=[],
        confirm_backfill=True,
    )).json()

    visible = client.get("/tasks").json()["items"]

    assert len(visible) == 1
    assert visible[0]["deadline_date"] == today.isoformat()
    assert visible[0]["outstanding_occurrence_count"] == 1
    with Session(get_engine()) as db:
        outcomes = list(db.execute(
            select(RecurrenceOccurrence)
            .where(RecurrenceOccurrence.template_id == template["id"])
            .order_by(RecurrenceOccurrence.cycle_start)
        ).scalars())
    assert [outcome.skipped for outcome in outcomes[:3]] == [True, True, False]


def test_carry_over_series_groups_oldest_occurrence_with_outstanding_count(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post("/recurring-templates", json=_daily_body(
        (today - dt.timedelta(days=2)).isoformat(),
        checklist_titles=[],
        confirm_backfill=True,
        keep_unfinished_overdue=True,
    )).json()

    visible = client.get("/tasks").json()["items"]

    assert len(visible) == 1
    assert visible[0]["deadline_date"] == (today - dt.timedelta(days=2)).isoformat()
    assert visible[0]["outstanding_occurrence_count"] == 3
    assert client.get(f"/recurring-templates/{template['id']}").json()["keep_unfinished_overdue"] is True


def test_future_plan_protects_scheduled_occurrence_until_planned_day_passes(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Protected"}).json()
    template = client.post("/recurring-templates", json=_daily_body(
        today.isoformat(), checklist_titles=[], task_type_id=task_type["id"]
    )).json()
    current = client.get("/tasks").json()["items"][0]
    _planned_block(client, today + dt.timedelta(days=1), current, task_type["id"])

    with Session(get_engine()) as db:
        synchronize(db, get_settings(), today=today + dt.timedelta(days=1))
        occurrence = db.execute(select(RecurrenceOccurrence).where(
            RecurrenceOccurrence.template_id == template["id"],
            RecurrenceOccurrence.task_id == current["id"],
        )).scalar_one()
        assert occurrence.skipped is False
        synchronize(db, get_settings(), today=today + dt.timedelta(days=2))
        assert occurrence.skipped is True


def test_quota_shortfall_skips_remaining_sessions_and_does_not_carry(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    task_type = client.post("/task-types", json={"name": "Quota"}).json()
    template = client.post("/recurring-templates", json={
        "title": "Practice", "mode": "quota", "frequency": "daily", "interval": 1,
        "quota_count": 3, "start_date": today.isoformat(), "task_type_id": task_type["id"],
    }).json()
    tracker = client.get("/tasks").json()["items"][0]
    assert client.post(f"/tasks/{tracker['session_tasks'][0]['id']}/complete").status_code == 200
    too_late = client.post(f"/days/{(today + dt.timedelta(days=1)).isoformat()}/blocks", json={
        "lane": "planned", "task_id": tracker["session_tasks"][1]["id"],
        "start_minute": 600, "end_minute": 630,
    })
    assert too_late.status_code == 422

    with Session(get_engine()) as db:
        synchronize(db, get_settings(), today=today + dt.timedelta(days=1))
        occurrence = db.execute(select(RecurrenceOccurrence).where(
            RecurrenceOccurrence.template_id == template["id"],
            RecurrenceOccurrence.cycle_start == today,
        )).scalar_one()
        sessions = list(db.execute(select(Task).where(Task.parent_id == tracker["id"])).scalars())
    assert occurrence.skipped is True
    assert sum(session.status == TaskStatus.completed for session in sessions) == 1
    assert all(not session.ready_to_plan for session in sessions if session.status != TaskStatus.completed)


def test_future_planning_materializes_on_demand_beyond_default_horizon(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post(
        "/recurring-templates", json=_daily_body(today.isoformat(), checklist_titles=[])
    ).json()
    target = today + dt.timedelta(days=30)

    planned_candidate = _task_for_planning_date(client, target, template["id"])

    assert planned_candidate["deadline_date"] == target.isoformat()
    assert planned_candidate["ready_to_plan"] is False
    assert client.get("/tasks").json()["items"][0]["deadline_date"] == today.isoformat()
    assert _generated_root_count(template["id"]) == 31


def test_series_position_carries_to_future_occurrences(client):
    today = dt.date.fromisoformat(client.get("/health").json()["today"])
    template = client.post(
        "/recurring-templates", json=_daily_body(today.isoformat(), checklist_titles=[])
    ).json()
    current = client.get("/tasks").json()["items"][0]
    assert client.post("/tasks/reorder", json={"placements": [{
        "task_id": current["id"], "status": current["status"], "position": 42,
    }]}).status_code == 204

    future = _task_for_planning_date(client, today + dt.timedelta(days=8), template["id"])

    assert future["position"] == 42


def test_quota_rejects_carry_over_setting(client):
    today = client.get("/health").json()["today"]
    response = client.post("/recurring-templates", json={
        "title": "Practice", "mode": "quota", "frequency": "weekly", "interval": 1,
        "quota_count": 3, "start_date": today, "keep_unfinished_overdue": True,
    })

    assert response.status_code == 422


def test_quota_series_cannot_enable_carry_over(client):
    today = client.get("/health").json()["today"]
    template = client.post("/recurring-templates", json={
        "title": "Practice", "mode": "quota", "frequency": "weekly", "interval": 1,
        "quota_count": 3, "start_date": today,
    }).json()

    response = client.patch(f"/recurring-templates/{template['id']}", json={
        "keep_unfinished_overdue": True,
    })

    assert response.status_code == 422
    assert response.json()["detail"] == "Quota shortfalls cannot carry into the next period"
