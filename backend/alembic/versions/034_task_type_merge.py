"""Retain merged category identities for delayed device writes."""

import sqlalchemy as sa

from alembic import op

revision = "034_task_type_merge"
down_revision = "033_assistant_conversations"
branch_labels = None
depends_on = None


def upgrade():
    with op.batch_alter_table("task_types") as batch:
        batch.add_column(sa.Column("is_merged", sa.Boolean(), nullable=False, server_default=sa.false()))
        batch.add_column(sa.Column("merged_into_id", sa.Integer(), nullable=True))
        batch.create_foreign_key(
            "fk_task_type_merge_target", "task_types", ["merged_into_id"], ["id"], ondelete="SET NULL"
        )


def downgrade():
    # Merges lose source membership; rollback cannot restore that history.
    if op.get_bind().execute(sa.text("SELECT 1 FROM task_types WHERE is_merged = true LIMIT 1")).first():
        raise RuntimeError("Merged Task Types require restoring a backup to downgrade")
    with op.batch_alter_table("task_types") as batch:
        batch.drop_constraint("fk_task_type_merge_target", type_="foreignkey")
        batch.drop_column("merged_into_id")
        batch.drop_column("is_merged")
