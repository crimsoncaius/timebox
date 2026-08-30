from __future__ import annotations

import datetime as dt
from pathlib import Path
import tempfile

from alembic import command
from alembic.config import Config
import pytest
import sqlalchemy as sa

from app.core.config import get_settings
from app.db.base import Base
import app.models  # noqa: F401


UTC = dt.timezone.utc
BACKEND_ROOT = Path(__file__).resolve().parents[1]
CUTOVER_BASE = "014_recurrence_occurrence_protection"


@pytest.fixture
def cutover_tmp_path() -> Path:
    # Keep migration fixture databases under the writable workspace. The shared
    # Windows sandbox may deny pytest's user-profile temp root.
    with tempfile.TemporaryDirectory(prefix=".cutover-", dir=BACKEND_ROOT / "tests") as path:
        yield Path(path)


def _insert_task(connection: sa.Connection, task_id: int, title: str, **values) -> None:
    timestamp = values.pop("updated_at", dt.datetime(2026, 8, 20, 12, tzinfo=UTC))
    connection.execute(
        Base.metadata.tables["tasks"].insert().values(
            id=task_id,
            title=title,
            status=values.pop("status", "open"),
            created_at=values.pop("created_at", timestamp),
            updated_at=timestamp,
            **values,
        )
    )


def _prepare_database(tmp_path: Path, monkeypatch) -> tuple[sa.Engine, Config]:
    database_path = tmp_path / "legacy-cutover.sqlite3"
    engine = sa.create_engine(
        f"sqlite:///{database_path.as_posix()}", poolclass=sa.pool.NullPool
    )
    Base.metadata.create_all(engine)
    with engine.begin() as connection:
        connection.execute(sa.text("CREATE TABLE alembic_version (version_num VARCHAR(32) NOT NULL)"))
        connection.execute(
            sa.text("INSERT INTO alembic_version (version_num) VALUES (:revision)"),
            {"revision": CUTOVER_BASE},
        )
    monkeypatch.setattr(get_settings(), "database_url", f"sqlite:///{database_path.as_posix()}")
    return engine, Config(str(BACKEND_ROOT / "alembic.ini"))


def _upgrade(config: Config) -> None:
    command.upgrade(config, "head")


def _as_utc(value: str | dt.datetime | None) -> dt.datetime | None:
    if value is None:
        return None
    parsed = dt.datetime.fromisoformat(value) if isinstance(value, str) else value
    return parsed.replace(tzinfo=UTC) if parsed.tzinfo is None else parsed.astimezone(UTC)


def test_cutover_preserves_tasks_and_plans_but_deletes_legacy_actual_and_undo(
    cutover_tmp_path, monkeypatch
):
    engine, config = _prepare_database(cutover_tmp_path, monkeypatch)
    completed_at = dt.datetime(2026, 8, 21, 9, 30, tzinfo=UTC)
    with engine.begin() as connection:
        connection.execute(
            Base.metadata.tables["task_types"].insert().values(id=1, name="migration")
        )
        connection.execute(
            Base.metadata.tables["days"].insert().values(
                id=1, date=dt.date(2026, 8, 21), start_hour=8, end_hour=20,
                show_full_day=False,
            )
        )
        _insert_task(connection, 1, "Completed standalone", status="completed", updated_at=completed_at)
        _insert_task(
            connection, 2, "Incomplete standalone", status="open",
            completed_at=dt.datetime(2020, 1, 1, tzinfo=UTC),
        )
        _insert_task(connection, 10, "Parent")
        _insert_task(connection, 11, "Completed child", parent_id=10, status="completed")
        _insert_task(connection, 12, "In-progress child", parent_id=10, status="in_progress")
        _insert_task(connection, 13, "Scheduled child", parent_id=10, status="completed")
        connection.execute(
            Base.metadata.tables["time_blocks"].insert(),
            [
                {
                    "id": 100, "day_id": 1, "lane": "planned", "task_type_id": 1,
                    "task_id": 1, "note": "completed task plan stays", "start_minute": 480,
                    "end_minute": 540,
                },
                {
                    "id": 101, "day_id": 1, "lane": "planned", "task_type_id": 1,
                    "task_id": 13, "note": "child plan stays", "start_minute": 600,
                    "end_minute": 660,
                },
                {
                    "id": 102, "day_id": 1, "lane": "actual", "task_type_id": 1,
                    "task_id": 13, "planned_block_id": 101, "note": "linked legacy actual",
                    "start_minute": 610, "end_minute": 650,
                },
                {
                    "id": 103, "day_id": 1, "lane": "actual", "task_type_id": 1,
                    "task_id": None, "note": "manual legacy actual", "start_minute": 700,
                    "end_minute": 730,
                },
            ],
        )
        connection.execute(
            Base.metadata.tables["task_completion_operations"].insert().values(
                token="legacy-completion", root_task_id=1, snapshot_json="{}"
            )
        )
        connection.execute(
            Base.metadata.tables["actual_block_record_operations"].insert().values(
                token="legacy-record", actual_block_id=102, planned_block_id=101
            )
        )

    _upgrade(config)
    command.check(config)

    with engine.connect() as connection:
        tasks = {
            row.id: row
            for row in connection.execute(
                sa.text(
                    "SELECT id, parent_id, status, checked, completed_at, ready_to_plan, "
                    "is_blocked, reminder_at, deadline_date FROM tasks ORDER BY id"
                )
            )
        }
        assert tasks[1].status == "completed"
        assert _as_utc(tasks[1].completed_at) == completed_at
        assert tasks[2].completed_at is None
        assert tasks[11].parent_id == 10
        assert (tasks[11].status, tasks[11].checked, tasks[11].completed_at) == ("open", 1, None)
        assert tasks[12].parent_id == 10
        assert (tasks[12].status, tasks[12].checked) == ("open", 0)
        assert tasks[13].parent_id is None
        assert tasks[13].status == "completed"
        assert tasks[13].completed_at is not None

        blocks = connection.execute(
            sa.text(
                "SELECT id, lane, task_id, planned_block_id, note, start_minute, end_minute "
                "FROM time_blocks ORDER BY id"
            )
        ).all()
        assert [row.id for row in blocks] == [100, 101]
        assert [(row.task_id, row.note, row.start_minute, row.end_minute) for row in blocks] == [
            (1, "completed task plan stays", 480, 540),
            (13, "child plan stays", 600, 660),
        ]
        assert all(row.lane == "planned" and row.planned_block_id is None for row in blocks)
        assert connection.scalar(sa.text("SELECT count(*) FROM task_completion_operations")) == 0
        assert connection.scalar(sa.text("SELECT count(*) FROM actual_block_record_operations")) == 0


def test_cutover_detaches_every_stateful_or_malformed_child_without_orphaning_plans(
    cutover_tmp_path, monkeypatch
):
    engine, config = _prepare_database(cutover_tmp_path, monkeypatch)
    completed_at = dt.datetime(2026, 8, 22, 16, tzinfo=UTC)
    with engine.begin() as connection:
        connection.execute(Base.metadata.tables["task_types"].insert().values(id=1, name="owned"))
        connection.execute(
            Base.metadata.tables["days"].insert().values(
                id=1, date=dt.date(2026, 8, 22), start_hour=8, end_hour=20,
                show_full_day=False,
            )
        )
        _insert_task(connection, 10, "Parent")
        _insert_task(connection, 21, "Reminder", parent_id=10, reminder_at=completed_at)
        _insert_task(connection, 22, "Delivered reminder", parent_id=10, reminder_delivered_at=completed_at)
        _insert_task(connection, 23, "Blocked", parent_id=10, is_blocked=True, blocking_reason="wait")
        _insert_task(connection, 24, "Ready", parent_id=10, ready_to_plan=True)
        _insert_task(connection, 25, "Archived", parent_id=10, archived_at=completed_at)
        _insert_task(connection, 26, "Trashed", parent_id=10, deleted_at=completed_at)
        _insert_task(connection, 27, "Deadline date", parent_id=10, deadline_date=dt.date(2026, 9, 1))
        _insert_task(connection, 28, "Deadline instant", parent_id=10, deadline_at=completed_at)
        _insert_task(connection, 29, "Task type", parent_id=10, task_type_id=1)
        _insert_task(connection, 30, "Urgency", parent_id=10, urgency="high")
        _insert_task(connection, 31, "Importance", parent_id=10, importance="medium")
        _insert_task(
            connection, 32, "Recurrence identity", parent_id=10,
            recurrence_kind="scheduled", occurrence_key="2026-08-22",
        )
        _insert_task(connection, 33, "Planned", parent_id=10)
        _insert_task(
            connection, 34, "Completed reminded", parent_id=10, status="completed",
            reminder_at=completed_at, updated_at=completed_at,
        )
        _insert_task(connection, 35, "Lifecycle-free", parent_id=10, status="completed")
        _insert_task(connection, 40, "Malformed middle", parent_id=10)
        _insert_task(connection, 41, "Malformed grandchild", parent_id=40)
        _insert_task(connection, 42, "Missing parent", parent_id=999)
        connection.execute(
            Base.metadata.tables["time_blocks"].insert().values(
                id=500, day_id=1, lane="planned", task_type_id=1, task_id=33,
                note="must stay linked", start_minute=540, end_minute=600,
            )
        )

    _upgrade(config)

    detached_ids = set(range(21, 35)) | {40, 41, 42}
    with engine.connect() as connection:
        rows = {
            row.id: row
            for row in connection.execute(
                sa.text(
                    "SELECT id, parent_id, status, checked, completed_at, reminder_at, "
                    "archived_at, deleted_at, recurrence_kind FROM tasks"
                )
            )
        }
        assert all(rows[task_id].parent_id is None for task_id in detached_ids)
        assert rows[34].status == "completed"
        assert _as_utc(rows[34].completed_at) == completed_at
        assert rows[25].archived_at is not None
        assert rows[26].deleted_at is not None
        assert rows[32].recurrence_kind == "scheduled"
        assert rows[35].parent_id == 10
        assert (rows[35].status, rows[35].checked, rows[35].completed_at) == ("open", 1, None)
        plan = connection.execute(
            sa.text("SELECT id, task_id, note FROM time_blocks WHERE id = 500")
        ).one()
        assert (plan.task_id, plan.note) == (33, "must stay linked")


def test_cutover_reinterprets_scheduled_checklists_in_place_and_keeps_quota_semantics(
    cutover_tmp_path, monkeypatch
):
    engine, config = _prepare_database(cutover_tmp_path, monkeypatch)
    completed_at = dt.datetime(2026, 8, 23, 11, tzinfo=UTC)
    with engine.begin() as connection:
        connection.execute(Base.metadata.tables["task_types"].insert().values(id=1, name="series"))
        connection.execute(
            Base.metadata.tables["days"].insert().values(
                id=1, date=dt.date(2026, 8, 23), start_hour=8, end_hour=20,
                show_full_day=False,
            )
        )
        connection.execute(
            Base.metadata.tables["recurring_templates"].insert(),
            [
                {
                    "id": 1, "title": "Scheduled", "description": "", "mode": "scheduled",
                    "frequency": "daily", "start_date": dt.date(2026, 8, 23),
                    "generation_start_date": dt.date(2026, 8, 23),
                },
                {
                    "id": 2, "title": "Quota", "description": "", "mode": "quota",
                    "frequency": "weekly", "quota_count": 2,
                    "start_date": dt.date(2026, 8, 23),
                    "generation_start_date": dt.date(2026, 8, 23),
                },
            ],
        )
        shared = {
            "recurring_template_id": 1,
            "occurrence_key": "2026-08-23",
            "task_type_id": 1,
            "urgency": "high",
            "importance": "medium",
            "deadline_date": dt.date(2026, 8, 23),
        }
        _insert_task(connection, 100, "Scheduled", recurrence_kind="scheduled", ready_to_plan=True, **shared)
        _insert_task(connection, 101, "Completed checklist", parent_id=100, recurrence_kind="checklist", status="completed", **shared)
        _insert_task(
            connection, 102, "Edited checklist", parent_id=100, recurrence_kind="checklist",
            status="in_progress", recurrence_overrides_json='["title", "description"]', **shared,
        )
        _insert_task(connection, 103, "Planned checklist", parent_id=100, recurrence_kind="checklist", **shared)
        _insert_task(
            connection, 104, "Mismatched checklist", parent_id=100, recurrence_kind="checklist",
            **{**shared, "task_type_id": None},
        )
        _insert_task(
            connection, 200, "Quota", recurrence_kind="quota_parent", status="completed",
            completed_at=completed_at, recurring_template_id=2, occurrence_key="2026-W34",
            quota_period_start=dt.date(2026, 8, 17), quota_period_end=dt.date(2026, 8, 23),
            expected_sessions=2,
        )
        _insert_task(
            connection, 201, "Session 1", parent_id=200, recurrence_kind="quota_session",
            status="completed", updated_at=completed_at, recurring_template_id=2,
            occurrence_key="2026-W34", quota_period_start=dt.date(2026, 8, 17),
            quota_period_end=dt.date(2026, 8, 23), session_index=1,
        )
        _insert_task(
            connection, 202, "Session 2", parent_id=200, recurrence_kind="quota_session",
            status="open", completed_at=completed_at, recurring_template_id=2,
            occurrence_key="2026-W34", quota_period_start=dt.date(2026, 8, 17),
            quota_period_end=dt.date(2026, 8, 23), session_index=2,
        )
        connection.execute(
            Base.metadata.tables["recurrence_occurrences"].insert(),
            [
                {
                    "id": 1, "template_id": 1, "occurrence_key": "2026-08-23",
                    "cycle_start": dt.date(2026, 8, 23), "cycle_end": dt.date(2026, 8, 23),
                    "task_id": 100, "structurally_protected": True,
                },
                {
                    "id": 2, "template_id": 2, "occurrence_key": "2026-W34",
                    "cycle_start": dt.date(2026, 8, 17), "cycle_end": dt.date(2026, 8, 23),
                    "task_id": 200, "structurally_protected": False,
                },
            ],
        )
        connection.execute(
            Base.metadata.tables["time_blocks"].insert().values(
                id=600, day_id=1, lane="planned", task_type_id=1, task_id=103,
                start_minute=660, end_minute=720,
            )
        )

    _upgrade(config)

    with engine.connect() as connection:
        rows = {
            row.id: row
            for row in connection.execute(
                sa.text(
                    "SELECT id, parent_id, status, checked, completed_at, task_type_id, urgency, "
                    "importance, deadline_date, recurring_template_id, occurrence_key, "
                    "recurrence_kind, recurrence_overrides_json FROM tasks"
                )
            )
        }
        assert rows[101].parent_id == 100
        assert (rows[101].status, rows[101].checked, rows[101].completed_at) == ("open", 1, None)
        assert rows[102].parent_id == 100
        assert (rows[102].status, rows[102].checked) == ("open", 0)
        assert rows[102].recurrence_overrides_json == '["title", "description"]'
        for task_id in (101, 102):
            assert rows[task_id].recurring_template_id == 1
            assert rows[task_id].occurrence_key == "2026-08-23"
            assert rows[task_id].recurrence_kind == "checklist"
            assert rows[task_id].task_type_id is None
            assert rows[task_id].urgency is None
            assert rows[task_id].importance is None
            assert rows[task_id].deadline_date is None
        assert rows[103].parent_id is None
        assert rows[104].parent_id is None
        assert connection.scalar(sa.text("SELECT task_id FROM time_blocks WHERE id = 600")) == 103

        assert rows[200].parent_id is None
        assert rows[200].status == "completed"
        assert rows[200].completed_at is None
        assert rows[201].parent_id == 200
        assert _as_utc(rows[201].completed_at) == completed_at
        assert rows[202].parent_id == 200
        assert rows[202].completed_at is None

        ledgers = connection.execute(
            sa.text(
                "SELECT id, template_id, occurrence_key, task_id, suppressed, "
                "structurally_protected FROM recurrence_occurrences ORDER BY id"
            )
        ).all()
        assert [(row.id, row.task_id) for row in ledgers] == [(1, 100), (2, 200)]
        assert ledgers[0].structurally_protected == 1


def test_cutover_downgrade_is_explicitly_restore_only(cutover_tmp_path, monkeypatch):
    _, config = _prepare_database(cutover_tmp_path, monkeypatch)
    _upgrade(config)

    try:
        command.downgrade(config, CUTOVER_BASE)
    except RuntimeError as exc:
        assert "restore" in str(exc).lower()
        assert "irreversible" in str(exc).lower()
    else:
        raise AssertionError("The destructive cutover must not expose a fake downgrade")
