"""Persist reporting zone; existing activity installations retain the server zone."""
from alembic import op
import sqlalchemy as sa
from app.core.config import get_settings

revision = "026_activity_reporting_timezone"
down_revision = "025_activity_reconciliation"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("activity_state", sa.Column("reporting_timezone", sa.String(100), nullable=True))
    op.get_bind().execute(sa.text("UPDATE activity_state SET reporting_timezone = :zone WHERE enabled = true"), {"zone": get_settings().app_timezone})


def downgrade():
    op.drop_column("activity_state", "reporting_timezone")
