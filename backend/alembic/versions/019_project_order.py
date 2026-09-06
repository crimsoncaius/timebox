"""Persist Project order, preserving the previous alphabetical presentation."""
from alembic import op
import sqlalchemy as sa

revision = "019_project_order"
down_revision = "018_planned_block_name"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("projects", sa.Column("position", sa.Integer(), nullable=False, server_default="0"))
    projects = sa.table("projects", sa.column("id", sa.Integer()), sa.column("name", sa.Text()), sa.column("position", sa.Integer()))
    connection = op.get_bind()
    ids = list(connection.execute(sa.select(projects.c.id).order_by(sa.func.lower(projects.c.name), projects.c.id)).scalars())
    for position, project_id in enumerate(ids):
        connection.execute(projects.update().where(projects.c.id == project_id).values(position=position))


def downgrade():
    op.drop_column("projects", "position")
