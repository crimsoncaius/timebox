"""Retain task query manifests and exact per-attempt context inputs."""

import sqlalchemy as sa
from alembic import op

revision = "040_assistant_task_reads"
down_revision = "039_plan_now"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("assistant_attempts") as batch:
        batch.add_column(sa.Column("context_inputs", sa.JSON(), nullable=True))
    op.create_table("assistant_queries",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("conversation_id", sa.String(36), sa.ForeignKey("assistant_conversations.id"), nullable=False),
        sa.Column("evidence", sa.JSON(), nullable=False))
    op.create_index("ix_assistant_queries_conversation_id", "assistant_queries", ["conversation_id"])
    op.create_table("assistant_query_cursors",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("query_id", sa.String(36), sa.ForeignKey("assistant_queries.id"), nullable=False),
        sa.Column("offset", sa.Integer(), nullable=False))


def downgrade():
    op.drop_table("assistant_query_cursors")
    op.drop_table("assistant_queries")
    with op.batch_alter_table("assistant_attempts") as batch:
        batch.drop_column("context_inputs")
