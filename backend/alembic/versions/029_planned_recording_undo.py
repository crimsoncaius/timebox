"""Atomic planned recording Undo snapshots."""
from alembic import op
import sqlalchemy as sa

revision = "029_planned_recording_undo"
down_revision = "028_activity_cutover"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("planned_recording_undo",
        sa.Column("token", sa.String(36), primary_key=True),
        sa.Column("payload", sa.JSON(), nullable=False))


def downgrade():
    op.drop_table("planned_recording_undo")
