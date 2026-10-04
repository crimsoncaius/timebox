# Plan and start from now — issue 297

## Production integration — October 4

The user approved the connected Android prototype and authorized implementation, validation, commit, and merge into master without a PR. Work began on `codex/297-plan-start` in worktree `33c8`, from the prototype merge `fb339956`; local and remote master were equal and the Desktop checkout was clean. The prototype merge alone did not ship this feature.

The normal Android and web applications now share the accepted Start / Switch / Edit workflow. Duration choices are 15 / 30 / 60 / 90 minutes and Custom (1–90). Starting with a covering plan retains the one-tap shortcut; the Current Activity editor adjusts its duration afterward. A fresh Switch draft is separate from the prefilled Current Activity editor. The selected Task Type keeps its label and outlined row with Change. Running Time leads the smaller remaining / over-plan display; Focus stays a separate choice and recording never switches or stops automatically.

The final accepted decisions were to adopt and resize a matching unlinked covering plan, and to require online, synced state for duration planning while ordinary tracking remains available offline. Matching uses Task Type, Task, and Block Name. Matching-plan adjustments retain earlier start, identity, and Supporting Note without a resumed tail; same-activity adjustments retain Actual identity and Running Time. Interruptions preserve preceding planned time and resume the remaining plan. Later overlapping plans require preview and explicit confirmation, while plans outside the interval stay in place.

### Production protocol and precision

`POST /activity/plan-now` writes plans and Activity Tracking in one transaction using the existing activity journal. Clients persist the Save ID and request before transmission, so unknown outcomes retry the same operation after reconnect or restart. Preview revisions and exact affected-plan IDs reject concurrent edits and time-crossed boundaries. Undo restores plan fragments, correspondence, recorded time, Task readiness, and recurring-plan realization state only while the observed state remains unchanged. Recurring structural protection intentionally survives Undo, following existing domain rules.

Exact plans use authoritative timestamps in the ordinary TimeBlock row, including seconds and cross-midnight intervals. Day exposes fractional positions and clipped elapsed durations; metadata edits preserve precision, moves preserve seconds, and edited time boundaries take the user's entered minute. Reporting and Record as planned consume the same intervals. Day still uses its established wall-clock axis: repeated DST wall times do not get a second visual axis, but timestamp details and elapsed totals remain exact. See [ADR 0019](../../adr/0019-exact-planned-intervals-for-plan-now.md).

The `PLAN_NOW_PROTOTYPE` flag, special Android HTTP client, scratch endpoint, precision side table, and old experimental verifier are removed. `backend/plan_now_review.py` now only seeds disposable data and launches the normal API; production never imports it. The old web `/prototype/297` fixture remains an isolated historical reference.

### Validation and review

- Backend: full run passed 602 cases with 8 existing skips; one added test used the wrong expected status (201 instead of the existing 200). After correcting that fixture, all 16 planning and migration cases passed, including the new cross-midnight replacement/Undo regression. Ruff passed. Coverage includes atomic rollback, exact second 59.732 with delayed receipt, retry identity, unlinked adoption, conflicts, task readiness, recurring-plan synchronization, midnight, DST, metadata editing, and migration preservation. New IDs are allocated before deleting fully replaced plans, preventing SQLite identity reuse from breaking Undo.
- Web: all 472 tests, production build, and lint passed. Lost-response retry survives restart, stale previews refresh, final replacement confirmation captures the instant, and Escape cannot dismiss a pending save.
- Android: ordinary debug APK and unit tests passed, including durable Plan Now replay and precise Record as planned availability. Both new UI instrumentation cases passed: retained Task identity with only displaced plans confirmed, and offline duration disabled while open-ended tracking stays available.
- The 12 existing ActivityTracking instrumentation cases had 8 passes and 4 failures. All four also failed on master `496f6a2a`: the two Switch cases timed out, and Focus still expected the obsolete `elapsed` label. The planned-start case failed with a duplicate Writing node on this branch and a view-thread error on master; that different signature remains a test limitation, not an identical baseline result. The comparison emulator was automatically released.
- Normal Android UI Start → Email → 15 min produced Actual and Planned start `2026-10-04T02:42:08.071Z`, ending exactly 900 seconds later. Day displayed 15m. A subsequent confirmed adjustment kept Actual ID/start and retained the later plan remainder.
- A web editor opened before the Android adjustment rejected its stale save and visibly requested review. Production captures are in ignored `backend/artifacts/297-production-*.png`, with exact API evidence in `297-production-saved.json` and `297-production-adjusted.json`.
- Final normal-APK walkthrough verified prefilled Edit, fresh Switch, separate Focus, and a confirmed Writing switch with exactly 900 seconds between its plan boundaries. The refreshed `297-production-edit-final.png` and `297-production-confirm.png` show the compact selected type and exclude the matching plan from the displaced-plan list. The finish review found no remaining material UI fixes.

Review runtime uses the normal `com.timebox.android` app and normal web routes at `http://127.0.0.1:5174`, backed by the disposable normal API on 12075 and `backend/artifacts/plan-now-297-production.sqlite`. No feature-only APK or endpoint is involved. The Android review reservation is released after the authorized final merge, per the repository lifecycle.

### Reproduce

Run `backend/plan_now_review.py` with the backend environment. Build the ordinary debug APK with `-PreviewApiBaseUrl=http://10.0.2.2:12075/` (this changes only the server address), then install it on a newly managed emulator. For web, set `VITE_API_PROXY_TARGET=http://127.0.0.1:12075` and run Vite on the configured port 5174. The seed starts idle with Writing 20 minutes later and a Meeting after that; an existing scratch database retains review edits.

## Historical prototype record

The sections below describe earlier experiments and their limitations at that time. Their prototype-only commands and outstanding questions are superseded by the production integration above; old emulator tokens are retired.
## Progressive exploration — October 1

Continue the existing experiment on `codex/297-progressive-prototype`. Preserve accepted choices and explore unresolved behavior through focused rounds in the real Android application. This record distinguishes implemented UI from prototype behavior; issue 297 remains open.

### Coverage map

| Decision or scenario | Current status | Next evidence needed |
| --- | --- | --- |
| Tracking and Focus countdown | A · Inline accepted and implemented for the linked Planned Block, including overrun and no-plan states | Combined journey review |
| Selected Task Type hierarchy and Switch heading | Refined and reviewed in the actual Android sheet | Retain through subsequent rounds |
| Custom duration | Compact minutes field reviewed in the experimental editor | Include validation and keyboard behavior in combined review |
| Real plan versus timer; separate Focus entry | Accepted: real Planned Block; Focus remains a separate choice | Start and switch journey |
| Interrupting a different activity | Accepted: preserve preceding plan and resume its remainder; tracking never switches automatically | Review with task-linked and multiple-plan examples |
| Replacing later plans | Preview plus explicit confirmation accepted; experimental interaction exists | Accurate preview, cancellation, and stale-preview recovery |
| Timeboxing the same Current Activity | Accepted: chosen duration sets the new end; preserve Running Time and preceding plan | Review shortening and extending the linked matching plan |
| Starting with no Current Activity | Not explored with duration in the real app | Reuse the existing Start flow and review its outcome |
| Existing covering plan shortcut | Proposed by issue; not reviewed | Adoption versus explicit resize |
| Task linkage and recurring plans | Prototype does not support replacement | Inspect existing identity and recurrence rules before an experiment |
| End behavior | Tracking continues; inline overrun accepted | Whether any additional prompt is needed remains open |
| Recovery and boundaries | Atomic save, offline behavior, concurrent changes, midnight and time-zone boundaries remain unresolved | Representative recovery and boundary scenarios |
| Web parity | Earlier local-state fixture only | Carry the connected accepted experience to the real web workflow |

### Current round: change the duration of the same activity

Scenario: Writing is planned 10:00–11:00 and is currently recording. At 10:20 the user selects 15 minutes for Writing. The user accepted the recommendation: the new intended end is 10:35, preserving preceding planned time and the continuous Actual Block. No Writing remainder resumes after 10:35. The experiment resizes a linked matching plan in place, retaining its identity and Supporting Note. A matching but unlinked plan remains a separate coverage gap.

Exercise this case alongside an unplanned Current Activity in the actual editor. Keep the accepted inline countdown, Task Type field, and separate Focus entry stable. Subsequent rounds follow discoveries rather than a fixed component sequence. Production integration is a later step after the connected experience and material coverage gaps have been reviewed.

This round uses `backend/artifacts/plan-now-297-same-activity.sqlite`, preserving the earlier review database. Its fresh seed links a running Writing activity (Draft the proposal) to a plan with 40 minutes remaining and places a Meeting later. Tap the Current Activity, choose 15 min, inspect the new-end preview, and Save plan. Running Time continues and the Day plan retains its earlier start. Try 30 min next to extend the same plan. This is still the isolated experimental protocol.

Validated this round: the isolated check uses a fixed 10:20 clock and passes interruption/resume plus same-activity shortening and extension, preserving plan identity, Supporting Note, Actual start and linkage. The prototype APK built successfully. In the actual app, choosing 15 minutes and saving retained plan ID 1, its 21:55 start and note, changed its end to 22:34, and retained the running Actual start. The Tracking control showed continued Running Time and roughly 14 minutes remaining; no resumed Writing segment was created. Preview screenshot: `backend/artifacts/297-same-activity-preview.png` (local review evidence).

Historical Android review: `emulator-5582`, token `92e70adb4d5b486ea8025f611ea8362a`, owner `issue-297: progressive same-activity plan review`, was released before the October 4 continuation, and its backend stopped. This token is retired.

### Exact duration from Save — accepted

The user noticed 21 seconds of Running Time alongside only 28:39 remaining after choosing 30 minutes. The plan started at the rounded minute (22:40:00), while the Actual Block started at 22:40:59.732. The user explicitly chose **exactly the selected duration from pressing Save**. This supersedes the minute-rounded behavior of earlier rounds.

The Android prototype captures the repository's calibrated instant at confirmation and sends it to the isolated plan endpoint. A timed switch uses that same instant as its Actual start. The review backend stores precise intervals in `prototype_plan_intervals` in the disposable database and uses them in Activity snapshots; shortening, extension and interruption/resume preserve those precise boundaries. These records survive server restart. If an ordinary Day resize changes the grid boundaries, its previous precision override is ignored.

This remains an experiment: production Planned Blocks and the Day layout use whole minutes. Day displays the grid approximation, while Tracking and Focus use the precise interval. A production representation for exact planned boundaries, consistent Day editing/reporting and sub-minute fragments still needs design and implementation; the review table is not a production migration. Atomic planning/tracking and stale-preview handling remain unresolved.

Validation: the isolated check passes with Save at 10:20:59.732, delayed receipt, persistence across sessions, same-activity shortening/extension and interruption/resume. APK build succeeded and the app was reinstalled and relaunched on the same managed review. A real UI switch to Writing for 15 minutes produced Actual and plan start `2026-10-01T22:46:43.082+08:00` and plan end `23:01:43.082+08:00`, exactly 900 seconds apart. The app is left running on Day with this scenario and the backend on port 12075.

## Current review — real Android UI

The user rejected the custom sample Day layout and activity chips as unrepresentative. They have been removed from Android. This round uses the real DayScreen, CurrentActivityControl, ActivitySelectionFields, TaskTypePicker, SwitchActivitySheet, and Day timeline. Tap the activity name to prefill the existing sheet, choose 30 minutes, and Save plan. The existing disclosure arrow still reveals Notes / Switch / Focus / Stop. Duration chips and the plan preview are the added UI. Open-ended switching retains the existing backdated-switch timeline. Timed planning starts now; combining it with backdating remains out of scope. Focus is separate.

### Selected countdown: A · Inline

On October 1, the user selected **A · Inline**. Tracking and Focus Mode show Running Time first, with a smaller planned countdown underneath. The countdown uses the Current Activity's linked Planned Block end, becomes time over plan after the end, and disappears when no plan is linked. The comparison controls and sample timers have been removed. All three original variants are preserved in [codex/297-countdown-comparison](https://github.com/crimsoncaius/timebox/tree/codex/297-countdown-comparison), commit `7cab184e`, published with the user's explicit approval.

Run `backend/.venv/Scripts/python.exe backend/plan_now_review.py` for the isolated API on port 12075. It uses only `backend/artifacts/plan-now-297.sqlite`, seeded with a running Writing activity and a later Meeting. It never reads the ordinary database. Build with `scripts/android-gradle.ps1 assembleDebug '-PplanNowPrototype=true' '-PreviewApplicationIdSuffix=.plan297' '-PreviewApiBaseUrl=http://10.0.2.2:12075/'`. Launch `com.timebox.android.plan297/com.timebox.android.MainActivity` normally. The feature defaults off in ordinary debug and release builds. Sample changes persist in the disposable review database.

This is an interaction experiment, not a production protocol. Plan mutations are transactional in the isolated launcher, but linking/switching tracking happens in a subsequent request. Atomic retry/undo and concurrency checks remain open; do not deploy this launcher. Task-linked replacement, recurrence and midnight are unsupported. The read-only preview and minute-rounded save may differ slightly as real time advances. Existing same-activity plan shortening/merging is still an unresolved product decision.

The web development-only in-app prototype is at `/prototype/297`.
Run `npm run dev -- --host 127.0.0.1` in frontend, then open
http://127.0.0.1:5174/prototype/297.

## Accepted decisions

- A real Planned Block represents the chosen duration.
- Interrupting Writing at 10:20 for 15 minutes of Email preserves Writing before 10:20 and resumes its plan at 10:35 until its original 11:00 end.
- Only the plan resumes automatically; Activity Tracking continues until explicitly switched.
- Replacing time across later plans requires a preview and explicit confirmation. Plans outside the chosen interval do not move.
- A simple interruption has one Switch confirmation with its consequences visible beforehand.
- Focus progress follows the fixed Planned Block interval, independently of Running Time.
- Both web and Android are in scope; this first interaction experiment uses web.

## Earlier experiments (superseded on Android)

### Android round 2 — edit the Current Activity

User requested exploring a tappable Current Activity that opens the same editor used by Switch. The Current Activity is preselected; choosing a duration saves a plan from now without splitting the running Actual Block or restarting Running Time. Switching to a different activity still starts a new record. Focus remains separate.

Review task: choose the prototype control **Unplanned Writing**, tap **Writing / Tap to edit**, choose **30 min**, then **Save plan**. Running Time remains 20 minutes while planned time remaining becomes 30 minutes. The **Keep as is** choice makes no changes. The same editor changes its confirmation to **Switch activity** when another activity is selected.

These entry points, copy, and default selection are provisional. Existing same-activity plan adjustment remains unresolved: this fixture preserves plan portions outside the chosen interval, even if they are also Writing; it does not silently shorten or merge them. Use the unplanned sample for this round. Web remains the earlier round.

Question: Is duration selection and the resumed-plan preview clear enough to confidently interrupt an activity?
Uses the existing Layout, navigation, ActivitySwitchDialog, and design tokens.
Sample scenario: Monday 28 September, 10:20; Writing 10:00–11:00; Meeting 11:00–11:30.
Try Switch → Email → 15 min → Switch, then open Focus separately. Exit Focus to inspect both lanes.
Advance the review clock to 10:35: Email continues recording, and Switching to Writing is explicit.
Reset, then try 60 min to inspect the additional replacement confirmation.
All changes are local component state. No plan or activity writes are sent to the backend.
The review clock is manual and resets on reload. App navigation leaves the fixture.

## Provisional choices, not accepted requirements

- Duration chips in Switch; Open-ended is the initial selection.
- Focus entry is separate from switching (accepted; combined entry removed).
- Persistent in-app end indication, without sound or OS notification.
- Fixture activities only; Task selection is not explored yet.

## Open decisions and coverage gaps

- Start flow; timeboxing an already-running activity; using or adjusting a matching existing plan.
- Task selection and identity matching; end notifications.
- Offline combined planning, atomic undo, concurrent edits and stale previews.
- Minute rounding, midnight, reporting-zone transitions, edits/deletion during Focus.
- Native Android accessibility beyond the standard Compose controls, plus small-screen and large-font coverage.
- Tiny timeline blocks and longer/multiple interruptions are outside this focused fixture.

## Implementation findings

Existing Actual Blocks link to Planned Blocks. Resizing a plan preserves Actual facts; reclassification/deletion detaches links. Split remainders need an explicit identity policy. Recurring generated plans must retain customization/deletion behavior. Existing day mutation helpers commit internally, so production plan-and-start needs transaction-internal composition. Activity cursor alone does not version plan edits. Existing Undo Switch restores Actual ranges only. Android plan writes are online while tracking has a replay journal.

Sources: `backend/app/services/day_service.py`, `activity_service.py`, `activity_selection.py`, `activity_reconciliation.py`; Android `TimeboxRepository.kt` and `ActivityRepository.kt`.

## Validation

Current real-UI round: isolated `.plan297` debug APK built and launched on emulator-5582. Through the actual Current Activity editor, saved a 30-minute plan; API assertions confirmed the Actual start timestamp stayed unchanged and its linked plan had exactly 30 minutes. The real Day timeline displayed the saved block. `backend/.venv/Scripts/python.exe backend/verify_plan_now_review.py` passed split/resume, original segment identity, Supporting Note preservation, longer replacement, and unchanged Actual start checks on an in-memory database. The first cold launch showed an Android ANR dialog; choosing Wait recovered, and subsequent editing/saving was responsive. Existing instrumentation results below refer to the retired sample UI, not this integrated round.

Custom duration refinement: the Android editor now shows a compact duration field with a `min` suffix and a visible 1–90-minute hint beside the selected Custom chip. The isolated APK rebuilt successfully, the field and plan preview were inspected in the real sheet, and the Impeccable design scan reported no findings. Cleared the disposable review app's stale local snapshot so its Running Time follows the current isolated API.

Switch entry refinement: opening **Switch activity** clears any earlier Current Activity edit draft and keeps the **Switch activity** heading; tapping the Current Activity still opens its prefilled **Edit current activity** sheet. Rebuilt the isolated APK and verified both entry paths on the review emulator.

Selected Task Type refinement: the Start and Switch sheets keep a visible **Task Type** label after selection, and the chosen value sits in a full-width outlined row with a **Change** action. Rebuilt the isolated APK and verified that selecting Email shows the field hierarchy and Change reopens the picker.

Web production build passed. Playwright exercised the 15-minute split, Focus countdown, continued Email recording at the planned end, explicit Writing handoff, and 60-minute replacement confirmation. Desktop and 390px mobile views were inspected. Mechanical design scan returned no findings.

Android debug APK built successfully. Both targeted prototype instrumentation tests passed: the 15-minute Focus handoff and multi-plan replacement confirmation. Installed and relaunched the updated APK afterward; visually verified the native Day fixture and the 15-minute Switch sheet.

Round 2 validation: all three targeted instrumentation tests passed, including preserving 20 minutes of Running Time when saving a new 30-minute plan. Reinstalled and relaunched the APK, then inspected the Current Activity editor on the managed device.

Countdown validation: the isolated `.plan297` APK built successfully. A, B, and C were inspected in the real Day Tracking control; A, B, Over, and No plan were inspected in Focus Mode on the managed emulator. The two-clock Focus layout was adjusted to keep Running Time on one line, and the progress rail no longer draws a separate end dot. The earlier reservation went offline during review and was released; a fresh instance was acquired. After selecting A, rebuilt and relaunched the app, verified the countdown is absent without a linked plan, saved a real 30-minute plan in the disposable backend, and inspected the inline countdown in Tracking and Focus. API inspection confirmed the original Current Activity start remained unchanged.

Android review completed: emulator-5582, token `a8d8a9ee12b44bfe83d9789363dc3c8a`, was released with `--review-done` on October 1 at the user's request. Its managed writable storage was cleaned up. The disposable backend database remains `backend/artifacts/plan-now-297.sqlite`; the prior September 28 sample is saved as `backend/artifacts/plan-now-297-sep28.sqlite`. Acquire a fresh reservation to reopen the isolated `.plan297` app against the API on port 12075.
