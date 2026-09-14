# Record Actual from a Planned Block

Issue: [#173](https://github.com/crimsoncaius/timebox/issues/173)

Status: Design agreed and implemented on 2026-09-14. See [implementation verification](../../artifacts/issue-173/README.md).

## Purpose

Let the user record that an activity happened as planned without re-entering its details. Preserve the Planned Block as the record of intent and create a linked Actual Block as the record of elapsed activity. Recording time never completes the Battle Plan Task or starts Activity Tracking.

Scope is one Planned Block at a time. Bulk actualization and automatic reconciliation are outside this design.

## Entry point and interval

Expose the action in saved Planned Block details on web and Android.

| Time when invoked | Action | Result |
| --- | --- | --- |
| Before the planned start | Disabled, with an explanation that recording becomes available after the block starts | No record |
| After the start and before the end | Record Actual until now | Closed Actual from planned start to invocation time |
| At or after the planned end | Record Actual as planned | Closed Actual covering the entire planned interval |

Do not create a zero-duration Actual at the exact start boundary. The interval must contain elapsed time.

Freeze the interval when the user invokes the action. A confirmation viewed at 10:31 for an action invoked at 10:30 still records only through 10:30. Show the exact interval in any replacement preview.

Copy the plan's saved Task Type, optional Battle Plan Task linkage, Block Name, and Note. Retain the Planned Block and link the new Actual to it. An uncontested recording takes effect immediately and offers Undo through Transient Feedback.

## Repeated use

A linked Actual does not disable the action. This supersedes the intermediate discussion decision to prohibit repeat use.

For a 10:00–11:00 plan, recording at 10:30 creates 10:00–10:30. Invoking the action at 11:00 offers to replace that recording with 10:00–11:00 through the overlap confirmation below.

If a linked Actual already matches the requested interval and all copied details, make no mutation. Show “Already recorded” and provide access to that Actual. Different times or copied details permit replacement.

Replacement copies the plan's current saved details; it does not merge notes from previous Actuals. The preview must highlight existing names or notes that would be lost, including user edits to a previous recording.

## Overlap warning and override

Never silently overwrite an Actual. If the requested interval overlaps any Actual, show a warning and concrete preview identifying affected records and the resulting intervals. Offer “Replace overlapping time” as an explicit confirmation, with an option to cancel without changes.

On confirmation, replace only the portions inside the requested interval:

- Trim an overlapping edge while preserving the portion outside the interval.
- Split an Actual spanning both boundaries into its two surviving portions.
- Remove an Actual entirely contained in the replacement interval.
- Preserve original details and Planned Block links on every surviving portion.
- Create the new Actual with the source plan's saved details and link.

For example, recording 10:00–11:00 over an existing 09:45–10:15 Actual preserves 09:45–10:00 and creates the new 10:00–11:00 Actual.

Preview and replacement must account for every overlapping Actual, whether linked to this plan, linked to a different plan, or standalone.

## Activity Tracking

Allow recording while Activity Tracking runs. If the requested interval ends before the Current Activity began, leave tracking unchanged.

If the interval overlaps the running Actual, include the tracking effect in the replacement preview. Replace only the elapsed overlap. Preserve recorded time outside the interval and keep the same Current Activity running from the replacement interval's end. Do not stop tracking or switch its activity details to those of the plan.

The frozen end time also applies here: elapsed time after that boundary belongs to the continuing Current Activity, including time spent reviewing the preview.

## Subsequent changes and recognition

Show “Actual recorded” with access to linked Actuals. This communicates that a recording exists without claiming the records still exactly match after edits. Do not use Task Completion language for this action.

The two records remain independent:

- Editing time, Block Name, or Note does not propagate and preserves the link.
- Changing Task Type or Battle Plan Task linkage detaches the affected link.
- Deleting the Planned Block preserves its Actuals and detaches their links.
- Deleting an Actual preserves the Planned Block. Recording is available again, subject to the same time and overlap rules.

## Undo and concurrent changes

Undo is one atomic reversal of the entire operation. For an uncontested creation, it removes only the created Actual. For replacement, it restores the previous trimmed, split, or removed records and tracking continuity together.

Subsequent edits or tracking changes that make restoration unsafe invalidate Undo. Explain that Undo is unavailable and allow ordinary record editing or deletion. Normal passage of time while tracking continues does not invalidate Undo.

If relevant records change while a replacement preview is open, refresh the preview and require confirmation again. Never apply consent to an older preview to newly changed records. Apply replacement atomically so a conflict or failure cannot leave a partial modification.

## Implementation scope

The original code had a web action and backend creation/Undo path, but no corresponding Android action. It rejected any linked Actual and lacked the agreed time eligibility and replacement rules. The implementation covers the following work:

- Update web availability, wording, exact-match feedback, and replacement preview.
- Add the equivalent interaction to Android Planned Block details.
- Implement frozen interval evaluation, repeated recording, overlap preview, and atomic replacement with stale-preview protection.
- Integrate the operation with shared Activity Tracking state and reconciliation; do not assume the legacy creation endpoint already handles it.
- Extend Undo to restore all affected records and preserve continuing tracking safely.
- Retain existing independent-edit and link-detachment semantics.

## Acceptance scenarios

1. A finished, uncontested plan records immediately with all saved details, a retained plan, a link, and Undo; task completion is unchanged.
2. An underway plan records only through invocation time; future and zero-duration recordings are unavailable.
3. Repeating an earlier partial recording offers replacement through the later endpoint; an exact match produces no duplicate.
4. Replacement previews and correctly handles edge overlap, full containment, spanning overlap, and multiple conflicting Actuals without losing outside portions or their links.
5. Replacement warns when previous Actual names or notes would be lost.
6. Tracking continues unchanged when disjoint and preserves the Current Activity and outside time when overlapping.
7. Delaying confirmation does not extend the requested interval. Concurrent edits require a refreshed preview and new confirmation.
8. Undo restores the complete operation atomically; unsafe later changes prevent Undo, but ordinary ongoing elapsed time does not.
9. Independent edits and deletion follow the lifecycle rules above, and linked-record feedback does not imply Task Completion or continued exact agreement.
10. Web and Android expose equivalent behavior. Implementation verification includes launching the affected applications from the updated working tree for review.
