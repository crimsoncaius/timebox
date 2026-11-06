# Android bug and usability audit — 6 November 2026

Scope: current Android source, domain contracts, navigation, task and routine state, reminder scheduling, timezone handling, settings accessibility, calendar conflict editing, and JVM tests. No production code was changed. This sub-audit did not operate a device; the parent audit owns visual review. Device reproduction steps below are supplied for review but were not executed by this sub-audit.

## Verification

- Existing JVM suite: **454 tests in 83 suites, zero failures/errors/skips**, executed successfully after setting process-local `ANDROID_HOME` to the installed SDK. See `unit-suite-summary.json`.
- `assembleDebug` succeeded with the isolated review endpoint `http://10.0.2.2:18001/`.
- Three additional audit probes failed with the expected, specific assertions against real production ViewModels/scheduling logic. These are new checks, not pre-existing suite failures.
- No instrumentation run. The historical baseline in `docs/agents/android-instrumentation-baseline.md` was read; its stale/known/flaky cases are not counted as new findings.
- Reproduce the three confirmed logic bugs from the repository root with `powershell -NoProfile -ExecutionPolicy Bypass -File docs/reports/audit-2026-11-06/android/reproduce.ps1`. This temporarily copies the supplied test into the unit source tree, runs it, captures XML, then removes only that temporary file. **Expected current result: exit 1, three failed tests.** No API, emulator, real data, or paid model calls are used.

## Confirmed bugs

### AND-01 — An older Task detail request can replace the task currently being edited

**Priority: P1. Verification: deterministic production ViewModel test.**

Reproduction:

1. Create two Battle Plan Tasks, A and B.
2. Delay the task-list response initiated by opening A, using a test server/proxy or the supplied harness.
3. While A is loading, choose **Close task**, then open B.
4. Allow B's read to finish; optionally begin changing B's title.
5. Release A's earlier response.

Expected: B remains the selected Task, with its draft intact. Actual: the shared ViewModel is replaced with A's identity and draft, even though navigation still refers to B. Any B draft is overwritten. Further actions use the now-replaced state, creating a risk of changing the wrong Task.

Evidence: `lateTaskReadMustNotReplaceNewerTask` loads Task 2, then releases Task 1's response. Assertion output: `Current task must remain Task 2 expected:<2> but was:<1>`.

Cause and location: `android/app/src/main/java/com/timebox/android/ui/battleplan/TaskDetailViewModel.kt:154` launches every load without cancelling earlier loads or validating a generation/Task ID on completion; line 188 unconditionally assigns the response to the shared state. This is the same ViewModel used for every Task detail route (`ui/TimeboxApp.kt:119`, `ui/TimeboxNavHost.kt:340`). The loading UI provides **Close task** (`ui/battleplan/BattlePlanScreen.kt:1604`).

Suggested correction: cancel superseded loads and guard every success/failure publication against the current request/Task identity. Preserve drafts when a refresh finishes after editing starts. The audit probe can become the regression test after the behavior is repaired.

### AND-02 — An older routine request can replace the currently opened routine

**Priority: P1. Verification: deterministic production ViewModel test.**

Reproduction:

1. Create two Recurring Task Series, A and B.
2. Delay A's `getRecurringTemplate` response while opening A.
3. Return to the series list while the request is pending and open B.
4. Let B finish loading; release A's old response afterward.

Expected: B's series and edit state remain selected. Actual: the editor's `templateId`, title, rule, and baseline become A's. The screen route still targets B. Starting or finishing a subsequent edit can operate on A.

Evidence: `lateRoutineReadMustNotReplaceNewerRoutine` reports `Current routine must remain Routine 2 expected:<2> but was:<1>`.

Cause and location: `android/app/src/main/java/com/timebox/android/ui/battleplan/RecurringEditorViewModel.kt:105` cancels only the recurrence preview, not the previous template load; line 119 unconditionally installs its result. The navigation graph shares this editor between all recurring routes. Unlike Task detail, the routine loading surface has no visible Close control (`RoutineScreen.kt:38`), so the interaction route needs system Back or another navigation entry; the direct ViewModel sequence is confirmed.

Suggested correction: give template loading a cancellable job and identity guard, separate from preview jobs; do not let obsolete responses replace the baseline or current editor.

### AND-03 — A Daily Reminder shifts from 02:30 to 03:30 after the spring DST transition

**Priority: P2. Verification: deterministic production scheduling-function test; continuing WorkManager schedule verified in source.**

Reproduction:

1. Set the device zone to `America/New_York` in an isolated test environment.
2. Configure a Daily Reminder for 02:30.
3. At `2026-03-08 04:00` in that zone, reschedule reminders (for example, save the preference again).
4. Inspect the next scheduled occurrence.

Expected: tomorrow's reminder is `2026-03-09 02:30 EDT`. Actual: it is `2026-03-09 03:30 EDT`. The selected preference remains 02:30. This also affects a reminder scheduled through the missing hour: the worker stores the normalized 03:30 time and passes that into subsequent daily scheduling, allowing the shift to persist until the settings are rescheduled.

Evidence: `springGapMustNotMoveTomorrowReminder` prints `Expected tomorrow 02:30; actual 2026-03-09T03:30-04:00[America/New_York]` and fails `expected:<02:30> but was:<03:30>`.

Cause and location: `android/app/src/main/java/com/timebox/android/reminders/DailyReminderInfrastructure.kt:55` resolves today's nonexistent local 02:30 to 03:30, then line 56 adds a day to the already-normalized value. Line 92 stores that adjusted clock time in worker input, and line 111 schedules the next reminder from it.

Suggested correction: choose the target local date first, then combine that date with the saved reminder time; carry the user's saved time separately from the resolved scheduled instant.

## Additional timezone and accessibility findings

AND-04 remains source-only. The parent audit subsequently confirmed AND-05 through the running app's native accessibility tree.

### AND-04 — Track Task and Daily Reminder links use the device date rather than Reporting Time Zone Today

**Priority: P2. Verification: source trace.**

Reproduction:

1. Use device zone `Asia/Singapore` and Reporting Time Zone `UTC`.
2. Between Singapore 00:00 and 07:59, open Day once so its Today resolution has completed.
3. Open a Task and choose **Track**.
4. Alternatively, deliver a Daily Reminder in that interval and tap it.

Expected: Day opens on the date containing the current instant in UTC. Actual path: the navigation request uses Singapore's newer date. For example, at Singapore `2026-11-07 01:00`, it requests November 7 while Reporting Today is November 6. The running Actual Block/Now Line belongs to the prior day and may seem missing from the opened timeline.

Locations: `ui/TimeboxNavHost.kt:365` calls `AppRoutes.day(LocalDate.now())` after tracking; `reminders/DailyReminderInfrastructure.kt:132` embeds the same device date in its deep link. `ui/TimeboxApp.kt:188` applies the explicit route date. `ui/day/DayViewModel.kt:212` resolves Today only once and subsequently reloads the selected date, so the existing Day initialization does not generally repair this navigation. Fresh-process behavior has an additional initial Today resolution and needs separate confirmation.

Suggested correction: use `activityRepository.now()` in the current snapshot's Reporting Time Zone for Track, and resolve a Today-intent in that zone when a Daily Reminder is opened. Daily Reminder *delivery time* remains device-local, as intended by the domain contract.

### AND-05 — Settings switches have no accessible name on their actionable node

**Priority: P2. Verification: source semantics and parent audit native accessibility-tree inspection; TalkBack speech remains untested.**

The parent opened Settings on Android 36 and captured the Keep display awake in Focus and Inactivity check-ins controls. Both actionable nodes have `roleDescription=Switch` and `checked=true` but no label; the setting titles are siblings. See [native tree](../ui/android-settings-semantics.json) and [screenshot](../ui/android-settings.png). These measured controls have 126×126px effective bounds at density 2.625, or 48×48dp; the smaller drawn switch should not itself be reported as a deficient hit target.

Reproduction: enable TalkBack, open Settings, and navigate by controls to **Dark theme**, **Day planning reminder**, **Day review reminder**, or **Remind me before Planned Blocks**. Inspect the accessibility name and state of the focused switch.

Expected: the actionable switch announces its setting name and On/Off value. Actual semantics in source: each switch supplies `Role.Switch` and `stateDescription` only. Its visible title is a sibling, and `SettingRow` neither merges semantics nor labels the toggle. Users navigating directly among controls can encounter several indistinguishable On/Off switches.

Locations: `ui/components/Common.kt:69` (`TimeboxSwitch`), line 197 (`SettingRow`); call sites in `ui/settings/SettingsScreen.kt:167`, `:355`, and `:402`. Some callers elsewhere may provide labels via the optional modifier; these Settings callers do not.

Suggested correction: label each switch explicitly or make the labeled row one accessible toggle with the child switch excluded as a separate action. Confirm names, state, focus order, and toggle behavior with TalkBack and semantics tests.

## Usability opportunities

All are source-grounded recommendations, not measured usability study results. Preserve the app's existing visual language and domain model.

| ID | Priority | Friction and evidence | Improvement and acceptance check |
| --- | --- | --- | --- |
| AND-U1 | P2 | Google Calendar conflict resolution requires typing full `YYYY-MM-DDTHH:MM` strings in **Move or shorten** (`ui/calendar/GoogleCalendarPanel.kt:226`). This makes a routine time adjustment significantly harder than the rest of Day editing. | Reuse the existing date/time controls and show duration plus Reporting Time Zone. Invalid intervals should produce adjacent field feedback before preview. Verify a move, shortening, midnight crossing, and keyboard visibility. |
| AND-U2 | P2 | Calendar conflict editing silently chooses the earlier occurrence of a repeated local time: line 241 checks only that an offset exists, then calls `local.atZone(zoneId)`. It cannot express the later 01:30 at a fall-back transition, unlike `data/ReportingTime.kt`. | Offer Earlier/Later selection for ambiguous local times and show the UTC offset in review. Verify both occurrences of New York `2026-11-01 01:30` can be selected without a one-hour surprise. Treat as a functional timezone defect if support for these edits is required. |
| AND-U3 | P2 | Calendar Task Type and Task pickers render all candidates in plain dropdown menus (`GoogleCalendarPanel.kt:253`, `:263`) instead of the established searchable Task Type picker. Large collections are difficult to scan; Task names lack project context. | Reuse the shared searchable category control and add searchable task selection with Project/series context. Verify two identically named tasks in different Projects remain distinguishable. |
| AND-U4 | P2 | **Save connection** shows “Connection saved” before verifying usability (`ui/settings/SettingsViewModel.kt:92`). Input has no URL validation; the example is port 8000 although this workspace defaults to 8001 (`SettingsScreen.kt:184`). | Validate/normalize the URL before saving, distinguish “settings saved” from “connected,” and expose a Test connection result. Use a correct or neutral example. Verify malformed URLs, unreachable servers, and wrong keys give clear recovery without discarding entered values. |
| AND-U5 | P2 | API key uses the same unmasked `ConnectionField` as a URL (`SettingsScreen.kt:187`, `:407`). Opening Advanced exposes the complete secret; ordinary text keyboard settings are used. | Default to masked input with an explicit Show/Hide action and password-appropriate keyboard behavior. Verify the key remains editable/pasteable and does not appear in screenshots while hidden. |
| AND-U6 | P2 | The routine's loading/error branches return before rendering its Close control (`RoutineScreen.kt:38`); users must infer system Back while a slow server is pending. | Keep the sheet/header and a visible Close/Back action in loading and error states. Verify leaving a stalled load remains possible and the next routine cannot be replaced by its late response (AND-02). |
| AND-U7 | P3 | Compact custom switches draw at 48×28 dp (`Common.kt:85`), and several date controls draw at 40 dp (`ui/chronicle/CustomRangeSheet.kt:222`). Framework touch expansion may help but source dimensions alone cannot establish effective hit area. | Measure effective targets on a device, including adjacent controls; expand the interactive row/target where needed without inflating the visual switch. Verify with large fonts and motor-accessibility use. |

## Limits and exclusions

No claims are made about frame rate, battery usage, real notification delivery timing, microphone capture quality, TalkBack speech, landscape/large-font clipping, or network-loss behavior on a physical device. Those require instrumentation or live-device evidence. The source review deliberately did not count documented best-effort reminder delivery, one-time Today initialization preserving a selected date, or the existing instrumentation baseline as fresh bugs. No Android runtime crash was observed in this sub-audit.
