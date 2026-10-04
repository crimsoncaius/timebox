# Time Goal archive — issue 306

Status: design accepted and implemented on 4 October 2026; validated and running for user review on `codex/time-goal-archive`. No PR or merge.

## Settled

- Archive replaces the user-facing End goal action, preserving end-today behavior and excusing an unmet final period.
- Archived goals retain historical targets, results and contributing Actual Blocks; results still follow corrections to Actual Blocks (ADR 0017).
- Delete removes the goal and assessments, never Actual Blocks. It remains separately confirmed.
- The user explicitly chose no reactivation. Starting again requires a new Time Goal.
- The user chose A: a visible Archive action opens a separate, all-time list of archived goals.
- Archived goals disappear immediately from the current active collection, but remain marked Archived in past weeks where they applied.
- Each archived goal opens a newest-first list of periods, showing its dates, historical target, recorded time and outcome. A period opens its contributing Actual Blocks.
- Existing ended goals, including goals ended by replacement, belong in the archive.

## First comparison

Question: how should users reach and inspect archived goals while reviewing past weeks?

- A: a visible Archive action opens a separate, all-time list.
- B: Active / Archived collection controls remain above the content.
- Both initially use a newest-first period list for each archived goal. Comparison controls switch to weekly browsing and separately include/exclude archived goals from historical weeks.
- All alternatives use identical local sample data and no API mutations. Reset restores the scenario. The sample clock is 4 October 2026.

A was accepted on 4 October 2026. The user subsequently accepted historical-week inclusion and period-list history, completing the design decisions.

## Coverage

Try finding an older fortnightly goal, inspecting a changed historical target, viewing contributing blocks, archiving an active goal and locating it again, and comparing last week with archived goals included/excluded. Empty archive and offline retained data can be simulated in the comparison controls.

Review covers a goal archived during the selected current week, long daily histories, replacement copy and future-start goals (which retain delete-only lifecycle actions before their start). Pause/resume is outside this feature.

Prototype source: debug-only `GoalArchivePrototype.kt`, branch `codex/time-goal-archive`.

## Prototype evidence

Build with `./scripts/android-gradle.ps1 :app:assembleDebug '-PreviewApplicationIdSuffix=.goalarchive' '-PreviewApiBaseUrl=http://127.0.0.1:1/'`. The isolated debug package is `com.timebox.android.goalarchive`; its sample flow needs no backend. The unreachable API address prevents the surrounding app from reaching shared data.

Use `./scripts/goal-archive-prototype.ps1 -Token TOKEN -Variant page` (or `switch`). Deep links: `timebox://prototype/goal-archive?variant=page` and `timebox://prototype/goal-archive?variant=switch`. A/B changes preserve the current compatible navigation and data. Reset sample data restores the scenario and navigation while preserving A/B, past-week inclusion and period-list choices. Sample mutations are temporary and can reset on activity recreation.

Review device: `emulator-5586`, token `efbcdfe41eab4ce08eee8bec75b9d1ea`, owner `issue-306: archive design comparison`, storage root `C:/Users/Caius/TimeboxRuntime/emulators`. Preserve its pending review until the user finishes. Resume it with the repository helper for revisions, then restore its review hold.

The debug APK built successfully. On-device checks exercised A/B switching, past-week inclusion, period-list and week-based history, historical targets, contributing blocks, archive confirmation/removal from the current collection, rediscovery in the archive and separately confirmed deletion. Screenshots are under `.impeccable/review/goal-archive/`.

Independent native finish review: **ship for the design comparison**. Light/default and dark/1.3 text evidence was readable; no material fixes were requested. The documenter found no durable design-system change. This is not production validation: API integration, persistence, error recovery, tablet, TalkBack, hardware and full instrumentation are outside this prototype. Empty/offline controls are available for design exploration; they do not validate real network behavior. The main list uses selected-period summaries; unchanged production day-cell expansion and creation editing are outside this round.

Observed trade-off: B's Active label conflicts with showing archived goals in historical weeks. The accepted A preserves that complete historical record without the conflicting label.

## Production implementation

The archive uses existing end dates, including legacy ended goals and goals ended by replacement. No migration or reactivation endpoint is added. The existing `/end` mutation keeps its wire compatibility; all user-facing lifecycle labels use Archive. Deletion retains its separate confirmation.

`GET /time-goals/archive` returns metadata sorted by archive date, newest first. `GET /time-goals/{id}/history` returns at most 30 periods by default (maximum 50), with an exclusive `before` cursor for earlier pages. Each page queries its recorded-time range once and applies the existing assessment rules, preserving historical targets and recalculating outcomes after corrections. Current-week reports exclude archived goals; past-week reports retain them where their lifespan overlapped the week.

Android keeps the week position while visiting the archive, uses a lazy period list with Show earlier periods, and reuses one details sheet and action confirmation for current and archived goals. Failed refreshes retain the last complete loaded report; refreshing a paged history preserves all loaded pages atomically. Successful deletion removes the local goal and cached history even if its following reload fails.

Production review uses the same managed device above and an isolated SQLite backend at `http://127.0.0.1:12083` (Android `http://10.0.2.2:12083/`). Runtime database and launcher are ignored files under `artifacts/goal-archive/`; start with `start-backend.ps1`. Build with `./scripts/android-gradle.ps1 :app:assembleDebug '-PreviewApplicationIdSuffix=.goalarchive' '-PreviewApiBaseUrl=http://10.0.2.2:12083/'`, then launch the ordinary MainActivity and open Chronicle → Time Goals. The prototype route remains debug-only evidence, not the review entry point.

## Production validation

- Full backend suite: **592 passed, 8 skipped**. Time Goal coverage includes legacy archives, current/past week visibility, target changes, live corrections, pagination, daily/weekly/monthly boundaries, mutation rejection and preserved Actual Blocks.
- Android unit suite: **392 passed**. Added coverage for paginated refresh failure, cancelled navigation, immediate archive removal and delete-cache invalidation even when subsequent reloads fail.
- Focused device checks: **4 passed** across `TimeGoalArchiveTest` and existing `TimeGoalCreationTest`. One first-run archive test selected the identically named sheet action instead of its dialog confirmation; the selector was scoped to the confirmation dialog and both archive cases passed on rerun. The full instrumentation suite was not rerun; its known failures remain documented in `docs/agents/android-instrumentation-baseline.md`.
- Backend-connected phone walkthrough: archive from the current week, immediate removal and archive rediscovery, historical-week inclusion, changed targets and contributing blocks, long daily-history pagination, separate delete confirmation, retained history during a real backend outage and recovery through Retry. Deleting an archived sample goal left all **93 Actual Blocks** in the isolated database.
- Native screenshots in `.impeccable/review/goal-archive-production/` cover light/default and dark/1.3 text. The independent finish reviewer returned **ship** for this scoped phone extension with no material fixes. Tablet, physical hardware, TalkBack, measured contrast and motion were not assessed.
- The documenter confirmed this extends existing Android theme roles and components; no design-system files were changed. Pre-existing documentation/schema drift was left untouched.

The review app is left on the production Goal history screen with the isolated backend running. Sample data is synthetic. Offline retention is in-memory while the app remains open; this feature does not add a persistent offline archive. No reactivation is offered.
