"""Remove the configurable quota week boundary; weeks begin Monday."""
from alembic import op
import sqlalchemy as sa

revision = "030_monday_weeks"
down_revision = "029_planned_recording_undo"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("app_settings") as batch_op:
        batch_op.drop_column("week_start")


def downgrade():
    with op.batch_alter_table("app_settings") as batch_op:
        batch_op.add_column(sa.Column("week_start", sa.Text(), nullable=False, server_default="monday"))
