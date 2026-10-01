# Plan and start from now — issue 297

Android is now the primary design-review platform, per user request. The web experiment remains available as an earlier reference.

## Current review — real Android UI

The user rejected the custom sample Day layout and activity chips as unrepresentative. They have been removed from Android. This round uses the real DayScreen, CurrentActivityControl, ActivitySelectionFields, TaskTypePicker, SwitchActivitySheet, and Day timeline. Tap the activity name to prefill the existing sheet, choose 30 minutes, and Save plan. The existing disclosure arrow still reveals Notes / Switch / Focus / Stop. Duration chips and the plan preview are the added UI. Open-ended switching retains the existing backdated-switch timeline. Timed planning starts now; combining it with backdating remains out of scope. Focus is separate.

### Countdown comparison

The real Tracking control and Focus Mode now share a throwaway countdown comparison. **Prev** and **Next** cycle through A · Inline, B · Two clocks, and C · Plan rail. The same variant carries across the two surfaces while the app stays open. **Live** uses the Current Activity's linked Planned Block end when one exists; otherwise it shows a clearly marked sample 30-minute plan that restarts after 30 minutes to keep the review usable later. **Near end**, **Over**, and **No plan** are local sample states for visual review. They do not change the Planned Block or Activity Tracking. Running Time continues counting up in every state. This is a design prototype; choosing a variant is still pending.

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

Countdown validation: the isolated `.plan297` APK built successfully. A, B, and C were inspected in the real Day Tracking control; A, B, Over, and No plan were inspected in Focus Mode on the managed emulator. The two-clock Focus layout was adjusted to keep Running Time on one line, and the progress rail no longer draws a separate end dot. The earlier reservation went offline during review and was released; a fresh instance was acquired.

Pending Android review: emulator-5582, token `a8d8a9ee12b44bfe83d9789363dc3c8a`, pool root `C:\Users\Caius\TimeboxRuntime\emulators`. Reopened on October 1 with a replacement emulator and the existing disposable database `backend/artifacts/plan-now-297.sqlite`; the prior September 28 sample is saved as `backend/artifacts/plan-now-297-sep28.sqlite`. The isolated `.plan297` app and API on port 12075 must remain running for review. Resume this reservation for revisions, and return it to review afterward.
