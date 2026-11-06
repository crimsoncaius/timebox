# Backend audit fixes

The historical audit and its defect-detection script remain unchanged. That script intentionally asserts the former bad outcomes; use the regression tests below for post-fix validation. Application fixes, regression tests, and the migration test fixtures now address the backend findings. No database migration, issue, PR, or commit was created.

## Behavior changes

- **B1:** Task Completion resolves the saved Reporting Time Zone after acquiring the Activity State lock. Both HTTP and direct service callers preserve already-past wall-clock Planned Blocks and remove future ones using the same shared zone. Exact plans continue using their stored instants.
- **B2:** Battle Plan and Recurring routes use shared reporting settings. Task-list and recurrence service boundaries also resolve them, covering background/direct callers and recurrence lifecycle operations. Date-only overdue state, previews, generation, current occurrences, and routine calendars now agree about Today. Task Reminder instants remain independent of this calendar setting.
- **B3:** Archived Task Occurrences remain eligible evidence for Habit history, including Checklist Item checks and quota progress. Archived history remains read-only: the API returns `tickable=false` and rejects edits until the Task is restored. Restoring it enables edits again.
- **U10:** Existing durable unavailable-slot notices now identify the affected Task and explain the conflict. They stop appearing once the task completes, is archived or trashed, the series pauses, the schedule is removed, or the slot materializes successfully. Notices do not automatically change Ready to Plan or create/move plans.

## Unavailable-slot API contract

Existing list/detail/create/edit responses at `/recurring-templates` retain `preplanning_schedule.unavailable_slots`. Each entry now contains:

```json
{
  "date": "2026-11-06",
  "slot_key": "existing-stable-slot-key",
  "start_minute": 540,
  "end_minute": 600,
  "task_id": 123,
  "task_title": "Daily review",
  "reason": "planned_block_overlap",
  "can_add_to_ready": true
}
```

`can_add_to_ready` is computed using shared Reporting Today and the current task readiness, so future or already-ready occurrences do not offer an inappropriate queue action. Clients can open the intended Day; when allowed, they can explicitly PATCH the Task with `{"ready_to_plan":true}` and refresh the series. The existing realization ledger persists unavailable slots, so no new table or migration is necessary.

Issue #102 was read, including the follow-up correcting its old automatic-readiness wording. This implements the audit's authorized in-app discoverability improvement. It does not define OS/push notification delivery, permissions, or escalation for the separate notifications initiative.

## Test reliability fixes

The seven failing migration tests no longer construct today's schema and pretend it is historical. They start with the frozen SQLite schema exported from models at commit `436e2649c1aac424a93f483bcd318cadeacdef33`, when revision 014 was current, then apply real migrations to the required starting revision. The snapshot includes its historical constraints and overlap triggers. Fixtures provide the required historical Day-window fields. Production migrations were not weakened or made to skip existing objects.

The provider-503 check now asserts the actual ServiceUnavailable response and fails immediately if the SDK enters its backoff routine. It no longer uses a two-second wall-clock threshold that fails under concurrent build/test load. The HTTP transport remains mocked.

Four pre-existing Ruff import-order findings were also corrected; the changes in `models/time_block.py`, `services/day_service.py`, and the two task-type tests are formatting only.

## Validation

- Full backend suite: `uv run --extra dev --extra eval python -m pytest -q --tb=short` — **903 passed, 22 skipped, 8 warnings**, 214.14 seconds. See [full output](full-test-results.txt). The seven migration failures and timing-sensitive provider check from the original audit now pass.
- Final additive `can_add_to_ready` response field and future-date regression were validated with `uv run --extra dev python -m pytest tests/test_audit_regressions.py tests/test_recurrence.py -q --tb=short` — **94 passed**, 32.60 seconds. This narrow API addition landed while the full suite was running; the focused run covers the final response shape.
- `uv run --extra dev ruff check app tests` — passed.
- `git diff --check` — passed.

All checks use isolated test databases and controlled model dependencies. PostgreSQL concurrency and live external services are outside this local validation. The eight warnings are existing Python 3.12 SQLite date-adapter deprecations in the historical migration fixture.

The parent agent owns relaunching the affected review application and integrated web/Android validation. No device commands or application launch were performed by this backend task.
