from __future__ import annotations

from app.services.recurrence.common import (
    CUSTOMIZABLE_FIELDS,
    INHERITED_FIELDS,
    LEAD_DAYS,
    SCHEDULE_FIELDS,
    Window,
    _date_in_tz,
    _json_list,
    _month_date,
    _rule_value,
    _utc_now,
    _week_boundary,
)
from app.services.recurrence.helpers import (
    _load_template,
    _next_position,
    _replace_checklist,
    _task_kwargs,
    _validate_refs,
)
from app.services.recurrence.preview import preview
from app.services.recurrence.synchronization import (
    _cleanup_future,
    _derive_quota_parents,
    _has_future_planned_block,
    _is_pristine,
    _materialize,
    _propagate_template_fields,
    _rebuild_unprotected_subtasks,
    _suppress_pause_interval,
    recalculate_weekly_quotas,
    synchronize,
)
from app.services.recurrence.task_overrides import quota_progress, record_task_overrides
from app.services.recurrence.templates import (
    clear_template_type_references,
    create_template,
    end_template,
    get_template,
    list_templates,
    move_project_templates_to_admin,
    pause_template,
    patch_template,
    resume_template,
    template_type_counts,
    to_read,
)
from app.services.recurrence.windows import _windows_for_preview, iter_windows
from app.services.recurrence.cadence import _cadence
