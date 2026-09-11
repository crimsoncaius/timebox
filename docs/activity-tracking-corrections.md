# Day activity corrections (#152)

Implements the accepted current and historical correction flows under #144 and
the historical resolution in #139. Native blockers #149, #150 and #151 were
closed and integrated before implementation (baseline `12589ea`).

Both Day tracking controls offer Now, 15 min ago, explicit date/time selection,
and an After this change preview. Android uses native date/time picker actions.
Cancel leaves tracking unchanged. The form retains its observed Current Activity
identity; a remote switch cannot silently retarget an open correction. Effective
time is bounded to the current interval and now; action time remains the time
the correction was authored. The API also checks predecessor transitions.

Earlier Actual selection uses the existing Day inspector rail/sheet on web and
the existing Day block sheet on Android. Missed time uses the existing Actual
lane add flow. Both dates and times edit the full original record across midnight.
Unchanged fields preserve original seconds. A daylight-saving gap is rejected;
a newly selected repeated local time requires an explicit earlier/later choice.
The shared Reporting Time Zone is displayed in the editor.

Add, edit and delete use the durable activity journal. Clients reject known
overlap before saving; the API validates the complete interval against observed
history and the author's preceding offline changes. Corrections retain source
and observed start identity across local IDs, moves and partial acknowledgements.
Deletes paint only the observed record's interval as unrecorded; neither adjacent
records nor the Current Activity are extended or inferred. Plan correspondence,
Undo invalidation and independent Task Completion retain the established rules.

Day projects pending Actual changes immediately, including offline earlier dates
and restart. Android's running day share uses calibrated time with a minute tick;
Actual projection preserves unrelated Day fetch/error/materialization state.
Offline fallback can show recorded history without an available Day request;
uncached planning information still requires connectivity.

## Verification

- Full backend: **281 passed**, three PostgreSQL-only skips.
- Isolated PostgreSQL `activity_test`: **42 passed**, including concurrent writers,
  delivery permutations, repeated move/delete, overlap and predecessor bounds.
- Full web: **265 passed**. Final focused correction/tracking: **17 passed**;
  TypeScript, changed-file ESLint and gated review build passed.
- Android ActivityRepository: **7 passed**. Native Compose: **5 passed**, including
  both new correction tests, fold/gap rejection, earlier-day full-record editing,
  local Day updates, late-stop preview and inert Cancel. Both APKs built.
- Full Android attempt reproduced the existing DayWorkModeViewModelTest restoration
  assertion and planning/midnight coroutine timeouts. The isolated test worker
  was stopped after a bounded attempt; the full Android suite is not green.
- Independent Standards and Spec reviews each reported one finding: projection
  clearing unrelated Day state, and predecessor lower-bound validation. Both were
  fixed and independently re-reviewed with no remaining findings.

Real Chromium: a fold-day edit changed 20 to 30 minutes offline, survived offline
reload, then synchronized. Real Android: added NativeMissed152 through the prior
Day sheet offline, force-stopped/restarted, and replayed the original 13:15–13:45Z
range. Web then changed that record to Writing 10–12, rejected Lunch 11–11:30,
shortened Writing to 10–11, successfully added Lunch, and deleted Lunch. The final
Day contains Writing 10–11 and an unrecorded gap after it. Current Chapter review
and its plan correspondence remained intact. Evidence: `artifacts/activity-152-*`.

## Running review instances

- Web: <http://127.0.0.1:12005/day/2026-09-10>, visible Chrome `activity152`.
- Android: `com.timebox.android.activitydev` on `emulator-5554`.
- API: loopback 12004; PostgreSQL 12006, database `activity_review`.

Both apps run from the final working tree. Additive development gates remain.
No production migration, cutover, deployment or GitHub writes were performed.
