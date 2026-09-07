"""Keep occurrence history when an ended Recurring Task Series is deleted."""
from __future__ import annotations

from alembic import op
import sqlalchemy as sa

revision = "020_detach_series_history"
down_revision = "019_project_order"
branch_labels = None
depends_on = None


def _change_template_reference(*, nullable: bool, ondelete: str) -> None:
    convention = {"fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s"}
    foreign_key = next(
        key for key in sa.inspect(op.get_bind()).get_foreign_keys("recurrence_occurrences")
        if key["constrained_columns"] == ["template_id"]
    )
    name = foreign_key["name"] or "fk_recurrence_occurrences_template_id_recurring_templates"
    with op.batch_alter_table("recurrence_occurrences", naming_convention=convention) as batch:
        batch.drop_constraint(name, type_="foreignkey")
        batch.alter_column("template_id", existing_type=sa.Integer(), nullable=nullable)
        batch.create_foreign_key(name, "recurring_templates", ["template_id"], ["id"], ondelete=ondelete)


def upgrade() -> None:
    _change_template_reference(nullable=True, ondelete="SET NULL")


def downgrade() -> None:
    # Deleted series cannot be reconstructed. Never discard their durable
    # occurrence history just to satisfy the old non-null reference contract.
    detached = op.get_bind().execute(sa.text(
        "SELECT COUNT(*) FROM recurrence_occurrences WHERE template_id IS NULL"
    )).scalar_one()
    if detached:
        raise RuntimeError("Cannot downgrade while detached occurrence history exists")
    _change_template_reference(nullable=False, ondelete="CASCADE")
