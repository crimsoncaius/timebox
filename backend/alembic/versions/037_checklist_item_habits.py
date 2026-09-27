"""Give Checklist Items a stable identity and let them be Habits (ADR 0016)."""

import sqlalchemy as sa

from alembic import op

revision = "037_checklist_item_habits"
down_revision = "036_habit_tracking"
branch_labels = None
depends_on = None


def _key(title: str) -> str:
    return title.strip().casefold()


def link_existing_subtasks(connection) -> None:
    """Link each generated Subtask to its series' Checklist Item by title, once.

    Within one Task Occurrence, Subtasks are matched in position order so that
    repeated titles pair with repeated items; unmatched Subtasks stay unlinked.
    """

    items: dict[int, list[tuple[int, str]]] = {}
    for item_id, template_id, title in connection.execute(sa.text(
        "SELECT id, template_id, title FROM recurring_checklist_items ORDER BY template_id, position, id"
    )):
        items.setdefault(template_id, []).append((item_id, _key(title)))
    used: dict[int, set[int]] = {}
    links = []
    for task_id, parent_id, template_id, title in connection.execute(sa.text(
        "SELECT id, parent_id, recurring_template_id, title FROM tasks "
        "WHERE recurrence_kind = 'checklist' AND parent_id IS NOT NULL "
        "AND recurring_template_id IS NOT NULL AND checklist_item_id IS NULL "
        "ORDER BY parent_id, position, id"
    )):
        taken = used.setdefault(parent_id, set())
        match = next(
            (item_id for item_id, key in items.get(template_id, []) if key == _key(title) and item_id not in taken),
            None,
        )
        if match is not None:
            taken.add(match)
            links.append({"task_id": task_id, "item_id": match})
    if links:
        connection.execute(sa.text("UPDATE tasks SET checklist_item_id = :item_id WHERE id = :task_id"), links)


def upgrade():
    connection = op.get_bind()
    inspector = sa.inspect(connection)
    # Schemas built from the current models already have these columns.
    if "track_as_habit" not in {c["name"] for c in inspector.get_columns("recurring_checklist_items")}:
        with op.batch_alter_table("recurring_checklist_items") as batch:
            batch.add_column(sa.Column("track_as_habit", sa.Boolean(), nullable=False, server_default=sa.false()))
    if "checklist_item_id" not in {c["name"] for c in inspector.get_columns("tasks")}:
        with op.batch_alter_table("tasks") as batch:
            batch.add_column(sa.Column("checklist_item_id", sa.Integer(), nullable=True))
            batch.create_foreign_key(
                "fk_tasks_checklist_item", "recurring_checklist_items", ["checklist_item_id"], ["id"],
                ondelete="SET NULL",
            )
            batch.create_index("ix_tasks_checklist_item_id", ["checklist_item_id"])
    # A series already tracked as a Habit tracks all of its Checklist Items.
    connection.execute(sa.text(
        "UPDATE recurring_checklist_items SET track_as_habit = true WHERE template_id IN "
        "(SELECT id FROM recurring_templates WHERE track_as_habit = true)"
    ))
    link_existing_subtasks(connection)


def downgrade():
    inspector = sa.inspect(op.get_bind())
    indexes = {index["name"] for index in inspector.get_indexes("tasks")}
    foreign_keys = {key["name"] for key in inspector.get_foreign_keys("tasks")}
    with op.batch_alter_table("tasks") as batch:
        if "ix_tasks_checklist_item_id" in indexes:
            batch.drop_index("ix_tasks_checklist_item_id")
        if "fk_tasks_checklist_item" in foreign_keys:
            batch.drop_constraint("fk_tasks_checklist_item", type_="foreignkey")
        batch.drop_column("checklist_item_id")
    with op.batch_alter_table("recurring_checklist_items") as batch:
        batch.drop_column("track_as_habit")
