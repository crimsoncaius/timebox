# T3 Code device workflow

This is the default way to open, install on, launch, and review an Android
emulator. The app appears in the chat's Device panel. Use
`scripts/android-emulator.py` (`docs/agents/android-emulators.md`) only for
instrumentation tests, which need ADB, and as the fallback when the T3 device
tools fail (see [Device tool errors](#device-tool-errors)).

## Emulators

`device_list` shows five identical emulators: `Pixel_9a` and `Pixel_9a_2` through
`Pixel_9a_5`, each a Pixel 9a on Android 16 with Google Play. The user has
assigned them to this workflow. Each chat uses one at a time. There is no older
Android version; ask the user before testing on one.

- A booted emulator that is not under `open` in `device_list` belongs to another
  chat. Leave it alone.
- If every emulator is booted and none is under `open`, tell the user and stop.
  Do not shut down another chat's emulator to free one.
- These emulators keep app data between uses, so a reinstall keeps the previous
  session's data. Say so when a review depends on a fresh state.

## Steps

1. **Open a device.** Call `device_list`, then `device_open` with the device's
   `hostId` and `id`. Open the device the user names; otherwise choose one that
   is not booted. Done when `device_open` returns an `agent-device` command. That
   command is bound to one serial and one session. Once the device boots, T3
   identifies it only by its serial: `device.id` in the `device_open` result,
   such as `emulator-5556`. The name from `device_list` stops working. Use the
   serial for every later `device_open`, `device_screenshot` and `device_close`.
2. **Install.** Build the APK with `android/gradlew :app:assembleDebug`. It
   finishes in seconds when nothing changed, so rebuild rather than reuse an APK
   of unknown origin. Then run
   `<agent-device> install com.timebox.android <path-to-apk>`.
   Done when it prints `Installed: com.timebox.android`.
3. **Launch.** Run `<agent-device> open com.timebox.android --relaunch`.
4. **Verify.** Run `<agent-device> snapshot -i`. Done when the nodes come from
   Timebox. Nodes from `com.google.android.apps.nexuslauncher` mean the launcher
   is still in front and the launch failed. The `Opened:` line from step 3 is not
   evidence: agent-device prints it even when Android rejected the launch.
5. **Diagnose a failed launch.** Rerun step 3 with `--debug`. The `stdout` field
   carries Android's `am start` result, such as
   `Error: Activity class {...} does not exist`.

## Launching a specific screen

Older docs give launches as `am start` commands through the helper. Translate
them:

| Helper `am start` | agent-device |
|---|---|
| `-a android.intent.action.VIEW -d '<url>'` | `open com.timebox.android '<url>'` |
| `-n com.timebox.android/.ui.Foo` | `open com.timebox.android --activity .ui.Foo` |
| `--ez name true`, `--es name value`, `-f 0x...` | one `--launch-args` per word: `--launch-args --ez --launch-args name --launch-args true` |

For a build with a package suffix, replace `com.timebox.android` with the
suffixed package, and pass the full class name to `--activity`.

The `agent-device` command is a `.cmd` file, so `cmd.exe` reads `&` in its
arguments as a command separator, even inside quotes. Write each `&` in a URL as
`^^^&`, for example
`'timebox://prototype/assistant-polish?layout=focused^^^&scenario=complete'`.

## Lifecycle

An emulator keeps running until something shuts it down. Settling, archiving or
leaving the chat does not, and neither does removing its worktree or calling
`device_close` without `shutdown: true`.

- **Review:** after a fix, leave the device open with the fixed app in front, and
  name its serial in the final response.
- **Stop:** when the user finishes reviewing, or after their requested merge into
  `master` succeeds, call `device_close` with the serial and `shutdown: true`.
  Then call `device_list`: the device is listed by its name again, with
  `booted: false`. If its serial is still booted, report that to the user. Stop
  temporary devices that are not held for review when the task ends, after
  success or failure. Stop only devices this chat opened.
- **Order:** stop the emulator before the merged worktree is removed. A chat that
  is archived or removed first leaves its emulator running.
- **Device opened by another chat:** `device_list` shows only this chat's devices
  under `open`. If the review's device is not there, name its serial to the user
  and leave it running.
- **Console windows:** each emulator shows two console windows on Windows, for
  `emulator.exe` and `qemu-system-x86_64-headless.exe`. Closing either one kills
  that emulator, so stop emulators with `device_close`. Tell the user the windows
  can be minimized. A shutdown closes both windows, except when the emulator
  started `netsimd.exe`, the network simulator all emulators share: netsimd keeps
  that emulator's qemu window open and keeps running after the emulator stops.
  Leave that window alone while any emulator runs.

## Device tool errors

A device call can fail with a device-hub error or
`Agent tools did not start within 30000 ms` while T3's device agent restarts;
`device_list` then shows the agent's `runningVersion: null`. Retry the call once.
If it fails again, tell the user the T3 error and switch to
`docs/agents/android-emulators.md` for the rest of the task. Release that
helper's emulator under its own rules.

When the failure persists, `~/.t3/userdata/device/agent-device/daemon.log` shows
`Daemon registration busy`: an older agent-device daemon still holds
`daemon.lock` (its PID is in `daemon.lock/owner.json`) after losing its
`daemon.json`, and restarting T3 Code does not stop it. Include this in the
report; stopping that PID lets T3 start a fresh daemon.

## Launcher filter

agent-device launches with `MAIN` plus the `DEFAULT` and `LAUNCHER` categories.
On API 33+, Android rejects an explicit intent whose categories the target
filter lacks, and reports the activity as missing. `MainActivity`'s launcher
filter in `android/app/src/main/AndroidManifest.xml` therefore declares
`DEFAULT`. Any other activity with intent filters that agent-device launches
needs the same.
