"""Durable Google connection and occurrence identity, independent of placement."""
import sqlalchemy as sa
from alembic import op

revision = "044_google_calendar"
down_revision = "043_whole_day"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("google_calendar_connection", sa.Column("id", sa.Integer(), primary_key=True),
                    sa.Column("credentials", sa.Text()), sa.Column("data", sa.JSON(), nullable=False))
    op.create_table("google_calendar_occurrences",
                    sa.Column("id", sa.Text(), primary_key=True),
                    sa.Column("account", sa.Text(), nullable=False),
                    sa.Column("block_id", sa.Integer(), sa.ForeignKey("time_blocks.id", ondelete="SET NULL"), unique=True),
                    sa.Column("task_type_id", sa.Integer(), sa.ForeignKey("task_types.id", ondelete="SET NULL")),
                    sa.Column("task_id", sa.Integer(), sa.ForeignKey("tasks.id", ondelete="SET NULL")),
                    sa.Column("note", sa.Text()), sa.Column("data", sa.JSON(), nullable=False))
    op.create_index("ix_google_calendar_occurrences_account", "google_calendar_occurrences", ["account"])


def downgrade():
    op.drop_table("google_calendar_occurrences")
    op.drop_table("google_calendar_connection")
