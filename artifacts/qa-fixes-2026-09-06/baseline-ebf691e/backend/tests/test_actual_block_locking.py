from __future__ import annotations

from sqlalchemy.dialects import postgresql

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
