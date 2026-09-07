"""Add Task completion lifecycle and immediate Undo records.

Revision ID: 011_task_completion_lifecycle
Revises: 010_blocked_task_condition
Create Date: 2026-08-25
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "011_task_completion_lifecycle"
down_revision = "010_blocked_task_condition"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "tasks",
        sa.Column("last_non_completed_status", sa.String(length=24), nullable=True),
    )
    op.create_table(
        "task_completion_operations",
        sa.Column("token", sa.Text(), nullable=False),
        sa.Column("root_task_id", sa.Integer(), nullable=True),
        sa.Column("snapshot_json", sa.Text(), nullable=False),
        sa.Column("undone_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["root_task_id"], ["tasks.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("token"),
    )
    op.create_index(
        "ix_task_completion_operations_root_task_id",
        "task_completion_operations",
        ["root_task_id"],
    )
    op.create_index(
        "uq_time_blocks_planned_completion",
        "time_blocks",
        ["planned_block_id"],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index("uq_time_blocks_planned_completion", table_name="time_blocks")
    op.drop_table("task_completion_operations")
    op.drop_column("tasks", "last_non_completed_status")
