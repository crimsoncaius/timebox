# Production Activity Tracking conversion (#165)

Completed 2026-09-12, Asia/Singapore, on Railway `timebox` / `production` / `api`.
The deployed backend was `a3d222e002a0cbb7d2e8a94997598e35129597ac`.
Its schema was current at `028_activity_cutover`, but its persistent activity
marker was absent. Health and database readiness both passed while `/activity`
returned 404, `Activity Tracking requires database upgrade`.

## Backup and qualification

The production PostgreSQL 18 custom-format dump was retained both in the database
container and locally at `artifacts/issue165-before.dump`. SHA-256:
`1661513c21f431add4f302f5264af628a25a6f61a190b8ac531ef41d9ab3fa70`.
Treat dumps and extracted SQL as private data; do not commit them.

The backup was restored into a separate local database, `issue165_verified`,
using PostgreSQL 18 client tools. The local server is PostgreSQL 17, so its SQL
restore omitted only the unsupported `SET transaction_timeout = 0` session
setting. Every table's normalized content fingerprint matched production.

Read-only preflight found two completed Actuals, IDs 16 and 17, no running Actual,
and no invalid references or overlaps. The original reporting zone was
Asia/Singapore. Restored-copy conversion verified every unchanged record field,
identity, timestamp, interval, relationship, and every non-activity table.
Known instants and elapsed durations were preserved. Reapplication was idempotent.

The rollback rehearsal found SQLAlchemy's `onupdate` clock replacing archived
`updated_at` values on PostgreSQL. The fix explicitly marks that field for update.
The PostgreSQL regression failed before the fix and passed afterward. The restored
production copy then passed conversion, exact rollback, and reapplication.

## Production execution and verification

The local-only `app.activity_cutover` CLI was not weakened or used against
production. The already-deployed `app.services.activity_cutover.apply` service
was invoked explicitly through Railway SSH in the API environment. This service
repeats preflight under the exclusive admission lock and atomically imports and
enables the timeline. The source fingerprint was checked again before execution;
the returned archive and canonical records were checked against preflight.
`ACTIVITY_TRACKING_DEV` was not enabled. No schema migration was needed.

The verification plan required stopped tracking before starting, used a unique
`issue165-verification-*` device identity, exercised start/switch/stop, checked the
exact shared switch boundary, then deleted only the two verification records
through activity commands. Original records 16 and 17 remained byte-for-byte
equal in the API snapshot. Tracking was left stopped; operation receipts remain.
Production has no configured API_KEY, so verification used the existing server
authentication configuration and the activity protocol header.

Post-verification backup: `artifacts/issue165-after.dump`, SHA-256:
`c40a63d410ba237d11ee54d95537c27c3acea302b58bdeb0f9a68aa368522451`.
Evidence scripts, command envelopes and logs are under `artifacts/issue165-*`.

## Release gate

Run from the repository root before distributing an Activity Tracking client:

```powershell
backend/.venv/Scripts/python scripts/verify-api-release.py https://api-production-db7f.up.railway.app
```

Supply `API_KEY` through the environment when the target requires authentication.
This read-only check requires `/health`, `/ready`, and a compatible `/activity`
snapshot; it exits nonzero on the upgrade gate even when the first two pass.
It was observed failing before conversion and passing afterward. Its regression
test covers both gated and enabled responses.

Android now reserves Offline for network I/O failures, preserves HTTP JSON detail,
and falls back to the HTTP status for non-JSON responses. The repository tests
cover the original upgrade response, a network failure, an HTTP 503, and recovery.
The isolated review APK uses package `com.timebox.android.issue165` and production
as its default endpoint, retaining the regular app's local journal separately.

The API 36 emulator loaded production records 16 and 17 with no pending commands,
tracking stopped, and no Offline/404 error. The first launch reported a background
job ANR (`No response to onStartJob`). After force-stop/relaunch, Day and Settings
navigation responded normally and the review instance was left on Day. Physical
devices were not exercised. Evidence: `issue165-final.png`, `issue165-responsive.xml`.

Validation: 11 Android repository tests passed and both normal and isolated review
APKs built. PostgreSQL activity/cutover suites passed 20 tests. The SQLite run
including the release-check regressions passed 20 tests with two PostgreSQL-only
tests skipped. The full Android suite was not run for this change.

## Recovery after this verification

New operation receipts now exist: pre-write rollback is no longer permitted.
Use the deployed `activity_cutover.pause(engine, True)` service through the API
environment to reject new writes while retaining readable history. Retain another
dump, diagnose and repair forward, then call `pause(engine, False)` and retry the
original envelopes. Do not restore the old dump over new activity, clear the
enabled marker, or reopen legacy writers. The restored-copy CLI remains restricted
to local rehearsals. The timestamp rollback fix is in the working tree; it is not
a claim that a new backend release was deployed during this conversion.
