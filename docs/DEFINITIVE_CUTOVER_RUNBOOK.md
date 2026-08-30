# Definitive Task + Timeboxing data cutover

This runbook covers Alembic revision `015_definitive_legacy_cutover`. The
revision is intentionally irreversible: it deletes all pre-cutover Actual Block
history and all pre-cutover Undo snapshots. An Alembic downgrade cannot recover
that data. A verified database backup is the only recovery mechanism.

Production execution is deferred to GitHub issue #38. Do not run this cutover
against production as an isolated backend deployment or while old web/Android
clients can still write the superseded model.

## What the revision does

- Preserves every Task row and every Planned Block row, including identifiers
  and existing user-visible fields.
- Detaches legacy children with independent Task state into standalone Battle
  Plan Tasks without changing their Planned Block `task_id`.
- Converts lifecycle-free children into Subtasks. Legacy `completed` becomes
  checked; every other status becomes unchecked; Task lifecycle fields clear.
- Reinterprets compatible scheduled checklist rows in place and retains their
  series/occurrence provenance. It does not generate an occurrence.
- Leaves Quota Trackers and Session Tasks parented and compatible. Quota Tracker
  completion remains derived.
- Backfills `completed_at = updated_at` only for completed independently
  completable Tasks, including completed Session Tasks. Incomplete Tasks and
  Quota Trackers have no `completed_at`.
- Deletes every Actual Block, both manually entered and linked, and discards
  every legacy Planned/Actual correspondence.
- Deletes all Task Completion Undo and Record-actual-as-planned Undo rows.

## Required backup and restore proof

Before every rehearsal and before the #38 production window:

1. Stop writes to the source long enough to take a transactionally consistent
   PostgreSQL backup.
2. Record the backup URI, timestamp, source revision, PostgreSQL version, file
   size, and checksum in the #38 cutover record. Do not put credentials there.
3. Restore that backup into an isolated, non-production PostgreSQL database.
4. Connect to the restored database and prove that representative Task,
   Planned Block, Actual Block, recurrence, quota, archive, and trash rows are
   readable. A successful backup command without a successful restore is not a
   verified backup.
5. Retain the verified pre-cutover backup until #38 explicitly ends the recovery
   window.

## Rehearsal on the restored copy

Use a shell whose `DATABASE_URL` points only to the isolated restored copy.
Confirm the host and database name interactively before continuing. Never paste
the URL into logs.

Bring the restored copy to the cutover base, then record the revision:

```powershell
Set-Location backend
uv run alembic upgrade 014_recurrence_occurrence_protection
uv run alembic current
```

Capture the following pre-cutover evidence. Save query output outside the
repository or in the private #38 cutover record.

```sql
SELECT count(*) AS tasks, min(id) AS min_id, max(id) AS max_id,
       md5(string_agg(id::text, ',' ORDER BY id)) AS identity_digest
FROM tasks;

SELECT count(*) AS planned_blocks, min(id) AS min_id, max(id) AS max_id,
       md5(string_agg(id::text, ',' ORDER BY id)) AS identity_digest
FROM time_blocks WHERE lane = 'planned';

SELECT lane, count(*) FROM time_blocks GROUP BY lane ORDER BY lane;
SELECT count(*) FROM task_completion_operations;
SELECT count(*) FROM actual_block_record_operations;
SELECT count(*) FROM recurrence_occurrences;
SELECT recurrence_kind, count(*) FROM tasks
GROUP BY recurrence_kind ORDER BY recurrence_kind NULLS FIRST;
```

Run the cutover exactly once:

```powershell
uv run alembic upgrade 015_definitive_legacy_cutover
uv run alembic current
```

The Task and Planned Block count, minimum/maximum identifier, and identifier
digest must match the pre-cutover evidence. Then run these invariant checks:

```sql
-- All pre-cutover Actual history and both Undo stores are gone.
SELECT count(*) FROM time_blocks WHERE lane = 'actual';
SELECT count(*) FROM time_blocks WHERE planned_block_id IS NOT NULL;
SELECT count(*) FROM task_completion_operations;
SELECT count(*) FROM actual_block_record_operations;

-- No definitive Subtask retains Task lifecycle state.
SELECT id FROM tasks
WHERE parent_id IS NOT NULL
  AND COALESCE(recurrence_kind, '') <> 'quota_session'
  AND (
    status <> 'open' OR completed_at IS NOT NULL OR ready_to_plan
    OR is_blocked OR blocking_reason IS NOT NULL
    OR task_type_id IS NOT NULL OR urgency IS NOT NULL OR importance IS NOT NULL
    OR deadline_date IS NOT NULL OR deadline_at IS NOT NULL
    OR reminder_at IS NOT NULL OR reminder_delivered_at IS NOT NULL
  );

-- Completion instants follow the cutover rule.
SELECT id, parent_id, recurrence_kind, status, completed_at, updated_at
FROM tasks
WHERE
  (recurrence_kind = 'quota_parent' AND completed_at IS NOT NULL)
  OR (status <> 'completed' AND completed_at IS NOT NULL)
  OR (
    status = 'completed'
    AND COALESCE(recurrence_kind, '') <> 'quota_parent'
    AND (parent_id IS NULL OR recurrence_kind = 'quota_session')
    AND completed_at IS DISTINCT FROM updated_at
  );

-- Planned Blocks still reference existing Tasks or remain intentionally unassigned.
SELECT block.id, block.task_id FROM time_blocks AS block
LEFT JOIN tasks AS task ON task.id = block.task_id
WHERE block.lane = 'planned' AND block.task_id IS NOT NULL AND task.id IS NULL;
```

Every check above must return zero rows or a count of zero. Compare recurrence
ledger and quota counts to the pre-cutover evidence, then run the backend suite:

```powershell
uv run pytest -q
```

Attach the command log, before/after evidence, backup checksum, restore proof,
duration, and any warnings to #38. A rehearsal is not accepted if it used an
empty database or skipped the restore proof.

## Production window (#38 only)

The human cutover owner must confirm all clients and backend artifacts are ready,
announce the write freeze, stop writers, take and verify the final backup, and
give an explicit go/no-go decision. Run the same commands and checks as the
successful rehearsal. Do not run `alembic downgrade` for this revision.

If the migration or any invariant check fails, keep writers stopped. Do not
repair rows in place and do not attempt to reconstruct Actual history. Replace
the database with the verified pre-cutover backup, verify the restored Alembic
revision and row evidence, and only then reopen the old stack or schedule a new
cutover attempt.

## Local test-dialect boundary

Focused migration tests create the complete revision-014 schema, seed
production-shaped legacy fixtures, stamp revision 014, and invoke Alembic through
revision 015 on SQLite. The historical 001–014 chain itself is not used as a
SQLite fixture builder because revision 003 contains PostgreSQL `now()` SQL.
Likewise, a base-to-head offline render is not a substitute for an online chain:
historical revisions 002, 004, and 005 inspect live metadata to support divergent
old installations. The 014-to-015 PostgreSQL offline SQL is verified separately;
the mandatory restored-copy rehearsal is the full production-dialect chain and
data proof without weakening PostgreSQL constraints.
