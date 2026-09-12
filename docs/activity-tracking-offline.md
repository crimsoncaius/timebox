# Activity Tracking offline development slice (#148)

Implements the single-device offline/restart portion of #144 on top of #147.
This is additive development functionality. Production Work Mode, Task
Completion, recurrence, Quota Tracker, and Session Task behavior are unchanged.

## Prerequisite verification

GitHub's native blocked-by endpoint lists #147. Its implementation is present in
this checkout: cc5b27c, c4bd4fc, and b9c8009. The API, web screen, Android
repository and Compose checks exercise that foundation again. The issue was open
at the start of this work and was closed at 2026-09-11T05:49:08Z. The final native
dependency query confirms #147 CLOSED, so the blocker is complete and present.

## Persistence and synchronization

Both clients commit one journal containing installation identity, increasing local
sequence, immutable command UUID/action time/calibration, observed base cursor,
target identity, explicit effective instant, confirmed snapshot/cursor and outbox.
The persisted projection combines canonical history with pending start/switch/stop
commands. A command is shown as saved locally only after storage succeeds.
Storage read/write failures remain visible and do not send an unsaved command.

The first successful connection must advertise `offline_ready`. Older servers and
initially offline installations cannot admit offline commands. Existing online
receipts remain recoverable and must be confirmed before offline authoring.
Reporting Time Zone and available Task Types are cached with the snapshot.

In-process clocks use a monotonic anchor; after restart the retained calibration
is applied to wall time. Local sequence and action time remain increasing despite
a backward wall-clock change. The chosen calibration travels with the command,
including when a refresh races with authoring. Reconnect never replaces the
command ID, action instant, calibration, observed revision, or effective instant.

Commands in an offline sequence name their predecessor operation instead of
inventing a server Actual ID. The server admits only an uninterrupted applied
chain from the same installation. Original effective instants close/open Actuals
atomically; receipts and interval mutations commit together. Duplicate delivery
returns the original outcome and current canonical history. Canonical state and
outbox receipt removal are committed together locally. Older snapshots cannot
roll back a newer cursor or replace fresher clock calibration.

Visible clients poll/retry automatically and refresh on return; browser Retry and
native Retry also synchronize immediately. No background scheduling or absence-
based stopping is introduced. Compact Offline / Unsynced / Synced state remains
visible; recording stays available offline after bootstrap, including after a
normal app restart. Storage loss restores only server-confirmed history after
connection and never guesses missing activity.

Competing-device reconciliation is deliberately still #149: stale/conflicting
chains receive a conflict outcome rather than arrival-order overwrites. Rejected
pending envelopes are retained locally for diagnosis; this slice does not claim
a complete user-facing conflict-recovery workflow or release readiness.

## Review launch

Use the isolated API/database configuration documented in
[activity-tracking-development.md](activity-tracking-development.md): API 12004,
web 12005, PostgreSQL 12006. Local database role is `timebox`; review database is
`activity_review`, and destructive test fixtures use only `activity_test`.

For full browser close/restart while disconnected, build the explicitly gated
review bundle from `frontend/`:

```powershell
$env:VITE_ACTIVITY_TRACKING_DEV='1'
npx vite build --mode activity-review --outDir dist-activity-review
$env:VITE_API_PROXY_TARGET='http://127.0.0.1:12004'
node node_modules/vite/bin/vite.js preview --outDir dist-activity-review --host 127.0.0.1 --port 12005 --strictPort
```

Only development or explicit `activity-review` builds can expose the feature;
normal production builds remain disabled even with the flag. The review bundle
registers a same-origin app-shell service worker. API responses are never cached
by that worker; durable activity data lives in the journal. Visit the review app
online once before offline closure. Normal Vite hot-reload mode is not the
full-offline app-shell review target: its websocket runtime fails when started
without a connection. No production deployment is authorized or performed.

Android remains the separate `com.timebox.android.activitydev` application:

```powershell
.\scripts\android-gradle.ps1 assembleDebug -PactivityTrackingDev=true
```

## Validation (2026-09-11)

- Backend full SQLite suite: 252 passed, one PostgreSQL-only test skipped.
- Real PostgreSQL activity suite: 11 passed, including concurrent writers,
  duplicate commands, immutable offline sequence replay and overlap rejection.
- Full web suite: 251 passed. Focused activity tests, TypeScript and changed-file
  ESLint pass after final changes.
- Android ActivityRepository tests: three passed; native Compose interaction
  test passed. Final debug application and instrumentation APKs build.
- Android full-suite attempt exposed pre-existing DayWorkModeViewModelTest
  restoration failures and an uncompleted-coroutine timeout; a focused rerun of
  that class also failed. It is not reported as an all-green Android suite.
- Real Chromium persistent profile: offline switch; full browser close/reopen
  while still offline; stop after restart; reconnect; exact start/end instants
  verified through the API. Lost acknowledgement injected after server commit,
  followed by offline browser restart and replay: one cursor advance and the
  original running start. Journal loss blocked initially offline authoring and
  restored eight confirmed records without creating a record.
- Android emulator: network disabled, offline switch, force-stop/relaunch,
  recovered Current Activity and Unsynced status, offline stop after restart,
  reconnect. API start/end matched both persisted command instants exactly.
  SharedPreferences journal loss was checked separately from other app settings:
  offline Start was disabled, then reconnection restored all ten canonical
  records at cursor 16 with tracking off and no invented interval.

Evidence is under `artifacts/activity-148-*`. These are local development data.
Emulator process death and browser closure were tested; hardware reboot/Doze,
optional inactivity sensing and notifications are outside this slice.
