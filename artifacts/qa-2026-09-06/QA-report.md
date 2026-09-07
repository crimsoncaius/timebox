**Timebox QA report — 6 September 2026**

Five confirmed issues. No application source files were changed and no fixes were made.

Tested the running web app at http://127.0.0.1:5176 in Chrome 152 at 1440×1000, 768×1024, 390×844, and 360×800. Also tested the running native Android app on the Pixel_9a emulator: Android 16, 1080×2424, density 420, font scale 1.0. Repository HEAD: `ebf691e`; application timezone: Asia/Singapore. This was an exploratory pass, not an exhaustive test suite or a physical-device/iOS test.

**1. High — Subtasks cannot be checked off (web and Android)**

Reproduction:

1. Open Battle Plan and create a task.
2. Open its details, enter a subtask title, and add it.
3. On web, click the subtask checkbox. On Android, scroll to Subtasks and tap Check.

Expected: the subtask becomes checked, the progress count updates, and the checked state survives reopening the task.

Actual: the subtask remains unchecked and progress stays at 0/1. The request returns HTTP 500. Android displays “Server error (500).” The web task dialog provides no visible explanation for the failed click; the error appears in the console. Reproduced repeatedly in desktop and mobile web layouts, in Android, and on a subtask generated from a recurring series.

Evidence: [Android error](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/android-subtask-check.png), [web dialog](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/web-subtask-failed.png), [console errors](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/subtask-console-errors.txt). Failed requests included `POST /api/subtasks/3/check` and `POST /api/subtasks/5/check`.

**2. High — Saving a block name overwrites a note being edited (web)**

Reproduction:

1. Open a Planned Block in Day and save a note such as “Previously saved note.”
2. Use browser network throttling to make saves take about 1–2 seconds.
3. Change the block's Name, then immediately move to Note and replace its text before the name save finishes.
4. Keep the Note field focused and wait for the name save to return.

Expected: the newly typed note remains in the field and can be saved.

Actual: the new text is silently replaced by the previously saved note when the name save finishes. Initially observed during normal editing; reproduced twice with controlled network latency. In the final reproduction, “New note that should not disappear” reverted to “Previously saved note.” Network throttling was removed afterward.

Evidence: [before the save response](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/note-before-save-response.png), [after the save response](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/note-after-save-response.png).

**3. Medium — Blocked tasks are displayed as Open (web)**

Reproduction:

1. Open a task in Battle Plan.
2. Set Status to Blocked and click Save.
3. Inspect the board, reopen the task, and refresh the page.

Expected: the saved blocked condition remains visible and the task's controls accurately represent it.

Actual: the task appears in Open, the Blocked column remains empty, and the details selector shows Open. The save actually succeeds: the response contains `is_blocked: true` and `status: "open"`, but the web UI does not represent the blocked condition. Android's task card does display the blocked badge for the same saved task. Confirmed in the desktop board and mobile web details.

Evidence: [web board](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/web-blocked-shown-open.png), [mobile web details](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/web-mobile-task-detail.png), [Android blocked card](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/android-qa-refreshed.png), [successful save response](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/blocked-save-response.txt).

**4. Medium — Permanently deleting an ended recurring series fails (Android)**

Reproduction:

1. In Battle Plan, open the list selector and choose Recurring.
2. Create a recurring series, or open an existing test series.
3. Open its details, scroll down, choose End, and confirm End template.
4. Choose Delete and confirm Delete permanently.

Expected: the ended series is removed, with generated tasks handled as described in the confirmation.

Actual: Android displays “Method Not Allowed” and leaves the series in place. Reproduced twice. The Android client calls `DELETE /recurring-templates/{id}`, while the running API exposes only GET and PATCH at that path.

Evidence: [failed deletion](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/android-delete-series-failed.png), [UI state](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/android-delete-series-failed.xml).

**5. Low — Unset priority values are clipped (Android)**

Reproduction:

1. Use the Pixel_9a emulator in portrait with its default font scale of 1.0.
2. Create or open a task with no Importance or Urgency set.
3. Look at the Priority card on the task details screen.

Expected: both values display the full “Not specified” label.

Actual: both values display only “Not”; the rest of the label is clipped. Reproduced on repeated visits without increasing the system font size. The accessibility hierarchy still contains the full text.

Evidence: [clipped priority values](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/android-priority-clipped.png), [full text in UI hierarchy](C:/Users/Caius/Desktop/timebox/artifacts/qa-2026-09-06/android-task-priority-repeat.xml).

**Other checks and cleanup**

The exercised task creation, Ready to Plan scheduling, block movement, recording Actual as planned, Work Mode recording and exit, Chronicle navigation, recurring-series creation and pausing/ending, task-type hierarchy creation and rename, mobile project drawer, and tablet route layouts worked in this pass. Global day-window settings were inspected but not changed. Notification delivery, physical devices, and iOS were not tested.

Temporary Planned and Actual Blocks and temporary Task Types were removed. The two temporary parent tasks were moved to Trash. The test series “QA 0906 web series” remains under Ended because issue 4 prevents deletion; it will not generate more tasks. The original active task and projects were preserved, and the application remains running. `git diff --exit-code` confirmed no tracked source changes.
