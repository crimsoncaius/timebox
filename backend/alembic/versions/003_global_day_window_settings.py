"""Add app_settings singleton for global day window.

Revision ID: 003_app_settings
Revises: 002_time_blocks
Create Date: 2026-04-14

"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "003_app_settings"
down_revision = "002_time_blocks"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "app_settings",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
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
    )
    op.execute(
        sa.text(
            "INSERT INTO app_settings (id, start_hour, end_hour, show_full_day) "
            "VALUES (1, 8, 20, false)"
        )
    )


def downgrade() -> None:
    op.drop_table("app_settings")
