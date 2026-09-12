"""Add development activity command receipts without changing legacy records.

Revision ID: 024_activity_online_protocol
Revises: 023_recurring_preplanning
"""
from alembic import op
import sqlalchemy as sa

revision = "024_activity_online_protocol"
down_revision = "023_recurring_preplanning_foundation"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("activity_state", sa.Column("id", sa.Integer(), primary_key=True),
                    sa.Column("enabled", sa.Boolean(), nullable=False),
                    sa.Column("cursor", sa.Integer(), nullable=False))
    op.create_table("activity_operations",
        sa.Column("operation_id", sa.String(36), primary_key=True),
        sa.Column("device_id", sa.String(100), nullable=False),
        sa.Column("sequence", sa.Integer(), nullable=False),
        sa.Column("envelope", sa.JSON(), nullable=False),
        sa.Column("cursor", sa.Integer(), nullable=False, unique=True),
        sa.Column("outcome", sa.String(30), nullable=False),
        sa.Column("effective_at", sa.DateTime(timezone=True)),
        sa.UniqueConstraint("device_id", "sequence"))


def downgrade():
    op.drop_table("activity_operations")
    op.drop_table("activity_state")
