"""Let a Recurring Task Series opt into habit tracking."""

import sqlalchemy as sa

from alembic import op

revision = "036_habit_tracking"
down_revision = "035_assistant_tracking_proposal"
branch_labels = None
depends_on = None


def upgrade():
    columns = {column["name"] for column in sa.inspect(op.get_bind()).get_columns("recurring_templates")}
    if "track_as_habit" not in columns:
        with op.batch_alter_table("recurring_templates") as batch:
            batch.add_column(
                sa.Column("track_as_habit", sa.Boolean(), nullable=False, server_default=sa.false())
            )


def downgrade():
    with op.batch_alter_table("recurring_templates") as batch:
        batch.drop_column("track_as_habit")
