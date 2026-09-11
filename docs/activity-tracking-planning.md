# Plan-guided and direct Task tracking (#150)

Implements slice 4 of [#144](https://github.com/crimsoncaius/timebox/issues/144).
Production Work Mode remains gated separately; no deployment or cutover occurred.

## Prerequisite

GitHub's native blocked-by endpoint lists only #147, closed as completed with no
open blockers. Its implementation commits cc5b27c, c4bd4fc and b9c8009 are present.
The existing activity tests exercise that foundation again. Existing uncommitted
#148/#149 and unrelated changes were preserved; this slice uses their journal
and reconciliation.

## Behavior

- Empty Start captures the current plan in the Reporting Time Zone: Block Name,
  Task Type, Task identity, note and correspondence. Without a current plan it
  records unspecified time. Selection facts are journaled before local recording;
  retries and restart retain those facts and instants.
- Task details offers Track Task without creating a plan or changing readiness
  or Task Completion. Android returns to Day with Transient Feedback. Session
  Tasks can record; Quota Trackers and completed Tasks cannot start new work.
  Task-detail drafts must be resolved before tracking from that surface.
- A different current plan produces a quiet explicit Switch suggestion. It
  disappears when adopted, expired or tracking stops. Plan boundaries and ordinary
  plan edits do not automatically transition recording.
- Plans expose additive `actual_block_ids` and `actual_duration_minutes` in both
  Day representations, retaining the singular compatibility field. Both clients
  display record counts and aggregate duration with independent Task Completion.
  Development Actuals use the existing additive provenance index.
- Plan rename preserves Actual names. Plan reclassification/deletion detaches
  all corresponding Actuals and their replay intent without changing recorded
  facts; later replay cannot restore those links. Existing Actual correction
  name/unlink rules remain. Record-as-planned stays gated in development, with
  existing correspondence displayed; legacy duplicate guards and operation-scoped
  Undo remain intact.

## Validation (2026-09-11)

- Full backend SQLite suite: **275 passed, 2 PostgreSQL-only tests skipped**.
- Real PostgreSQL tracking/reconciliation/planning suite: **35 passed**, including
  concurrency, duplicates, resumed correspondence, delayed selections,
  rename/reclassification/deletion and Session/Quota behavior.
- Full web suite: **256 passed**. TypeScript build checking and changed-component
  ESLint passed. Screen tests cover offline plan start/restart, explicit resume,
  suggestion expiry, multiple records and direct Session Task identity.
- Android ActivityRepository suite: **6 passed**. Native Compose suite:
  **3 passed** on the emulator, including plan start/interruption/explicit resume.
  Debug app and instrumentation APKs built and installed successfully.
- Android full-suite attempt hit the previously documented DayWorkModeViewModelTest
  restoration assertion and repeated uncompleted-coroutine timeouts. The stuck
  test worker was stopped; the full Android suite is not claimed green.
- Live Chromium and Android: web empty Start adopted plan 12 with Task 1 and its
  name/type/note; native Track Task returned to Day with feedback and no plan
  link; web Track Task also recorded direct Task identity. Explicit plan resume
  on both clients produced three linked Actuals. Canonical API and Day duration/
  collection reads agreed, and Task status stayed open.

Evidence is under `artifacts/activity-150-*` (local development data). The local
PostgreSQL process needed restarting during review; its preserved cluster was
reopened on the same registered port without resetting review data.

## Review

Web: <http://127.0.0.1:12005/day/2026-09-11>

Android: `com.timebox.android.activitydev` on emulator-5554. Both updated apps are
left running. API uses port 12004; PostgreSQL uses port 12006 and `activity_review`.
Destructive tests used the separate `activity_test` database. Launch instructions
remain in [the development guide](activity-tracking-development.md) and
[offline review guide](activity-tracking-offline.md).

No production deployment, cutover, issue closure, or merge was performed.
Focus, inactivity/notifications, reporting-zone editing and historical correction
UI remain separate #144 slices. This slice adds no OS capability and makes no
hardware reboot/Doze or inactivity capability claim.
