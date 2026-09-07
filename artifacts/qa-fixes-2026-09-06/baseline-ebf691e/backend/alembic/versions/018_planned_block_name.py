"""Add an optional Block Name to time blocks.

Revision ID: 018_planned_block_name
Revises: 017_non_accumulating_recurrence
Create Date: 2026-09-04

"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op


revision = "018_planned_block_name"
down_revision = "017_non_accumulating_recurrence"
branch_labels = None
depends_on = None


def upgrade() -> None:
    columns = {
        column["name"]
        for column in sa.inspect(op.get_bind()).get_columns("time_blocks")
    }
    if "name" not in columns:
        op.add_column("time_blocks", sa.Column("name", sa.Text(), nullable=True))


def downgrade() -> None:
    columns = {
        column["name"]
        for column in sa.inspect(op.get_bind()).get_columns("time_blocks")
    }
    if "name" in columns:
        op.drop_column("time_blocks", "name")
