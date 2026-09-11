# Activity Tracking reconciliation (#149)

Implements the competing-device slice of [#144](https://github.com/crimsoncaius/timebox/issues/144).
The existing development gates remain: isolated API/database, explicit web review
build, and `com.timebox.android.activitydev`. Production Work Mode is not replaced
and nothing was deployed to production.

## Prerequisites and scope

GitHub's native blocked-by endpoint for #149 lists #148, closed as completed.
Its native prerequisite #147 is also closed as completed, with no open blockers.
Both implementations are present in this working tree. In particular, the #148
durable journals, outboxes, restart projections, calibration, and stale-response
guards were present as uncommitted changes when this work began. They and the
unrelated Task Type/composer changes were preserved; no reset, checkout, merge,
commit, or issue mutation was performed.

The accepted web and Android prototype resolutions informed the compact
newer-change explanation. No prototype implementation was promoted.

## Reconciliation contract

The existing immutable envelope remains the authoring record. `action_at` is
already calibrated by the client; the retained offset is not applied twice.
Ordering is `(action_at, device_id, sequence, operation_id)`, never admission
cursor, reconnect time, or retrospective range start. Device identities use
printable ASCII so Python, JavaScript and Kotlin compare ties identically.
Out-of-order delivery of a valid local sequence is accepted. Its timestamps must
remain nondecreasing. Duplicate IDs with changed content are rejected.

Each admitted command stores resolved range intent separately from its original
envelope. Start/switch paint from their explicit instant onward; stop paints an
empty range. Historical `add`, `edit`, and `delete` require `effective.mode=range`
with explicit `at` and `end`. Edit clears the observed target and paints its new
range; delete clears exactly the observed target range. Historical editing of a
running block is rejected. The full correction UI remains #152.

Validation reconstructs the observed revision to reject overlaps the author
already knew about. Independently valid stale commands are then replayed by
action order. Only conflicting portions change. Empty ranges survive replay, so
older switches and corrections cannot revive a stop or deletion. A later
explicit action can legitimately fill a gap.

The pre-reconciliation development records form an immutable baseline, retaining
their identities, instants and metadata. Replay preserves original provenance
through splits and corrections, including disjoint concurrent moves. Canonical
fragment IDs and tombstones are deterministic across delivery permutations.
Hash-derived IDs fit the existing native integer contract; a collision aborts
admission rather than overwriting an unrelated record.

The singleton database lock covers admission, replay, projection, receipts and
cursor advancement in one transaction. Unaffected rows are not rewritten.
Changed rows invalidate their Record-as-planned Undo capability. Surviving
fragments preserve Task, Note and Planned Block correspondence. The additive
`activity_source` column allows multiple protocol fragments per plan while a
partial unique index retains legacy-writer correspondence protection. ORM
correspondence loading uses a collection; the old singular compatibility field
still selects the first corresponding Actual. Broader correspondence UI/cutover
work remains with the subsequent specification slices.

Snapshots include canonical records, tombstones, provenance, ordered coverage
(including gaps), and operation outcomes. An acknowledged operation may later
become `superseded` when a newer remote action replaces some of its range.
Clients acknowledge it, drain the rest of their durable outbox, and display a
six-second explanation without a conflict dialog. Coverage lets pending local
commands project around newer canonical time, even if transport fails partway
through draining a chain. Old snapshots cannot regress the cursor/calibration.

Synchronization continues to use full canonical snapshots; journal compaction,
production compatibility/cutover, and the remaining #144 features are not enabled
by this development slice. Uncalibrated offline clocks cannot establish exact
real-world ordering.

## Validation — 2026-09-11

- Full SQLite backend suite: **269 passed, 2 PostgreSQL-only tests skipped**.
- Actual PostgreSQL activity suites: **29 passed**, including simultaneous
  writers, competing range transactions and concurrent duplicate submissions.
- Every delivery permutation for competing switches/stops; exact equality of
  canonical records, provenance, tombstones, coverage and outcomes across range
  delivery permutations; equal-action ties; partial overlaps; deleted time;
  retrospective ordering; disjoint moves; preserved Task/plan links and notes;
  known-overlap rejection; lost acknowledgements and duplicate replay.
- Full web suite: **252 passed**. After adding the final clock-reversal and
  microsecond-ordering cases, focused Activity Tracking suite: **9 passed**. TypeScript and changed-file
  ESLint passed.
- Android ActivityRepository: **5 passed**, including competing offline chains,
  canonical pending projection, restart, backward wall clock, and immutable
  calibration. Native Compose ActivityTracking: **2 passed**, including canonical
  remote activity, transient feedback, and continued controls.
- Full Android suite reproduced the previously recorded
  `DayWorkModeViewModelTest` restoration assertion and coroutine timeouts (planning
  and midnight cases). The stalled full-suite worker was stopped after about
  three minutes. This is not an all-green Android suite. The focused final run
  and application/instrumentation builds succeeded afterward.
- Migration 025 was applied to the existing isolated PostgreSQL review database,
  retaining its eleven records and active identity at cursor 17. Migration
  regressions passed; downgrade refuses to discard admitted reconciliation
  intent and requires forward repair.
- Live Chromium web offline Lunch → later native Reading → web reconnect:
  Lunch survived from `06:08:35Z` to `06:09:13Z`; both clients showed Reading.
  Web's newer-change explanation was observed and captured.
- Reverse live check: native offline Lunch, force-stop/restart, later web
  Reading, then native reconnect. Native Lunch survived from `06:11:11Z` to
  `06:11:14Z`; both clients converged to WebReading149. Android's native feedback
  presentation was separately asserted by the Compose test.
- Final API restart, visible browser refresh and Android relaunch retained the
  same canonical Current Activity and Synced status. Emulator network settings
  were restored.

Evidence is in `artifacts/activity-149-*`. Emulator process restart and real
browser transport disconnection were exercised. Hardware reboot, Doze, inactivity
sensing, notification permissions and wake locks are outside this slice.

## Review

- Web: <http://127.0.0.1:12005/day/2026-09-11>, launched in visible Chrome.
- Android: running `com.timebox.android.activitydev` on the connected emulator.
- API: loopback port 12004, PostgreSQL review database on 12006; destructive test
  fixtures used the separate `activity_test` database.

Both applications are left tracking **WebReading149** in development data. Launch
instructions remain in [offline development](activity-tracking-offline.md).
