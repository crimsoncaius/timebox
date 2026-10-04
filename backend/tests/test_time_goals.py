import datetime as dt

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.routes.trends import capture_now
from app.core.config import Settings, get_settings
from app.db.session import get_engine
from app.main import app
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.models.time_goal import TimeGoal
from app.services.time_goals import period_bounds


def instant(value):
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00"))


@pytest.fixture
def api(client):
    app.dependency_overrides[capture_now] = lambda: instant("2026-09-27T05:00:00Z")
    app.dependency_overrides[get_settings] = lambda: Settings(app_timezone="Asia/Singapore")
    yield client
    app.dependency_overrides.pop(capture_now, None)
    app.dependency_overrides.pop(get_settings, None)


def goal(api, path="exercise", **kwargs):
    kind = api.post("/task-types", json={"name": path}).json()["id"]
    response = api.post("/time-goals", json={"task_type_id": kind, "start_date": "2026-09-21",
                                            "target_minutes": 240, **kwargs})
    assert response.status_code == 201, response.text
    return response.json()


def record(path, start, end):
    with Session(get_engine()) as db:
        kind = db.scalar(select(TaskType).where(TaskType.name == path))
        if kind is None:
            kind = TaskType(name=path); db.add(kind); db.flush()
        row = TimeBlock(lane=BlockLane.actual, task_type_id=kind.id, start_at=instant(start),
                        end_at=instant(end) if end else None)
        db.add(row); db.commit()
        return row.id


def period(api, goal_id, date="2026-09-27"):
    response = api.get(f"/time-goals/{goal_id}/period", params={"anchor": date, "week": date})
    assert response.status_code == 200, response.text
    return response.json()["period"]


def test_parent_and_child_credit_actuals_and_corrections(api):
    parent = goal(api)
    child = goal(api, "exercise/cardio")
    strength = goal(api, "exercise/strength")
    first = record("exercise/cardio", "2026-09-22T01:00:00Z", "2026-09-22T03:00:00Z")
    record("exercise/strength", "2026-09-23T01:00:00Z", "2026-09-23T02:00:00Z")
    record("exercises", "2026-09-24T01:00:00Z", "2026-09-24T02:00:00Z")
    assert [period(api, g["id"])["duration_seconds"] for g in (parent, child, strength)] == [10800, 7200, 3600]
    with Session(get_engine()) as db:
        db.get(TimeBlock, first).end_at = instant("2026-09-22T05:00:00Z"); db.commit()
    assert period(api, child["id"])["outcome"] == "met"
    with Session(get_engine()) as db:
        db.delete(db.get(TimeBlock, first)); db.commit()
    assert period(api, parent["id"])["duration_seconds"] == 3600
    assert period(api, child["id"])["outcome"] == "in_progress"


@pytest.mark.parametrize("start,unit,interval,anchor,bounds", [
    ("2026-09-23", "week", 2, "2026-10-04", ("2026-09-23", "2026-10-04")),
    ("2026-09-23", "week", 2, "2026-10-05", ("2026-10-05", "2026-10-18")),
    ("2024-01-31", "month", 2, "2024-02-29", ("2024-01-31", "2024-02-29")),
    ("2024-01-31", "month", 2, "2024-03-01", ("2024-03-01", "2024-04-30")),
    ("2026-09-21", "day", 3, "2026-09-27", ("2026-09-27", "2026-09-29")),
])
def test_fixed_period_boundaries(start, unit, interval, anchor, bounds):
    model = TimeGoal(start_date=dt.date.fromisoformat(start), unit=unit, interval=interval)
    assert tuple(d.isoformat() for d in period_bounds(model, dt.date.fromisoformat(anchor))) == bounds


def test_target_history_and_no_carryover(api):
    g = goal(api, target_minutes=30, unit="day")
    record("exercise", "2026-09-26T01:00:00Z", "2026-09-26T02:00:00Z")
    assert api.patch(f'/time-goals/{g["id"]}/target', json={"target_minutes": 90}).status_code == 204
    assert period(api, g["id"], "2026-09-26")["target_minutes"] == 30
    assert period(api, g["id"])["duration_seconds"] == 0
    assert period(api, g["id"], "2026-09-28")["target_minutes"] == 90
    assert api.patch(f'/time-goals/{g["id"]}/target', json={"target_minutes": 60}).status_code == 204
    assert period(api, g["id"], "2026-09-28")["target_minutes"] == 60


def test_end_replace_and_delete_never_remove_actuals(api):
    old = goal(api)
    row = record("exercise", "2026-09-25T01:00:00Z", "2026-09-25T02:00:00Z")
    new = api.post(f'/time-goals/{old["id"]}/replace', json={"task_type_id": old["task_type_id"],
                   "unit": "month", "interval": 2, "target_minutes": 600})
    assert new.status_code == 200, new.text
    assert (new.json()["period"]["start"], new.json()["period"]["end"]) == ("2026-09-27", "2026-10-31")
    assert period(api, old["id"])["outcome"] == "excused"
    assert api.patch(f'/time-goals/{old["id"]}/target', json={"target_minutes": 10}).status_code == 422
    assert api.delete(f'/time-goals/{old["id"]}').status_code == 204
    with Session(get_engine()) as db:
        assert db.get(TimeBlock, row) is not None


def test_midnight_running_and_dst(api):
    g = goal(api, unit="day", target_minutes=60)
    record("exercise", "2026-09-26T15:30:00Z", "2026-09-26T16:30:00Z")
    record("exercise", "2026-09-27T04:45:00Z", None)
    assert period(api, g["id"])["duration_seconds"] == 2700
    assert period(api, g["id"], "2026-09-26")["duration_seconds"] == 1800
    app.dependency_overrides[get_settings] = lambda: Settings(app_timezone="America/New_York")
    sleep = goal(api, "sleep", unit="day", start_date="2025-11-02")
    record("sleep", "2025-11-02T04:00:00Z", "2025-11-03T05:00:00Z")
    assert period(api, sleep["id"], "2025-11-02")["duration_seconds"] == 25 * 3600


def test_type_merge_preserves_separate_goals_and_delete_is_guarded(api):
    source = goal(api, "cardio")
    target = goal(api, "exercise/cardio")
    preview = api.post(f'/task-types/{source["task_type_id"]}/merge-preview',
                       json={"target_id": target["task_type_id"]}).json()
    merged = api.post(f'/task-types/{source["task_type_id"]}/merge',
                      json={"target_id": target["task_type_id"], "preview_token": preview["preview_token"]})
    assert merged.status_code == 200, merged.text
    goals = api.get('/time-goals').json()["goals"]
    assert len(goals) == 2
    assert all(g["task_type_id"] == target["task_type_id"] for g in goals)
    assert api.delete(f'/task-types/{target["task_type_id"]}').status_code == 422


def test_failed_replacement_rolls_back_end_and_invalid_inputs(api):
    g = goal(api)
    response = api.post(f'/time-goals/{g["id"]}/replace', json={"task_type_id": 99999,
                        "unit": "week", "interval": 1, "target_minutes": 60})
    assert response.status_code == 422
    assert api.get('/time-goals').json()["goals"][0]["end_date"] is None
    for extra in [{"target_minutes": 0}, {"interval": 0}, {"start_date": "9999-12-31"}]:
        response = api.post('/time-goals', json={"task_type_id": g["task_type_id"],
                            "target_minutes": 60, "start_date": "2026-09-21", **extra})
        assert response.status_code == 422


def test_type_deletion_requires_explicit_goal_resolution_and_is_atomic(api):
    source = goal(api, "cardio")
    target = api.post('/task-types', json={"name": "fitness"}).json()["id"]
    block_id = record("cardio", "2026-09-22T01:00:00Z", "2026-09-22T02:00:00Z")
    assert api.get('/task-types').json()[0]["time_goal_usage_count"] == 1
    # Goal-only authorization does not authorize losing Actual Blocks.
    assert api.delete(f'/task-types/{source["task_type_id"]}?delete_goals=true').status_code == 409
    assert len(api.get('/time-goals').json()["goals"]) == 1
    response = api.delete(f'/task-types/{source["task_type_id"]}', params={
        "migrate_goals_to": target, "migrate_blocks_to": target,
    })
    assert response.status_code == 204, response.text
    retained = api.get('/time-goals').json()["goals"][0]
    assert retained["id"] == source["id"]
    assert retained["task_type_id"] == target
    assert retained["period"]["duration_seconds"] == 3600
    with Session(get_engine()) as db:
        assert db.get(TimeBlock, block_id).task_type_id == target


def test_ended_met_goal_reassesses_corrections_and_excludes_prestart_time(api):
    g = goal(api, start_date="2026-09-25", target_minutes=60)
    record("exercise", "2026-09-24T01:00:00Z", "2026-09-24T02:00:00Z")
    block = record("exercise", "2026-09-25T01:00:00Z", "2026-09-25T02:00:00Z")
    assert api.post(f'/time-goals/{g["id"]}/end').status_code == 204
    assert period(api, g["id"])["outcome"] == "met"
    with Session(get_engine()) as db:
        db.delete(db.get(TimeBlock, block)); db.commit()
    assessment = period(api, g["id"])
    assert assessment["outcome"] == "excused"
    assert assessment["duration_seconds"] == 0


def test_long_period_total_stays_while_browsing_weeks_and_rename_preserves_identity(api):
    g = goal(api, unit="month", interval=2, start_date="2026-09-01")
    record("exercise", "2026-09-02T01:00:00Z", "2026-09-02T02:00:00Z")
    record("exercise", "2026-09-22T01:00:00Z", "2026-09-22T02:00:00Z")
    api.patch(f'/task-types/{g["task_type_id"]}', json={"name": "fitness"})
    early = api.get('/time-goals?week=2026-09-01').json()["goals"][0]
    current = api.get('/time-goals').json()["goals"][0]
    assert early["period"]["duration_seconds"] == current["period"]["duration_seconds"] == 7200
    assert early["task_type"] == "fitness"
    assert early["days"] == {"2026-09-02": 3600}


def test_migration_adds_goal_tables_to_existing_schema():
    import importlib.util
    from pathlib import Path

    from alembic.migration import MigrationContext
    from alembic.operations import Operations

    from app.models.time_goal import TimeGoalTarget

    spec = importlib.util.spec_from_file_location("time_goal_migration",
        Path(__file__).resolve().parents[1] / "alembic/versions/038_time_goals.py")
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    engine = get_engine()
    TimeGoalTarget.__table__.drop(engine)
    TimeGoal.__table__.drop(engine)
    with engine.begin() as connection, Operations.context(MigrationContext.configure(connection)):
        migration.upgrade()
        migration.upgrade()  # current-model/bootstrap schemas are safe too
        connection.exec_driver_sql("INSERT INTO task_types(id, name) VALUES (1, 'exercise')")
        connection.exec_driver_sql("INSERT INTO time_goals(id, task_type_id, unit, interval, start_date) "
                                   "VALUES (1, 1, 'week', 1, '2026-09-21')")
        connection.exec_driver_sql("INSERT INTO time_goal_targets VALUES (1, '2026-09-21', 240)")
    with Session(engine) as db:
        assert db.get(TimeGoal, 1).targets[0].minutes == 240


def test_archive_separates_current_goals_but_keeps_historical_weeks(api):
    old = goal(api, start_date="2026-09-01")
    block = record("exercise", "2026-09-22T01:00:00Z", "2026-09-22T02:00:00Z")
    assert api.post(f'/time-goals/{old["id"]}/end').status_code == 204
    assert api.get('/time-goals').json()['goals'] == []
    assert api.get('/time-goals?week=2026-09-14').json()['goals'][0]['id'] == old['id']
    archived = api.get('/time-goals/archive').json()['goals']
    assert [(g['id'], g['end_date']) for g in archived] == [(old['id'], '2026-09-27')]
    assert api.get(f'/time-goals/{old["id"]}/history').json()['periods'][0]['outcome'] == 'excused'
    assert api.delete(f'/time-goals/{old["id"]}').status_code == 204
    assert api.get('/time-goals/archive').json()['goals'] == []
    assert api.get(f'/time-goals/{old["id"]}/history').status_code == 404
    with Session(get_engine()) as db:
        assert db.get(TimeBlock, block) is not None


def test_archive_lists_legacy_and_replaced_goals_newest_first(api):
    old = goal(api, "reading", start_date="2026-08-01")
    with Session(get_engine()) as db:
        db.get(TimeGoal, old['id']).end_date = dt.date(2026, 8, 31)
        db.commit()
    replaced = goal(api)
    assert api.post(f'/time-goals/{replaced["id"]}/replace', json={
        'task_type_id': replaced['task_type_id'], 'unit': 'day', 'interval': 1, 'target_minutes': 30,
    }).status_code == 200
    assert [g['id'] for g in api.get('/time-goals/archive').json()['goals']] == [replaced['id'], old['id']]
    assert len(api.get('/time-goals').json()['goals']) == 1
    assert api.post(f'/time-goals/{old["id"]}/end').status_code == 422
    assert api.patch(f'/time-goals/{old["id"]}/target', json={'target_minutes': 5}).status_code == 422


def test_history_pages_targets_blocks_and_live_corrections(api):
    g = goal(api, unit='day', start_date='2026-08-01', target_minutes=30)
    app.dependency_overrides[capture_now] = lambda: instant('2026-09-01T05:00:00Z')
    assert api.patch(f'/time-goals/{g["id"]}/target', json={'target_minutes': 60}).status_code == 204
    app.dependency_overrides[capture_now] = lambda: instant('2026-09-27T05:00:00Z')
    block = record('exercise', '2026-09-01T01:00:00Z', '2026-09-01T01:40:00Z')
    assert api.post(f'/time-goals/{g["id"]}/end').status_code == 204
    first = api.get(f'/time-goals/{g["id"]}/history').json()
    assert len(first['periods']) == 30
    assert first['periods'][0]['start'] == '2026-09-27'
    assert first['periods'][0]['target_minutes'] == 60
    assert first['next_before'] == '2026-08-29'
    earlier = next(p for p in first['periods'] if p['start'] == '2026-09-01')
    assert (earlier['target_minutes'], earlier['duration_seconds'], earlier['outcome']) == (30, 2400, 'met')
    assert earlier['blocks'][0]['id'] == block
    second = api.get(f'/time-goals/{g["id"]}/history', params={'before': first['next_before']}).json()
    assert second['periods'][0]['start'] == '2026-08-28'
    assert second['periods'][-1]['start'] == '2026-08-01'
    assert second['next_before'] is None
    assert len({p['start'] for p in first['periods'] + second['periods']}) == 58
    with Session(get_engine()) as db:
        db.get(TimeBlock, block).end_at = instant('2026-09-01T01:10:00Z')
        db.commit()
    refreshed = api.get(f'/time-goals/{g["id"]}/history').json()
    assert next(p for p in refreshed['periods'] if p['start'] == '2026-09-01')['outcome'] == 'missed'
    assert api.get(f'/time-goals/{g["id"]}/history?before=0001-01-01').json()['periods'] == []
    assert api.get(f'/time-goals/{g["id"]}/history?limit=0').status_code == 422
    assert api.get(f'/time-goals/{g["id"]}/history?limit=51').status_code == 422


@pytest.mark.parametrize('unit,interval,start,expected', [
    ('week', 2, '2026-08-19', [('2026-09-14', '2026-09-27'), ('2026-08-31', '2026-09-13'), ('2026-08-19', '2026-08-30')]),
    ('month', 1, '2026-07-20', [('2026-09-01', '2026-09-27'), ('2026-08-01', '2026-08-31'), ('2026-07-20', '2026-07-31')]),
])
def test_history_respects_natural_periods_and_short_first_and_final_periods(api, unit, interval, start, expected):
    g = goal(api, unit=unit, interval=interval, start_date=start)
    assert api.get(f'/time-goals/{g["id"]}/history').status_code == 422
    assert api.post(f'/time-goals/{g["id"]}/end').status_code == 204
    history = api.get(f'/time-goals/{g["id"]}/history').json()
    assert [(p['start'], p['end']) for p in history['periods']] == expected
    assert all(p['target_minutes'] == 240 for p in history['periods'])
    assert history['next_before'] is None
