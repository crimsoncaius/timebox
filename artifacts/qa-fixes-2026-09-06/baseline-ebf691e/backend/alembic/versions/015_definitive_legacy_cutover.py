"""Irreversibly convert legacy Task and Time Block data.

Revision ID: 015_definitive_legacy_cutover
Revises: 014_recurrence_occurrence_protection
Create Date: 2026-08-30

This is the one-time destructive data cutover. It intentionally deletes every
pre-cutover Actual Block and every Undo snapshot. A database backup, not an
Alembic downgrade, is the only recovery mechanism.
"""

from __future__ import annotations

from alembic import op


revision = "015_definitive_legacy_cutover"
down_revision = "014_recurrence_occurrence_protection"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Preserve independently meaningful legacy children as standalone Battle
    # Plan Tasks. Classification happens before Actual history is deleted so a
    # child that owned any block cannot be silently collapsed into a Subtask.
    # Quota Session Tasks remain parented because they are independently
    # completable by definition, not legacy checklist rows.
    op.execute(
        """
        UPDATE tasks AS child
        SET parent_id = NULL
        WHERE child.parent_id IS NOT NULL
          AND COALESCE(child.recurrence_kind, '') != 'quota_session'
          AND (
            NOT EXISTS (
              SELECT 1 FROM tasks AS parent WHERE parent.id = child.parent_id
            )
            OR EXISTS (
              SELECT 1 FROM tasks AS parent
              WHERE parent.id = child.parent_id AND parent.parent_id IS NOT NULL
            )
            OR EXISTS (
              SELECT 1 FROM tasks AS grandchild
              WHERE grandchild.parent_id = child.id
            )
            OR EXISTS (
              SELECT 1 FROM time_blocks AS block WHERE block.task_id = child.id
            )
            OR child.ready_to_plan = true
            OR child.is_blocked = true
            OR child.status = 'blocked'
            OR child.blocking_reason IS NOT NULL
            OR child.reminder_at IS NOT NULL
            OR child.reminder_delivered_at IS NOT NULL
            OR child.archived_at IS NOT NULL
            OR child.deleted_at IS NOT NULL
            OR NOT EXISTS (
              SELECT 1
              FROM tasks AS parent
              WHERE parent.id = child.parent_id
                AND (
                  (
                    child.recurrence_kind IS NULL
                    AND child.recurring_template_id IS NULL
                    AND child.occurrence_key IS NULL
                    AND child.quota_period_start IS NULL
                    AND child.quota_period_end IS NULL
                    AND child.expected_sessions IS NULL
                    AND child.session_index IS NULL
                    AND replace(COALESCE(child.recurrence_overrides_json, '[]'), ' ', '') = '[]'
                    AND child.project_id IS NOT DISTINCT FROM parent.project_id
                    AND child.task_type_id IS NULL
                    AND child.urgency IS NULL
                    AND child.importance IS NULL
                    AND child.deadline_date IS NULL
                    AND child.deadline_at IS NULL
                  )
                  OR
                  (
                    child.recurrence_kind = 'checklist'
                    AND parent.parent_id IS NULL
                    AND parent.recurrence_kind = 'scheduled'
                    AND child.recurring_template_id IS NOT NULL
                    AND child.recurring_template_id IS NOT DISTINCT FROM parent.recurring_template_id
                    AND child.occurrence_key IS NOT NULL
                    AND child.occurrence_key IS NOT DISTINCT FROM parent.occurrence_key
                    AND child.project_id IS NOT DISTINCT FROM parent.project_id
                    AND child.task_type_id IS NOT DISTINCT FROM parent.task_type_id
                    AND child.urgency IS NOT DISTINCT FROM parent.urgency
                    AND child.importance IS NOT DISTINCT FROM parent.importance
                    AND child.deadline_date IS NOT DISTINCT FROM parent.deadline_date
                    AND child.deadline_at IS NOT DISTINCT FROM parent.deadline_at
                    AND child.quota_period_start IS NULL
                    AND child.quota_period_end IS NULL
                    AND child.expected_sessions IS NULL
                    AND child.session_index IS NULL
                    AND replace(COALESCE(child.recurrence_overrides_json, '[]'), ' ', '') IN (
                      '[]', '["title"]', '["description"]',
                      '["description","title"]', '["title","description"]'
                    )
                  )
                )
            )
          )
        """
    )

    # Every remaining non-quota child is now a definitive Subtask. Its explicit
    # check fact comes only from legacy completion; all Task lifecycle state is
    # removed. Scheduled checklist provenance stays for occurrence snapshots.
    op.execute(
        """
        UPDATE tasks
        SET checked = CASE WHEN status = 'completed' THEN true ELSE false END,
            status = 'open',
            completed_at = NULL,
            last_non_completed_status = NULL,
            ready_to_plan = false,
            is_blocked = false,
            blocking_reason = NULL,
            task_type_id = NULL,
            urgency = NULL,
            importance = NULL,
            deadline_date = NULL,
            deadline_at = NULL,
            reminder_at = NULL,
            reminder_delivered_at = NULL
        WHERE parent_id IS NOT NULL
          AND COALESCE(recurrence_kind, '') != 'quota_session'
        """
    )

    # Legacy completion Undo and Record-actual-as-planned Undo snapshots encode
    # rows and behavior that this cutover deliberately destroys.
    op.execute("DELETE FROM task_completion_operations")
    op.execute("DELETE FROM actual_block_record_operations")

    # Correspondence is discarded before Actual deletion. Planned rows retain
    # their identities and all user-visible data.
    op.execute("UPDATE time_blocks SET planned_block_id = NULL WHERE planned_block_id IS NOT NULL")
    op.execute("DELETE FROM time_blocks WHERE lane = 'actual'")

    # Only independently completable rows receive a completion instant. Quota
    # Tracker completion remains derived; completed Session Tasks are genuine
    # Task Completions and are backfilled like standalone Battle Plan Tasks.
    op.execute(
        """
        UPDATE tasks
        SET completed_at = CASE
              WHEN status = 'completed'
               AND COALESCE(recurrence_kind, '') != 'quota_parent'
               AND (parent_id IS NULL OR recurrence_kind = 'quota_session')
              THEN updated_at
              ELSE NULL
            END,
            last_non_completed_status = NULL
        """
    )
    op.execute(
        """
        UPDATE tasks
        SET checked = false
        WHERE parent_id IS NULL OR recurrence_kind = 'quota_session'
        """
    )


def downgrade() -> None:
    raise RuntimeError(
        "Migration 015_definitive_legacy_cutover is irreversible; restore the "
        "required pre-cutover database backup instead of downgrading."
    )
