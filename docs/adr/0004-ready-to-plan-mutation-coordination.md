# Coordinate Ready to Plan mutations per task

Ready to Plan changes are applied immediately but persisted serially per Battle Plan Task; different tasks may save concurrently. Each client projects the coordinator's confirmed, desired, and pending readiness state across Battle Plan and Day Planning, reconciles a failed latest intent from the server, and offers retry only for that latest intent. This prevents stale responses and local rollbacks from overriding a newer choice or exposing unsaved work as schedulable.

The coordinator is app-scoped and in-memory: it survives in-app navigation, and refreshes merge confirmed server data without replacing a pending intent. Server task-lifecycle validation remains authoritative; a rejected readiness change reconciles the saved task and removes it from scheduling. Android Task Detail retains its explicit multi-field draft, but submits its readiness field through this coordinator. Cross-device changes remain last-write-wins and are observed on a later refresh rather than through a conflict protocol.

Ready to Plan controls remain available for an immediate reversal and expose their saving state accessibly. Day Planning shows a pending addition but disables selection, drag, and block creation. Failure and retry are task-scoped so recovery remains available after navigation. The coordinator is deliberately narrow rather than a generic optimistic-mutation layer.

A readiness response confirms only the readiness field and must not replace unrelated, newer task data. A failed request is not an error when reconciliation shows the latest desired readiness is already saved; the user receives recovery affordances only for an unmet latest intent.

Task versions are a read-side freshness guard: clients ignore responses older than their newest known task version without introducing conditional writes. The behavior is proven with deterministic transport-controlled tests for projection, reversal, stale responses and reloads, lifecycle rejection, recovery, navigation, editor submission, and disabled Day interactions; web and Android are launched from the final working tree for review before the issue closes.

If neither the write nor reconciliation can be confirmed, clients fall back to the last confirmed readiness, keep an unconfirmed addition unschedulable, and retain retry for the explicit latest value. Failures and retries are independent per task, so concurrent errors cannot overwrite one another's recovery path.

A pending removal immediately invalidates a matching Day Planning selection and unsaved draft; an already-sent block request remains subject to server validation. Retries are manual only, and a new explicit toggle supersedes the failed intent and clears its old recovery state.

## Considered Options

- Concurrent writes with client sequence numbers were rejected because the server could still receive and persist an older request after a newer one.
- Page-local optimistic state was rejected because Battle Plan and Day Planning can otherwise show contradictory readiness.
