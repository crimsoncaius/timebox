"""Canonicalize task_types.name as slash-delimited materialized paths.

Revision ID: 006_task_type_materialized_paths
Revises: 005_planned_block_link
Create Date: 2026-04-15

"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

from app.services.task_type_paths import canonicalize_task_type_path

revision = "006_task_type_materialized_paths"
down_revision = "005_planned_block_link"
branch_labels = None
depends_on = None


def upgrade() -> None:
    conn = op.get_bind()
    rows = conn.execute(sa.text("select id, name from task_types order by id")).mappings().all()

    seen: dict[str, int] = {}
    for row in rows:
        try:
            canonical = canonicalize_task_type_path(row["name"])
        except ValueError as exc:
            raise RuntimeError(
                f"Invalid task type path during migration: {row['name']!r}: {exc}"
            ) from exc

        existing_id = seen.get(canonical)
        if existing_id is not None and existing_id != row["id"]:
            raise RuntimeError(
                f"Canonical task type collision during migration: {row['name']!r} -> {canonical!r}"
            )

        seen[canonical] = row["id"]
        conn.execute(
            sa.text("update task_types set name = :name where id = :id"),
            {"id": row["id"], "name": canonical},
        )


def downgrade() -> None:
    # Irreversible data normalization; leave values as canonical paths.
    pass
