"""Durable atomic Assistant proposals, submissions and receipts."""
import sqlalchemy as sa
from alembic import op

revision = "041_assistant_task_operations"
down_revision = "040_assistant_task_reads"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("tasks") as batch:
        batch.add_column(sa.Column("completion_precision", sa.String(16)))
        batch.add_column(sa.Column("completion_local_date", sa.Date()))
        batch.add_column(sa.Column("completion_timezone", sa.Text()))
    op.create_table("assistant_task_proposals",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("operation_id", sa.String(36), nullable=False, unique=True),
        sa.Column("conversation_id", sa.String(36), sa.ForeignKey("assistant_conversations.id"), nullable=False),
        sa.Column("run_id", sa.String(36), sa.ForeignKey("assistant_attempts.run_id"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("status", sa.String(24), nullable=False),
        sa.Column("review", sa.JSON(), nullable=False),
        sa.Column("guards", sa.JSON(), nullable=False),
        sa.Column("source_completed_at", sa.DateTime(timezone=True)),
        sa.Column("receipt", sa.JSON()),
        sa.Column("undo_operation_id", sa.String(36), unique=True),
        sa.Column("undo_token", sa.Text()),
        sa.Column("undo_receipt", sa.JSON()),
        sa.Column("events", sa.JSON(), nullable=False))
    for column in ("conversation_id", "run_id"):
        op.create_index("ix_assistant_task_proposals_" + column, "assistant_task_proposals", [column])
    op.create_table("assistant_task_submissions",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("proposal_id", sa.String(36), sa.ForeignKey("assistant_task_proposals.id"), nullable=False),
        sa.Column("operation_id", sa.String(36), nullable=False),
        sa.Column("state", sa.String(24), nullable=False),
        sa.Column("reason", sa.String(80)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False))
    op.create_index("ix_assistant_task_submissions_proposal_id", "assistant_task_submissions", ["proposal_id"])


def downgrade():
    op.drop_table("assistant_task_submissions")
    op.drop_table("assistant_task_proposals")
    with op.batch_alter_table("tasks") as batch:
        batch.drop_column("completion_timezone")
        batch.drop_column("completion_local_date")
        batch.drop_column("completion_precision")
