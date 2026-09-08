"""Add Recurring Pre-planning Schedule slots and generated-block realizations."""

from alembic import op
import sqlalchemy as sa


revision = "023_recurring_preplanning_foundation"
down_revision = "022_separate_recurring_work_from_projects"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "recurring_preplanning_slots",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("slot_key", sa.Text(), nullable=False),
        sa.Column("template_id", sa.Integer(), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("weekday", sa.Integer(), nullable=True),
        sa.Column("start_minute", sa.Integer(), nullable=False),
        sa.Column("end_minute", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("removed_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["template_id"], ["recurring_templates.id"], ondelete="CASCADE"),
    )
    op.create_index(
        "ix_recurring_preplanning_slots_slot_key",
        "recurring_preplanning_slots",
        ["slot_key"],
        unique=True,
    )
    op.create_index(
        "ix_recurring_preplanning_slots_template_id",
        "recurring_preplanning_slots",
        ["template_id"],
    )
    op.create_index(
        "uq_recurring_preplanning_slot_position",
        "recurring_preplanning_slots",
        ["template_id", "position"],
        unique=True,
        postgresql_where=sa.text("removed_at IS NULL"),
        sqlite_where=sa.text("removed_at IS NULL"),
    )
    op.create_table(
        "recurring_planned_block_realizations",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("occurrence_id", sa.Integer(), nullable=False),
        sa.Column("slot_id", sa.Integer(), nullable=True),
        sa.Column("slot_key", sa.Text(), nullable=False),
        sa.Column("planned_block_id", sa.Integer(), nullable=True),
        sa.Column("state", sa.String(length=16), nullable=False, server_default="untouched"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.ForeignKeyConstraint(["occurrence_id"], ["recurrence_occurrences.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["slot_id"], ["recurring_preplanning_slots.id"], ondelete="SET NULL"),
        sa.ForeignKeyConstraint(["planned_block_id"], ["time_blocks.id"], ondelete="SET NULL"),
        sa.UniqueConstraint("occurrence_id", "slot_key", name="uq_recurring_planned_block_realization"),
        sa.UniqueConstraint("planned_block_id", name="uq_recurring_planned_block_realization_block"),
    )
    op.create_index(
        "ix_recurring_planned_block_realizations_slot_key",
        "recurring_planned_block_realizations",
        ["slot_key"],
    )
    op.create_index(
        "ix_recurring_planned_block_realizations_occurrence_id",
        "recurring_planned_block_realizations",
        ["occurrence_id"],
    )
    op.create_index(
        "ix_recurring_planned_block_realizations_slot_id",
        "recurring_planned_block_realizations",
        ["slot_id"],
    )
    op.create_index(
        "ix_recurring_planned_block_realizations_planned_block_id",
        "recurring_planned_block_realizations",
        ["planned_block_id"],
    )


def downgrade():
    op.drop_index(
        "ix_recurring_planned_block_realizations_slot_key",
        table_name="recurring_planned_block_realizations",
    )
    op.drop_index(
        "ix_recurring_planned_block_realizations_planned_block_id",
        table_name="recurring_planned_block_realizations",
    )
    op.drop_index(
        "ix_recurring_planned_block_realizations_slot_id",
        table_name="recurring_planned_block_realizations",
    )
    op.drop_index(
        "ix_recurring_planned_block_realizations_occurrence_id",
        table_name="recurring_planned_block_realizations",
    )
    op.drop_table("recurring_planned_block_realizations")
    op.drop_index(
        "uq_recurring_preplanning_slot_position",
        table_name="recurring_preplanning_slots",
    )
    op.drop_index(
        "ix_recurring_preplanning_slots_template_id",
        table_name="recurring_preplanning_slots",
    )
    op.drop_index(
        "ix_recurring_preplanning_slots_slot_key",
        table_name="recurring_preplanning_slots",
    )
    op.drop_table("recurring_preplanning_slots")
