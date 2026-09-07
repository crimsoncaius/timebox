"""Persist structural protection for Task Occurrences.

Revision ID: 014_recurrence_occurrence_protection
Revises: 013_actual_block_recording
Create Date: 2026-08-30
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "014_recurrence_occurrence_protection"
down_revision = "013_actual_block_recording"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "recurrence_occurrences",
        sa.Column(
            "structurally_protected",
            sa.Boolean(),
            nullable=False,
            server_default=sa.false(),
        ),
    )
    # Reinterpret compatible existing scheduled recurrence data in place. A
    # durable ledger flag must reflect user-owned state already present at the
    # moment this migration lands, not only mutations performed afterward.
    op.execute(
        """
        UPDATE recurrence_occurrences
        SET structurally_protected = true
        WHERE task_id IN (
          SELECT root.id
          FROM tasks AS root
          WHERE root.recurrence_kind = 'scheduled'
            AND (
              root.status != 'open'
              OR root.is_blocked = true
              OR root.blocking_reason IS NOT NULL
              OR root.archived_at IS NOT NULL
              OR root.deleted_at IS NOT NULL
              OR root.recurrence_overrides_json != '[]'
              OR EXISTS (
                SELECT 1 FROM tasks AS child
                WHERE child.parent_id = root.id
                  AND (
                    child.checked = true
                    OR child.status != 'open'
                    OR child.archived_at IS NOT NULL
                    OR child.deleted_at IS NOT NULL
                    OR child.recurrence_overrides_json != '[]'
                  )
              )
              OR EXISTS (
                SELECT 1 FROM time_blocks AS block
                WHERE block.task_id = root.id
                   OR block.task_id IN (
                     SELECT child_with_block.id FROM tasks AS child_with_block
                     WHERE child_with_block.parent_id = root.id
                   )
              )
            )
        )
        """
    )


def downgrade() -> None:
    op.drop_column("recurrence_occurrences", "structurally_protected")
