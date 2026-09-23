"""Track missed Task Reminders and coordinate account-wide delivery."""

from alembic import op
import sqlalchemy as sa

revision = "030_task_reminder_delivery"
down_revision = "029_planned_recording_undo"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("tasks", sa.Column("reminder_skipped_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("tasks", sa.Column("reminder_claim_token", sa.String(36), nullable=True))
    op.add_column("tasks", sa.Column("reminder_claim_until", sa.DateTime(timezone=True), nullable=True))


def downgrade():
    op.drop_column("tasks", "reminder_claim_until")
    op.drop_column("tasks", "reminder_claim_token")
    op.drop_column("tasks", "reminder_skipped_at")
