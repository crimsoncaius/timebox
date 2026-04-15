"""Add task_types; time_blocks use task_type_id + note; migrate from title.

Revision ID: 004_task_types
Revises: 003_app_settings
Create Date: 2026-04-14

"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op
from sqlalchemy import inspect, text

revision = "004_task_types"
down_revision = "003_app_settings"
branch_labels = None
depends_on = None


def _column_names(bind, table: str) -> set[str]:
    insp = inspect(bind)
    return {c["name"] for c in insp.get_columns(table)}


def _fk_names(bind, table: str) -> set[str]:
    insp = inspect(bind)
    return {fk["name"] for fk in insp.get_foreign_keys(table)}


def upgrade() -> None:
    bind = op.get_bind()
    dialect = bind.dialect.name
    tables = inspect(bind).get_table_names()

    if "task_types" not in tables:
        op.create_table(
            "task_types",
            sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
            sa.Column("name", sa.Text(), nullable=False),
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

    tb_cols = _column_names(bind, "time_blocks") if "time_blocks" in inspect(bind).get_table_names() else set()
    has_title = "title" in tb_cols
    if "task_type_id" not in tb_cols:
        op.add_column(
            "time_blocks",
            sa.Column("task_type_id", sa.Integer(), nullable=True),
        )
    if "note" not in tb_cols:
        op.add_column(
            "time_blocks",
            sa.Column("note", sa.Text(), nullable=True),
        )

    tb_cols = _column_names(bind, "time_blocks")
    has_title = "title" in tb_cols

    if has_title:
        if dialect == "sqlite":
            now_expr = "datetime('now')"
            trim_expr = "trim(time_blocks.title)"
            group_expr = "lower(trim(time_blocks.title))"
        else:
            now_expr = "now()"
            trim_expr = "trim(time_blocks.title::text)"
            group_expr = "lower(trim(time_blocks.title::text))"

        bind.execute(
            text(
                f"""
                INSERT INTO task_types (name, created_at, updated_at)
                SELECT min({trim_expr}), {now_expr}, {now_expr}
                FROM time_blocks
                WHERE length(trim(time_blocks.title)) > 0
                GROUP BY {group_expr}
                """
            )
        )
        bind.execute(
            text(
                f"""
                INSERT INTO task_types (name, created_at, updated_at)
                SELECT 'unspecified', {now_expr}, {now_expr}
                WHERE NOT EXISTS (
                    SELECT 1 FROM task_types WHERE lower(name) = 'unspecified'
                )
                """
            )
        )
        bind.execute(
            text(
                """
                UPDATE time_blocks
                SET task_type_id = (
                    SELECT tt.id FROM task_types tt
                    WHERE lower(tt.name) = lower(trim(time_blocks.title))
                )
                WHERE length(trim(time_blocks.title)) > 0
                """
            )
        )
        bind.execute(
            text(
                """
                UPDATE time_blocks
                SET task_type_id = (SELECT id FROM task_types WHERE lower(name) = 'unspecified' LIMIT 1)
                WHERE task_type_id IS NULL
                """
            )
        )

    fks = _fk_names(bind, "time_blocks")
    if "fk_time_blocks_task_type_id" not in fks and "time_blocks" in inspect(bind).get_table_names():
        op.create_foreign_key(
            "fk_time_blocks_task_type_id",
            "time_blocks",
            "task_types",
            ["task_type_id"],
            ["id"],
            ondelete="RESTRICT",
        )

    if "task_type_id" in _column_names(bind, "time_blocks"):
        op.alter_column(
            "time_blocks",
            "task_type_id",
            existing_type=sa.Integer(),
            nullable=False,
        )

    if has_title and "title" in _column_names(bind, "time_blocks"):
        op.drop_column("time_blocks", "title")


def downgrade() -> None:
    bind = op.get_bind()
    tb_cols = _column_names(bind, "time_blocks")
    if "fk_time_blocks_task_type_id" in _fk_names(bind, "time_blocks"):
        op.drop_constraint("fk_time_blocks_task_type_id", "time_blocks", type_="foreignkey")

    if "title" not in tb_cols:
        op.add_column(
            "time_blocks",
            sa.Column("title", sa.Text(), nullable=False, server_default=""),
        )
        bind.execute(
            text(
                """
                UPDATE time_blocks
                SET title = COALESCE(
                    (SELECT tt.name FROM task_types tt WHERE tt.id = time_blocks.task_type_id),
                    ''
                )
                """
            )
        )
        op.alter_column("time_blocks", "title", server_default=None)

    if "task_type_id" in tb_cols:
        op.drop_column("time_blocks", "task_type_id")
    if "note" in tb_cols:
        op.drop_column("time_blocks", "note")

    tables = inspect(bind).get_table_names()
    if "task_types" in tables:
        op.drop_table("task_types")
