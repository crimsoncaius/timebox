from __future__ import annotations

from sqlalchemy.dialects import postgresql

from app.services import actual_block_service, day_service


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
    ]

    for statement in statements:
        assert "FOR UPDATE" in _postgresql_sql(statement)
