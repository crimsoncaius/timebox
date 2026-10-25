"""Every Day spans the whole day; remove the configurable day window."""
import sqlalchemy as sa
from alembic import op

revision = "043_whole_day"
down_revision = "042_assistant_tracking_review"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("days") as batch_op:
        batch_op.drop_column("show_full_day")
        batch_op.drop_column("end_hour")
        batch_op.drop_column("start_hour")
    op.drop_table("app_settings")


def downgrade():
    op.create_table(
        "app_settings",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("start_hour", sa.Integer(), nullable=False, server_default="8"),
        sa.Column("end_hour", sa.Integer(), nullable=False, server_default="20"),
        sa.Column("show_full_day", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("CURRENT_TIMESTAMP"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("CURRENT_TIMESTAMP"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    with op.batch_alter_table("days") as batch_op:
        batch_op.add_column(sa.Column("start_hour", sa.Integer(), nullable=False, server_default="8"))
        batch_op.add_column(sa.Column("end_hour", sa.Integer(), nullable=False, server_default="20"))
        batch_op.add_column(sa.Column("show_full_day", sa.Boolean(), nullable=False, server_default=sa.false()))
