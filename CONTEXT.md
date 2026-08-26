# Timebox

Timebox plans intended work, records actual work, and tracks the tasks that those periods of work may advance.

## Language

**Battle Plan Task**:
A unit of work tracked until no further work remains. It may be linked to planned periods of work, but its completion is independent of whether any one of those periods was completed.
_Avoid_: Time block, scheduled block

**Parent Task**:
A Battle Plan Task that contains first-level Subtasks and represents the complete unit of work. Completing it completes every Subtask, while its own completion remains explicit even when every Subtask is already complete.
_Avoid_: Epic, subtask group

**Subtask**:
A first-level unit of work contained by a Parent Task. A Subtask cannot contain further Subtasks, and its completion does not imply Parent Task completion.
_Avoid_: Child task, nested task

**Quota Tracker**:
A generated progress aggregate for a recurring quota, expressed as completed sessions out of required sessions. Its completion is derived from its Session Tasks rather than set explicitly.
_Avoid_: Parent Task, completable task

**Session Task**:
An individually completable unit of work that contributes to a Quota Tracker.
_Avoid_: Quota Tracker, quota parent

**Blocked**:
A condition indicating that an incomplete Battle Plan Task cannot currently progress. It is not a completion status and is cleared when the task completes.
_Avoid_: Blocked status

**Ready to Plan**:
A queue condition indicating that an incomplete Battle Plan Task is available to receive a Planned Block. Completed tasks are never Ready to Plan.
_Avoid_: Open status, unscheduled task

**Planned Block**:
An allocation of time intended for work. Each Planned Block tracks whether its own allocation was fulfilled, independently of any linked Battle Plan Task.
_Avoid_: Task, appointment

**Actual Block**:
A record of time that occurred. It may fulfill a Planned Block or stand alone without one.
_Avoid_: Completed task

**Time Block Completion**:
Confirmation that an allocated period of work was fulfilled. It does not imply that a linked Battle Plan Task is complete.
_Avoid_: Task completion

**Task Completion**:
Confirmation that no further work remains for a Battle Plan Task. It does not imply that every linked period of planned work was fulfilled.
_Avoid_: Time block completion

**Allocation Progress**:
The number of a Battle Plan Task's existing Planned Blocks that have been fulfilled, expressed against all of its existing Planned Blocks.
_Avoid_: Task progress, time worked
