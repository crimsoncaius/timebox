# Ongoing Actual Block details (#174)

Selected design: variant C, a compact summary with expandable editing. Prototype retained separately on `codex/prototype-current-activity` at `85df0cd`; no prototype controls or sample records ship with this implementation.

Android shows the activity identity, classification when present, reporting-zone start, live elapsed duration, optional note and linked task. Edit details saves name, Task Type, note, and start without ending tracking. Switch and Stop use the existing timing sheets. Actions and saves prevent duplicate submission; corrections reject overlaps and future starts. A changed or ended current record closes the running sheet.

The API accepts an open-ended edit only for a known running target. Finite edits cannot stop a running block; deleting a running block remains disallowed. Existing reconciliation orders corrections by action time, including a later Stop arriving before an older correction.

Validation so far:
- Activity API/reconciliation/check-in/planning/reporting: 50 passed, 5 PostgreSQL-specific skips using an isolated SQLite test database.
- Android ActivityRepository tests passed, including durable offline running correction and restart.
- Native interaction verification recorded separately after emulator run.
- Native RunningActualSheetTest passed on Pixel_9a after restarting an unresponsive emulator. Verified summary, editable name, save, preserved note, and open end. Latest native build and all 10 repository tests passed.
