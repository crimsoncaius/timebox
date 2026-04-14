"""Replace hour_entries with time_blocks for existing databases.

Revision ID: 002_time_blocks
Revises: 001_initial
Create Date: 2026-04-14

Fresh installs that already have time_blocks from an updated 001_initial will
only skip creating time_blocks; hour_entries is dropped if present.
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op
from sqlalchemy import inspect

revision = "002_time_blocks"
down_revision = "001_initial"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    insp = inspect(bind)
    tables = insp.get_table_names()

    if "hour_entries" in tables:
        op.drop_table("hour_entries")

    if "time_blocks" not in tables:
        op.create_table(
            "time_blocks",
            sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
            sa.Column("day_id", sa.Integer(), nullable=False),
            sa.Column("lane", sa.String(length=16), nullable=False),
            sa.Column("title", sa.Text(), nullable=False, server_default=""),
            sa.Column("start_minute", sa.Integer(), nullable=False),
            sa.Column("end_minute", sa.Integer(), nullable=False),
            sa.Column(
                "created_at",
                sa.DateTime(timezone=True),
                server_default=sa.text("now()"),
                nullable=False,
            ),
            sa.Column(
                "updated_at",
                sa.DateTime(timezone=True),
                server_default=sa.text("now()"),
                nullable=False,
            ),
            sa.ForeignKeyConstraint(["day_id"], ["days.id"], ondelete="CASCADE"),
            sa.PrimaryKeyConstraint("id"),
        )
        op.create_index(op.f("ix_time_blocks_day_id"), "time_blocks", ["day_id"], unique=False)


def downgrade() -> None:
    bind = op.get_bind()
    insp = inspect(bind)
    tables = insp.get_table_names()

    if "time_blocks" in tables:
        op.drop_index(op.f("ix_time_blocks_day_id"), table_name="time_blocks")
        op.drop_table("time_blocks")

    if "hour_entries" not in tables:
        op.create_table(
            "hour_entries",
            sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
            sa.Column("day_id", sa.Integer(), nullable=False),
            sa.Column("hour_index", sa.Integer(), nullable=False),
            sa.Column("planned_task", sa.Text(), nullable=True),
            sa.Column("status", sa.String(length=32), nullable=False, server_default="unrated"),
            sa.Column("note", sa.Text(), nullable=True),
            sa.Column(
                "created_at",
                sa.DateTime(timezone=True),
                server_default=sa.text("now()"),
                nullable=False,
            ),
            sa.Column(
                "updated_at",
                sa.DateTime(timezone=True),
                server_default=sa.text("now()"),
                nullable=False,
            ),
            sa.ForeignKeyConstraint(["day_id"], ["days.id"], ondelete="CASCADE"),
            sa.PrimaryKeyConstraint("id"),
            sa.UniqueConstraint("day_id", "hour_index", name="uq_hour_entries_day_hour"),
        )
