"""Add planned_block_id self-FK on time_blocks for Actual rows linked from Planned.

Revision ID: 005_planned_block_link
Revises: 004_task_types
Create Date: 2026-04-14

"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op
from sqlalchemy import inspect

revision = "005_planned_block_link"
down_revision = "004_task_types"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    insp = inspect(bind)
    cols = {c["name"] for c in insp.get_columns("time_blocks")} if "time_blocks" in insp.get_table_names() else set()
    if "planned_block_id" in cols:
        return
    op.add_column(
        "time_blocks",
        sa.Column("planned_block_id", sa.Integer(), nullable=True),
    )
    op.create_foreign_key(
        "fk_time_blocks_planned_block_id",
        "time_blocks",
        "time_blocks",
        ["planned_block_id"],
        ["id"],
        ondelete="SET NULL",
    )
    op.create_index(
        "ix_time_blocks_planned_block_id",
        "time_blocks",
        ["planned_block_id"],
        unique=False,
    )


def downgrade() -> None:
    bind = op.get_bind()
    insp = inspect(bind)
    if "time_blocks" not in insp.get_table_names():
        return
    fks = {fk["name"] for fk in insp.get_foreign_keys("time_blocks")}
    if "fk_time_blocks_planned_block_id" in fks:
        op.drop_constraint("fk_time_blocks_planned_block_id", "time_blocks", type_="foreignkey")
    indexes = {ix["name"] for ix in insp.get_indexes("time_blocks")}
    if "ix_time_blocks_planned_block_id" in indexes:
        op.drop_index("ix_time_blocks_planned_block_id", table_name="time_blocks")
    cols = {c["name"] for c in insp.get_columns("time_blocks")}
    if "planned_block_id" in cols:
        op.drop_column("time_blocks", "planned_block_id")
