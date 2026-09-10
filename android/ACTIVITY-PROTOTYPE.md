# Native activity tracking prototype

Throwaway native Compose prototype for the Android review. Progressive rounds follow the accepted web discussion, but Android presentation gets its own human verdict.

## Round 1: ordinary tracking

Question: is a compact current-activity row above the native Day timeline understandable and comfortable to tap? Start immediately adopts the current plan or unspecified. Switch opens a native bottom sheet with required task type and optional name. Back/swipe dismisses the sheet without switching. Stop is outside Focus (Focus comes next).

Reuses TimeboxTheme and DayTimeline with sample data; navigation labels provide static context. Separate application ID com.timebox.android.activityprototype; plain Application avoids the real app startup services. All changes are memory-only. No API access or persistence. No production code changes should be promoted from this branch.

Run from repository root: scripts/android-gradle.ps1 assembleDebug
Install android/app/build/outputs/apk/debug/app-debug.apk with adb, then launch:

    adb shell am start -n com.timebox.android.activityprototype/com.timebox.android.ui.day.prototype.ActivityTrackingPrototypeActivity

Review task: Start tracking, advance +5 min, switch to Break, advance, stop. Evaluate touch layout and bottom-sheet interaction. No human verdict yet. Subsequent rounds: Focus, unspecified prompt, planned suggestion, inactivity, corrections, recovery/lifecycle, combined experience. Carry shared domain decisions forward; evaluate presentation progressively.

Launch shortcut: scripts/android-activity-prototype.ps1 builds, installs the isolated APK, and opens the prototype. Validation: assembleDebug succeeded; emulator verified immediate planned start, elapsed-time display, and native sheet switch to Break with empty name. Human review pending.

## Round 2: Focus entry and exit

Round 1 accepted by user. Added quiet Focus control beside tracking. Starts immediately if off, preserves interval if already active. Dedicated centered activity display with Switch sheet and Exit Focus; no Stop or navigation. Android Back exits Focus without stopping; Back inside sheet dismisses the sheet first. Back policy and layout await human review. Build succeeded; emulator verified entry from off, elapsed time, no Stop in Focus, and Back returning to Day with the same recording.

### Round 2 verdict

User accepted the native Focus layout and Android Back exiting Focus while preserving tracking. Next unresolved presentation: persistent unspecified-activity prompt in Focus, task type first with optional name, original-start versus Start now choice.

## Round 3: persistent unspecified prompt

Starts in Focus at 13:30, unspecified since 13:20. Inline required type chips and optional name, Apply from 13:20 retains original start; Start now preserves earlier unspecified interval. No modal dismissal. Focus content scrolls with keyboard insets. Reset restores sample; no persistence. Build succeeded; emulator verified applying Break with no name resolves prompt and retains 10 minutes from 13:20. Human review pending.

Round 3 feedback: user accepted the prompt with removal of name input. Unspecified Focus prompt now asks only for Task Type and timing; stores no name. Explicit switch sheet remains separately scoped. Build passed; updated app installed and launched.

## Round 4: planned suggestion

Round 3 accepted with no name field in unspecified prompt. Round 4 starts at 11:55 tracking Writing. Advance five minutes to see a subtle surface with Lunch is planned now and Switch. Available above Day timeline and under Focus controls. No automatic transition. Build passed; emulator verified Writing persists at noon and explicit switch begins Lunch and clears suggestion. Reset left ready; visual review pending.

### Round 4 verdict

User accepted the native planned-activity suggestion in Day and Focus. Next unresolved presentation: persistent inactivity check-in with clear primary/secondary actions and no Stop inside Focus.

## Round 5: inactivity check-in

Simulated pending prompt at 11:30, recording Writing since 10. Native stacked filled confirmation and outlined switch actions; Stop only in Day. Prominent activity name below smaller question. Switch and stop clear pending question; confirmation preserves interval. Build passed; emulator verified prompt survives Focus entry, no Stop in Focus, and confirmation preserves 90-minute recording. Reset ready for human review. Device detection and notification delivery are not implemented in this presentation round.

Round 5 revised to user's supplied visual reference: rounded modal bottom sheet, generous spacing, large question, separate activity name, full-width filled confirmation and outlined switch. This reopens earlier nonblocking presentation: modal dims underlying UI, but Back/swipe dismisses to a persistent Check-in waiting control; tracking continues. Sheet shows the two reference actions; Stop remains on Day after dismissal, absent in Focus. This presentation/interaction interpretation awaits human verdict. Build passed and updated native app launched.

### Round 5 verdict

User accepted the reference-inspired bottom sheet: dimmed background, rounded top corners, clear heading/activity hierarchy and two full-width actions. Dismissal leaves a persistent Check-in waiting reminder while tracking continues. This supersedes the earlier inline presentation for Android; web retains its accepted inline design. Stop remains available in Day outside the sheet and absent in Focus. Next round: native earlier switch/stop correction with affected-time preview.

## Round 6: native correction sheet

Sample at 12:15, Writing since 10. Switch sheet now groups task details, native time-picker action, 15 min ago shortcut, and After this change preview. Stop in Day opens matching timing/preview sheet. Cancel/Back leaves recording unchanged. Bounds limited to current interval; wider history corrections not represented. No Stop inside Focus. Human review pending.

Validation: assembleDebug passed; emulator verified native correction sheet and switch to Break at noon yielding 15 recorded minutes at 12:15. Revised sheet left open for review.

### Round 6 verdict

User accepted native correction sheet and preview.

## Round 7: offline recovery feedback

Starts offline with Lunch from noon at 12:30. Scripted Reconnect receives newer Reading change from 12:10, preserves Writing and Lunch before that boundary. Compact offline/synced text, dismissible explanation available in Day and Focus. No real persistence/network/conflict engine. Build passed; emulator verified Reading 20 minutes, Synced status, explanation and prior timeline retained. Reset ready; human verdict pending. Lifecycle, notification presentation and connected review remain.
