# Activity Tracking cutover rehearsal (#157)

Additive development tooling, not production deployment. Backend/web/Android
development flags remain required. Migration 028 adds an archive column without
converting records or enabling Activity Tracking.

## Admission and import

Every API database request holds a PostgreSQL shared session advisory lock on a
pinned connection, including across service commits. Explicit cutover holds its
exclusive counterpart through preflight, conversion, baseline creation and the
enabled-marker commit. An admitted legacy request finishes before the snapshot;
later requests check compatibility after the boundary. Every API process must
run this admission-aware backend before switching; an older backend cannot
participate in a lock it does not know about.

After cutover, non-activity API routes require
`X-Timebox-Protocol: activity-online-v1`. Old clients receive 426 with update/reload
instructions, including for incompatible reads. Existing legacy Actual mutation
routes remain rejected even with that header. Compatible development clients
send it. Disabling development flags does not reopen legacy writers.

Preflight validates references, Quota Tracker exclusion, grid bounds, positive
intervals, overlaps and multiple running records before changing rows. Grid
bounds use the existing server Reporting Time Zone; ambiguous/nonexistent local
times fail. The archive retains source values and reporting dates. IDs, known
instants, names, notes, timestamps and links survive. The saved running Actual
keeps its original start; no running row means off. Its reconciliation baseline
precedes new operations. Repeated import never rebuilds that baseline.

## Restored-copy workflow

1. Back up a legacy dataset with `pg_dump -Fc`, retain the dump, and restore it
   with `pg_restore --exit-on-error` into a separately named database. Verify
   counts, IDs, metadata, links, task state and daily totals against the source.
2. Point `DATABASE_URL` at the local restored PostgreSQL database. Set
   `ACTIVITY_TRACKING_DEV=true` and `APP_TIMEZONE` to its original reporting zone.
3. From `backend/`, run `.venv\Scripts\alembic upgrade head`, then:

   ```powershell
   .venv\Scripts\python -m app.activity_cutover preflight --restored-database activity_rehearsal_final_157
   .venv\Scripts\python -m app.activity_cutover apply --restored-database activity_rehearsal_final_157
   ```

   The CLI checks the exact database name, loopback PostgreSQL and development
   flag. `apply` repeats preflight under the exclusive lock: an old report cannot
   authorize a changed dataset.
4. Launch both gated clients and the API using the existing review ports in
   [development setup](activity-tracking-development.md). Check history, running
   identity/start, bootstrap, offline recovery, old requests and corrections.

SQLite admission is single-process only, for tests/offline rehearsal. Use real
PostgreSQL for concurrent online cutover qualification.

## Recovery

`rollback --restored-database <name>` restores archived Actual values and clears
the cutover/baseline marker atomically only while the complete imported row set,
cursor and operation receipts remain unchanged. Rejected requests do not block
this pre-write rollback. Failed preflight leaves the original dataset intact.

After new writes, use `pause --restored-database <name>`. Reads continue; mutations
return 503 and client outboxes remain retryable. Preserve a new backup, diagnose
and repair forward, then `resume` and retry original operation envelopes. Neither
pause nor resume reopens legacy writers. Rollback refuses changed records,
cursors or receipts. There is no generic reverse migration or old-backup restore
that discards new activity. Schema downgrade refuses a nonempty cutover archive.

Legacy Work Mode snapshots never become activity commands. Both clients retain
their source and show **Review old Work Mode data** on Day. **Review rejected
changes** exposes cumulatively retained rejected envelopes. Use normal Day
add/edit for corrections; observations are not inferred recorded time.

First recording requires online bootstrap. A matching valid active legacy
snapshot can restore local Focus only after successful bootstrap and subject to
planning guards. Ended/stale snapshots cannot start or stop Actuals. Archival
preservation and durable migration completion are separate; storage failures are
retryable and explicit Focus exit wins over retry. Later ordinary offline
authoring retains the existing durable calibrated journal.

## Evidence, 2026-09-11

The synthetic PostgreSQL source at schema 027 held completed linked history, a
legacy grid Actual and a saved running Actual. It was dumped and restored before
upgrade to 028; no production data was inspected.

- Actual IDs 2, 3, 4 survived repeated import. Actual 2 retained Task 1, Planned
  Block 1 and its note. Grid time became September 10 15:00–16:00 UTC, preserving
  its Singapore reporting day. Day totals remained 120 actual/60 planned minutes;
  the Task remained completed.
- Verified pre-write rollback and re-import. Added a real historical Actual
  through the new command API, paused, observed 503, verified rollback refusal,
  resumed and retried the same envelope. Four records survived without duplication;
  running Actual 4 and its original start remained unchanged. A post-write dump
  also preserves the resulting state.
- Backend full suite: **294 passed, 6 skipped**. Real PostgreSQL activity,
  cutover, planning, reporting and reconciliation suites: **49 passed**, including
  an admitted legacy HTTP request held across cutover and concurrent writers.
- Web full suite: **285 passed**. TypeScript and changed-file ESLint passed.
  Live Chromium restored matching legacy Focus, retained tracking on exit/reload
  and displayed the source on Day.
- Android repository/Focus suites: **12 passed**; debug and test APKs built.
  Opt-in real API/device rehearsal passed on API 36 Pixel 9a: native DataStore
  snapshot → restored Focus → exit → Day source review, with identical canonical
  records and running identity/start. Prior development-app data was archived.
- Full Android suite was attempted and stopped after the existing legacy
  `DayWorkModeViewModelTest` restoration assertion and planning coroutine timeout.
  It is not a passing full suite. Physical devices and an old released APK upgrade
  were not exercised; old-client rejection was checked at the real API boundary.

Final API `12004` and web `12005` use `activity_rehearsal_final_157` on PostgreSQL
`12006`. Chromium and the Android development app remain on Day with the migrated
running activity. Backups, logs and screenshots are local `artifacts/activity-157-*`
files. Standards and specification re-reviews found no remaining code blockers.
