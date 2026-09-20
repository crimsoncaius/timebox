"""Public surface of the recurrence package.

The package is split by concern (templates / synchronization / windows / ...);
routers and other services depend on this module so that split stays an
internal detail.
"""

from __future__ import annotations

from app.services.recurrence.preview import preview
from app.services.recurrence.synchronization import (
    _derive_quota_parents,
    recalculate_weekly_quotas,
    synchronize,
)
from app.services.recurrence.task_overrides import quota_progress, record_task_overrides
from app.services.recurrence.templates import (
    clear_template_type_references,
    create_template,
    delete_template,
    end_template,
    get_template,
    list_templates,
    patch_template,
    pause_template,
    resume_template,
    template_type_counts,
    to_read,
)
from app.services.recurrence.windows import iter_windows

__all__ = [
    "_derive_quota_parents",
    "clear_template_type_references",
    "create_template",
    "delete_template",
    "end_template",
    "get_template",
    "iter_windows",
    "list_templates",
    "patch_template",
    "pause_template",
    "preview",
    "quota_progress",
    "recalculate_weekly_quotas",
    "record_task_overrides",
    "resume_template",
    "synchronize",
    "template_type_counts",
    "to_read",
]
