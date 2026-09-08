# Issue 77: Reliable immediate Ready to Plan changes

Source: https://github.com/crimsoncaius/timebox/issues/77

## Problem Statement

Changing Ready to Plan should feel immediate, but the current implementation is only a page-local optimistic toggle. It can leave Battle Plan and Day Planning inconsistent, allow unsaved work to be scheduled, let a stale response reverse a newer choice, and lose recovery after navigation. The application must be responsive without representing an unconfirmed task as safely schedulable.

## Solution

Each client maintains one app-scoped, in-memory Ready to Plan coordinator. The coordinator owns each Battle Plan Task's confirmed value, latest desired value, pending status, failed latest intent, retry action, and freshness version. It immediately projects the latest intent to every relevant screen, serializes persistence per task, and reconciles failures with the server. Pending additions appear in Day Planning but cannot be selected, dragged, or used to create a Planned Block until confirmed; pending removals immediately lose those privileges.

## User Stories

1. As a planner, I want selecting Ready to Plan in Battle Plan to update immediately, so that the application feels responsive.
2. As a planner, I want a Ready to Plan change to agree in task detail and Battle Plan, so that I do not see contradictory state.
3. As a planner, I want the Day Ready to Plan queue to immediately reflect my latest choice, so that it matches my current planning intent.
4. As a planner, I want a pending addition visible in Day Planning, so that I can see work I just chose to prepare.
5. As a planner, I want a pending addition unavailable for selection, dragging, and Planned Block creation, so that unsaved work cannot be scheduled.
6. As a planner, I want a pending removal immediately absent from queue interactions, so that I cannot schedule work I just removed.
7. As a planner, I want to reverse a readiness choice while it is saving, so that my latest choice remains visible without waiting for the network.
8. As a planner, I want rapid toggles to persist only in a safe order, so that an older response cannot replace a newer choice.
9. As a planner, I want successful persistence not to flicker or revert the visible choice, so that confirmation is uneventful.
10. As a planner, I want a failed latest change reconciled with saved server state, so that the display is truthful.
11. As a planner, I want a task-scoped explanation and Retry when my latest readiness intent is not saved, so that I can recover after navigation.
12. As a planner, I do not want a failed obsolete request to show an error when my latest intent is already saved, so that I am not misled.
13. As a planner, I want independent failures for two tasks to retain independent Retry actions, so that one problem does not hide another.
14. As a planner, I want a network timeout with no successful reconciliation to leave an addition unschedulable, so that unknown state is not mistaken for confirmation.
15. As a planner, I want a retry to be manual, so that an abandoned or superseded choice is never restored automatically.
16. As a planner, I want any new explicit toggle to supersede an old failed retry, so that the application follows my current intent.
17. As a planner, I want a selected Day task and unsubmitted draft cleared when it is pending removal, so that eligibility is enforced at interaction time.
18. As a planner, I want completed or otherwise server-rejected tasks reconciled to their saved state, so that lifecycle rules remain authoritative.
19. As a planner using Android Battle Plan, I want the direct readiness action to behave identically, so that mobile responsiveness is reliable.
20. As a planner editing an Android Task Detail draft, I want Ready to Plan to remain unsaved until I save the draft, so that multi-field editing remains explicit.
21. As a planner, I want an Android Task Detail save to respect a newer readiness choice, so that the draft cannot bypass readiness ordering.
22. As a planner using two devices, I want existing last-write-wins behavior to remain unchanged, so that this improvement does not promise conflict resolution it does not provide.
23. As a planner, I want delayed data reloads not to regress a newer known task state, so that the interface remains stable during normal refreshes.
24. As a planner using assistive technology, I want saving and unavailable states announced clearly, so that readiness controls remain understandable.

## Implementation Decisions

- Ready to Plan remains the glossary-defined queue condition for an incomplete Battle Plan Task; task-transition semantics do not change.
- Add a dedicated, app-scoped, in-memory readiness coordinator on web and Android. It is a narrow module, not generic optimistic mutation infrastructure.
- Model a Pending Ready to Plan Change as a task's latest unconfirmed intent. The coordinator owns confirmed readiness, desired readiness, pending/failed/retry state, and the newest known task version.
- Apply the desired value synchronously to every readiness projection. Keep the direct readiness control enabled for a reversal and expose its saving state accessibly.
- Serialize writes per Battle Plan Task while allowing different tasks to save concurrently. If an intent returns to the original confirmed value while a prior write is in flight, persist the compensating value after that write settles.
- Merge a readiness response only into readiness coordinator state; do not install its whole returned task over unrelated, newer task data.
- Use task versions only as client-side read freshness guards. Ignore a response older than the newest known version; do not add conditional writes, API fields, or a server conflict protocol.
- Preserve pending desired readiness across in-app navigation and ordinary refreshes. The coordinator is not persisted across process/browser restart; a later normal load resolves server state.
- Day Planning shows a pending addition with saving/unavailable presentation and blocks selection, drag, and Planned Block creation. A pending removal immediately disappears from interaction, clears a matching selection, and discards an unsubmitted draft linked to that task.
- On a latest-intent failure, reconcile the saved task from the server. If saved readiness matches desired readiness, settle silently. Otherwise restore reconciled state and expose task-scoped error and manual Retry for that exact latest intent.
- If reconciliation is unavailable, fall back to the locally confirmed state, keep an unconfirmed addition unschedulable, and explain that confirmation failed. Retrying an explicit boolean value is idempotent.
- A newer explicit toggle clears and supersedes a failed intent. Failed obsolete requests are never user-visible.
- Server validation remains authoritative for completion and every existing task lifecycle rule. Rejection reconciles the saved task and removes it from scheduling as appropriate.
- Existing cross-device behavior remains last-write-wins. A later refresh may reveal a remote overwrite; this issue does not add conflict detection or resolution.
- Android Task Detail retains its multi-field draft. When the draft saves, its Ready to Plan field passes through the same coordinator so it cannot bypass ordering.
- Readiness errors and Retry actions are task-scoped and remain reachable wherever that task's readiness is presented; an application notice may supplement but not replace them.

## Testing Decisions

- Test externally observable behavior, not coordinator counters, queues, maps, or other implementation internals.
- The principal web seam is a route-level AppRoutes harness with controllable transport. It navigates between Battle Plan and Day and proves immediate projection, reversals, serialized persistence, no-flicker confirmation, stale response/reload protection, pending queue disablement, failure/reconciliation/Retry, multiple independent failures, selection invalidation, and accessible saving/error states.
- The principal Android seam is a TimeboxApp navigation Compose harness backed by a controllable repository. It proves the same cross-screen contract, including direct Battle Plan changes, Day eligibility, navigation survival, retries, lifecycle rejection, and the Task Detail draft submission boundary.
- Use deterministic deferred responses and explicit fake transport outcomes; do not use wall-clock timing assertions to prove races or timeouts.
- Include cases where a stale response has an older task version, a failed request is superseded, reconciliation already matches desired state, and reconciliation itself fails.
- Preserve regression coverage for completion, recurrence, Projects, Subtasks, blocked state, Day Planning, and Planned Block validation.
- Existing frontend BattlePlanPage and TodayPage tests, Android BattlePlan screen tests, Day ViewModel tests, and Task Detail tests are prior art, but the route/app harnesses are the primary proof for this cross-surface behavior.
- Before closure, run targeted web and Android suites and launch updated web and Android applications from the final working tree, leaving both reviewable as required by AGENTS.md.

## Out of Scope

- HTTP conditional writes, API schema changes, database migrations, or a cross-device conflict-resolution protocol.
- Offline persistence or guaranteed completion after process/browser termination.
- Automatic retries of failed readiness changes.
- A generic optimistic mutation framework for task fields other than Ready to Plan.
- Redefining Ready to Plan, task completion, recurrence, Projects, Subtasks, blocked state, or Planned Block validation.
- Independently persisting Ready to Plan from Android Task Detail before its full draft is saved.

## Further Notes

The coordinator design is recorded in `docs/adr/0004-ready-to-plan-mutation-coordination.md`; the domain term Pending Ready to Plan Change is in `CONTEXT.md`. This supersedes the earlier issue-77 implementation note and is the acceptance basis for closing #77.
