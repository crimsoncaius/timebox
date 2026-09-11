"""Retain immutable range intent and the pre-reconciliation development baseline."""
from alembic import op
import sqlalchemy as sa

revision = "025_activity_reconciliation"
down_revision = "024_activity_online_protocol"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("activity_state", sa.Column("reconciliation", sa.JSON(), nullable=True))
    op.add_column("activity_operations", sa.Column("intent", sa.JSON(), nullable=True))
    op.add_column("time_blocks", sa.Column("activity_source", sa.Text(), nullable=True))
    constraints = sa.inspect(op.get_bind()).get_unique_constraints("time_blocks")
    if any(c['name'] == 'uq_time_blocks_planned_actual_correspondence' for c in constraints):
        # SQLite table rebuilds otherwise discard overlap/correspondence triggers.
        triggers = []
        if op.get_bind().dialect.name == "sqlite":
            triggers = list(op.get_bind().execute(sa.text("SELECT sql FROM sqlite_master WHERE type='trigger' AND tbl_name='time_blocks'" )).scalars())
        with op.batch_alter_table("time_blocks") as batch:
            batch.drop_constraint("uq_time_blocks_planned_actual_correspondence", type_="unique")
        for trigger in triggers:
            op.execute(trigger)
    op.create_index("uq_time_blocks_legacy_correspondence", "time_blocks", ["planned_block_id"], unique=True,
                    postgresql_where=sa.text("activity_source IS NULL"), sqlite_where=sa.text("activity_source IS NULL"))


def downgrade():
    if op.get_bind().execute(sa.text("SELECT 1 FROM activity_operations WHERE intent IS NOT NULL LIMIT 1")).first():
        raise RuntimeError("Reconciled activity exists; repair forward instead of discarding admitted intent")
    op.drop_index("uq_time_blocks_legacy_correspondence", table_name="time_blocks")
    with op.batch_alter_table("time_blocks") as batch:
        batch.create_unique_constraint("uq_time_blocks_planned_actual_correspondence", ["planned_block_id"])
    op.drop_column("time_blocks", "activity_source")
    op.drop_column("activity_operations", "intent")
    op.drop_column("activity_state", "reconciliation")
