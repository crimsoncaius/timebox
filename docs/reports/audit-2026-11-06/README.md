# Timebox bug and usability audit

**Implementation update:** The findings below describe the original audited commit. All listed fixes and improvements are now implemented on `t3/bug-usability-audit`; see the [resolution report](resolution.md) for current behavior, validation, and running review instances. The reproduction steps remain useful for verifying the original defects; the fixed branch is expected to pass the new permanent regression tests.

6 November 2026 · Commit `ab7b736b4aa486aaa0c2e0ea470a2731a838fe5a`

The audit identified **14 verified defect findings and one additional source-supported finding requiring device confirmation**, plus **12 prioritized usability improvements**. Four findings deserve priority: Task Completion deleting past plans after a timezone change, stale web responses hiding plans, and stale Android responses replacing the Task or routine being edited. The last two create a risk of acting on the wrong item.

This was an investigation, not an implementation. No application source was changed, no issues or pull requests were created, and no production data was used. Findings describe the audited commit; they are not regressions introduced by this work. B1 and B2 share a root cause and should be addressed together rather than treated as unrelated implementation tasks.

## Evidence and scope

The investigation combined independent backend, web, and Android source reviews; existing unit/API tests; new deterministic reproductions; live web inspection at 1280×800 and 390×844; and an Android 36 emulator launch, Day/Settings review, and native accessibility-tree inspection. Domain definitions, relevant ADRs, design contracts, open issue titles, and the Android instrumentation baseline were checked.

Evidence labels matter:

- **API:** real FastAPI routes/services with an isolated in-memory SQLite database and controlled clock where necessary.
- **Component:** actual React components with controlled responses and selected unrelated dependencies mocked. These prove the named state/validation defects, not a complete browser journey.
- **JVM:** actual Android ViewModels or scheduling logic with controlled dependencies.
- **Browser / native tree:** directly observed in the running app. Native accessibility names were inspected; TalkBack speech was not tested.
- **Source only:** a concrete code path with supplied reproduction conditions, still awaiting execution on a device.

The browser used a separate SQLite audit database on API port 18001 and web port 15174. Activity Tracking was enabled for the later checks. The first empty-Day capture used the default tracking gate; its 404 responses were an environment setup condition, not a reported application bug. The Android APK was built from this worktree against the isolated API. Only synthetic audit data was created.

Not covered end-to-end: PostgreSQL concurrency, real Google OAuth/import, paid Assistant models, actual notification delivery, microphone capture, physical-device behavior, full Android instrumentation, large-font/landscape layouts, full dark-theme coverage, or production-volume performance. The web E2E suite was not run. Existing fixture-backed tests cover portions of these features, but do not substitute for those live checks.

## Prioritized defect register

P1 means meaningful risk to saved history, current editing identity, or the planner's reliability. P2 means a bounded incorrect result, inaccessible interaction, or recoverable workflow failure. No P0 outage was established.

| ID | Priority | Defect | Evidence |
| --- | --- | --- | --- |
| B1 | P1 | Completing a Task can delete an already-past Planned Block after changing Reporting Time Zone | API |
| W1 | P1 | A late Day response removes the selected day's visible plans | Component |
| AND-01 | P1 | An older Task load replaces the Task currently being edited | JVM |
| AND-02 | P1 | An older routine load replaces the currently opened routine | JVM |
| B2 | P2 | Battle Plan and Recurring disagree with the saved Reporting Time Zone | API |
| B3 | P2 | Archiving a completed occurrence changes its Habit result from Met to Missed | API; click-only archive path not established |
| W2 | P2 | Concurrent Subtask checks visually undo an earlier successful check | Component |
| W3 | P2 | One failed date/health request silently disables recurring creation until remount | Component |
| W4 | P2 | Keyboard focus escapes the recurring modal | Component |
| W5 | P2 | Task Save silently discards an invalid visible deadline edit | Component |
| AND-03 | P2 | A saved 02:30 Daily Reminder becomes 03:30 after spring DST | JVM |
| AND-05 | P2 | Android Settings switches lack accessible names | Native tree + source |
| UI-01 | P2 | The closed mobile web sidebar remains in the keyboard focus sequence | Browser + source |
| UI-02 | P2 | Navigation from Day opens Settings partway down the page | Browser + source |

**Separate, not counted as executed:** AND-04 — Track Task and Daily Reminder navigation use the device date rather than Reporting Today. Source trace is strong, but the date-boundary journey still needs device confirmation.

## How to reproduce the bugs

### B1 — Task Completion deletes past plans

Use deployment timezone UTC and Reporting Time Zone Asia/Singapore. At `2026-11-05T18:00:00Z` (02:00 Singapore on November 6), create a Task with a Planned Block on November 5, 21:00–21:30, then complete the Task. Read that Day again.

**Expected:** the historical plan remains. **Actual:** the completion response lists its ID in `removed_planned_block_ids`, and the Day loses it. Completion interprets the plan as 21:00 UTC instead of Singapore time. The reproduction harness freezes the relevant clock; no machine clock change is required.

Start with `backend/app/api/routes/battle_plan.py:142` and `backend/app/services/task_completion_service.py:129`. Resolve the persisted reporting zone before deciding which plans are future. [API steps, payloads, and evidence](backend/findings.md#b1--high-completing-a-task-can-delete-already-past-planned-blocks-after-a-reporting-time-zone-change).

### W1 — Late Day response hides current plans

Have different plans on dates A and B and a loaded Activity snapshot. Delay the Day A response, navigate to B, let B load, then release A's old response.

**Expected:** B stays intact. **Actual:** B's heading remains but its Planned lane becomes empty. This is a display-state bug; the audit did not establish server-side deletion. Reloading B recovers it.

Guard load and mutation-refresh responses by current date/request identity. See `frontend/src/features/today/TodayPage.tsx:164` and `frontend/src/features/activity/activityDay.ts:23`. [Detailed reproduction](web/findings.md#w1--a-late-day-response-removes-the-selected-days-plans).

### AND-01 — Late Task response replaces the current editor

Open Task A with its read delayed, close it while loading, open Task B, and allow B's read to finish. Then release A's response. The supplied JVM probe performs this order deterministically.

**Expected:** B and its draft remain selected. **Actual:** state changes back to A while the route still targets B. A B draft can be overwritten; subsequent actions use the replaced state. The wrong-item mutation risk was identified from the callers, not demonstrated by saving a wrong Task on the device.

Cancel superseded jobs and guard both success and error publication. See `android/.../ui/battleplan/TaskDetailViewModel.kt:154,188`. [Full paths and test evidence](android/findings.md#and-01--an-older-task-detail-request-can-replace-the-task-currently-being-edited).

### AND-02 — Late routine response replaces the current editor

Open routine A with its detail response delayed; go back and open B; let B load first, then release A.

**Expected:** B remains selected. **Actual:** the editor's template ID, title, recurrence rule, and baseline become A's. The loading route requires system Back because it lacks a visible Close action. The response ordering is confirmed by the production ViewModel probe; the full gesture sequence was not executed.

See `android/.../ui/battleplan/RecurringEditorViewModel.kt:105,119`. [Full reproduction](android/findings.md#and-02--an-older-routine-request-can-replace-the-currently-opened-routine).

### B2 — Different parts of the app disagree about Today

Use B1's timezones and frozen instant. Create a Task due November 5 and inspect `/tasks`. Also create a daily Recurring Task Series starting November 5 and compare its response with its calendar endpoint.

**Expected:** the deadline is overdue and both recurrence views use November 6. **Actual:** `/tasks` reports UTC and `overdue=false`; the series reports next occurrence November 5 while its calendar reports Today/upcoming November 6.

Use the shared reporting settings consistently across Battle Plan and recurrence routes. Fix alongside B1. [Exact API reproduction](backend/findings.md#b2--medium-battle-plan-and-recurring-use-a-different-today-from-day-habits-and-the-routine-calendar).

### B3 — Archive changes Habit history

Create a daily series with habit tracking starting two days ago. Complete that past Habit day. Retrieve the occurrence's Task ID from the routine calendar, then call `POST /tasks/archive-completed` with that ID. Read Habits and archived Tasks.

**Expected:** the Habit stays Met. **Actual:** it becomes Missed even though its archived Task remains completed. The archive API accepts the operation; the current clients hide some completed occurrences, so a click-only path was not demonstrated.

Historical completion counting must not depend on active-list eligibility. See `backend/app/services/recurrence/habits.py:146,195`. [Detailed API steps](backend/findings.md#b3--medium-archiving-a-completed-task-occurrence-turns-its-habit-from-met-into-missed).

### W2 — Concurrent Subtask checks lose visible progress

Create a Task with two unchecked Subtasks. Check both before either request completes; resolve the first successful response, then the second.

**Expected:** 2/2 stay checked. **Actual:** the first becomes unchecked again because the second response applies an old Tasks array. This is a confirmed rendering race, not proven backend loss. Waiting between checks avoids it; refreshing should recover server state.

Use a functional state merge in `frontend/src/features/battle-plan/BattlePlanPage.tsx:363`. [Detailed evidence](web/findings.md#w2--checking-two-subtasks-quickly-visually-loses-one-successful-check).

### W3 — Recurring creation stays disabled after a transient failure

Open Recurring while `/health` fails once, with other list requests succeeding. Restore connectivity and change the Active/Paused/Ended tabs.

**Expected:** an error and Retry, followed by recovery. **Actual:** the normal list/empty state appears, but New recurring task remains disabled without explanation. Reloading or leaving/re-entering is required.

See `frontend/src/features/battle-plan/RecurringPage.tsx:89,158`. [Detailed evidence](web/findings.md#w3--a-transient-healthdate-failure-permanently-disables-recurring-creation).

### W4 — Recurring modal permits focus behind it

Open New recurring task. Shift+Tab from the Title field back to Close recurring form, then Shift+Tab once more.

**Expected:** wrap to the modal's last control. **Actual:** focus leaves the modal and reaches the underlying page despite `aria-modal=true`. Component keyboard reproduction is confirmed; screen-reader behavior was not tested.

Use shared modal focus containment, background inertness, and focus restoration. See `frontend/src/features/battle-plan/RecurringPage.tsx:483,601`. [Detailed evidence](web/findings.md#w4--keyboard-focus-escapes-the-recurring-modal).

### W5 — Invalid deadline text is silently dropped on Save

Open a Task due `2026-06-02`. Change its title, replace its deadline text with `2026-06-99`, then Save.

**Expected:** validation blocks saving and retains the draft. **Actual:** the title is saved with the old June 2 deadline and the editor closes. The field marks itself invalid, but Save does not check that validity. The backend never receives the invalid text.

See `frontend/src/components/CalendarDateField.tsx:27,46` and `frontend/src/features/battle-plan/TaskDetailPanel.tsx:123,184,459`. Incorporate this acceptance case into [existing issue #289](https://github.com/crimsoncaius/timebox/issues/289). [Probe evidence](web/findings.md#w5--save-silently-ignores-an-invalid-visible-deadline).

### AND-03 — Daily Reminder shifts after DST

In America/New_York, configure 02:30. Evaluate rescheduling at March 8, 2026, 04:00 local time, after the spring-forward gap.

**Expected:** March 9 at 02:30. **Actual:** March 9 at 03:30. The scheduler resolves today's missing hour first, then adds a day to that normalized value. Its worker also carries the adjusted clock time forward.

Combine the target date with the saved preference afresh, and retain the preference separately from its resolved instant. See `android/.../reminders/DailyReminderInfrastructure.kt:55,92,111`. [Deterministic reproduction](android/findings.md#and-03--a-daily-reminder-shifts-from-0230-to-0330-after-the-spring-dst-transition).

### AND-05 — Unnamed Android Settings switches

Open Settings and inspect the actionable switches for Keep display awake in Focus and Inactivity check-ins. With TalkBack, the corresponding review is navigation directly among controls.

**Expected:** each switch exposes its setting name and state. **Observed native tree:** both expose `roleDescription="Switch"` and `checked=true`, but no label; their titles are separate sibling nodes. TalkBack speech itself was not tested. The shared implementation affects additional Settings callers identified in the source report.

Label each control, or make the named row one accessible toggle. Source: `ui/components/Common.kt:69,197`. Evidence: [native tree](ui/android-settings-semantics.json), [Settings capture](ui/android-settings.png), and [source analysis](android/findings.md#and-05--settings-switches-have-no-accessible-name-on-their-actionable-node).

### UI-01 — Hidden mobile sidebar receives keyboard focus

1. Open `/battle-plan` at 390×844 with Lists & projects closed.
2. Start keyboard navigation at the page's first control.
3. Tab through Settings, theme, and Lists & projects.
4. Tab again.

**Expected:** the next visible board control receives focus. **Observed:** focus lands on Close project sidebar, at `x=-30.59`, right edge `-20`, completely outside the viewport. The hidden sidebar's remaining links/buttons are also in the accessibility tree. This was observed with browser keyboard input, independently of the component probes.

`frontend/src/features/battle-plan/BattlePlanSidebar.tsx:48` only translates the sidebar off-screen; it does not make it inert or remove its controls from focus navigation. Keep the closed mobile variant out of focus/accessibility traversal while preserving the visible desktop sidebar. [Recorded measurements](ui/observations.json).

### UI-02 — Settings inherits Day's scroll position

1. Open a past, empty Day such as `/day/2026-11-04` at 1280×800.
2. Let the documented automatic scroll move the timeline to 08:00.
3. Activate the persistent Settings navigation link without first scrolling up.

**Expected:** Settings starts at its heading or restores its own previous position. **Observed:** its heading is above the viewport and the page opens in its later sections. One fresh check measured Day `scrollY=1067`, then Settings `scrollY=781` and heading top `-645px`. The browser clamps the old Day offset to Settings' shorter document. The initial navigation was clicked; the later measurement used DOM link activation after preview click timeouts.

Add route-aware scroll handling rather than carrying one document offset between unrelated pages. Preserve per-route position where useful. Relevant integration: `frontend/src/App.tsx` and `frontend/src/components/Layout.tsx`; Day scrolling occurs in `DayTimeline.tsx:240`. [Capture](ui/web-settings-scroll.png), [measurements](ui/observations.json).

### AND-04 — Device-date navigation needs confirmation

Set the device to Asia/Singapore and Reporting Time Zone to UTC. Between Singapore midnight and 07:59, initialize Day, then Track a Task or open a delivered Daily Reminder.

The source constructs `AppRoutes.day(LocalDate.now())`, requesting the newer Singapore date while Reporting Today is still yesterday. A previously initialized Day can retain that explicit wrong date, hiding the relevant running record/Now Line from the opened day. A fresh process has an additional Today resolution, so it needs a separate check.

This is **source-supported, not a reproduced device finding**. See `TimeboxNavHost.kt:365`, `DailyReminderInfrastructure.kt:132`, and [the complete trace](android/findings.md#and-04--track-task-and-daily-reminder-links-use-the-device-date-rather-than-reporting-time-zone-today).

## Usability improvements

These are distinct from the defect count. Priority reflects task completion and clarity, not a proposal to replace Timebox's established design system.

| # | Priority | Improvement | Evidence and acceptance criterion |
| --- | --- | --- | --- |
| U1 | High | Keep Day's date, Tracking, View, and lane context available while scrolling | Automatic scrolling put Tracking at `y=-1215` in the initial desktop capture. Use a compact sticky Day toolbar or a timeline-owned scroll area. Preserve whole-day visibility from ADR 0020; do not restore a hidden-hour window. [Capture](ui/web-day-controls.png). |
| U2 | High | Improve the mobile Battle Plan layout | At 390px the board retains a 1024px minimum width; only Open and part of In progress appear, and filters push work down the screen. Offer a compact list/status view or clear status navigation with counts; keep horizontal movement discoverable. Test access to Blocked and Completed without searching off-screen. [Capture](ui/web-mobile-board.png). |
| U3 | High | Make save status and recovery consistent | Planned text uses delayed autosave, Actual editing and Task details use explicit Save. Show Saving/Saved/Retry near edited content and protect uncertain dismissals. Keep drafts after failure. See web report U1; W5 must be fixed first. |
| U4 | Medium | Remove the false first-use prerequisite | Day says to create Task Types before adding blocks, although Planned Blocks can use the neutral `unspecified` type. Offer Create first plan and optional categorization. Verify a first-time user can plan without leaving Day. `TodayPage.tsx:750`. |
| U5 | High | Make connection setup both safe and testable | Android currently exposes the API key as plain text and reports Connection saved before checking connectivity. Mask it by default with Show/Hide; validate URL, test connection, and distinguish saving from connecting. Test malformed URL, wrong key, unreachable server, and successful recovery. See Android U4/U5. |
| U6 | Medium | Use the established date/time editor for calendar conflicts | Android requires ISO strings for Move or shorten and cannot choose the later occurrence of an ambiguous fall-back time. Reuse pickers, show duration/zone, and offer Earlier/Later when needed. Verify midnight and both New York 01:30 occurrences. See Android U1/U2. |
| U7 | Medium | Make calendar assignment searchable and distinguish identical Tasks | Plain dropdowns list all Task Types/Tasks. Reuse search-or-create type selection and show Project/series context in task search. Test large collections and duplicate names. See Android U3. |
| U8 | High | Standardize accessible modal and loading behavior | Fix W4/UI-01; add dialog semantics, focus handling, error feedback and busy-action handling to Project editing. Keep a visible Close/Back action on Android routine loading/error states. Verify keyboard exit, focus return, and navigation during slow loads. |
| U9 | Medium | Present recovery before deployment details | Web connection errors mention starting the backend and the Vite proxy; Google setup instructions are visible in ordinary Settings. Lead with Connection unavailable and Retry, retain drafts, and place diagnostic/configuration details behind a separate affordance. See `lib/api/client.ts:116` and Settings. |
| U10 | Medium | Unify recurrence terms and explain missed planning | Use Recurring Task Series consistently instead of switching to templates. Show an actionable reason when pre-planning cannot allocate a slot; this is already tracked by [#102](https://github.com/crimsoncaius/timebox/issues/102), not a new discovery. |
| U11 | Medium | Reduce date/time entry surprises | Support time-first input, expose Reporting Time Zone beside consequential date/time edits, and block incomplete/invalid fields. Use W5 as a regression check and align with [#289](https://github.com/crimsoncaius/timebox/issues/289). |
| U12 | Medium | Enlarge small mobile actions without increasing visual clutter | Browser filter pills measured 28px high; sidebar Close/New project were roughly 11×24px. Make the interaction areas comfortably larger and give repeated Low/Medium/High controls group context. Android switch hit boxes measured 48×48dp through framework expansion, so the 48×28dp visual alone is not evidence of a small Android hit target. |

The product already has useful strengths to preserve: distinct Planned/Actual meaning, explicit reporting-zone vocabulary, restrained shared styling, contextual help, named Day controls, readiness coordination, and confirmation for unsaved Task edits. The live web check confirmed that choosing Cancel in the discard prompt kept the edited Task title intact. Android keeps its date and tracking controls visible above its scrolling timeline, which is a useful reference for U1.

## Validation results

- Backend existing suite: **884 passed, 8 failed, 22 skipped**. Seven failures are migration fixture drift: fixtures stamp older schemas while leaving a table introduced by migration 040 present. This does not demonstrate that a genuine historical database migration fails. The eighth was a provider-timeout test that passed its isolated rerun. Details and exact test names are in the backend report.
- Web existing suite: **466 passed, 3 failed**. All three failures involve a broad alert query matching an additional mocked GoogleCalendar 404 alert; the expected validation message is present. They are fixture/query failures, not three additional verified product bugs.
- Android existing JVM suite: **454 passed**, zero failures/errors/skips. Debug APK build passed.
- Web production build passed. Lint completed with zero errors and two hook-dependency warnings in GoogleCalendar.
- New reproduction checks: all five web probes and all three Android probes fail the expected correct-behavior assertions. The API bundle successfully reproduces all three API findings. Backend and web bundles were independently rerun during consolidation.

The Android instrumentation suite has documented existing failures/flakes and was not rerun or counted as newly broken. Dependency installation reported 24 npm advisories; reachability and exploitability were not assessed, so these are not presented as 24 application vulnerabilities.

The Impeccable detector emitted eight suggestions: six concern a prototype HTML file, one dislikes the existing navigation accent, and one dislikes Inter. The prototype suggestions do not establish shipped defects; the font/accent suggestions do not outweigh the committed design language. None was promoted to a bug or contrast-compliance claim. [Raw detector output](ui/design-detector.json). A numeric whole-product accessibility/performance score would overstate this audit's coverage, so none is assigned.

## Run the reproductions

From the repository root, after installing the documented backend/frontend/Android dependencies:

```powershell
# API: exit 0 means the current defects were reproduced.
& backend/.venv/Scripts/python.exe docs/reports/audit-2026-11-06/backend/reproduce.py

# Web: exit 1 with five assertion failures is the expected current result.
powershell -NoProfile -ExecutionPolicy Bypass -File docs/reports/audit-2026-11-06/web/run-repros.ps1

# Android: exit 1 with three assertion failures is the expected current result.
powershell -NoProfile -ExecutionPolicy Bypass -File docs/reports/audit-2026-11-06/android/reproduce.ps1
```

The web and Android runners insert one temporary test file and remove it in `finally`; both refuse to overwrite an existing file. They need exclusive use of that test location while running. The backend bundle uses disposable in-memory data. No server, emulator, external account, or paid model is needed by these reproduction bundles.

## Recommended implementation order

1. Fix B1/B2 together and establish one authoritative reporting-date dependency.
2. Guard stale responses in W1, AND-01, and AND-02; convert the supplied probes into regression tests.
3. Repair W5 validation and W2 state merging, then B3's historical counting.
4. Fix AND-03 and execute AND-04's device-date scenario.
5. Address W3 recovery and W4/AND-05/UI-01 accessibility, then UI-02 navigation.
6. Improve the high-priority usability items before lower-priority copy and polish. Focused Impeccable passes can support this work: harden for failure/validation flows, adapt for mobile layouts, clarify for copy, and polish after the behavioral fixes.

Technical evidence remains in the [backend report](backend/findings.md), [web report](web/findings.md), and [Android report](android/findings.md). Questions skipped: the requested deliverable is an investigation report; no implementation choice or permission is required to complete it.
