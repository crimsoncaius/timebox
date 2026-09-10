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
