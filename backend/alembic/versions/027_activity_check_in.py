"""Shared durable inactivity question, additive development only."""
from alembic import op
import sqlalchemy as sa
revision = '027_activity_check_in'
down_revision = '026_activity_reporting_timezone'
branch_labels = None
depends_on = None

def upgrade():
    op.add_column('activity_state', sa.Column('check_in', sa.JSON(), nullable=True))

def downgrade():
    op.drop_column('activity_state', 'check_in')
