"""Default recurring occurrences to non-accumulating.

Revision ID: 017_non_accumulating_recurrence
Revises: 016_planned_block_type_resolution
Create Date: 2026-09-02

"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op


revision = "017_non_accumulating_recurrence"
down_revision = "016_planned_block_type_resolution"
branch_labels = None
depends_on = None


def upgrade() -> None:
    recurring_columns = {
        column["name"]
        for column in sa.inspect(op.get_bind()).get_columns("recurring_templates")
    }
    if "keep_unfinished_overdue" not in recurring_columns:
        op.add_column(
            "recurring_templates",
            sa.Column(
                "keep_unfinished_overdue",
                sa.Boolean(),
                nullable=False,
                server_default=sa.false(),
            ),
        )
    if "position" not in recurring_columns:
        op.add_column(
            "recurring_templates",
            sa.Column("position", sa.Integer(), nullable=False, server_default="0"),
        )

    occurrence_columns = {
        column["name"]
        for column in sa.inspect(op.get_bind()).get_columns("recurrence_occurrences")
    }
    if "skipped" not in occurrence_columns:
        op.add_column(
            "recurrence_occurrences",
            sa.Column("skipped", sa.Boolean(), nullable=False, server_default=sa.false()),
        )
    op.execute(
        """
        UPDATE recurring_templates
        SET position = COALESCE(
          (SELECT MIN(tasks.position) FROM tasks WHERE tasks.recurring_template_id = recurring_templates.id),
          recurring_templates.id
        )
        """
    )


def downgrade() -> None:
    occurrence_columns = {
        column["name"]
        for column in sa.inspect(op.get_bind()).get_columns("recurrence_occurrences")
    }
    if "skipped" in occurrence_columns:
        op.drop_column("recurrence_occurrences", "skipped")

    recurring_columns = {
        column["name"]
        for column in sa.inspect(op.get_bind()).get_columns("recurring_templates")
    }
    if "position" in recurring_columns:
        op.drop_column("recurring_templates", "position")
    if "keep_unfinished_overdue" in recurring_columns:
        op.drop_column("recurring_templates", "keep_unfinished_overdue")
