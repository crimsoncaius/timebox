"""initial schema

Revision ID: 001_initial
Revises:
Create Date: 2026-04-13

"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "001_initial"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "days",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("start_hour", sa.Integer(), nullable=False, server_default="8"),
        sa.Column("end_hour", sa.Integer(), nullable=False, server_default="20"),
        sa.Column("show_full_day", sa.Boolean(), nullable=False, server_default=sa.text("false")),
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
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("date", name="uq_days_date"),
    )
    op.create_index(op.f("ix_days_date"), "days", ["date"], unique=False)

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
    op.drop_index(op.f("ix_time_blocks_day_id"), table_name="time_blocks")
    op.drop_table("time_blocks")
    op.drop_index(op.f("ix_days_date"), table_name="days")
    op.drop_table("days")
