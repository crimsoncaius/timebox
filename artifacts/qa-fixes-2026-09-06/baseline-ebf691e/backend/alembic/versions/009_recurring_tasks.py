"""Add recurring Battle Plan templates, occurrence ledger, and task metadata.

Revision ID: 009_recurring_tasks
Revises: 008_ready_to_plan_task_blocks
Create Date: 2026-08-16
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "009_recurring_tasks"
down_revision = "008_ready_to_plan_task_blocks"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("app_settings", sa.Column("week_start", sa.Text(), nullable=False, server_default="monday"))
    op.create_table(
        "recurring_templates",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("project_id", sa.Integer(), sa.ForeignKey("projects.id", ondelete="SET NULL"), nullable=True),
        sa.Column("task_type_id", sa.Integer(), sa.ForeignKey("task_types.id", ondelete="SET NULL"), nullable=True),
        sa.Column("title", sa.Text(), nullable=False),
        sa.Column("description", sa.Text(), nullable=False, server_default=""),
        sa.Column("mode", sa.String(16), nullable=False),
        sa.Column("status", sa.String(16), nullable=False, server_default="active"),
        sa.Column("frequency", sa.String(16), nullable=False),
        sa.Column("interval", sa.Integer(), nullable=False, server_default="1"),
        sa.Column("weekdays_json", sa.Text(), nullable=False, server_default="[]"),
        sa.Column("month_day", sa.Integer(), nullable=True),
        sa.Column("quota_count", sa.Integer(), nullable=True),
        sa.Column("start_date", sa.Date(), nullable=False),
        sa.Column("generation_start_date", sa.Date(), nullable=False),
        sa.Column("end_date", sa.Date(), nullable=True),
        sa.Column("cycle_limit", sa.Integer(), nullable=True),
        sa.Column("urgency", sa.String(16), nullable=True),
        sa.Column("importance", sa.String(16), nullable=True),
        sa.Column("paused_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("ended_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_recurring_templates_project_id", "recurring_templates", ["project_id"])
    op.create_index("ix_recurring_templates_task_type_id", "recurring_templates", ["task_type_id"])
    op.create_table(
        "recurring_checklist_items",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("template_id", sa.Integer(), sa.ForeignKey("recurring_templates.id", ondelete="CASCADE"), nullable=False),
        sa.Column("title", sa.Text(), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
    )
    op.create_index("ix_recurring_checklist_items_template_id", "recurring_checklist_items", ["template_id"])

    for name, column in (
        ("recurring_template_id", sa.Column("recurring_template_id", sa.Integer(), sa.ForeignKey("recurring_templates.id", ondelete="SET NULL"), nullable=True)),
        ("occurrence_key", sa.Column("occurrence_key", sa.Text(), nullable=True)),
        ("recurrence_kind", sa.Column("recurrence_kind", sa.Text(), nullable=True)),
        ("quota_period_start", sa.Column("quota_period_start", sa.Date(), nullable=True)),
        ("quota_period_end", sa.Column("quota_period_end", sa.Date(), nullable=True)),
        ("expected_sessions", sa.Column("expected_sessions", sa.Integer(), nullable=True)),
        ("session_index", sa.Column("session_index", sa.Integer(), nullable=True)),
        ("recurrence_overrides_json", sa.Column("recurrence_overrides_json", sa.Text(), nullable=False, server_default="[]")),
    ):
        op.add_column("tasks", column)
    op.create_index("ix_tasks_recurring_template_id", "tasks", ["recurring_template_id"])

    op.create_table(
        "recurrence_occurrences",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("template_id", sa.Integer(), sa.ForeignKey("recurring_templates.id", ondelete="CASCADE"), nullable=False),
        sa.Column("occurrence_key", sa.Text(), nullable=False),
        sa.Column("cycle_start", sa.Date(), nullable=False),
        sa.Column("cycle_end", sa.Date(), nullable=False),
        sa.Column("task_id", sa.Integer(), sa.ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True),
        sa.Column("suppressed", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.UniqueConstraint("template_id", "occurrence_key", name="uq_recurrence_occurrence_key"),
    )
    op.create_index("ix_recurrence_occurrences_template_id", "recurrence_occurrences", ["template_id"])
    op.create_index("ix_recurrence_occurrences_task_id", "recurrence_occurrences", ["task_id"])


def downgrade() -> None:
    op.drop_table("recurrence_occurrences")
    for name in ("recurrence_overrides_json", "session_index", "expected_sessions", "quota_period_end", "quota_period_start", "recurrence_kind", "occurrence_key", "recurring_template_id"):
        op.drop_column("tasks", name)
    op.drop_table("recurring_checklist_items")
    op.drop_table("recurring_templates")
    op.drop_column("app_settings", "week_start")
