"""Remove Project descriptions and deadlines; Task metadata is unchanged."""
from alembic import op
import sqlalchemy as sa

revision = "021_remove_project_metadata"
down_revision = "020_detach_series_history"
branch_labels = None
depends_on = None


def upgrade():
    # Direct column drops preserve references to Projects, including on SQLite.
    op.drop_column("projects", "description")
    op.drop_column("projects", "deadline_date")
    op.drop_column("projects", "deadline_at")


def downgrade():
    # Restore the schema only; deliberately discarded metadata cannot be recovered.
    op.add_column("projects", sa.Column("description", sa.Text(), nullable=False, server_default=""))
    op.add_column("projects", sa.Column("deadline_date", sa.Date(), nullable=True))
    op.add_column("projects", sa.Column("deadline_at", sa.DateTime(timezone=True), nullable=True))
