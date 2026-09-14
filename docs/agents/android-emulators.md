# Android emulator ownership

## Acquire and use

Run from the working tree being tested. Python 3 is required. Unit tests and APK
builds need no device reservation. For instrumentation tests, use:

```powershell
python scripts/android-emulator.py test --owner "task-id: UI tests"
```

This reserves a device, boots a clean instance, runs `connectedDebugAndroidTest`
against its serial, and stops/releases it after success or failure. Additional
Gradle arguments can follow the owner. A full pool reports its owners and pending
reviews through `status`; continue independent work and retry later. Never take
another reservation to bypass the capacity limit.

If the user explicitly requests an additional emulator, use
`acquire --extra --owner "task-id: user-requested additional emulator"`.
This reserves a separate managed slot beyond the ordinary capacity without
changing shared configuration or existing reservations. The extra device uses
the same token, command, release, and review lifecycle. Use this option only
for an explicit user request, not as an automatic response to a full pool.

For interactive work, capture the token printed by `acquire`:

```powershell
python scripts/android-emulator.py acquire --owner "task-id: prototype"
python scripts/android-emulator.py adb TOKEN install -r android/app/build/outputs/apk/debug/app-debug.apk
python scripts/android-emulator.py adb TOKEN shell am start -n com.timebox.android/.MainActivity
python scripts/android-emulator.py gradle TOKEN connectedDebugAndroidTest
python scripts/android-emulator.py renew TOKEN
python scripts/android-emulator.py release TOKEN
```

Use the exact returned token. The helper selects the serial for ADB and sets
`ANDROID_SERIAL` for Gradle. Device operations are serialized and revalidate
ownership under a kernel lock. Global ADB commands, including server restarts,
are excluded. Use separate worktrees for concurrent builds. Commands outside
the helper remain a behavioral boundary: raw ADB, IDE test launches, and other
device tools can bypass these safeguards, so agents must use the helper.

Renew during gaps in interactive work before the configured lease expires.
Active commands renew automatically. Expiration never reallocates a device by
itself. On successful temporary work, release without asking the user.

## Leave a review

Install and launch the updated app, verify the intended screen, then run:

```powershell
python scripts/android-emulator.py review TOKEN
```

Name the device serial and pending review in the final response. Review holds
do not expire. When the user says they are done reviewing, release it with
`release TOKEN --review-done`. When they request changes to that review, use
`resume TOKEN`, make the changes, and mark it for review again. These actions
require the user's review completion or continuation instruction, not another
routine confirmation question. A task finishing or a branch merging does not
implicitly end a pending review.

## Inspect and recover

```powershell
python scripts/android-emulator.py status
python scripts/android-emulator.py recover TOKEN
```

Recovery accepts only expired non-review reservations. It obtains the device
operation lock, checks any leftover host-command marker, stops the matching
managed emulator (ending orphaned instrumentation), then releases ownership.
Old tokens cannot operate after release. Uncertain command startup or a live
host process blocks recovery. Interrupted Gradle commands also require checking
their descendants, even if the launcher has exited. For an interrupted startup, inspect the recorded
task and host processes; remove its `operation-N.json` marker only after verifying
the entire command and its descendants have ended. Never remove registry or lock
files to bypass ownership. Boot failures retain a reservation visible in status;
the owner can release it immediately, or another task can recover it after expiry.

## Pool and isolation

Machine-wide state is in `%LOCALAPPDATA%/Timebox/emulators`, shared by all
worktrees and clones using this helper. SQLite transactions allocate atomically;
per-device kernel locks prevent release while a command is running. Treat this
directory as local operational state, not version-controlled project content.

`scripts/android-emulator.json` defines the capacity, port range, lease duration,
installed system-image path, and display configuration. Change it only when all
slots are free and all participating worktrees can use the same configuration.
The initial profile uses the installed Android 36 Google Play x86_64 image,
1080×2424 display at density 420, 4 GB RAM, and font scale 1.0. The smaller
2 GB profile caused startup ANRs under fresh Google Play setup load in local
validation. It is a stock Android
baseline, not a Samsung One UI emulator. Navigation uses that image's default.
An exact S26 match requires confirming the physical phone's display and OS settings.

Managed AVDs have separate storage and names `timebox-agent-01`, etc. Each new
reservation wipes only its managed AVD and cold boots it; release stops it.
Existing personal emulators are not adopted, reset, or stopped. A port occupied
by an unrelated emulator fails safely. This pool's capacity does not include
personal emulators; lower it if the machine is under memory pressure.

The pool isolates devices, not backend data. Debug apps default to port 8001;
before a flow that writes data, configure an isolated test backend/database or
explicitly coordinate its data ownership. A retained review may also depend on
keeping its backend and worktree available.

For changes to the reservation helper, run
`python -m unittest discover -s scripts/tests -p 'test_android_emulator*.py'`.
Then exercise a managed emulator's acquire, targeted command, and release flow;
the unit tests cannot verify SDK startup or shutdown behavior.
