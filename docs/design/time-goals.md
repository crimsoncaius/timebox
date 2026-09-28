# Time Goals in Chronicle

Status: Time Goals and its separate Chronicle tab are implemented. The creation refinement on `codex/time-goal-entry` was accepted for merge on 28 September 2026. The original design was accepted on 2026-09-27, including B (expandable rows), the connected creation/edit/lifecycle flow and the effective-date distinction between target edits and replacements.

## Production implementation

The `/time-goals` API persists goal identity, cadence, start/end dates and period-effective target history. Progress is derived on each read through the same Reporting Time Zone clipping used by Trends. Migration `038_time_goals` adds the two tables; deploy the backend migration with the updated Android app. Goal deletion never deletes Actual Blocks. Task Type deletion separately requires explicit goal deletion or retargeting; merge retains separate goals and reports their count.

Android's Chronicle has separate Habits and Time Goals tabs, with dedicated Add Habit and Add Time Goal actions. Time Goals includes expandable duration rows, day-selected periods, contributing blocks and creation/edit/end/delete sheets. It refreshes while displayed, retains the last complete report on connection failure, and keeps a failed-save draft available for retry. Existing completion Habits retain their controls. No durable report cache or offline goal-write queue was added.

Validation: full backend suite 559 passed, 6 skipped; all 382 Android unit tests and the debug APK build passed. Tests cover fixed daily/weekly/monthly boundaries, short first periods, target history, no carryover, parent/descendant credit, running intervals and DST, corrected historical outcomes, atomic replacement, migration, Task Type merge/deletion resolution and offline report retention. On-device checks cover real creation, target edits, selected-day periods, end/delete, replacement, contributing records and offline retention. The full Android instrumentation suite was not run; its existing baseline is documented in `docs/agents/android-instrumentation-baseline.md`.

Native visual review: `ship` for Android phone light/default and dark/1.3-font captures in `.impeccable/review/time-goals-production/`. The feature reuses the native theme and recurring-editor controls; the independent documenter found no durable design-system change.

Review runs against isolated SQLite sample data at `http://127.0.0.1:12073` (Android `http://10.0.2.2:12073/`). Local runtime files are under ignored `artifacts/time-goals/`; restart with its `server.ps1`. The debug build uses `-PreviewApiBaseUrl=http://10.0.2.2:12073/`. No shared backend data is used.

Managed review device: `emulator-5582`, token `cb73d7346f46486e9ccd7188ef94b0dc`, owner `time-goals: production review`, storage root `C:/Users/Caius/TimeboxRuntime/emulators`. Leave this device/backend available until review is complete, then release it using the emulator helper. The prototype branch remains independent; its source and comparison controls were not promoted into production.

## Accepted navigation split

On 2026-09-28 the user accepted **B — Separate tabs** and authorized implementation. Chronicle now has Calendar, Trends, Habits and Time Goals; bottom navigation is unchanged. Each tracker owns its week, loading/error state, and saved scrolling state. Goal expansion and selected periods survive tab switches. Time Goals failures do not prevent Habits from loading. Existing goal editor and lifecycle behavior are unchanged.

Validation of the navigation split: 383 Android unit tests passed and the debug APK built successfully. The added regression covers separate week positions and a Time Goals connection failure while Habits continues loading. Existing Time Goals tests now exercise its dedicated view model, and Habits tests no longer stub goal requests. Backend behavior did not change. On-device checks confirmed independent weeks, retained expansion/scrolling, and both creation sheets. The independent native reviewer returned **ship for user review** for light/default and dark/1.3 captures in `.impeccable/review/time-goals-split-production/`; documentation review found no durable design-system change.

Primary comparison evidence: local branch `codex/prototype-habits-goals-split`, commit `4845a68a`, including `docs/design/habits-goals-split-prototype.md` and its screenshot set. The debug prototype and its sample data stay on that branch.

## Accepted behavior

### Creation refinement — 28 September 2026

The user chose C1's underlined sentence, then requested bottom sheets instead of inline controls and authorized implementation. New Time Goal reads “I want to spend [duration] on [Task Type Path] [every N periods].” Each underlined value opens its own native sheet. Duration offers hours/minutes and 30m, 1h, 2h and 4h presets; Task Type reuses the searchable hierarchy picker and creation action; period reuses the calendar cadence controls. The complete Task Type Path wraps rather than truncates.

The start date and first-period deadline remain visible, with period dates and counting rules behind First period. A shortened first period always shows its full-target warning. Default calendar starts, explicit custom starts, input validation, failed-save/offline draft retention and discard confirmation retain their existing semantics. Done, sheet dismissal and Android Back return to the sentence with the current draft. Existing-goal editing retains its target-versus-replacement explanation and save behavior.

The comparison source remains on `codex/prototype-time-goal-entry`, including accepted C1 bottom sheets at `4f02714f`; no prototype switcher, sample data or debug route was promoted. This is a scoped composition using existing Android tokens, not a new design system.

Validation: debug build and all 388 unit tests passed. Two focused Compose instrumentation tests passed on the managed phone: invalid-minute validation and corrected creation payload (monthly default start), plus draft retention, field-sheet Back and discard confirmation. A real creation against isolated SQLite saved a 2h goal every two weeks and returned the matching calendar period. Light/default and dark/1.3 captures, including a long Task Type Path, are in `.impeccable/review/time-goal-entry-production/`. Independent native finish review returned **ship**; the documenter found no durable system changes. Full instrumentation, tablet, TalkBack and physical-device IME behavior were not covered; the emulator offered handwriting input.

Completed creation review: `emulator-5586`, token `3bad67a4fb1b48d6973cf3b14632c726`, storage `C:/Users/Caius/TimeboxRuntime/emulators`. Isolated package `com.timebox.android.goalentry` uses backend `http://127.0.0.1:12077` (Android `http://10.0.2.2:12077/`) and ignored `artifacts/goal-entry/review.db`. Restart the backend with `artifacts/goal-entry/start-backend.ps1`; build with quoted Gradle arguments `'-PreviewApiBaseUrl=http://10.0.2.2:12077/' '-PreviewApplicationIdSuffix=.goalentry'`. The user accepted this review and requested merge on 28 September 2026. Its device is released through the emulator helper; acquire a fresh managed device to reproduce it.

### Goal rules

- Independent Time Goals have their own Chronicle tab beside completion-based Habits; no recurring task is required.
- A selected Task Type includes its own Actual Blocks and all descendants. Exercise, cardio and strength goals can coexist; cardio time credits both cardio and exercise.
- Target duration repeats every N days, weeks or months. Periods are fixed, not rolling, with no surplus carried forward.
- Daily cycles anchor to the chosen start date; week and month cycles anchor to its Monday-based calendar week or calendar month in the Reporting Time Zone. An intentionally shortened first period retains its full target. Creation shows the dates before saving.
- Creation defaults to the current period's start and allows backdating. Existing records count; periods before the chosen start are not judged.
- Actual Blocks alone supply credit, including manually recorded blocks and running blocks through now. Corrections, deletion and reclassification recalculate past progress and outcomes. Planned Blocks and Task Completion supply no credit.
- A target edit takes effect next period and preserves historical targets. Changing Task Type or recurrence ends the old goal today and starts a replacement today, with the full target applying to its potentially shortened first period.
- End goal preserves history and excuses an unfinished final period. Delete goal requires confirmation and removes goal assessments, never Actual Blocks. Pause/resume is deferred.
- Task Type rename preserves goal identity; merge transfers goals to the survivor while retaining separate goals. Deletion requires retargeting or deleting affected goals. Current hierarchy determines historical credit, matching Trends.
- Android first. Daily cells show duration; row details show full period dates, progress and contributing Actual Blocks. Longer periods retain full-period totals across browsed weeks. No time-goal completion ticks.
- A row initially selects Today's period in the current week, or Sunday's period in a past week. Selecting a day changes the row's selected period and its dates/progress, including when one displayed week intersects several periods.
- Habits offers Add Habit; Time Goals offers Add Time Goal. Time Goal creation collects Task Type, hours/minutes, every N days/weeks/months, start date and a first-period preview. Details expose editing, ending and deletion, reusing recurring-task editor controls.
- Connection is required to create/edit goals and refresh progress. Already-loaded progress remains visible offline with a last-updated message; unsynced local Activity Tracking contributes only after synchronization. Durable offline reports and goal mutation queues are outside the initial scope.

## Prototype question

How should daily recorded time and independently sized goal periods coexist with completion Habits on a narrow phone?

Compare three structures in the existing Android debug prototype shell: compact grid, expandable rows, and period-first list. Inherit native Timebox tokens, Chronicle tabs and bottom navigation. All records and interactions are in-memory samples; no goal API or database is involved.

Use the same exercise/cardio/strength data plus daily reading and fortnightly study, with current and previous weeks. Inspect overlap credit, target met/exceeded, and period boundaries. The first round chose the presentation; the connected round now adds creation, day selection, editing, ending/deletion and simulated offline state using temporary sample data.

## Selected presentation

The user selected B, expandable rows. Keep the full Task Type Path, period dates, actual/target duration and progress visible; expand a row for daily recorded time and access to contributing Actual Blocks. This balances explicit period boundaries and hierarchy with optional daily detail. A and C remain on the prototype branch as comparison evidence.

The user accepted the connected workflow after reviewing the prototype. The design interview is complete; no design choices from this review remain open. Implementation must use the accepted behavior above rather than treating the throwaway sample model as production code.

## Run and review

Branch: `codex/prototype-time-goals`. Debug source only; no production goal behavior.

Build with `./scripts/android-gradle.ps1 :app:assembleDebug`. Follow `docs/agents/android-emulators.md` to acquire or resume a device, then install the debug APK through its helper. Launch the connected round with `./scripts/time-goals-prototype.ps1 -Token TOKEN` (defaults to `flow`). The original comparison remains available with `-Variant rows`, `grid` or `periods`. Each has a deep link: `timebox://prototype/time-goals?variant=flow` (substitute the variant). Reset, Empty and Go offline are prototype-only controls.

Review tasks: compare exercise against cardio/strength; open cardio's contributing records; navigate to 14–20 September to see cardio above target; confirm study retains 7h / 8h across both weeks of 14–27 September; inspect the daily reading goal by tapping a day's cell in rows or periods. The compact grid uses minutes in its narrow cells and exposes the final day's daily goal period from the row.

The completion Habit rows are static context samples. The connected round has in-memory goal mutations and a simulated offline state; backend integration and real synchronization remain outside this study. Chronicle Calendar/Trends tabs are contextual; use the real app navigation to exit.

Connected review tasks: Add → Time Goal; select a Task Type; change cadence and inspect first-period dates; save and inspect progress. Tap a day on Reading to change the period shown. Edit a target and inspect its next-period effective date. Change Task Type or cadence and inspect the explicit end-and-replace preview. End an unmet goal to see its excused final period; delete it to remove its row while leaving sample Actual Blocks intact. Go offline to retain progress while disabling mutations and uncached week navigation; Empty reveals the first-goal entry point; Reset restores all samples.

Accepted effective-date distinction: replacing a goal takes effect today, ends the previous goal and starts the replacement with its full target for the shortened first period. A target-only edit takes effect next period. The editor explicitly previews these effects before saving.

## Validation and completed review

Debug APK built successfully. Emulator checks exercised the layout switcher, previous/current week navigation, contributing-record sheet and Android Back. Normal light captures cover all three layouts; dark theme at 1.3 font scale covers expandable rows and period-first. Independent visual review disposition: ship for this scoped prototype comparison. The user subsequently selected B. Minor refinements for the selected design: expansion-chevron state and daily-cell spacing.

Connected round: build passed; on-device checks created a two-month cardio goal with 7h of existing records, selected Reading's Monday period (30m / 30m), scheduled a 1h 30m target for 28 September without changing its existing 30m target, ended its unmet current period (Excused), and deleted its goal row. Also exercised the end-and-replace save, discard prompt, empty state and simulated offline retained progress. Expansion chevrons now reflect state, cells use 8dp spacing, and sheets are opaque with the native sheet shape. Fresh light and dark/1.3-font editor captures passed independent visual review: ship for this prototype round. No durable design-system change was introduced.

Completed review used `emulator-5584`, owner `time-goals: Habits prototype review`, under `C:/Users/Caius/TimeboxRuntime/emulators`. The review reservation was retired after acceptance; acquire a fresh managed device to reproduce the study. No backend is required. Prototype source is preserved on `codex/prototype-time-goals`, with the connected round at commit `7620b33c`.

Local screenshots: `.impeccable/review/time-goals/` (original layout comparisons plus connected flow, create, create-months, edit-target, replace-preview, flow-detail, daily-selection, target-saved, ended, offline, empty and dark/large editor captures). This is a temporary design model, not production persistence/synchronization or exhaustive recurrence validation; no real records were modified.
