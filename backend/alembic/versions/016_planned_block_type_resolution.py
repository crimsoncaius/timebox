"""Make canonical Task Type materialization concurrency-safe.

Revision ID: 016_planned_block_type_resolution
Revises: 015_definitive_legacy_cutover
Create Date: 2026-09-01

"""

from __future__ import annotations

from alembic import op


revision = "016_planned_block_type_resolution"
down_revision = "015_definitive_legacy_cutover"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # A pre-migration race may have left duplicate canonical paths. Retarget all
    # references to the oldest row before enforcing the invariant used by the
    # atomic INSERT ... ON CONFLICT path.
    for table in ("time_blocks", "tasks", "recurring_templates"):
        op.execute(
            f"""
            UPDATE {table}
            SET task_type_id = (
              SELECT MIN(canonical.id)
              FROM task_types AS duplicate
              JOIN task_types AS canonical ON canonical.name = duplicate.name
              WHERE duplicate.id = {table}.task_type_id
            )
            WHERE task_type_id IN (
              SELECT duplicate.id
              FROM task_types AS duplicate
              JOIN task_types AS canonical ON canonical.name = duplicate.name
              WHERE duplicate.id > canonical.id
            )
            """
        )
    op.execute(
        """
        DELETE FROM task_types
        WHERE id NOT IN (SELECT MIN(id) FROM task_types GROUP BY name)
        """
    )
    op.create_index(
        "uq_task_types_name",
        "task_types",
        ["name"],
        unique=True,
        if_not_exists=True,
    )


def downgrade() -> None:
    op.drop_index(
        "uq_task_types_name", table_name="task_types", if_exists=True
    )
