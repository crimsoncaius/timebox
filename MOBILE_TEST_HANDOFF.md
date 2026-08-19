# Native Android Mobile Testing Handoff

Copy the prompt below into a new Codex task to complete the remaining mobile coverage.

## Handoff prompt

```text
Continue read-only QA of the native Android app in:

C:\Users\Caius\Desktop\timebox

Read MOBILE_BUG_REPORT.md first. It contains the completed environment details, automated-test results, five verified defects, tested areas, and screenshot evidence. Do not change application source, project configuration, schemas, or existing Timebox data. Do not attempt to fix bugs. Produce only additional reproducible bug findings and a final coverage update.

Important current state:

- Previous tested commit: eed9327b93275f58b7a0802552b516dca1dfe85a
- App version: 0.1.0, versionCode 1
- Previous debug APK SHA-256: B33CACD0A648567FCC7218C920E87468B068C1F84F541D22CF4F05C5E9E2E301
- Previous device: Pixel 9a, Android 16/API 36, 1080x2424, 420dpi
- Previous backend: disposable SQLite, AUTO_CREATE_TABLES=1, APP_TIMEZONE=Asia/Singapore, no API key
- Timebox API port 8001 is already registered for this workspace. Follow the manage-dev-ports skill and verify the registry plus live listeners before starting anything.
- The previous emulator and backend were stopped, and the previous disposable SQLite database was deleted.
- The existing Pixel_9a AVD was previously launched read-only with -no-snapshot-load and -no-snapshot-save, so its persistent profile was not modified.
- The worktree already contains substantial user changes. Preserve all of them.

Before testing:

1. Record the current Git commit and status.
2. Record a fresh SHA-256 manifest of all tracked and non-ignored workspace files. Do not assume the prior manifest still applies because MOBILE_BUG_REPORT.md and MOBILE_TEST_HANDOFF.md were intentionally added afterward.
3. Verify port 8001 is free and still registered to this Timebox workspace.
4. Create a new uniquely named temporary directory under the OS temp directory and place a fresh disposable SQLite database there.
5. Start the backend on 127.0.0.1:8001 with AUTO_CREATE_TABLES=1, APP_TIMEZONE=Asia/Singapore, no API key, and CORS_ORIGINS=*.
6. Launch Pixel_9a read-only with snapshots disabled, or create an equivalent disposable API 36 emulator if that AVD is unavailable.
7. Build/install the current debug APK. If the source changed since the previous run, rerun unit, assemble, and connected tests and report the new APK hash.

Prioritize the unfinished coverage:

1. Weekly recurring templates: creation, edit, preview, generation dates, pause/resume/end, and validation.
2. Monthly recurring templates: edge dates such as 29/30/31, month boundaries, preview, generation, and validation.
3. Quota recurrence: actual task generation and cycle behavior, not just editor controls and preview.
4. Recurrence backfill, cycle limits, checklist/subtask generation, and generated-task linkage.
5. Battle Plan manual drag reordering with several tasks, persistence after navigation/relaunch, and interactions with filters/sorts.
6. Subtask matrix: create multiple levels where supported, edit, complete/uncomplete, reorder, trash, restore, archive, parent state changes, and persistence.
7. Project edit flow, empty/duplicate-name validation, linkage, deletion constraints, and deletion effects.
8. Destructive confirmations using test-only records: permanent task deletion, permanent template deletion, type migration, and type deletion. Verify both cancel and confirm paths. Pause before any action that could affect non-test data.
9. Malformed and boundary input matrix across task, project, type, block, reminder, and recurrence forms. Include empty, whitespace-only, oversized, invalid date/time order, invalid numeric bounds, and Unicode where applicable.
10. Controlled HTTP 500/502/503 responses and recovery. Capture user-facing state, retry behavior, duplicate-submit protection, and whether unsaved input survives.
11. Reminder resilience: process death, emulator reboot, Doze/app standby if practical, deep links after cold start, and cancellation after task state/deadline changes.

Known bugs already verified in MOBILE_BUG_REPORT.md:

- BUG-01 P3: task-detail header exposes numeric database ID.
- BUG-02 P2: immediate Pause -> Resume removes today's recurring occurrence.
- BUG-03 P3: Server preview heading has poor dark-theme contrast.
- BUG-04 P3: status-bar icons are dark/invisible in dark theme.
- BUG-05 P1: End template executes destructively without confirmation.

Do not report those again unless behavior changed in a newer build or you discover a materially different impact. If retested, mark the result as regression verification rather than a new defect.

For every new defect include:

- ID continuing from BUG-06, severity P0-P3, affected area, and frequency.
- Exact preconditions and numbered steps.
- Expected versus actual result.
- Screenshot plus relevant logcat/API evidence in a uniquely named OS temp evidence directory.
- Workaround, if any.
- A note distinguishing direct observation from inference.

At the end:

1. Stop only the backend and emulator started for this continuation.
2. Delete the new disposable database and any emulator profile created specifically for the continuation. Do not delete the user's existing Pixel_9a AVD.
3. Keep screenshots/log evidence in the OS temp evidence directory and identify that path.
4. Recompute the workspace manifest and verify it matches the fresh baseline exactly.
5. Do not edit MOBILE_BUG_REPORT.md or any repository file. Return the additional report inline so the user can decide whether to merge it into the existing report.
```
