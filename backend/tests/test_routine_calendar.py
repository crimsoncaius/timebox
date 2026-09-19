import datetime as dt

from app.core.config import get_settings
from app.db.session import get_engine
from app.models.battle_plan import (
    RecurrenceOccurrence,
    RecurringTemplate,
    Task,
    TaskStatus,
)
from app.services.recurrence.calendar import read_calendar
from sqlalchemy import func, select
from sqlalchemy.orm import Session


def create(client, **changes):
    body = {
        "title": "Review",
        "mode": "scheduled",
        "frequency": "monthly",
        "interval": 1,
        "month_day": 31,
        "start_date": "2026-01-31",
        "confirm_backfill": True,
    }
    body.update(changes)
    response = client.post("/recurring-templates", json=body)
    assert response.status_code == 201, response.text
    return response.json()["id"]


def test_calendar_limits_and_read_only_projection(client):
    key = create(client, start_date="2090-01-31", cycle_limit=3)
    with Session(get_engine()) as db:
        count = db.scalar(select(func.count()).select_from(Task))
    result = client.get(f"/recurring-templates/{key}/calendar?month=2090-02-01").json()
    assert result["first_month"] == "2090-01-01"
    assert result["last_month"] == "2090-03-01"
    assert result["upcoming"] == ["2090-02-28"]
    assert (
        client.get(f"/recurring-templates/{key}/calendar?month=2091-01-01").json()[
            "month"
        ]
        == "2090-03-01"
    )
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(Task)) == count


def test_quota_completion_dates_counts_and_exclusions(client):
    key = create(
        client, mode="quota", frequency="weekly", month_day=None, quota_count=3
    )
    with Session(get_engine()) as db:
        for kind, deleted in [
            ("quota_session", False),
            ("quota_session", False),
            ("checklist", False),
            ("quota_parent", False),
            ("quota_session", True),
        ]:
            db.add(
                Task(
                    title=kind,
                    recurring_template_id=key,
                    recurrence_kind=kind,
                    status=TaskStatus.completed,
                    completed_at=dt.datetime(2026, 9, 18, 18, tzinfo=dt.timezone.utc),
                    deleted_at=dt.datetime.now(dt.timezone.utc) if deleted else None,
                )
            )
        db.commit()
        settings = get_settings().model_copy(update={"app_timezone": "Asia/Singapore"})
        result = read_calendar(db, key, dt.date(2026, 9, 1), settings)
        assert len(result.completed) == 2
        assert {t.date for t in result.completed} == {dt.date(2026, 9, 19)}
        assert result.upcoming == []
        assert result.first_month == result.last_month == dt.date(2026, 9, 1)


def test_empty_quota_and_paused_schedule_have_no_projected_dates(client):
    key = create(
        client, mode="quota", frequency="weekly", month_day=None, quota_count=3
    )
    result = client.get(f"/recurring-templates/{key}/calendar").json()
    assert result["has_dates"] is False
    scheduled = create(client, start_date="2090-01-31")
    client.post(f"/recurring-templates/{scheduled}/pause")
    result = client.get(
        f"/recurring-templates/{scheduled}/calendar?month=2090-01-01"
    ).json()
    assert result["upcoming"] == []
    assert result["has_dates"] is False


def test_end_date_and_never_ending_bounds(client):
    key = create(client, start_date="2090-01-31", end_date="2090-03-15")
    result = client.get(f"/recurring-templates/{key}/calendar?month=2090-03-01").json()
    assert result["last_month"] == "2090-03-01"
    assert result["upcoming"] == []
    forever = create(client, start_date="2090-01-31")
    assert (
        client.get(f"/recurring-templates/{forever}/calendar").json()["last_month"]
        is None
    )
    assert (
        client.get(
            f"/recurring-templates/{forever}/calendar?month=2090-13-01"
        ).status_code
        == 422
    )
    assert client.get("/recurring-templates/999999/calendar").status_code == 404


def test_reporting_zone_and_late_completion_extend_finite_history(client):
    from app.models.activity import ActivityState

    key = create(client, start_date="2026-01-31", end_date="2026-02-28")
    with Session(get_engine()) as db:
        db.add(ActivityState(id=1, reporting_timezone="Asia/Singapore"))
        task = db.scalars(select(Task).where(Task.recurring_template_id == key)).first()
        task.status = TaskStatus.completed
        task.completed_at = dt.datetime(2026, 3, 31, 18, tzinfo=dt.timezone.utc)
        db.commit()
    result = client.get(f"/recurring-templates/{key}/calendar?month=2026-04-01").json()
    assert result["last_month"] == "2026-04-01"
    assert result["completed"][0]["date"] == "2026-04-01"
    assert result["upcoming"] == []


def test_suppressed_and_early_completed_occurrences_are_not_projected(client):
    key = create(client, start_date="2090-01-31")
    with Session(get_engine()) as db:
        task = Task(
            title="Done early",
            recurring_template_id=key,
            recurrence_kind="scheduled",
            status=TaskStatus.completed,
            completed_at=dt.datetime(2089, 12, 31, tzinfo=dt.timezone.utc),
        )
        db.add(task)
        db.flush()
        db.add(
            RecurrenceOccurrence(
                template_id=key,
                occurrence_key="scheduled:2090-01-31",
                cycle_start=dt.date(2090, 1, 31),
                cycle_end=dt.date(2090, 1, 31),
                task_id=task.id,
            )
        )
        db.add(
            RecurrenceOccurrence(
                template_id=key,
                occurrence_key="scheduled:2090-02-28",
                cycle_start=dt.date(2090, 2, 28),
                cycle_end=dt.date(2090, 2, 28),
                suppressed=True,
            )
        )
        db.commit()
    for month in ["2090-01-01", "2090-02-01"]:
        assert (
            client.get(f"/recurring-templates/{key}/calendar?month={month}").json()[
                "upcoming"
            ]
            == []
        )


def test_weekly_count_bounds_match_canonical_windows(client):
    key = create(
        client,
        frequency="weekly",
        month_day=None,
        weekdays=[0, 4],
        interval=2,
        start_date="2090-01-05",
        cycle_limit=7,
    )
    from app.services.recurrence.windows import iter_windows

    with Session(get_engine()) as db:
        row = db.get(RecurringTemplate, key)
        expected = iter_windows(row, dt.date(2091, 1, 1))[-1].start.replace(day=1)
    result = client.get(f"/recurring-templates/{key}/calendar").json()
    assert result["last_month"] == expected.isoformat()


def test_large_cycle_limit_still_allows_nearby_calendar(client):
    key = create(client, start_date="2090-01-31", interval=365, cycle_limit=10000)
    result = client.get(f"/recurring-templates/{key}/calendar?month=2090-01-01")
    assert result.status_code == 200
    assert result.json()["upcoming"] == ["2090-01-31"]
