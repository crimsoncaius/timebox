"""Add persistent one-shot Undo for Record actual as planned.

Revision ID: 013_actual_block_recording
Revises: 012_definitive_timeboxing_contracts
Create Date: 2026-08-30
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "013_actual_block_recording"
down_revision = "012_definitive_timeboxing_contracts"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "actual_block_record_operations",
        sa.Column("token", sa.Text(), nullable=False),
        sa.Column("actual_block_id", sa.Integer(), nullable=True),
        sa.Column("planned_block_id", sa.Integer(), nullable=True),
        sa.Column("invalidated_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("undone_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["actual_block_id"], ["time_blocks.id"], ondelete="SET NULL"
        ),
        sa.ForeignKeyConstraint(
            ["planned_block_id"], ["time_blocks.id"], ondelete="SET NULL"
        ),
        sa.PrimaryKeyConstraint("token"),
    )
    op.create_index(
        "ix_actual_block_record_operations_actual_block_id",
        "actual_block_record_operations",
        ["actual_block_id"],
        unique=True,
    )
    op.create_index(
        "ix_actual_block_record_operations_planned_block_id",
        "actual_block_record_operations",
        ["planned_block_id"],
    )


def downgrade() -> None:
    op.drop_index(
        "ix_actual_block_record_operations_planned_block_id",
        table_name="actual_block_record_operations",
    )
    op.drop_index(
        "ix_actual_block_record_operations_actual_block_id",
        table_name="actual_block_record_operations",
    )
    op.drop_table("actual_block_record_operations")
