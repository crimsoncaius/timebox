"""Retain Assistant conversations and every response attempt."""

from alembic import op
import sqlalchemy as sa

revision = "033_assistant_conversations"
down_revision = "032_preplanning_destination"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "assistant_conversations",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("capabilities", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("closed_at", sa.DateTime(timezone=True)),
    )
    op.create_table(
        "assistant_attempts",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("run_id", sa.String(36), unique=True, nullable=False),
        sa.Column("conversation_id", sa.String(36), sa.ForeignKey("assistant_conversations.id"), nullable=False),
        sa.Column("question", sa.Text(), nullable=False),
        sa.Column("answer", sa.Text(), nullable=False),
        sa.Column("status", sa.String(24), nullable=False),
        sa.Column("acknowledged", sa.Boolean(), nullable=False),
        sa.Column("model", sa.Text(), nullable=False),
        sa.Column("snapshots", sa.JSON(), nullable=False),
        sa.Column("displayed_plan", sa.JSON()),
        sa.Column("error", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_assistant_attempts_conversation_id_id", "assistant_attempts", ["conversation_id", "id"])


def downgrade():
    op.drop_table("assistant_attempts")
    op.drop_table("assistant_conversations")
