# Web behavior and usability audit

Investigation only; no application source files changed. Five findings below were reproduced with controlled React component tests against the actual page/editor code. HTTP responses and unrelated shell/tracking/timeline rendering are mocked to make races deterministic. These are component-level reproductions, not claims that browser/device end-to-end journeys were executed by this reviewer.

## Reproduction bundle

From the repository root after `cd frontend; npm ci; cd ..`:

```powershell
powershell -ExecutionPolicy Bypass -File docs/reports/audit-2026-11-06/web/run-repros.ps1
```

The runner temporarily copies `audit-repro.test.tsx` into the frontend root, runs Vitest, and removes the temporary file in `finally`. It does not change product code or contact a real backend. Each assertion describes the expected correct behavior; **all five tests currently fail**, intentionally. The observed output is retained in [repro-output.txt](repro-output.txt). The final runner verification completed in 3.58 seconds, exited 1 as expected, and left no temporary frontend probe behind.

## Confirmed bugs

### W1 — A late Day response removes the selected day's plans

**Priority:** P1 (misleading planner state). **Verification:** deterministic component reproduction.

**Reproduction:**

1. Have distinct Planned Blocks on two dates, A and B, and a loaded Activity Tracking snapshot.
2. Open date A while its `GET /days/A` request is delayed.
3. Navigate to date B before A finishes, e.g. browser history or another Day link. Let B's response finish first.
4. Confirm B's plans appear, then allow A's response to finish.

**Expected:** B's plans remain visible; obsolete responses are ignored.

**Actual:** B's heading remains visible but its Planned lane becomes empty. The old response replaces `storedDay`; `activityDay` discards its nonmatching date and supplies an empty `time_blocks` array. This is display corruption, not demonstrated server-side deletion. Without an Activity snapshot, the same stale response can instead replace the displayed date/data.

**Evidence:** W1 first asserts “Second day plan” is visible, resolves the delayed first-day response, then fails because the plans output becomes empty while the date is still `2026-06-02`.

**Source:** `frontend/src/features/today/TodayPage.tsx:164–189` unconditionally applies load responses; `frontend/src/features/activity/activityDay.ts:23` retains saved plans only when `day.date === date`. Mutation responses at `TodayPage.tsx:497–517` use the same unguarded setter and deserve the same protection.

**Direction:** Give each load a generation/date identity; apply responses only to the current date and latest relevant load. Guard mutation refreshes too. Reloading the selected date is a workaround.

### W2 — Checking two Subtasks quickly visually loses one successful check

**Priority:** P2. **Verification:** deterministic component reproduction with two deferred successful responses.

**Reproduction:**

1. Create an incomplete Battle Plan Task with two unchecked Subtasks.
2. Open its Subtasks on the card or in Task details.
3. On a slow connection, check the first and then the second before the first request completes.
4. Let the first check succeed, then the second.

**Expected:** Both checkboxes stay checked and progress reads 2/2.

**Actual:** The first checkbox becomes unchecked again after the second reply. Both requests succeeded. Refreshing should recover the server's state; this audit verified the rendering race, not a server persistence defect.

**Evidence:** W2 confirms the first checkbox is checked after response one, then finds it unchecked after response two.

**Source:** `frontend/src/features/battle-plan/BattlePlanPage.tsx:363–368` applies `withSubtask(storedTasks, saved)` to a closure snapshot. Adding a Subtask uses the same stale-array pattern at line 269. Card busy state in `BattlePlanCard.tsx:211–230` disables only the most recently clicked Subtask; it does not serialize different checks.

**Direction:** Merge individual successful Subtask responses into current state using a functional update, while preserving readiness coordination. Do not make unrelated successful changes roll back visually. Waiting for each check to finish is a workaround.

### W3 — A transient health/date failure permanently disables recurring creation

**Priority:** P2. **Verification:** deterministic component reproduction.

**Reproduction:**

1. Open Recurring while `GET /health` fails once (503, timeout, or blocked request), while template/project/type requests succeed.
2. Restore connectivity and use Active/Paused/Ended tabs.
3. Try New recurring task.

**Expected:** Explain the unavailable application date and provide Retry; successful recovery should re-enable creation.

**Actual:** The page shows the ordinary empty/list state, but New recurring task stays disabled without explanation. Changing status tabs never retries health. Leaving/re-entering or reloading the page is needed.

**Evidence:** W3 sees “No active templates,” a disabled create button, and no alert.

**Source:** `frontend/src/features/battle-plan/RecurringPage.tsx:89–96` silently swallows the mount-only health error; line 158 gates the button on `applicationToday` forever.

**Direction:** Retain an explicit health-load error and retry action, or obtain Today alongside the list response. Keep the loading state separate from failed loading.

### W4 — Keyboard focus escapes the recurring modal

**Priority:** P2 (keyboard/accessibility). **Verification:** deterministic Testing Library keyboard reproduction; real-browser assistive-technology behavior remains to be checked.

**Reproduction:**

1. Open New recurring task.
2. From the auto-focused Title field, press Shift+Tab repeatedly until Close recurring form is focused.
3. Press Shift+Tab again.

**Expected:** Focus wraps to the last control inside the modal; the background remains inert.

**Actual:** Focus reaches a control on the underlying page even though the editor declares `aria-modal="true"`. Keyboard navigation can operate obscured background controls.

**Evidence:** W4 focuses the modal's first button, sends a real simulated Shift+Tab, and finds `dialog.contains(document.activeElement) === false`.

**Source:** `frontend/src/features/battle-plan/RecurringPage.tsx:483–495` handles Escape/body scrolling but no focus containment; the editor at line 601 is a `<form role="dialog">`, not a native modal dialog.

**Direction:** Use a native modal dialog or shared dialog behavior with initial focus, focus containment, background inertness, Escape handling, and focus restoration.

### W5 — Save silently ignores an invalid visible deadline

**Priority:** P2. **Verification:** deterministic editor component reproduction.

**Reproduction:**

1. Open an incomplete Task with a date deadline, e.g. `2026-06-02`.
2. Change the title so Save is enabled.
3. Replace the deadline text with an invalid date, e.g. `2026-06-99` (a partial date has the same risk).
4. Click Save, then reopen the Task.

**Expected:** Prevent submission, explain the invalid date, and retain the draft for correction.

**Actual:** Save submits the title and the **old** deadline `2026-06-02`, then closes the editor. The visible invalid edit is silently dropped. This is not backend acceptance of an invalid date; the old value is what the web client sends.

**Evidence:** W5 first asserts `aria-invalid="true"`, then records `onPatch(11, {title: "Edited audit task", deadline_date: "2026-06-02", ...})` after Save.

**Source:** `frontend/src/components/CalendarDateField.tsx:27,46` keeps invalid visible text local and sets browser custom validity. `frontend/src/features/battle-plan/TaskDetailPanel.tsx:123,184–218,459` neither propagates this validity nor submits a form/checks validity; its ordinary button calls Save directly.

**Direction:** Make field validity part of Save eligibility and render an actionable validation message, or submit a valid enclosing form. Related redesign work already exists as issue #289; this is a specific behavioral acceptance case for it.

## Usability opportunities from source review

These are recommendations, not additional verified functional bugs or a pixel-level visual assessment.

1. **Make saving behavior explicit at each editor.** Planned Block Name/Note auto-save after 450 ms, Actual Blocks require Save changes, and Task details require Save. Use persistent “Saving / Saved / Couldn't save” feedback near the edited fields and protect dismissal while uncertain. Evidence: `TimeBlockInspectorContent.tsx:14–15,155–230`; `ActivityActualEditor.tsx:33–42`; `TaskDetailPanel.tsx:184`. This matters most during slow/offline saves.
2. **Correct the first-use Day instruction.** The Day empty-type banner says “Create task types before adding blocks,” although Planned Blocks support a name and the backend can materialize `unspecified`. Replace it with an optional categorization prompt and a direct first-plan action. Evidence: `TodayPage.tsx:750–758` and `TimeBlockInspectorContent.tsx:148–152`. Do not turn optional setup into an apparent prerequisite.
3. **Give Project editing standard dialog and error behavior.** `ProjectEditor.tsx:47–95` has an overlay but no dialog role/label, Escape handler or focus containment. Save/Delete callbacks can reject without inline editor error state; Delete remains enabled while busy. Use the same accessible modal contract as the other editors and show actionable operation errors next to the action. This was source-inspected, not separately reproduced as a bug in this bundle.
4. **Replace internal failure instructions with user recovery.** The API timeout says “Start the backend … check the Vite /api proxy target” (`lib/api/client.ts:116–119`). Show a concise connection error and Retry in the product, with technical details available separately. Keep task/plan drafts intact.
5. **Use domain terms consistently.** Recurring surfaces alternate “templates,” “Recurring,” and “Recurring Task Series” (`RecurringPage.tsx:154,175–177,604`). Choose concise labels that teach the same concept; reserve “template” for technical implementation details.
6. **Improve date/time entry alongside #289.** `CalendarDateTimeField` defaults to 09:00 and ignores time edits until a date is chosen (`CalendarDateField.tsx:69–89`). Make time-first entry possible, communicate reporting timezone near date/time fields, and validate incomplete edits before Save. This is a usability suggestion in addition to W5, not a separate reproduction claim.

## Existing verification results

| Check | Result |
| --- | --- |
| Dependency install (`npm ci`) | Passed; 332 packages installed. npm reported 24 dependency advisories, not assessed for reachable application impact here. |
| Existing Vitest suite (`npm test`, before probe insertion) | 466 passed, 3 failed; 51 files passed, 1 failed; 77.90 s. |
| Lint (`npm run lint`) | Passed with 0 errors, 2 `react-hooks/exhaustive-deps` warnings in GoogleCalendar.tsx (53, 162). |
| Production build (`npm run build`) | Passed. |
| Focused audit probes | Five reproducible failures against desired behavior; final runner verification 3.58 s. |

The three existing suite failures are all in `TodayPage.test.tsx`: future Actual placement and occupied Actual placement for Today/past date. They fail because `getByRole('alert')` now finds both the expected placement message and an unrelated GoogleCalendar “not found” alert from the fetch mock. The expected placement message is present. Treat these as test fixture/query maintenance until independently reproduced in the real app, not as three confirmed product bugs. No tests were changed to make this audit green.

## Scope and limits

Reviewed frontend routing, Day load/mutation flow, Planned/Actual editing, activity journal/time helpers, Battle Plan/subtasks, recurrence forms/lifecycle, Chronicle/Trends request handling, reusable calendar inputs, Settings, Google Calendar integration, and error/accessibility semantics. Read domain context and relevant ADR/design contracts. No paid model calls, production writes, live Google account changes, browser driving, device use, issue creation, or PR creation were performed by this reviewer. Browser visual evidence is owned by the main audit. A passing suite/build does not rule out the five interaction defects above.
