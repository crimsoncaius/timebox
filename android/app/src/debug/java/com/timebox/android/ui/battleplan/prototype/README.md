# Native Task Type Recommendation prototype

Throwaway source for issue 168, on branch `codex/prototype-168-task-type-recommendations`.

Run from the repository root:

```powershell
./scripts/start-issue-168-prototype.ps1
```

This builds and installs the isolated `com.timebox.android.prototype168` app, acquires a managed emulator, launches **Task Type Lab**, and retains the device for review. It uses a plain Application instead of TimeboxApplication, so application repositories and scheduled work do not start. Fixtures and classification choices are held only in memory; there are no Jev or Phoenix requests. Ordinary debug builds retain their normal application/launcher, and release builds contain no prototype activity.

Three variants use real Compose sheets, native text input, Timebox theme tokens, and the existing TaskTypePicker:

- A: a recommendation in the name editor, staged until Save.
- B: a recommendation beside the Task Type chip after saving the name.
- C: a combined name-and-type editor, with both changes staged until Save.

Use the bottom arrows in the sheet to compare variants. Switching resets the fixture. Tap the sliders icon for entity, response, linked-Block, reset, and live-state controls. Tap the name and change it to Practise guitar, Morning run, or Buy groceries; the mock result arrives after a 500 ms pause. Low confidence, no match, provider failure, and candidate overflow suppress suggestions.

The debug deep link is `timebox://prototype/task-type-168?variant=A` (also B/C). The launcher accepts a `variant` string extra. Use the emulator helper for every device command. Close/reopen the activity to apply a different deep link. The app's normal launcher is disabled in this isolated build.

This is a placement experiment. Supporting metadata is illustrative, saves affect fixtures only, and the normal persistence/network path is not under evaluation. No variant is selected yet. The agreed product requirements remain in `docs/specs/issue-168-task-type-recommendations.md`.

Validated on managed emulator-5600 (Android 36): APK build/install/launch; A recommendation with keyboard open and explicit acceptance; B recommendation after Save; C combined editor with keyboard open, real picker selection, explicit-choice protection, and discard restoring the original name and Unset classification. No AndroidRuntime errors appeared during the checks.
