"""Add definitive Task, Subtask, Planned Block, and Actual Block contracts.

Revision ID: 012_definitive_timeboxing_contracts
Revises: 011_task_completion_lifecycle
Create Date: 2026-08-30

This revision is deliberately forward/additive. Legacy Actual rows retain their
day/grid interval until the coordinated cutover migration deletes them; definitive
Actual rows are identified by a non-null ``start_at`` instant.
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "012_definitive_timeboxing_contracts"
down_revision = "011_task_completion_lifecycle"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Alembic creates version_num as VARCHAR(32), but this revision identifier is
    # longer. PostgreSQL enforces that limit when Alembic records the completed
    # revision after this function returns; SQLite does not enforce it.
    if op.get_bind().dialect.name == "postgresql":
        op.alter_column(
            "alembic_version",
            "version_num",
            existing_type=sa.String(length=32),
            type_=sa.String(length=255),
            existing_nullable=False,
        )

    op.add_column(
        "tasks",
        sa.Column("checked", sa.Boolean(), server_default=sa.false(), nullable=False),
    )
    op.add_column("tasks", sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column(
        "tasks",
        sa.Column("version", sa.Integer(), server_default="1", nullable=False),
    )
    op.add_column(
        "task_completion_operations",
        sa.Column("completed_task_version", sa.Integer(), nullable=True),
    )
    op.create_index(
        "uq_recurrence_occurrences_task_id",
        "recurrence_occurrences",
        ["task_id"],
        unique=True,
        postgresql_where=sa.text("task_id IS NOT NULL"),
        sqlite_where=sa.text("task_id IS NOT NULL"),
    )

    op.add_column("time_blocks", sa.Column("start_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("time_blocks", sa.Column("end_at", sa.DateTime(timezone=True), nullable=True))
    op.alter_column("time_blocks", "day_id", existing_type=sa.Integer(), nullable=True)
    op.alter_column("time_blocks", "start_minute", existing_type=sa.Integer(), nullable=True)
    op.alter_column("time_blocks", "end_minute", existing_type=sa.Integer(), nullable=True)
    op.drop_index("uq_time_blocks_planned_completion", table_name="time_blocks")
    op.create_unique_constraint(
        "uq_time_blocks_planned_actual_correspondence",
        "time_blocks",
        ["planned_block_id"],
    )

    op.create_check_constraint(
        "ck_time_blocks_correspondence_from_actual",
        "time_blocks",
        "planned_block_id IS NULL OR lane = 'actual'",
    )
    op.create_check_constraint(
        "ck_time_blocks_actual_end_requires_start",
        "time_blocks",
        "end_at IS NULL OR start_at IS NOT NULL",
    )
    op.create_check_constraint(
        "ck_time_blocks_planned_grid_shape",
        "time_blocks",
        "lane != 'planned' OR (day_id IS NOT NULL AND start_minute IS NOT NULL "
        "AND end_minute IS NOT NULL AND start_at IS NULL AND end_at IS NULL)",
    )
    op.create_check_constraint(
        "ck_time_blocks_definitive_actual_shape",
        "time_blocks",
        "lane != 'actual' OR start_at IS NULL OR "
        "(day_id IS NULL AND start_minute IS NULL AND end_minute IS NULL)",
    )
    op.create_check_constraint(
        "ck_time_blocks_actual_positive_interval",
        "time_blocks",
        "lane != 'actual' OR start_at IS NULL OR end_at IS NULL OR end_at > start_at",
    )
    op.create_index(
        "uq_time_blocks_one_active_actual",
        "time_blocks",
        ["lane"],
        unique=True,
        postgresql_where=sa.text(
            "lane = 'actual' AND start_at IS NOT NULL AND end_at IS NULL"
        ),
        sqlite_where=sa.text(
            "lane = 'actual' AND start_at IS NOT NULL AND end_at IS NULL"
        ),
    )

    if op.get_bind().dialect.name == "postgresql":
        op.execute(
            """
            ALTER TABLE time_blocks
            ADD CONSTRAINT ex_time_blocks_actual_no_overlap
            EXCLUDE USING gist (
              tstzrange(start_at, COALESCE(end_at, 'infinity'::timestamptz), '[)') WITH &&
            )
            WHERE (lane = 'actual' AND start_at IS NOT NULL)
            """
        )
        op.execute(
            """
            CREATE FUNCTION validate_actual_planned_correspondence()
            RETURNS trigger AS $$
            BEGIN
              IF NEW.planned_block_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM time_blocks planned
                WHERE planned.id = NEW.planned_block_id AND planned.lane = 'planned'
              ) THEN
                RAISE EXCEPTION 'Actual correspondence must target a Planned Block';
              END IF;
              RETURN NEW;
            END;
            $$ LANGUAGE plpgsql
            """
        )
        op.execute(
            """
            CREATE TRIGGER validate_time_block_correspondence
            BEFORE INSERT OR UPDATE OF lane, planned_block_id ON time_blocks
            FOR EACH ROW EXECUTE FUNCTION validate_actual_planned_correspondence()
            """
        )


def downgrade() -> None:
    if op.get_bind().dialect.name == "postgresql":
        op.execute("DROP TRIGGER validate_time_block_correspondence ON time_blocks")
        op.execute("DROP FUNCTION validate_actual_planned_correspondence()")
        op.drop_constraint("ex_time_blocks_actual_no_overlap", "time_blocks")

    op.drop_index("uq_time_blocks_one_active_actual", table_name="time_blocks")
    for name in (
        "ck_time_blocks_actual_positive_interval",
        "ck_time_blocks_definitive_actual_shape",
        "ck_time_blocks_planned_grid_shape",
        "ck_time_blocks_actual_end_requires_start",
        "ck_time_blocks_correspondence_from_actual",
    ):
        op.drop_constraint(name, "time_blocks", type_="check")
    op.drop_constraint(
        "uq_time_blocks_planned_actual_correspondence",
        "time_blocks",
        type_="unique",
    )
    op.create_index(
        "uq_time_blocks_planned_completion",
        "time_blocks",
        ["planned_block_id"],
        unique=True,
    )
    op.alter_column("time_blocks", "end_minute", existing_type=sa.Integer(), nullable=False)
    op.alter_column("time_blocks", "start_minute", existing_type=sa.Integer(), nullable=False)
    op.alter_column("time_blocks", "day_id", existing_type=sa.Integer(), nullable=False)
    op.drop_column("time_blocks", "end_at")
    op.drop_column("time_blocks", "start_at")

    op.drop_index("uq_recurrence_occurrences_task_id", table_name="recurrence_occurrences")
    op.drop_column("task_completion_operations", "completed_task_version")
    op.drop_column("tasks", "version")
    op.drop_column("tasks", "completed_at")
    op.drop_column("tasks", "checked")

    if op.get_bind().dialect.name == "postgresql":
        op.alter_column(
            "alembic_version",
            "version_num",
            existing_type=sa.String(length=255),
            type_=sa.String(length=32),
            existing_nullable=False,
        )
