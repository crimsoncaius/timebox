# Issue 77: Immediate Ready to Plan state

Source: https://github.com/crimsoncaius/timebox/issues/77

## Problem Statement

Changing a Battle Plan Task's Ready to Plan condition waits for the server before the
interface changes. The delay makes a simple planning decision feel unresponsive, and
leaves the Battle Plan and Day surfaces temporarily inconsistent with the user's latest
intent.

## Solution

Web and Android will show the latest Ready to Plan intent immediately and maintain that
pending state consistently in their Battle Plan and Day surfaces while persistence occurs.
Each client will coordinate mutations per Battle Plan Task so a late response cannot
restore an obsolete value. A pending addition appears in the Day Ready to Plan queue, but
cannot be selected, dragged, or scheduled until the server has confirmed it. If the latest
intent cannot be saved, the client will reconcile with the saved task state, explain that
the change was not saved, and offer a retry of that latest intent.

## User Stories

1. As a planner using the web Battle Plan, I want a card's Ready to Plan action to respond immediately, so that I can quickly prepare work for Day Planning.
2. As a planner using web task details, I want the Ready to Plan control to update immediately, so that it agrees with the Battle Plan card for the same Battle Plan Task.
3. As a planner using Android Battle Plan, I want the direct Ready to Plan action to respond immediately, so that the mobile interface feels as responsive as the web interface.
4. As a planner, I want a pending Ready to Plan change reflected in every relevant Battle Plan list and filter, so that I do not see contradictory task state within a client.
5. As a planner, I want a newly added pending Battle Plan Task to appear in the Day Ready to Plan queue, so that the queue reflects my current intent immediately.
6. As a planner, I want a newly added pending task to be visibly unavailable for scheduling, so that I cannot create a Planned Block from a state the server has not saved.
7. As a planner, I want to change my mind rapidly, so that my newest Ready to Plan choice remains visible and is the one the client persists.
8. As a planner, I want late responses from earlier taps to be ignored when I have made a newer choice, so that stale network activity cannot reverse my decision.
9. As a planner, I want a failed latest change to restore the saved state and explain that it was not saved, so that I do not mistake optimistic UI for durable data.
10. As a planner, I want to retry a failed latest change, so that a transient connection or server failure does not require me to rediscover the task and repeat the workflow.
11. As a planner who changed my mind before an earlier request failed, I do not want an error for that obsolete request when my latest intent already matches saved state, so that the application does not create misleading alarms.
12. As a planner editing an Android Task Detail draft, I want Ready to Plan to remain part of that explicit draft, so that changing it does not unexpectedly save only one field while other edits remain unsaved.
13. As a planner using more than one device, I want existing server behavior to remain predictable, so that this responsiveness improvement does not silently introduce a new cross-device conflict protocol.

## Implementation Decisions

- Use the glossary term **Ready to Plan**. It remains a queue condition on an incomplete Battle Plan Task; this work does not alter task-state transition rules.
- Cover web and Android. On web, apply the behavior to both Battle Plan card and task-detail controls. On Android, apply it to the direct Battle Plan list action; the Task Detail editor continues to hold Ready to Plan in its normal draft until the user saves the complete draft.
- Introduce one client-level, per-Battle-Plan-Task readiness mutation coordinator in each client. It owns confirmed state, latest intended state, pending status, request sequencing, reconciliation, and retry data. Views project its state rather than keeping independent optimistic copies.
- Apply the latest intent to the local task projection synchronously. The coordinator persists changes in an order that ensures older requests or responses cannot overwrite a newer local intent. Only the newest intent may determine final displayed state, error state, and retry availability.
- Project pending state into Battle Plan cards, task details, task filtering, and the Day Ready to Plan queue. Do not rely solely on a page-local control state.
- A pending transition from not Ready to Plan to Ready to Plan appears in the Day queue with saving/pending presentation. It must be disabled for selection, drag-and-drop, and Planned Block creation until confirmed. A pending removal is immediately absent from queue interactions.
- On successful persistence, retain the already displayed intended state without flicker, clear pending state, and reconcile task data without allowing a stale reload to replace newer pending intent.
- On failure of the latest intent, fetch or otherwise reconcile the saved task state, remove pending state, restore that saved state throughout the client, and expose a clear error with Retry. Retry attempts the latest failed intent against the reconciled saved state.
- A failed request superseded by a newer intent is not user-visible if reconciliation shows the newest intent is already saved. Do not roll back or report an obsolete failure.
- Do not add HTTP conditional writes, API schema fields, database migrations, or a server conflict protocol. Existing cross-device last-write-wins behavior remains outside this issue.
- Preserve existing behavior for recurrence, Projects, Subtasks, completion, blocked state, and Planned Block transitions. Server validation remains authoritative.

## Testing Decisions

- Test externally observable state: what a planner sees, can interact with, and can recover from. Do not assert coordinator internals, request counters, or implementation-specific state containers.
- At the web Battle Plan page seam, test immediate state for both card and detail actions; consistent filtering/list display; latest-intent-wins under delayed and out-of-order responses; successful settlement without flicker; latest-failure reconciliation and Retry; and suppression of obsolete-failure errors.
- At the Android Battle Plan ViewModel/UI seam, test immediate direct-list state; latest-intent-wins behavior; reconciliation, messaging, and Retry after a latest failure; and preservation of the separate Task Detail draft workflow.
- At each Day Ready to Plan queue seam, test that a pending addition is visible but cannot be selected, dragged, or scheduled; that confirmation enables scheduling; and that failure removes it and presents the retry path.
- Include regression coverage for task lifecycle rules: completed Battle Plan Tasks remain ineligible and normal server rejection remains authoritative.
- Follow existing frontend Battle Plan page tests, Android Battle Plan UI logic/ViewModel tests, and Android Day Ready to Plan tests as prior art. Use controllable transport responses to prove user-visible race and failure outcomes.

## Out of Scope

- A cross-device conflict-resolution or conditional-write protocol.
- Backend/API/database changes for task version comparison.
- Optimistic mutation infrastructure for task fields other than Ready to Plan.
- Changing the meaning of Ready to Plan, recurrence rules, Projects, Subtasks, completion, blocked state, or Planned Block validation.
- Independently persisting Ready to Plan from Android's multi-field Task Detail draft.

## Further Notes

All product decisions were resolved through a design review and confirmed by the requester.
After implementation, launch the updated web and Android applications from the updated
working tree and leave them in a reviewable state, as required by `AGENTS.md`.
