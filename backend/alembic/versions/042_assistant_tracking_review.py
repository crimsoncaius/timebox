"""Tracking execution receipts and cross-device review acknowledgements."""
import sqlalchemy as sa
from alembic import op

revision = "042_assistant_tracking_review"
down_revision = "041_assistant_task_operations"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("assistant_attempts") as batch:
        batch.add_column(sa.Column("tracking_mode", sa.String(16), nullable=False, server_default="confirm"))
        batch.add_column(sa.Column("tracking_result", sa.JSON()))
    op.create_table("assistant_tracking_reviews",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("run_id", sa.String(36), sa.ForeignKey("assistant_attempts.run_id"), nullable=False, unique=True),
        sa.Column("operation_id", sa.String(36), nullable=False, unique=True),
        sa.Column("receipt", sa.JSON(), nullable=False),
        sa.Column("reviewed_at", sa.DateTime(timezone=True)),
        sa.Column("reviewed_revision", sa.String(64)))


def downgrade():
    op.drop_table("assistant_tracking_reviews")
    with op.batch_alter_table("assistant_attempts") as batch:
        batch.drop_column("tracking_result")
        batch.drop_column("tracking_mode")
