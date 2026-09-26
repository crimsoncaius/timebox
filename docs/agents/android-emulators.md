# Android emulator ownership

## Acquire and use

Run from the working tree being tested. Python 3 is required. Unit tests and APK
builds need no device reservation. For instrumentation tests, use:

```powershell
python scripts/android-emulator.py test --owner "task-id: UI tests"
```

This reserves a device, boots a clean instance, runs `connectedDebugAndroidTest`
against its serial, and stops and deletes its device files after success or failure. Startup
failures use the same cleanup path. Additional
Gradle arguments can follow the owner. Acquire as many managed emulators as the
work needs; the pool grows on demand. `status` still reports owners and pending
reviews.

`acquire --extra --owner "task-id: additional emulator"` reserves a separate
managed slot beyond the configured baseline without changing shared
configuration or existing reservations. The extra device uses the same token,
command, release, and review lifecycle.

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
itself. Release temporary work after success or failure without asking the user.
Release removes the device's writable storage, AVD registration, and emulator log;
capture any diagnostic evidence needed before releasing it. Shared SDK images remain installed.

## Leave a review

Install and launch the updated app, verify the intended screen, then run:

```powershell
python scripts/android-emulator.py review TOKEN
```

Name the device serial, token, storage root, and pending review in the final response.
Review holds do not expire. When the user finishes reviewing, or after successfully
performing their requested merge, release the matching review with
`release TOKEN --review-done`. Match the recorded owner and worktree to the task;
preserve unrelated comparisons and any device the user explicitly asked to keep.
Complete this before removing the merged worktree or a backend needed by the review.
When revising the same review, use `resume TOKEN`, update that device, and mark it
for review again. These actions need no additional routine confirmation.
A task merely finishing leaves its pending review available.

## Inspect and recover

```powershell
python scripts/android-emulator.py status
python scripts/android-emulator.py status --summary
python scripts/android-emulator.py recover TOKEN
```

Default status is the reservation JSON list. Summary reports the actual resolved
root, free bytes, logical file lengths, allocated disk bytes, reservation counts,
and cleanup errors. Logical lengths can exceed physical allocation for sparse
images. Both forms are read-only; an absent pool remains absent.

Recovery accepts expired active reservations and retries `cleanup_pending`
immediately. It obtains the device operation lock, checks leftover command markers,
records cleanup intent, stops the matching emulator, verifies process exit, deletes
only that device's files, then frees the slot. Failed or interrupted cleanup keeps
the slot unavailable and records an error. Retry `release TOKEN` or `recover TOKEN`;
device operations and review/resume are blocked until cleanup finishes.
Old tokens cannot operate after release. Uncertain command startup or a live
host process blocks recovery. Interrupted Gradle commands also require checking
their descendants, even if the launcher has exited. For an interrupted startup, inspect the recorded
task and host processes; remove its `operation-N.json` marker only after verifying
the entire command and its descendants have ended. Never remove registry or lock
files to bypass ownership. Emulator launch identities include process creation
times and observed descendants. A `starting` launch record means startup was
interrupted before identity was confirmed: inspect the processes and descendants
before repairing that record. A live emulator with offline ADB remains pending;
close its matching window, verify exit, then retry cleanup. PID alone is insufficient.

## Pool and isolation

The per-user pool is `~/TimeboxRuntime/emulators`, outside `AppData`. All participating
worktrees and terminals must resolve this to the same physical directory and registry.
SQLite transactions allocate atomically;
per-device kernel locks prevent release while a command is running. Treat this
directory as local operational state, not version-controlled project content.

`scripts/android-emulator.json` defines the baseline slot count, port range,
lease duration, installed system-image path, and display configuration. Ordinary
`acquire` reuses free baseline slots first, then grows past that baseline.
Change the JSON only when all slots are free and all participating worktrees
can use the same configuration.
Allocation checks `min_free_space_gib` on the pool's drive inside the transaction.
This is a minimum-free-space guard, not a reservation of future disk growth. On
rejection, inspect status and release completed work; preserve pending reviews.
The initial profile uses the installed Android 36 Google Play x86_64 image,
1080×2424 display at density 420, 4 GB RAM, and font scale 1.0. The smaller
2 GB profile caused startup ANRs under fresh Google Play setup load in local
validation. It is a stock Android
baseline, not a Samsung One UI emulator. Navigation uses that image's default.
An exact S26 match requires confirming the physical phone's display and OS settings.

Managed AVDs have separate storage and names `timebox-agent-01`, etc. Each new
reservation wipes only its managed AVD and cold boots it; release stops it and
deletes its individual files. Small registry and kernel-lock files remain reusable.
Existing personal emulators are not adopted, reset, or stopped. A port occupied
by an unrelated emulator fails safely. Personal emulators are outside this pool.

The pool isolates devices, not backend data. Debug apps default to port 8001;
before a flow that writes data, configure an isolated test backend/database or
explicitly coordinate its data ownership. A retained review may also depend on
keeping its backend and worktree available.

## Updating older worktrees

Switch helper versions while emulator work is idle. Before an older worktree next
uses a device, incorporate the current helper, configuration, and these instructions.
Older copies still using `AppData/Local/Timebox/emulators` do not follow the new root
automatically. Windows packaged apps may redirect that legacy path into their
`Packages/<app>/LocalCache/Local` tree. Inspect both physical locations for old
reservations before a switch; resolve ownership before removing obsolete state.
Preserve dirty worktree changes when bringing its helper up to date.

The old managed review devices were retired and deleted on 2026-09-26. Historical
device serials and tokens in design evidence are not current reservations. Acquire
a fresh device to reproduce those reviews; preserve their screenshots and test history.

## Validate helper changes

For changes to the reservation helper, run
`python -m unittest discover -s scripts/tests -p 'test_android_emulator*.py'`.
Then exercise acquire, install/launch, review/resume, and release. Verify process
exit, disappearance of that device's files, and recovered disk space. Check the
resolved pool and registry from Codex and an ordinary terminal. Leave one newly
launched device for user review and release all temporary validation devices.
