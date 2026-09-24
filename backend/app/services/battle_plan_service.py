"""Public surface of the battle_plan package.

The package is split by concern (projects / tasks / reminders); routers and
other services depend on this module so that split stays an internal detail.
"""

from __future__ import annotations

from app.services.battle_plan._shared import _to_read
from app.services.battle_plan.projects import (
    create_project,
    delete_project,
    list_projects,
    patch_project,
    reorder_projects,
)
from app.services.battle_plan.reminders import (
    acknowledge_reminder,
    claim_reminder,
    due_reminders,
    release_reminder,
)
from app.services.battle_plan.tasks import (
    archive_tasks,
    clear_task_type_references,
    create_task,
    list_tasks,
    patch_task,
    permanently_delete_task,
    reorder_tasks,
    restore_task,
    task_type_counts,
    task_type_counts_by_state,
    trash_task,
    unarchive_task,
)

__all__ = [
    "_to_read",
    "acknowledge_reminder",
    "claim_reminder",
    "archive_tasks",
    "clear_task_type_references",
    "create_project",
    "create_task",
    "delete_project",
    "due_reminders",
    "release_reminder",
    "list_projects",
    "list_tasks",
    "patch_project",
    "patch_task",
    "permanently_delete_task",
    "reorder_projects",
    "reorder_tasks",
    "restore_task",
    "task_type_counts",
    "task_type_counts_by_state",
    "trash_task",
    "unarchive_task",
]
