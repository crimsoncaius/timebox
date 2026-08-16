"""Link ready-to-plan tasks to scheduled time blocks.

Revision ID: 008_ready_to_plan_task_blocks
Revises: 007_battle_plan
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "008_ready_to_plan_task_blocks"
down_revision = "007_battle_plan"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "tasks",
        sa.Column("ready_to_plan", sa.Boolean(), server_default=sa.false(), nullable=False),
    )
    with op.batch_alter_table("time_blocks") as batch_op:
        batch_op.add_column(sa.Column("task_id", sa.Integer(), nullable=True))
        batch_op.create_foreign_key(
            "fk_time_blocks_task_id_tasks",
            "tasks",
            ["task_id"],
            ["id"],
            ondelete="SET NULL",
        )
    op.create_index("ix_time_blocks_task_id", "time_blocks", ["task_id"])


def downgrade() -> None:
    op.drop_index("ix_time_blocks_task_id", table_name="time_blocks")
    with op.batch_alter_table("time_blocks") as batch_op:
        batch_op.drop_constraint("fk_time_blocks_task_id_tasks", type_="foreignkey")
        batch_op.drop_column("task_id")
    op.drop_column("tasks", "ready_to_plan")
