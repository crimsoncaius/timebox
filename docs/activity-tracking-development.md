# Activity Tracking: online development slice

Implements [#147](https://github.com/crimsoncaius/timebox/issues/147), under
[#144](https://github.com/crimsoncaius/timebox/issues/144). This is an additive,
online development protocol, not the production replacement for Work Mode.

## Isolation and launch

Apply Alembic migrations to a **separate development database**. Set
`ACTIVITY_TRACKING_DEV=true` for that API process. Bootstrap refuses a database
containing legacy Actual Blocks. The database then retains an enabled marker:
turning the environment flag off does not reopen legacy Actual writers.
Production defaults keep `/activity` disabled and legacy behavior intact.

Registered local review ports: API `12004`, web `12005`, PostgreSQL `12006`.
The review database is `activity_review`; tests use `activity_test` separately.
The local PostgreSQL cluster is under `artifacts/activity-147-postgres`, listens
only on loopback, and contains development data only.

From `backend/`, with `DATABASE_URL` pointing to the isolated database:

```powershell
$env:ACTIVITY_TRACKING_DEV='true'
$env:APP_TIMEZONE='Asia/Singapore'
.venv\Scripts\alembic upgrade head
.venv\Scripts\python -m uvicorn app.main:app --host 127.0.0.1 --port 12004
```

From `frontend/`:

```powershell
$env:VITE_ACTIVITY_TRACKING_DEV='1'
$env:VITE_API_PROXY_TARGET='http://127.0.0.1:12004'
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 12005 --strictPort
```

The web flag also requires Vite development mode; production builds cannot
expose the unfinished controls. Build Android from the repository root with:

```powershell
.\scripts\android-gradle.ps1 assembleDebug -PactivityTrackingDev=true
```

This installs as `com.timebox.android.activitydev`, independently of the normal
app, and defaults to emulator API `http://10.0.2.2:12004/`. Release builds always
disable the feature. Install the debug APK and launch `.MainActivity` using its
full class name `com.timebox.android.MainActivity`.

## Contract

`GET /activity` returns a consistent full snapshot: protocol, monotonic cursor,
server calibration instant, Reporting Time Zone, Current Activity, all canonical
Actual records, and an empty tombstone collection. Full snapshots are explicit;
incremental synchronization and historical mutations belong to later slices.

`POST /activity/commands` accepts a stable UUID operation ID, installation/device
ID, increasing local sequence, calibrated user-action timestamp, retained
calibration metadata, observed base cursor, target Actual identity, payload, and
an explicit effective-time object (`mode: server_now`). Start is unnamed and
unspecified. Switch requires Task Type and accepts optional Block Name. Stop
leaves subsequent time unrecorded. None of these commands completes a Task.

The singleton transaction lock serializes admission and snapshot reads on both
SQLite and PostgreSQL. Interval transitions and command receipts commit together.
The server captures the effective instant **after acquiring the lock**, closing
and opening at exactly the same instant. Existing Actual constraints remain in
force. A duplicate ID with identical content returns its original acknowledgement
and the latest canonical snapshot; different content with the same ID is rejected.

This slice explicitly rejects stale base/target commands with a durable
`conflict` outcome and current state. It does not apply permanent arrival-order
conflict resolution. Action timestamps, calibration, sequence and effective-time
intent remain immutable in the journal for the later offline reconciliation
implementation. Offline authoring and range reconciliation are not claimed here.

Clients persist the operation before sending, keep an unconfirmed operation on
network/storage failure, and retry the same envelope after restart. A confirmed
422 rejection retains the rejected envelope separately and permits another
action. Web uses origin/API-scoped localStorage with Web Locks for tab exclusion;
Android uses an app-scoped repository and synchronous durable preference commits
on IO dispatchers. Android refuses replay if the configured endpoint changes.
Confirmed snapshots persist with the cursor; older cursors cannot regress state.
Visible clients poll every five seconds and refresh on return. Recording itself
has no page timer, lifecycle stop, plan-end stop, or midnight stop.

## Scope boundaries

Current plan adoption/direct Task selection (#150), optional Focus, offline
reconciliation, corrections, inactivity sensing, notification delivery and final
production migration remain separate slices. Legacy Actual editing and recording
shortcuts are rejected in the development database. The existing Day projections
remain minute-based; the authoritative activity records retain precise instants.

## Checks

API tests: `backend/tests/test_activity_api.py`. To exercise real concurrent
PostgreSQL requests, run that file with `DATABASE_URL` pointing to an isolated
test database. The test fixture drops/recreates test tables, so never point it
at review or production data. The migrated PostgreSQL review database separately
exercises the production constraints and migration chain.

Web screen tests: `frontend/src/features/activity/ActivityTracking.test.tsx`.
Android repository tests: `ActivityRepositoryTest`; Compose interaction test:
`com.timebox.android.ui.day.ActivityTrackingTest`.

## Implementation validation (2026-09-11)

- Backend full suite: 250 passed, one PostgreSQL-only test skipped on SQLite.
- Dedicated PostgreSQL activity suite: nine passed, including simultaneous
  starts and duplicate concurrent switches. Fresh review database upgraded through
  the complete Alembic chain and exercised the migrated constraints live.
- Web full suite: 249 passed; TypeScript checking and changed-file ESLint passed.
- Android full unit suite: 198 passed, one `UncaughtExceptionsBeforeTest`
  coroutine-isolation failure in the legacy `WorkModeExecutionTest`. The new
  repository test and that complete legacy test class passed on focused rerun.
  An earlier full run also hit a timing-dependent legacy catch-up assertion;
  the complete affected class passed on its focused rerun. These full-suite
  limitations remain visible rather than being reported as an all-green run.
- Native Compose start/switch/stop interaction test passed on Pixel 9a.
- Live web start required no dialog; web switch appeared on Android, Android
  stop appeared on web, and web restart retained a gap. Background/lock, return,
  browser reload and API restart retained the same saved running identity/start.
- Standards and specification review findings were fixed and re-reviewed with
  no remaining actionable findings. Both review apps use the final working tree.

Local test logs and screenshots are under `artifacts/activity-147-*` and are
not production data or tracked deliverables. Review leaves tracking enabled in
the isolated database; use either client's Stop control when finished.
