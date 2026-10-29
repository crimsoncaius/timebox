"""Quick Tracking product history and expiring diagnostic events."""
from alembic import op
import sqlalchemy as sa

revision = "045_quick_failures"
down_revision = "044_google_calendar"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("quick_tracking_failures",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("device_id", sa.String(36), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("data", sa.JSON(), nullable=False),
        sa.Column("dismissed", sa.Boolean(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False))
    op.create_index("ix_quick_tracking_failures_created_at", "quick_tracking_failures", ["created_at"])
    op.create_table("quick_tracking_diagnostics",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("attempt_id", sa.String(36), nullable=False),
        sa.Column("data", sa.JSON(), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False))
    op.create_index("ix_quick_tracking_diagnostics_attempt_id", "quick_tracking_diagnostics", ["attempt_id"])
    op.create_index("ix_quick_tracking_diagnostics_expires_at", "quick_tracking_diagnostics", ["expires_at"])


def downgrade():
    op.drop_table("quick_tracking_diagnostics")
    op.drop_table("quick_tracking_failures")
