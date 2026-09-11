# Activity Tracking integrated review candidate (#158)

This is a local, development-gated review candidate for [#144](https://github.com/crimsoncaius/timebox/issues/144), not a production deployment or cutover approval. Normal web production builds and Android release builds still disable Activity Tracking. Keep compatible clients available before any separately approved production cutover.

## Retirement boundary

The gated clients use the durable activity command contract for recording and corrections. Plan boundaries, absence, one-minute confirmation and Focus exit do not end Activity Tracking. Android's shared legacy entry boundary now also rejects resume and continue-entry, in addition to begin/restore; a regression test verifies that these calls neither write Actuals nor overwrite the recovery snapshot.

Legacy Work Mode remains in the ungated application until approved cutover. Removing it globally now would break the required additive rollout. The server's persistent cutover marker rejects legacy writers even if development flags are subsequently disabled. Compatibility rejection, archived source values, rejected envelopes and ordinary recovery editing remain intact. Do not delete those contracts or replay old envelopes as new intent.

The glossary in `CONTEXT.md` already distinguishes Work Mode, Activity Tracking, Focus Mode and the dismissible Android Inactivity Prompt. Earlier slice documents describe their implementation stage; the matrix below is the integrated status. Domain ADRs 0001–0004 remain unchanged.

## Acceptance matrix

Numbers follow the sixteen scenarios in the parent specification. Automated checks use public API, repository/ViewModel and rendered-screen boundaries; live checks use the real local PostgreSQL API, Chromium and API 36 emulator. A suite check is not a claim that every permutation was manually exercised on each platform.

| # | Evidence and result |
|---|---|
| 1 | `test_activity_api.py`, `test_activity_planning.py`, web ActivityTracking and native ActivityTracking tests: immediate start, optional-plan adoption, direct Task identity, switch and stop without completion. Live web Focus while off started immediately. |
| 2 | API planning/reporting, client repository and Focus suites preserve continuous recording and snapshot correspondence across plan/day boundaries. Live offline reload and native process restart preserve recording. |
| 3 | API concurrent/duplicate command tests and both repository suites cover durable admission, lost acknowledgements, restart and storage failure. Live offline web reload retained its pending switch. |
| 4–5 | PostgreSQL reconciliation suite covers delivery permutations, calibrated action ordering, ties, tombstones and partial overlap. New `test_activity_release.py` checks plan-linked fragment convergence in both delivery orders. Live earlier offline web switch followed by later native switch converged, preserving the earlier interval up to the later action. |
| 6 | API corrections/reconciliation, web ActivityCorrection and native ActivityCorrection screen tests reject known overlap; independent offline corrections reconcile. Native Compose checks passed against the rebuilt APK. |
| 7 | Reporting API and both correction-screen suites cover earlier days, complete cross-midnight intervals, zone changes and DST ambiguity/gaps. New integrated API check edits a surviving fragment after changing to Honolulu, preserving 120 linked minutes and stable IDs on retries. |
| 8–9 | Focus/controller, planning and rendered-screen suites cover entry/exit, draft/save guards, unknown state and restoration. Live both clients restored Focus after restart; native stop exited web Focus, and web stop exited native Focus. Stop was absent inside Focus. |
| 10 | API planning/describe, web Focus and native Compose tests cover original-start classification versus Start now, real Task identity, web optional name and native type-only prompt. |
| 11–12 | PostgreSQL check-in suite and both adapter/presentation suites cover shared rearm, stale candidates/responses, concurrent switch/stop, delivery identity, denial/revocation and unknown observations. Native Compose verifies dismissal to Check-in waiting and confirmation without changing the Actual. |
| 13 | Prior real-platform qualification is preserved in [Android detection](activity-tracking-android-detection.md) and [browser detection](activity-tracking-browser-detection.md). It includes API 33/36, usage access, reboot/Doze, notifications, Chromium permission/freeze/discard and unsupported Firefox. These longer qualification experiments were not all repeated in #158. |
| 14 | Focus wake suites plus the real sentinel/emulator checks in [Focus](activity-tracking-focus.md) establish visible-only wake behavior and failure handling. Restart restoration was repeated here; manual/security lock still wins. |
| 15 | Fresh `pg_dump`/`pg_restore` of the synthetic schema-027 source into `activity_rehearsal_158`, upgrade, preflight, import, pre-write rollback and re-import succeeded. IDs 2/3/4, 120 historical minutes, Task completion, links and original running start survived. After a new command, pause returned 503, rollback refused, resume/retry preserved all four records. Old-client reads returned 426 and legacy writes 409. Both live clients still display the previously rehearsed migrated data. |
| 16 | Full backend/web suites include Task Completion/Undo, recurrence, quotas, Session Tasks, Ready to Plan and rename. New integration checks retain multiple Actuals per plan and reject record-as-planned duplication after reconciliation/zone editing. Native focused planning tests and Compose checks pass; see the full-suite limitation below. |

## Runs on 2026-09-11

- Backend full suite: **296 passed, 6 skipped** (PostgreSQL-only cases).
- Real PostgreSQL activity, planning, reporting, reconciliation, check-in, cutover and release suites: **60 passed**.
- Web full suite: **285 passed**. TypeScript/production build and gated review bundle build passed.
- Android full unit suite with legacy build configuration: **227 passed, 1 failed**. The remaining `UncaughtExceptionsBeforeTest` reports a repository IO continuation returning after the Main test dispatcher was reset. The working tree contains unrelated rename-test changes; they are preserved, not included in this commit. This is not an all-green full Android run.
- Android affected legacy planning/execution suites pass after correcting a fake preview that incorrectly changed the server's today to the browsed date. Activity/Focus/cutover-guard suites: **14 passed** with development gating enabled. Both APKs built; **7 Compose tests passed** on API 36.
- The new legacy-entry regression was observed failing before the guard and passing afterward.

## Limits and recovery

The full Android unit run still needs isolation cleanup before treating the entire working tree as release-qualified. Physical-device/OEM behavior, API 26–32 runtime coverage and an actual old released APK upgrade remain unverified. Background delivery timing is conditional; disconnected devices cannot guarantee global notification deduplication. An uncalibrated offline clock cannot establish exact real-world ordering. No production environment or real user dataset was migrated.

Use the preserved [cutover and forward-recovery instructions](activity-tracking-cutover.md). Before new writes, use verified pre-write rollback; after new writes, pause and repair forward without reopening legacy writers. Keep both pre/post rehearsal dumps and rejected local data. The test fixture database `activity_test` is destructive and must never be used as the review database.

Evidence is local under `artifacts/activity-158-*`: full/focused logs, migration dump and rehearsal report, offline/converged UI snapshots, canonical interval JSON, Focus restart/remote-stop snapshots and final screenshots. Review web is [localhost:12005](http://127.0.0.1:12005), API is 12004, and PostgreSQL is 12006. The Android package is `com.timebox.android.activitydev`. Both final applications are launched from this working tree.
