import datetime as dt

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.time import today_in_tz
from app.db.session import get_engine
from app.models.battle_plan import (
    RecurrenceOccurrence,
    Task,
    TaskCompletionOperation,
    TaskStatus,
)
from app.services.recurrence import habits

TODAY = today_in_tz(get_settings().app_timezone)
THIS_WEEK = TODAY - dt.timedelta(days=TODAY.weekday())
LAST_WEEK = THIS_WEEK - dt.timedelta(days=7)


def day(offset: int) -> dt.date:
    """A day of last week, Monday = 0."""
    return LAST_WEEK + dt.timedelta(days=offset)


def create(client, **changes) -> int:
    body = {
        "title": "Gym",
        "mode": "scheduled",
        "frequency": "weekly",
        "interval": 1,
        "weekdays": [0, 2, 4],
        "start_date": (LAST_WEEK - dt.timedelta(days=14)).isoformat(),
        "confirm_backfill": True,
        "track_as_habit": True,
    }
    body.update(changes)
    response = client.post("/recurring-templates", json=body)
    assert response.status_code == 201, response.text
    return response.json()["id"]


def week(client, start: dt.date = LAST_WEEK) -> dict:
    response = client.get(f"/habits?week={start.isoformat()}")
    assert response.status_code == 200, response.text
    return response.json()


def row(result: dict, template_id: int) -> dict:
    return next(h for h in result["habits"] if h["template_id"] == template_id)


def states(habit: dict) -> list[str]:
    return [cell["state"] for cell in habit["days"]]


def occurrence_for(db: Session, template_id: int, on: dt.date) -> RecurrenceOccurrence:
    return db.scalars(
        select(RecurrenceOccurrence).where(
            RecurrenceOccurrence.template_id == template_id,
            RecurrenceOccurrence.cycle_start <= on,
            RecurrenceOccurrence.cycle_end >= on,
        )
    ).one()


def test_track_as_habit_is_opt_in_and_rejects_null(client):
    key = create(client, track_as_habit=False)
    assert client.get(f"/recurring-templates/{key}").json()["track_as_habit"] is False
    assert week(client)["habits"] == []
    patched = client.patch(f"/recurring-templates/{key}", json={"track_as_habit": True})
    assert patched.status_code == 200 and patched.json()["track_as_habit"] is True
    assert [h["template_id"] for h in week(client)["habits"]] == [key]
    assert client.patch(f"/recurring-templates/{key}", json={"track_as_habit": None}).status_code == 422


def test_scheduled_week_shows_due_days_missed_and_total(client):
    key = create(client)
    result = week(client)
    assert result["week_start"] == LAST_WEEK.isoformat()
    assert result["earliest_week_start"] == (LAST_WEEK - dt.timedelta(days=14)).isoformat()
    habit = row(result, key)
    assert states(habit) == ["missed", "not_due", "missed", "not_due", "missed", "not_due", "not_due"]
    assert habit["total"] == {"done": 0, "target": 3, "unit": "days", "month": None, "tone": "missed"}


def test_past_tick_is_a_dated_completion_that_reverses_skip_and_untick_restores_it(client):
    key = create(client)
    wednesday = day(2)
    with Session(get_engine()) as db:
        assert occurrence_for(db, key, wednesday).skipped is True

    result = client.post(f"/habits/{key}/days/{wednesday.isoformat()}")
    assert result.status_code == 200, result.text
    habit = row(result.json(), key)
    assert states(habit)[2] == "met"
    assert habit["total"]["done"] == 1
    with Session(get_engine()) as db:
        occurrence = occurrence_for(db, key, wednesday)
        task = db.get(Task, occurrence.task_id)
        assert occurrence.skipped is False
        assert task.status == TaskStatus.completed
        assert task.completed_at.date() == wednesday
        # A backdated completion records no Undo operation or tracking side effects.
        assert db.scalar(select(func.count()).select_from(TaskCompletionOperation)) == 0

    # Ticking twice is harmless.
    assert client.post(f"/habits/{key}/days/{wednesday.isoformat()}").status_code == 200

    undone = client.delete(f"/habits/{key}/days/{wednesday.isoformat()}")
    assert states(row(undone.json(), key))[2] == "missed"
    with Session(get_engine()) as db:
        occurrence = occurrence_for(db, key, wednesday)
        assert occurrence.skipped is True
        assert db.get(Task, occurrence.task_id).status == TaskStatus.open


def test_future_and_not_due_days_cannot_be_recorded(client):
    key = create(client)
    tomorrow = TODAY + dt.timedelta(days=1)
    assert client.post(f"/habits/{key}/days/{tomorrow.isoformat()}").status_code == 422
    tuesday = day(1)
    assert client.post(f"/habits/{key}/days/{tuesday.isoformat()}").json()["detail"] == habits.NO_PERIOD
    assert client.post(f"/habits/999999/days/{tuesday.isoformat()}").status_code == 404


def test_weekly_quota_counts_by_day_allows_extras_and_removes_them_first(client):
    key = create(client, title="Read", mode="quota", weekdays=[], quota_count=2)
    monday = day(0)
    for _ in range(3):
        assert client.post(f"/habits/{key}/days/{monday.isoformat()}").status_code == 200
    habit = row(week(client), key)
    assert habit["days"][0]["state"] == "count" and habit["days"][0]["count"] == 3
    assert habit["days"][1]["state"] == "empty"
    assert habit["total"] == {"done": 3, "target": 2, "unit": "sessions", "month": None, "tone": "met"}
    with Session(get_engine()) as db:
        occurrence = occurrence_for(db, key, monday)
        tracker = db.get(Task, occurrence.task_id)
        assert tracker.status == TaskStatus.completed
        assert occurrence.skipped is False

    client.delete(f"/habits/{key}/days/{monday.isoformat()}")
    with Session(get_engine()) as db:
        sessions = db.scalars(
            select(Task).where(Task.recurring_template_id == key, Task.recurrence_kind == "quota_session")
        ).all()
        extra = [s for s in sessions if s.session_index == 3]
        assert len(extra) == 1 and extra[0].deleted_at is not None
    assert row(week(client), key)["total"]["done"] == 2

    client.delete(f"/habits/{key}/days/{monday.isoformat()}")
    habit = row(week(client), key)
    assert habit["total"] == {"done": 1, "target": 2, "unit": "sessions", "month": None, "tone": "missed"}
    with Session(get_engine()) as db:
        assert occurrence_for(db, key, monday).skipped is True


def test_daily_quota_cells_are_their_own_periods(client):
    key = create(client, title="Water", mode="quota", frequency="daily", weekdays=[], quota_count=2)
    monday = day(0)
    client.post(f"/habits/{key}/days/{monday.isoformat()}")
    cell = row(week(client), key)["days"][0]
    assert (cell["state"], cell["count"], cell["target"]) == ("partial", 1, 2)
    client.post(f"/habits/{key}/days/{monday.isoformat()}")
    habit = row(week(client), key)
    assert habit["days"][0]["state"] == "met"
    assert habit["days"][1]["state"] == "missed"
    assert habit["total"] == {"done": 1, "target": 7, "unit": "days", "month": None, "tone": "missed"}


def test_monthly_quota_total_is_month_to_date(client):
    key = create(client, title="Call", mode="quota", frequency="monthly", weekdays=[], quota_count=4)
    habit = row(week(client), key)
    anchor = day(6)
    assert habit["total"]["unit"] == "month"
    assert habit["total"]["month"] == anchor.replace(day=1).isoformat()


def test_paused_and_deleted_habits_leave_the_grid(client):
    key = create(client)
    assert client.post(f"/recurring-templates/{key}/pause").status_code == 200
    # Past active weeks keep the row.
    assert states(row(week(client), key))[0] == "missed"
    current = week(client, THIS_WEEK)
    if TODAY.weekday() == 0:
        assert current["habits"] == []
    else:
        cells = row(current, key)["days"]
        assert all(cell["state"] in {"excused", "not_due"} for cell in cells[TODAY.weekday():])
    client.post(f"/recurring-templates/{key}/end")
    assert client.delete(f"/recurring-templates/{key}").status_code == 204
    assert week(client)["habits"] == []


def test_completions_elsewhere_appear_in_the_grid(client):
    key = create(client, weekdays=[TODAY.weekday()], start_date=(THIS_WEEK - dt.timedelta(days=7)).isoformat())
    with Session(get_engine()) as db:
        task_id = occurrence_for(db, key, TODAY).task_id
    assert client.post(f"/tasks/{task_id}/complete").status_code == 200
    cell = row(week(client, THIS_WEEK), key)["days"][TODAY.weekday()]
    assert cell["state"] == "met" and cell["tickable"] is True
