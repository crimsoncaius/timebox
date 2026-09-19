# Recurring routine sheet — issue 218

## Accepted direction

The user wants recurring creation, details and editing to follow the settled Android Battle Plan task design. Opening a series should primarily manage the routine. Current work belongs below its definition. This revises the current-work-first direction in recurring-details-hierarchy.md for this exploration.

## Round 1

Question: does a compact Repeat row with a focused editor make recurrence fit naturally into the task sheet?

The debug-only `layout=routine` route uses the app theme, TaskSheetRow and TaskSubtasks. Title, description, individual Importance/Urgency and Task Type editors, and Subtask rows surround Repeat. Scheduled routines also show a separate Pre-planning row. There is no series completion checkbox or Ready to Plan switch: those belong to occurrences.

Local sample edits only. Close the sheet for Scheduled, Quota, creation and Reset controls. Review editing weekdays or weekly quota, returning to the summary, and adding Subtasks. Creation uses the same layout and a Create routine button.

Route: `timebox://prototype/recurring-details?flow=details&layout=routine&mode=scheduled` (also flow=create, mode=quota).

## Assumptions and coverage gaps

- Per-field edits follow the accepted ordinary-task pattern; recurrence edits commit as one group.
- Weekly recurrence is interactive. Other frequencies, starts/ends, preview and backfill consequences remain for later rounds.
- Pre-planning editor is a labelled placeholder. Occurrence navigation and lifecycle actions are deferred.
- Task Type editor is a text stand-in. Real picker, persistence, errors/retry, unsaved-change protection and state restoration are not implemented.
- No production behavior changes. The prior recurring prototype remains available at layout=rows.
- User has not yet reviewed or accepted the Repeat-row design.

## Verification

Debug APK build and diff whitespace check passed. Installed and launched on emulator-5596. Visually inspected the routine sheet; changed Repeat to Monday and Tuesday and verified the saved summary; switched to Quota and verified its weekly summary and session progress. Reset to the scheduled sample for user review.

Retained review token: `63443f6c1bfc48b69a10cd26d44ef737`. Screenshots/UI dumps: `artifacts/routine-218/`. No backend needed for sample edits.

## Round 2 — express schedule and quota

User liked the first sheet and requested discussion and a working presentation of the original recurrence choices. Keep the routine-first sheet and Repeat entry; expand its editor.

- Two creation choices: On a schedule and Flexible quota, each with a short behavioral explanation. Existing routines show their chosen mode as a fixed heading, matching the current domain restriction.
- Scheduled: Daily/Weekly/Monthly, interval, weekdays for Weekly, day of month for Monthly. Short months use their final day, matching backend behavior.
- Quota: Daily/Weekly/Monthly period and count. No date selection within the period or interval multiplier.
- Shared Starts date picker and mutually exclusive Ends choices: Never, On date, After cycles. End limits now live together rather than across optional sections.
- Scheduled-only keep-overdue control. Pre-planning stays separate on the routine sheet.
- Readable summary updates in the editor and commits with Save repeat / Set repeat. Cancel leaves the saved rule unchanged. Existing recurrence validation gates Save.

The earlier weekly-only coverage limitation is superseded. Live generation preview, backfill confirmation, pre-planning, persistence and recovery remain deferred. This is a single direction grounded in the supported model; the user has not yet accepted this expanded editor. Close the routine sheet and choose Try creation to compare both modes in one draft.

Round 2 verification: APK built successfully and diff check passed. On emulator-5596, opened creation Repeat, inspected Weekly and Monthly schedule controls, switched to Monthly quota, saved and verified `3 times per month` on the routine sheet with Pre-planning absent. Visually inspected both schedule and quota editor screenshots. Left creation Repeat open on quota so the user can switch modes directly. Date pickers and all end configurations have not yet received device interaction coverage.

## Production implementation

User accepted round 2 and requested implementation. Normal Android recurring creation and details now use RoutineScreen; the prior full-form editor is no longer on those navigation paths. Repeat supports both creation modes and all existing frequencies/end conditions, with the mode fixed on existing routines. Metadata uses ordinary TaskFieldChip controls and focused editors. Subtasks, Task Type search/create/unset, Pre-planning, upcoming windows, current-task navigation, lifecycle actions and confirmations are connected.

Existing routines save each focused edit with a minimal PATCH. Successful responses replace the saved baseline; errors retain the pending draft with Retry save and Discard edit. Backfill confirmation remains explicit. Repeat and other focused editors ask before discarding local changes and retain their draft through Activity recreation. Creation keeps the normal explicit Create routine action and displays the live recurrence preview. Editing a recurrence with pre-planned slots also exposes those slots so incompatible weekday changes can be corrected before saving.

Validation: 14 focused unit tests passed (recurrence rules, partial PATCH semantics, failed-save retry and baseline replacement). Debug APK build passed. On the isolated API, changed the scheduled sample from Monday to Monday/Thursday, verified its persisted API state and reopened it; created a weekly quota of three sessions through the UI; paused and resumed the scheduled routine. Inspected the real details layout. Full app regression suite and process-death recovery were not exercised.

Review uses emulator-5596, token `63443f6c1bfc48b69a10cd26d44ef737`, and isolated SQLite API on port 12031. Keep this API running for review. Build command: `./scripts/android-gradle.ps1 :app:assembleDebug -PreviewApiBaseUrl=http://10.0.2.2:12031/`. Ordinary builds still use the configured default API. Review data lives under ignored `artifacts/routine-218/`.
