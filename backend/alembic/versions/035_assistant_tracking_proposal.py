"""Retain the Tracking Proposal displayed by an Assistant attempt."""

import sqlalchemy as sa

from alembic import op

revision = "035_assistant_tracking_proposal"
down_revision = "034_task_type_merge"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("assistant_attempts") as batch:
        batch.add_column(sa.Column("tracking_proposal", sa.JSON(), nullable=True))


def downgrade():
    with op.batch_alter_table("assistant_attempts") as batch:
        batch.drop_column("tracking_proposal")
