# Backend audit — 6 November 2026

Audited commit: `ab7b736b4aa486aaa0c2e0ea470a2731a838fe5a`. At audit time `HEAD` and `master` were identical. No application source was changed. All mutations below ran through FastAPI `TestClient` against disposable in-memory SQLite, not the running review database or production. No paid model calls were made.

Three confirmed findings are below. B1 and B2 are two user-visible consequences of the same incomplete Reporting Time Zone migration and can be fixed together. B3 is an API-level reproduction; a current-client path for archiving a historical Task Occurrence was not demonstrated.

## B1 — High: completing a Task can delete already-past Planned Blocks after a Reporting Time Zone change

**Impact:** Task Completion can silently remove historical plans. In the opposite zone direction, future plans can be retained when completion should remove them. The past-plan deletion was directly reproduced; the opposite direction is a consequence of the same comparison, not a separately executed check.

**Reproduction prerequisites:** Server `APP_TIMEZONE=UTC`; Activity Tracking enabled; shared Reporting Time Zone set to `Asia/Singapore`. Freeze the completion instant at `2026-11-05T18:00:00Z`, which is 02:00 on 6 November in Singapore. The included harness overrides the API clock seam, so this is reproducible without changing the machine clock.

1. Set Reporting Time Zone to Asia/Singapore (`PUT /activity/reporting-timezone`, `{"timezone":"Asia/Singapore"}`).
2. Create an ordinary Battle Plan Task (`POST /tasks`, `{"title":"Audit historical plan"}`).
3. Allocate it a Planned Block for 5 November, 21:00–21:30 (`POST /days/2026-11-05/blocks`, `{"task_id":TASK_ID,"start_minute":1260,"end_minute":1290}`).
4. At the frozen instant, complete the Task (`POST /tasks/TASK_ID/complete`).
5. Read 5 November (`GET /days/2026-11-05`).

**Expected:** Preserve the plan: its start was five hours before completion in the Reporting Time Zone.

**Observed:** Completion returns the block ID in `removed_planned_block_ids`; the Day response contains no blocks. It treated the plan's start as 21:00 UTC, three hours *after* completion.

**Evidence:** `reproduce.py:timezone_completion`; the completion route obtains deployment settings at `backend/app/api/routes/battle_plan.py:142`; `_planned_start` in `backend/app/services/task_completion_service.py:129` interprets wall-clock plans using `settings.app_timezone`. The caller never applies `activity_service.reporting_settings`, unlike the Day API.

**Suggested correction:** Resolve the shared Reporting Time Zone before completion decisions, including callers outside the HTTP route. Test both directions and distinguish wall-clock plans from exact plans whose `start_at` is already authoritative.

## B2 — Medium: Battle Plan and Recurring use a different Today from Day, Habits, and the routine calendar

**Impact:** Date-only deadlines can stay non-overdue after midnight or become overdue too early. A Recurring Task Series and its own calendar can disagree about the next occurrence. Recurrence generation/lifecycle routes also receive the old zone, so the inconsistency extends beyond labels; the probes specifically confirm deadline state and next-occurrence disagreement.

**Reproduce deadline state:**

1. Use the same timezone configuration and instant as B1 (02:00 Singapore on 6 November; still 5 November UTC).
2. Create a Task whose `deadline_date` is `2026-11-05`.
3. Inspect the creation response and `GET /tasks`.

**Expected:** The Task is overdue; `/tasks.timezone` is Asia/Singapore, matching the saved shared setting.

**Observed:** `overdue=false`; `/tasks.timezone="UTC"`.

**Reproduce recurring mismatch:**

1. At the same instant, create a scheduled daily Recurring Task Series starting `2026-11-05` with `confirm_backfill=true`.
2. Compare its creation/detail response with `GET /recurring-templates/ID/calendar?month=2026-11-01`.

**Expected:** Both views agree that Today and the next incomplete occurrence are 6 November.

**Observed:** The series says `next_occurrence="2026-11-05"`; its calendar says `today="2026-11-06"` and begins `upcoming` on 6 November.

**Evidence:** `reproduce.py:timezone_overdue` and `timezone_recurrence`. `backend/app/api/routes/battle_plan.py:85` and most of `backend/app/api/routes/recurring.py:43` onward depend directly on `get_settings`. Date-only overdue boundaries are calculated in `backend/app/services/battle_plan/_shared.py:37`. The calendar route correctly uses `get_reporting_settings` at `backend/app/api/routes/recurring.py:132`.

**Suggested correction:** Use the persisted reporting zone consistently for domain dates, independently of device-local reminder times. Cover requests that straddle midnight in differing zones. Treat this with B1 as one implementation workstream.

## B3 — Medium: archiving a completed Task Occurrence turns its Habit from Met into Missed

**Impact:** Housekeeping changes a historical Habit result even though the Task Completion still exists. It also makes the day untickable until the Task is restored from archive. This contradicts the domain definition that a series Habit counts every Task Completion and treats a completed scheduled occurrence as Met.

**Reproduction (API):**

1. Create a daily scheduled Recurring Task Series with `track_as_habit=true`, `confirm_backfill=true`, and a start date two days ago.
2. Complete that past date via `POST /habits/SERIES_ID/days/YYYY-MM-DD`. Its day is `met`.
3. Obtain the completed Task ID from `GET /recurring-templates/SERIES_ID/calendar?month=YYYY-MM-01`.
4. Archive that Task using `POST /tasks/archive-completed`, `{"task_ids":[TASK_ID]}`. The API accepts this with 204.
5. Read `GET /habits?week=YYYY-MM-DD` and `GET /tasks?state=archived`.

**Expected:** The Habit remains Met. Archive removes work from active lists; it does not undo Task Completion.

**Observed:** Habit day becomes `missed`, while the archived Task still has `status="completed"`.

**Evidence:** `reproduce.py:archive_habit_progress`; `backend/app/services/recurrence/habits.py:146` excludes archived Tasks from `root_task`, and `_scheduled_row` at line 195 interprets the absent root as incomplete. The routine calendar still includes the completion because it filters deleted Tasks but not archived Tasks.

**Reachability limitation:** Current `/tasks` active results hide completed recurring occurrences, so a click-only archive journey was not verified. The public archive API permits it, and existing archived occurrences can exhibit the inconsistency. Do not describe this as a reproduced browser/device interaction.

**Suggested correction:** Separate history eligibility from whether a Task may be edited. Count archived completions and checked checklist history while retaining archive restrictions on mutations.

## Reproduction bundle and results

From the repository root:

```powershell
& backend/.venv/Scripts/python.exe docs/reports/audit-2026-11-06/backend/reproduce.py
```

Observed final output (exit 0, approximately seven seconds including imports):

```text
B1 CONFIRMED: completion at 2026-11-06 02:00 Singapore deletes the already-past Nov 5 21:00 plan
B2 CONFIRMED: shared zone Singapore, /tasks timezone UTC, yesterday's deadline overdue=false
B2 CONFIRMED: same series next_occurrence=Nov 5 but its calendar today/upcoming=Nov 6
B3 CONFIRMED: archiving a completed Task Occurrence changes its Habit day from met to missed
CONTROL PASSED: delayed task/series assignments resolve merged category IDs correctly
```

These audit probes assert the *observed defects* and therefore exit successfully when reproduced. They are not regression tests asserting correct behavior. No persistent database is used. B1 and the deadline portion of B2 were reproduced across multiple runs; the final bundle rechecked every reported finding.

## Existing-suite findings and coverage

Command from `backend/`:

```text
uv run --extra dev --extra eval python -m pytest -q
8 failed, 884 passed, 22 skipped, 8 warnings in 291.82s (0:04:51)
```

The failures occurred on the unchanged `master` commit. They are baseline findings, not regressions caused by this audit.

- Seven failures are migration test-fixture drift. Tests construct the **current** metadata schema, selectively remove old features, stamp an old revision, and migrate to head. They leave `assistant_queries` present, so revision `040_assistant_task_reads` fails with `sqlite3.OperationalError: table assistant_queries already exists`. This does **not** establish that migrating a real historical database fails. Relevant fixture: `backend/tests/conftest.py:40`; separate fixture: `backend/tests/test_plan_now_migration.py:16`.
- `tests/test_assistant.py::test_provider_503_is_not_retried` exceeded its two-second timeout in the full suite. The isolated rerun passed (`1 passed in 1.57s`). Classify it as a timing-sensitive test result, not evidence of a user-facing provider-retry bug.

Migration failures:

```text
tests/test_legacy_cutover_migration.py::test_cutover_preserves_tasks_and_plans_but_deletes_legacy_actual_and_undo
tests/test_legacy_cutover_migration.py::test_cutover_detaches_every_stateful_or_malformed_child_without_orphaning_plans
tests/test_legacy_cutover_migration.py::test_cutover_reinterprets_scheduled_checklists_in_place_and_keeps_quota_semantics
tests/test_legacy_cutover_migration.py::test_cutover_downgrade_is_explicitly_restore_only
tests/test_plan_now_migration.py::test_migration_preserves_plans_correspondence_and_actual_constraints
tests/test_planned_block_name_migration.py::test_migration_adds_nullable_name_without_backfilling_existing_blocks
tests/test_planned_block_type_resolution_migration.py::test_migration_consolidates_raced_task_types_before_enforcing_uniqueness
```

Coverage included the existing API/unit suites for tracking, reconciliation and undo, planning, Task Completion, task lifecycle, recurrence, Habits, Time Goals, Trends, task-type merges, calendar import with fixtures, and Assistant behavior with controlled dependencies. The audit did not certify PostgreSQL concurrency, real Google OAuth/calendar delivery, paid-model behavior, or skipped tests.

## Follow-up investigations, not confirmed bugs

- Activity snapshots load every Actual Block and every operation (`backend/app/services/activity_service.py:42–55`), and every planned interval is included. Long-lived accounts warrant a latency/payload benchmark and a bounded-history design that preserves offline reconciliation. No representative production-volume performance benchmark was run, so this is a scaling risk rather than a demonstrated performance defect.
- Restore the migration fixtures so these seven checks once again verify real historical upgrades. Prefer authentic historical schemas or migration-built fixtures; merely making migration 040 silently skip an existing table would conceal the fixture problem.
- Delayed assignment of a merged Task Type initially looked unsafe from local service reads. The executed control disproved it: the central `Session.before_flush` hook redirects both Task and Recurring Task Series references to the surviving ID. It is deliberately excluded from the bug list.
