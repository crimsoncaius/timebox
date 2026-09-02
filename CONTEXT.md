# Timebox

Timebox plans intended work, records actual work, and tracks the tasks that those periods of work may advance.

## Language

**Battle Plan Task**:
An independently meaningful unit of work that can be scheduled and completed in its own right. It may contain Subtasks and have zero or more Planned Blocks and Actual Blocks.
_Avoid_: Time block, scheduled block

**Parent Task**:
A Battle Plan Task whose execution is decomposed into first-level Subtasks. Its completion remains explicit and resolves its Subtasks without changing their checked states.
_Avoid_: Epic, subtask group

**Subtask**:
A non-exhaustive, first-level execution checkpoint within a Parent Task. It is not independently schedulable, and its checked state does not determine Parent Task completion.
_Avoid_: Child task, nested task

**Task Type**:
The category of work represented by a Planned Block or Actual Block. It remains required when no Battle Plan Task is linked and serves as the primary label for that taskless Block.
_Avoid_: Task, tag

**Work Mode**:
A present-tense execution surface that follows the current time and surfaces the active or next Planned Block for today. It opens independently of any Battle Plan Task entry point.
_Avoid_: Task detail, Task status, timer mode

**Recurring Task Series**:
A recurrence rule and template that produces Task Occurrences. It remains independent of the completion of any one occurrence.
_Avoid_: Recurring template, recurring parent task

**Task Occurrence**:
A Battle Plan Task representing one instance of a Recurring Task Series, with its own completion, Subtask state, Planned Blocks, and Actual Blocks.
_Avoid_: Recurring series, Quota Tracker

**Skipped Task Occurrence**:
A Task Occurrence whose recurrence period ended without a recorded Task Completion. It is historical rather than current work and is not created by a manual skip action.
_Avoid_: Deleted occurrence, missed occurrence

**Quota Tracker**:
A generated progress aggregate for a recurring quota, expressed as completed Session Tasks out of required Session Tasks. It is derived rather than explicitly completed.
_Avoid_: Recurring Task Series, Parent Task, completable task

**Session Task**:
An individually completable unit of work that contributes to a Quota Tracker.
_Avoid_: Actual Block, Task Occurrence, work session

**Blocked**:
A condition indicating that an incomplete Battle Plan Task cannot currently progress. It is not a completion status and is cleared when the task completes.
_Avoid_: Blocked status

**Ready to Plan**:
A queue condition indicating that an incomplete Battle Plan Task is available to receive a Planned Block. Completed tasks are never Ready to Plan.
_Avoid_: Open status, unscheduled task

**Planned Block**:
An allocation of time intended for one primary item. It records the plan, not whether work occurred.
_Avoid_: Planned timebox, Task, appointment

**Actual Block**:
A record of time that occurred. It may link to a Planned Block or stand alone and is authoritative for actual time.
_Avoid_: Actual session, work session, completed block

**Task Completion**:
An explicit statement that no work remains for a Battle Plan Task. It is independent of recording or ending an Actual Block and is not inferred from Subtask checks.
_Avoid_: Time completion, session completion
