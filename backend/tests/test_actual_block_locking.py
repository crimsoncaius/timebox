from __future__ import annotations

from sqlalchemy import event
from sqlalchemy.dialects import postgresql
from sqlalchemy.orm import Session

from app.services import actual_block_service, day_service, task_completion_service
from app.services.battle_plan import _shared


def _postgresql_sql(statement) -> str:
    return str(
        statement.compile(
            dialect=postgresql.dialect(),
            compile_kwargs={"literal_binds": True},
        )
    )


def test_correspondence_mutation_selects_lock_rows_on_postgresql():
    statements = [
        actual_block_service._planned_select(11, for_update=True),
        actual_block_service._actual_select(22, for_update=True),
        actual_block_service._record_operation_select("undo-token", for_update=True),
        actual_block_service._record_operation_for_actual_select(22, for_update=True),
        day_service._day_block_select(33, 11, for_update=True),
        day_service._task_select(44, for_update=True),
        actual_block_service._task_select(44, for_update=True),
        task_completion_service._task_select(44, for_update=True),
        task_completion_service._planned_for_task_select(44, for_update=True),
        task_completion_service._actual_select(22, for_update=True),
        task_completion_service._operation_select("completion-token", for_update=True),
        _shared._task_select(44, for_update=True),
    ]

    for statement in statements:
        assert "FOR UPDATE" in _postgresql_sql(statement)


def test_actual_block_select_locks_only_the_time_block_row_on_postgresql():
    sql = _postgresql_sql(
        actual_block_service._actual_select(22, for_update=True)
    )

    assert "FOR UPDATE OF time_blocks" in sql


def test_subtask_check_and_uncheck_do_not_lock_nullable_parent_join(client):
    parent = client.post("/tasks", json={"title": "Parent"}).json()
    child = client.post(
        "/tasks", json={"title": "Checkpoint", "parent_id": parent["id"]}
    ).json()
    statements = []

    def capture_statement(state):
        if state.is_select:
            statements.append(str(state.statement.compile(dialect=postgresql.dialect())))

    event.listen(Session, "do_orm_execute", capture_statement)
    try:
        for action, checked in [("check", True), ("uncheck", False)]:
            statements.clear()
            response = client.post(f"/subtasks/{child['id']}/{action}")
            assert response.status_code == 200, response.text
            assert response.json()["checked"] is checked
            locking_reads = [sql for sql in statements if "FOR UPDATE" in sql]
            assert len(locking_reads) >= 2  # Parent, then Subtask.
            for sql in locking_reads:
                if "LEFT OUTER JOIN" in sql:
                    assert "FOR UPDATE OF tasks" in sql
    finally:
        event.remove(Session, "do_orm_execute", capture_statement)
