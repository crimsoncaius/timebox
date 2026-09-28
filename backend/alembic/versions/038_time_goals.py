"""Independent Time Goals with period-effective targets (ADR 0017)."""
import sqlalchemy as sa

from alembic import op

revision = "038_time_goals"
down_revision = "037_checklist_item_habits"
branch_labels = None
depends_on = None


def upgrade():
    if "time_goals" not in sa.inspect(op.get_bind()).get_table_names():
        op.create_table(
            "time_goals",
            sa.Column("id", sa.Integer(), primary_key=True),
            sa.Column("task_type_id", sa.Integer(), sa.ForeignKey("task_types.id", ondelete="RESTRICT"), nullable=False),
            sa.Column("unit", sa.String(5), nullable=False),
            sa.Column("interval", sa.Integer(), nullable=False),
            sa.Column("start_date", sa.Date(), nullable=False),
            sa.Column("end_date", sa.Date()),
            sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
            sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
            sa.CheckConstraint("interval >= 1 AND interval <= 100", name="ck_time_goal_interval"),
            sa.CheckConstraint("unit IN ('day', 'week', 'month')", name="ck_time_goal_unit"),
            sa.CheckConstraint("end_date IS NULL OR end_date >= start_date", name="ck_time_goal_dates"),
        )
        op.create_index("ix_time_goals_task_type_id", "time_goals", ["task_type_id"])
    if "time_goal_targets" not in sa.inspect(op.get_bind()).get_table_names():
        op.create_table(
            "time_goal_targets",
            sa.Column("goal_id", sa.Integer(), sa.ForeignKey("time_goals.id", ondelete="CASCADE"), primary_key=True),
            sa.Column("effective_date", sa.Date(), primary_key=True),
            sa.Column("minutes", sa.Integer(), nullable=False),
            sa.CheckConstraint("minutes > 0", name="ck_time_goal_target_positive"),
        )


def downgrade():
    op.drop_table("time_goal_targets")
    op.drop_table("time_goals")
