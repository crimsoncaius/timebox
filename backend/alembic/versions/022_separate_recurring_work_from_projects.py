"""Keep recurring work independent of Projects."""
from alembic import op
import sqlalchemy as sa

revision = "022_separate_recurring_work_from_projects"
down_revision = "021_remove_project_metadata"
branch_labels = None
depends_on = None


def upgrade():
    connection = op.get_bind()
    columns = {column["name"] for column in sa.inspect(connection).get_columns("recurring_templates")}
    if "project_id" not in columns:
        return
    op.execute("""
        WITH RECURSIVE recurring_task_tree(id) AS (
            SELECT id FROM tasks WHERE recurrence_kind IS NOT NULL
            UNION
            SELECT task.id FROM tasks AS task
            JOIN recurring_task_tree AS parent ON task.parent_id = parent.id
        )
        UPDATE tasks SET project_id = NULL
        WHERE id IN (SELECT id FROM recurring_task_tree)
    """)
    indexes = {index["name"] for index in sa.inspect(connection).get_indexes("recurring_templates")}
    if "ix_recurring_templates_project_id" in indexes:
        op.drop_index("ix_recurring_templates_project_id", table_name="recurring_templates")
    with op.batch_alter_table("recurring_templates") as batch:
        batch.drop_column("project_id")


def downgrade():
    with op.batch_alter_table("recurring_templates") as batch:
        batch.add_column(sa.Column("project_id", sa.Integer(), nullable=True))
        batch.create_foreign_key(
            "fk_recurring_templates_project_id_projects", "projects", ["project_id"], ["id"], ondelete="SET NULL"
        )
    op.create_index("ix_recurring_templates_project_id", "recurring_templates", ["project_id"])
