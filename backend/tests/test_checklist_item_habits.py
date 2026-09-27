import datetime as dt
import importlib.util
from pathlib import Path

from alembic.migration import MigrationContext
from alembic.operations import Operations
from sqlalchemy import create_engine, select, text
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.battle_plan import RecurrenceOccurrence, Task, TaskStatus
from tests.test_habits import LAST_WEEK, THIS_WEEK, day, occurrence_for, row, states, week

ITEMS = ["Meditate", "Stretch", "Journal", "Make bed"]


def create_routine(client, **changes) -> int:
    body = {
        "title": "Morning routine",
        "mode": "scheduled",
        "frequency": "daily",
        "interval": 1,
        "start_date": (LAST_WEEK - dt.timedelta(days=14)).isoformat(),
        "confirm_backfill": True,
        "track_as_habit": True,
        "checklist_titles": ITEMS,
    }
    body.update(changes)
    response = client.post("/recurring-templates", json=body)
    assert response.status_code == 201, response.text
    return response.json()["id"]


def items(client, key: int) -> dict[str, dict]:
    return {item["title"]: item for item in client.get(f"/recurring-templates/{key}").json()["checklist_items"]}


def patch(client, key: int, **body) -> dict:
    response = client.patch(f"/recurring-templates/{key}", json=body)
    assert response.status_code == 200, response.text
    return response.json()


def subtasks(key: int, on: dt.date) -> dict[str, Task]:
    with Session(get_engine()) as db:
        root = occurrence_for(db, key, on).task_id
        return {
            task.title: task
            for task in db.scalars(select(Task).where(Task.parent_id == root, Task.deleted_at.is_(None)))
        }


def upcoming_subtasks(key: int) -> list[Task]:
    with Session(get_engine()) as db:
        roots = db.scalars(
            select(RecurrenceOccurrence.task_id).where(
                RecurrenceOccurrence.template_id == key,
                RecurrenceOccurrence.cycle_start > dt.date.today() + dt.timedelta(days=1),
            )
        ).all()
        return list(db.scalars(select(Task).where(Task.parent_id.in_(roots), Task.deleted_at.is_(None))))


def nested(result: dict, key: int) -> dict[str, dict]:
    return {item["title"]: item for item in row(result, key)["items"]}


# Slice 1: stable Checklist Item identity.


def test_generated_subtasks_link_to_their_checklist_item(client):
    key = create_routine(client)
    ids = {title: item["id"] for title, item in items(client, key).items()}
    past = subtasks(key, day(1))
    assert {title: task.checklist_item_id for title, task in past.items()} == ids


def test_rename_and_reorder_keep_identity_and_history(client):
    key = create_routine(client)
    before = {title: item["id"] for title, item in items(client, key).items()}
    # Reorder, and rename Meditate in place.
    patch(client, key, checklist_titles=["Meditate 10 min", "Journal", "Stretch", "Make bed"])
    after = items(client, key)
    assert after["Meditate 10 min"]["id"] == before["Meditate"]
    assert after["Journal"]["id"] == before["Journal"] and after["Journal"]["position"] == 1
    assert after["Stretch"]["id"] == before["Stretch"]
    # A past occurrence keeps its title snapshot but still belongs to the renamed item.
    assert subtasks(key, day(1))["Meditate"].checklist_item_id == before["Meditate"]
    renamed = [task for task in upcoming_subtasks(key) if task.checklist_item_id == before["Meditate"]]
    assert renamed and all(task.title == "Meditate 10 min" for task in renamed)


def test_deleting_an_item_and_adding_its_title_back_is_a_new_item(client):
    key = create_routine(client)
    journal = items(client, key)["Journal"]["id"]
    patch(client, key, checklist_titles=["Meditate", "Stretch", "Make bed"])
    assert "Journal" not in items(client, key)
    assert subtasks(key, day(1))["Journal"].checklist_item_id is None
    patch(client, key, checklist_titles=["Meditate", "Stretch", "Make bed", "Journal"])
    assert items(client, key)["Journal"]["id"] != journal


def test_occurrence_renames_keep_the_link_and_added_subtasks_have_none(client):
    key = create_routine(client)
    meditate = items(client, key)["Meditate"]["id"]
    task = subtasks(key, day(1))["Meditate"]
    assert client.patch(f"/tasks/{task.id}", json={"title": "Sit quietly"}).status_code == 200
    assert subtasks(key, day(1))["Sit quietly"].checklist_item_id == meditate
    added = client.post("/tasks", json={"title": "Cold shower", "parent_id": task.parent_id})
    assert added.status_code == 201, added.text
    assert subtasks(key, day(1))["Cold shower"].checklist_item_id is None


def test_migration_links_existing_subtasks_by_title_once():
    path = Path(__file__).parents[1] / "alembic/versions/037_checklist_item_habits.py"
    spec = importlib.util.spec_from_file_location("checklist_migration", path)
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    engine = create_engine("sqlite:///:memory:")
    with engine.begin() as connection:
        connection.execute(text("CREATE TABLE recurring_templates (id INTEGER PRIMARY KEY, track_as_habit BOOLEAN)"))
        connection.execute(text(
            "CREATE TABLE recurring_checklist_items (id INTEGER PRIMARY KEY, "
            "template_id INTEGER REFERENCES recurring_templates(id), title TEXT, position INTEGER)"
        ))
        connection.execute(text(
            "CREATE TABLE tasks (id INTEGER PRIMARY KEY, parent_id INTEGER, recurring_template_id INTEGER, "
            "recurrence_kind TEXT, title TEXT, position INTEGER)"
        ))
        connection.execute(text("INSERT INTO recurring_templates VALUES (1, 1), (2, 0)"))
        connection.execute(text(
            "INSERT INTO recurring_checklist_items VALUES "
            "(10, 1, 'Stretch', 0), (11, 1, 'Stretch', 1), (12, 1, 'Journal', 2), (20, 2, 'Read', 0)"
        ))
        connection.execute(text(
            "INSERT INTO tasks VALUES "
            "(100, NULL, 1, 'scheduled', 'Morning routine', 0), "
            "(101, 100, 1, 'checklist', ' stretch ', 0), (102, 100, 1, 'checklist', 'Stretch', 1), "
            "(103, 100, 1, 'checklist', 'Renamed before', 2), (104, 100, NULL, NULL, 'Journal', 3), "
            "(200, NULL, 2, 'scheduled', 'Evening', 0), (201, 200, 2, 'checklist', 'Read', 0)"
        ))
        with Operations.context(MigrationContext.configure(connection)):
            migration.upgrade()
            links = dict(connection.execute(text("SELECT id, checklist_item_id FROM tasks")).all())
            assert links == {100: None, 101: 10, 102: 11, 103: None, 104: None, 200: None, 201: 20}
            flags = dict(connection.execute(text("SELECT id, track_as_habit FROM recurring_checklist_items")).all())
            assert flags == {10: 1, 11: 1, 12: 1, 20: 0}
            migration.downgrade()
    engine.dispose()


# Slice 2: Checklist Item Habits.


def test_tracked_series_nests_every_item(client):
    key = create_routine(client)
    habit = row(week(client), key)
    assert habit["tracked"] is True
    assert [item["title"] for item in habit["items"]] == ITEMS
    assert states(habit["items"][0]) == ["missed"] * 7
    assert habit["items"][0]["total"] == {"done": 0, "target": 7, "unit": "days", "month": None, "tone": "missed"}


def test_series_switch_and_item_opt_outs(client):
    key = create_routine(client, track_as_habit=False)
    assert all(not item["track_as_habit"] for item in items(client, key).values())
    assert week(client)["habits"] == []

    patch(client, key, track_as_habit=True)
    ids = {title: item["id"] for title, item in items(client, key).items()}
    patch(client, key, habit_checklist_item_ids=[ids[t] for t in ITEMS if t != "Make bed"])
    assert list(nested(week(client), key)) == ["Meditate", "Stretch", "Journal"]

    # An item added to a tracked series is tracked; the opt-out survives.
    patch(client, key, checklist_titles=ITEMS + ["Floss"])
    # Floss has no Subtasks last week, so it only shows from this week on.
    assert list(nested(week(client), key)) == ["Meditate", "Stretch", "Journal"]
    assert list(nested(week(client, THIS_WEEK), key)) == ["Meditate", "Stretch", "Journal", "Floss"]
    assert items(client, key)["Floss"]["track_as_habit"] is True
    assert items(client, key)["Make bed"]["track_as_habit"] is False

    # Off opts every item out; on again clears the individual opt-outs.
    patch(client, key, track_as_habit=False)
    assert all(not item["track_as_habit"] for item in items(client, key).values())
    patch(client, key, track_as_habit=True)
    assert all(item["track_as_habit"] for item in items(client, key).values())


def test_untracked_series_heads_its_tracked_items(client):
    key = create_routine(client, track_as_habit=False)
    ids = {title: item["id"] for title, item in items(client, key).items()}
    patch(client, key, habit_checklist_item_ids=[ids["Stretch"]])
    habit = row(week(client), key)
    assert habit["tracked"] is False
    assert [item["title"] for item in habit["items"]] == ["Stretch"]
    other = create_routine(client, title="Evening", track_as_habit=False)
    assert all(h["template_id"] != other for h in week(client)["habits"])
    assert client.patch(f"/recurring-templates/{key}", json={"habit_checklist_item_ids": [999999]}).status_code == 422


def test_item_tick_on_a_completed_occurrence_leaves_the_completion(client):
    key = create_routine(client)
    stretch = items(client, key)["Stretch"]["id"]
    monday = day(0)
    assert client.post(f"/habits/{key}/days/{monday.isoformat()}").status_code == 200
    result = client.post(f"/habits/{key}/items/{stretch}/days/{monday.isoformat()}")
    assert result.status_code == 200, result.text
    habit = row(result.json(), key)
    assert states(habit)[0] == "met"
    assert states(nested(result.json(), key)["Stretch"])[0] == "met"
    with Session(get_engine()) as db:
        task = db.get(Task, occurrence_for(db, key, monday).task_id)
        assert task.status == TaskStatus.completed and task.completed_at.date() == monday
    assert subtasks(key, monday)["Stretch"].checked is True

    undone = client.delete(f"/habits/{key}/items/{stretch}/days/{monday.isoformat()}")
    assert states(nested(undone.json(), key)["Stretch"])[0] == "missed"
    assert states(row(undone.json(), key))[0] == "met"


def test_item_tick_on_a_skipped_day_does_not_reverse_the_skip(client):
    key = create_routine(client)
    bed = items(client, key)["Make bed"]["id"]
    thursday = day(3)
    result = client.post(f"/habits/{key}/items/{bed}/days/{thursday.isoformat()}")
    assert result.status_code == 200, result.text
    assert states(nested(result.json(), key)["Make bed"])[3] == "met"
    assert states(row(result.json(), key))[3] == "missed"
    with Session(get_engine()) as db:
        occurrence = occurrence_for(db, key, thursday)
        assert occurrence.skipped is True
        assert db.get(Task, occurrence.task_id).status == TaskStatus.open


def test_occurrence_without_the_items_subtask_is_excused(client):
    key = create_routine(client)
    journal = items(client, key)["Journal"]["id"]
    task = subtasks(key, day(2))["Journal"]
    assert client.delete(f"/tasks/{task.id}").status_code == 200
    result = week(client)
    assert states(nested(result, key)["Journal"])[2] == "excused"
    assert nested(result, key)["Journal"]["total"]["target"] == 6
    ticked = client.post(f"/habits/{key}/items/{journal}/days/{day(2).isoformat()}")
    assert ticked.status_code == 422


def test_item_tick_rejects_unknown_items_and_future_days(client):
    key = create_routine(client)
    stretch = items(client, key)["Stretch"]["id"]
    assert client.post(f"/habits/{key}/items/999999/days/{day(0).isoformat()}").status_code == 404
    other = create_routine(client, title="Evening")
    assert client.post(f"/habits/{other}/items/{stretch}/days/{day(0).isoformat()}").status_code == 404
    future = dt.date.today() + dt.timedelta(days=3)
    assert client.post(f"/habits/{key}/items/{stretch}/days/{future.isoformat()}").status_code == 422
