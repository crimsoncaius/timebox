"""Exact Planned Block intervals and atomic plan-and-track receipts."""
import sqlalchemy as sa

from alembic import op

revision = "039_plan_now"
down_revision = "038_time_goals"
branch_labels = None
depends_on = None


def upgrade():
    inspector = sa.inspect(op.get_bind())
    shape = next(c for c in inspector.get_check_constraints("time_blocks") if c["name"] == "ck_time_blocks_planned_grid_shape")
    replaced = "end_at > start_at" not in shape["sqltext"]
    if replaced:
        with op.batch_alter_table("time_blocks") as batch:
            batch.drop_constraint("ck_time_blocks_planned_grid_shape", type_="check")
            batch.create_check_constraint("ck_time_blocks_planned_grid_shape",
                "lane != 'planned' OR (day_id IS NOT NULL AND start_minute IS NOT NULL "
                "AND end_minute IS NOT NULL AND ((start_at IS NULL AND end_at IS NULL) "
                "OR (start_at IS NOT NULL AND end_at IS NOT NULL AND end_at > start_at)))")
    if "plan_now_operations" not in inspector.get_table_names():
        op.create_table("plan_now_operations",
            sa.Column("operation_id", sa.String(36), primary_key=True),
            sa.Column("request", sa.JSON(), nullable=False),
            sa.Column("payload", sa.JSON(), nullable=False))
    if replaced and op.get_bind().dialect.name == "sqlite":
        from app.models.time_block import _sqlite_validate_actual_insert, _sqlite_validate_actual_update
        op.execute(_sqlite_validate_actual_insert)
        op.execute(_sqlite_validate_actual_update)


def downgrade():
    # Downgrading cannot represent exact/cross-day plans without discarding intent.
    if op.get_bind().execute(sa.text("SELECT 1 FROM time_blocks WHERE lane='planned' AND start_at IS NOT NULL LIMIT 1")).first():
        raise RuntimeError("Exact Planned Blocks must be migrated before downgrading")
    op.drop_table("plan_now_operations")
    with op.batch_alter_table("time_blocks") as batch:
        batch.drop_constraint("ck_time_blocks_planned_grid_shape", type_="check")
        batch.create_check_constraint("ck_time_blocks_planned_grid_shape",
            "lane != 'planned' OR (day_id IS NOT NULL AND start_minute IS NOT NULL "
            "AND end_minute IS NOT NULL AND start_at IS NULL AND end_at IS NULL)")
    if op.get_bind().dialect.name == "sqlite":
        from app.models.time_block import _sqlite_validate_actual_insert, _sqlite_validate_actual_update
        op.execute(_sqlite_validate_actual_insert)
        op.execute(_sqlite_validate_actual_update)
