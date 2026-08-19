"""Make blocked a task condition instead of a workflow status.

Revision ID: 010_blocked_task_condition
Revises: 009_recurring_tasks
Create Date: 2026-08-19
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "010_blocked_task_condition"
down_revision = "009_recurring_tasks"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "tasks",
        sa.Column("is_blocked", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.add_column("tasks", sa.Column("blocking_reason", sa.Text(), nullable=True))
    op.execute("UPDATE tasks SET status = 'open', is_blocked = true WHERE status = 'blocked'")


def downgrade() -> None:
    op.execute("UPDATE tasks SET status = 'blocked' WHERE is_blocked = true AND status = 'open'")
    op.drop_column("tasks", "blocking_reason")
    op.drop_column("tasks", "is_blocked")
