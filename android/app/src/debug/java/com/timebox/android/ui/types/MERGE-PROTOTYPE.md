# Task Type Merge prototype

Question: does renaming into an existing Task Type make merging discoverable?
The user preferred web variant A (rename collision) over B (explicit merge action).
Android now previews A using the existing TypesScreen and a native bottom sheet.
Production merge behavior is specified in docs/adr/0012-task-type-branch-merge.md.

Both previews use in-memory sample data. Counts and Activity Tracking are illustrative.
No production merge endpoint, offline identity handling, or concurrency behavior is implemented.

Web: run `npm run prototype:merge --prefix frontend`, then rename Travel to Transportation.
Android: build `android/gradlew.bat -p android assembleDebug`, acquire a device using
`scripts/android-emulator.py`, install through its `adb TOKEN install -r` command, and
launch `com.timebox.android/.ui.types.TaskTypeMergePrototypeActivity` through the helper.
The activity is debug-only. Reset restores the original sample categories.

Source is retained on codex/prototype-task-type-merge; the native design verdict is pending.
`nAndroid confirmation refinement: 1 Summary (expandable branch details), 2 Branch map, and 3 Guided (two steps). Bottom arrows switch layouts. The opaque sheet and fixed action area address the original low hierarchy and background bleed-through. The activity opens directly to the sample confirmation; Back to rename exercises the entry flow. Layout verdict pending.
