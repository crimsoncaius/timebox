"""Add recurring pre-planning destinations, preserving existing timed schedules."""

from alembic import op
import sqlalchemy as sa

revision = "032_preplanning_destination"
down_revision = "031_monday_weeks"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("recurring_templates", sa.Column(
        "preplanning_mode", sa.Text(), nullable=False, server_default="none",
    ))
    op.execute("""
        UPDATE recurring_templates SET preplanning_mode = 'planned_time'
        WHERE EXISTS (
            SELECT 1 FROM recurring_preplanning_slots
            WHERE template_id = recurring_templates.id AND removed_at IS NULL
        )
    """)


def downgrade():
    op.drop_column("recurring_templates", "preplanning_mode")
