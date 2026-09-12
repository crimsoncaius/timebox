"""Add cutover archive without enabling or converting any installation."""
from alembic import op
import sqlalchemy as sa

revision = "028_activity_cutover"
down_revision = "027_activity_check_in"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("activity_state", sa.Column("cutover", sa.JSON(), nullable=True))


def downgrade():
    if op.get_bind().scalar(sa.text("SELECT count(*) FROM activity_state WHERE cutover IS NOT NULL")):
        raise RuntimeError("Cutover archives require verified pre-write rollback or lossless forward recovery")
    op.drop_column("activity_state", "cutover")
