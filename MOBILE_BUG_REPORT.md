# Native Android Mobile Bug Report

## Test status

This report covers the native Android testing completed on 2026-08-18 before the session was stopped. The project was exercised as it existed in the workspace. No application source, project configuration, schema, or existing Timebox data was changed by the testing session.

Five reproducible defects were found: one P1, one P2, and three P3 issues. No P0 issue, crash, or ANR was observed.

## Tested build and environment

| Item | Value |
| --- | --- |
| Git commit | `eed9327b93275f58b7a0802552b516dca1dfe85a` |
| App version | `0.1.0` (`versionCode 1`) |
| Build | Debug APK |
| APK | `C:\Users\Caius\Desktop\timebox\android\app\build\outputs\apk\debug\app-debug.apk` |
| APK SHA-256 | `B33CACD0A648567FCC7218C920E87468B068C1F84F541D22CF4F05C5E9E2E301` |
| Device | Pixel 9a emulator, 1080 x 2424, 420 dpi |
| Android | Android 16 / API 36 |
| Emulator mode | Existing `Pixel_9a` AVD launched read-only with snapshots disabled |
| Backend | Disposable SQLite-backed API on `127.0.0.1:8001` |
| Backend environment | `AUTO_CREATE_TABLES=1`, `APP_TIMEZONE=Asia/Singapore`, no API key, `CORS_ORIGINS=*` |
| Test data | Disposable test-only records; no existing Timebox database was used |

The test run started with 244 tracked and non-ignored workspace files. Their aggregate SHA-256 was `b229008218546d38f7d3d028b0c27de7d4df681bba2f6f445bfb93b5deb9a7bd`. The workspace already contained uncommitted user changes before testing.

## Automated test results

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | Passed; 60/60 tests, 0 skipped |
| `:app:assembleDebug` | Passed |
| `:app:connectedDebugAndroidTest` | Passed; 2/2 tests |
| Runtime crash/ANR check | No Timebox fatal exception or ANR observed in `logcat` |

Primary build command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain --no-daemon
```

The unit and connected test tasks were also rerun with `--rerun-tasks` so cached results could not mask failures.

## Defects

### BUG-01 — Task details header displays the database ID instead of the task title

| Field | Value |
| --- | --- |
| Severity | P3 |
| Area | Battle Plan / Task detail |
| Frequency | 2/2 |

Preconditions:

- At least one task exists.

Steps to reproduce:

1. Open **Battle Plan**.
2. Create a task named `QA ready task`.
3. Open the task from Battle Plan, or open its delivered reminder notification.
4. Inspect the task-detail header.

Expected result:

The header identifies the task by its title, or uses a generic label such as **Task details**.

Actual result:

The header reads **Task 1**, exposing the internal numeric database ID, while the actual task title appears separately in the title field.

Evidence:

![BUG-01 task header](C:/Users/Caius/AppData/Local/Temp/timebox-mobile-evidence-4bf15402482d411e958ce41c3ed45de4/BUG-01-task-header.png)

Workaround:

Use the title field to identify the task and ignore the header.

### BUG-02 — Pausing and immediately resuming a recurring template deletes today's occurrence

| Field | Value |
| --- | --- |
| Severity | P2 |
| Area | Battle Plan / Recurring templates |
| Frequency | 2/2: once through the UI and once with an API-created test template |

Preconditions:

- The app date is 2026-08-18.
- An active daily recurring template starts on 2026-08-18.
- Today's generated task is still pristine.

Steps to reproduce:

1. Create a daily recurring template whose start date is today.
2. Confirm that generated tasks exist for 2026-08-18 through 2026-08-25; the observed initial count was eight.
3. Open the template.
4. Tap **Pause**.
5. Immediately tap **Resume**.
6. Return to the generated-task list and inspect the dates.

Expected result:

Resuming immediately preserves today's generated occurrence, or the app explicitly warns that pausing will remove it and asks for confirmation.

Actual result:

Today's 2026-08-18 task is deleted. Seven tasks remain, dated 2026-08-19 through 2026-08-25. The template overview still reports **Next 2026-08-18**, which disagrees with the generated tasks.

Evidence:

![BUG-02 pause removes today's task](C:/Users/Caius/AppData/Local/Temp/timebox-mobile-evidence-4bf15402482d411e958ce41c3ed45de4/BUG-02-pause-removes-today.png)

Backend count/date checks confirmed the task loss in both reproductions.

Workaround:

Do not pause an active template on a day whose generated occurrence must be retained. If it has already happened, manually recreate the missing task.

### BUG-03 — The Server preview heading is unreadable in dark theme

| Field | Value |
| --- | --- |
| Severity | P3 |
| Area | Recurring-template editor / Dark theme |
| Frequency | 2/2 across create and edit surfaces |

Preconditions:

- App theme is dark.

Steps to reproduce:

1. Open **Battle Plan** and then **Recurring**.
2. Create a new recurring template, or edit an existing template.
3. Enter a valid recurrence rule so the server preview renders.
4. Scroll to the preview card.
5. Inspect the **Server preview** heading.

Expected result:

The heading meets readable dark-theme contrast expectations.

Actual result:

The heading is rendered in a near-black color on a dark-brown card and is difficult to read.

Evidence:

![BUG-03 dark preview contrast](C:/Users/Caius/AppData/Local/Temp/timebox-mobile-evidence-4bf15402482d411e958ce41c3ed45de4/BUG-03-dark-preview-contrast-clean.png)

Workaround:

Switch to light theme while editing recurring templates.

### BUG-04 — Status-bar icons become dark and effectively invisible in dark theme

| Field | Value |
| --- | --- |
| Severity | P3 |
| Area | App chrome / Dark theme |
| Frequency | Reproduced across Day, Battle Plan, and Recurring screens |

Preconditions:

- App theme is dark.

Steps to reproduce:

1. Enable dark theme.
2. Open **Day**, **Battle Plan**, or **Recurring**.
3. Inspect the system status bar.

Expected result:

The time, network, and battery icons use a light appearance against the black status bar.

Actual result:

The status-bar icons remain dark and are nearly invisible. Opening a dialog temporarily changes them to white, confirming that the underlying icons are present and their appearance is incorrect.

Evidence:

![BUG-04 dark status bar](C:/Users/Caius/AppData/Local/Temp/timebox-mobile-evidence-4bf15402482d411e958ce41c3ed45de4/BUG-04-dark-status-bar.png)

Workaround:

Use light theme when status-bar visibility is important.

### BUG-05 — End template is destructive and executes without confirmation

| Field | Value |
| --- | --- |
| Severity | P1 |
| Area | Battle Plan / Recurring-template lifecycle |
| Frequency | 1/1 |

Preconditions:

- An active recurring template has generated pristine current or future tasks.
- The tested template had seven such tasks.

Steps to reproduce:

1. Open the active recurring template.
2. Confirm that current or future generated tasks exist.
3. Tap **End** once.
4. Observe whether a confirmation appears.
5. Inspect the template state and generated-task count.

Expected result:

The app explains that ending the template may remove pristine generated tasks and requires explicit confirmation before applying the destructive action.

Actual result:

No confirmation dialog appears. The template ends immediately, the current generated-task count changes from seven to zero, and the screen reports **Recurring template ended**. The template then exposes a **Delete** action.

Evidence:

![BUG-05 end without confirmation](C:/Users/Caius/AppData/Local/Temp/timebox-mobile-evidence-4bf15402482d411e958ce41c3ed45de4/BUG-05-end-without-confirmation.png)

The separate permanent-delete flow was inspected afterward and did show a confirmation dialog; it was cancelled.

Workaround:

Do not tap **End** unless removal of pristine generated tasks is intended. There is no in-app undo for the observed action.

## Areas tested with no observed defects

- Cold launch against an empty disposable database.
- Main navigation, system back behavior, portrait/landscape rotation, light/dark switching, 1.3 font scale, and keyboard entry.
- Day timeline block creation, notes, nested type creation (`coding/qa`), task linkage, Ready-to-Plan, completion, and Actual creation.
- Timeline dragging and resizing. An overlap PATCH correctly returned HTTP 422 and the UI snapped the block back.
- Day swiping to the next date and back.
- Chronicle month navigation and opening a day in Day.
- Review planned/actual totals and type summary for the created test data.
- Types hierarchy, usage counts, used-type deletion warning, and migration options. The final destructive action was cancelled.
- Battle Plan task and project creation; empty-name validation; priorities; High filter; Urgency sort; status tabs; parent/child display; trash/restore; archive/unarchive.
- Scheduled daily recurrence creation, editing, preview, pause/resume UI, current tasks, quota editor controls, and preview rendering, apart from the defects above.
- Permanent recurring-template deletion confirmation and cancel behavior.
- Notification permission denial and later grant.
- Reminder delivery, task-title content, notification deep-linking, and automatic cancellation.
- Reminder cancellation after task completion. The task was completed before its reminder became due; no notification was delivered and `delivered_at` remained empty.
- Reminder validation without a deadline, which correctly returned HTTP 422.
- Offline behavior: Day displayed a connection error and **Try again**, Battle Plan displayed an unavailable banner, and both recovered after the backend restarted and retry was invoked.
- Empty states, expected validation failures, and lack of crash/ANR during the completed coverage.

## Coverage not completed

Testing stopped before these areas were exhaustively covered:

- Weekly, monthly, and quota recurrence generation and generated-task validation.
- Recurrence backfill, cycle limits, and checklist generation.
- Battle Plan manual drag reordering.
- The complete subtask completion, reordering, trash, and restore matrix.
- Project editing, duplicate validation, and deletion.
- Final permanent task/type/template deletion and actual type migration. Their dialogs were inspected, but destructive confirmation was not executed.
- A broad malformed-field and simulated HTTP 5xx response matrix.
- Long-duration reminder behavior across process death, emulator reboot, and Doze.

See `MOBILE_TEST_HANDOFF.md` for a ready-to-use continuation prompt.

## Evidence location

Screenshots remain in the OS temporary evidence directory:

```text
C:\Users\Caius\AppData\Local\Temp\timebox-mobile-evidence-4bf15402482d411e958ce41c3ed45de4
```

The disposable backend database is not retained.
