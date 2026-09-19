# Timebox

Timebox plans intended activities, records time spent on work and non-work activities, and tracks the tasks that those activities may advance.

## Language

**Project**:
A named, finite group of related Battle Plan Tasks that advances a specific outcome. Deadlines belong to individual Battle Plan Tasks.
_Avoid_: Parent Task, Task Type

**Project Color**:
The single dedicated visual color that identifies Projects throughout the Android application. It is distinct from the yellow accent used for other UI meanings.
_Avoid_: Per-Project Color, Project Accent Palette

**Project Deletion**:
The destructive removal of a Project and every Battle Plan Task assigned to it. It requires explicit confirmation.
_Avoid_: Unassigning a Project, archiving a Project

**Battle Plan Task**:
An independently meaningful unit of work that can be scheduled and completed in its own right. It may contain Subtasks and have zero or more Planned Blocks and Actual Blocks.
_Avoid_: Time block, scheduled block

**Task Description**:
Optional freeform context attached to one Battle Plan Task or Session Task. It persists across every Planned Block and Actual Block linked to that Task.
_Avoid_: Actual Block Note, journal entry

**Parent Task**:
A Battle Plan Task whose execution is decomposed into first-level Subtasks. Its completion remains explicit and resolves its Subtasks without changing their checked states.
_Avoid_: Epic, subtask group

**Subtask**:
A non-exhaustive, first-level execution checkpoint within a Parent Task. It is not independently schedulable, and its checked state does not determine Parent Task completion. It has a title only and no Task Description; its title can be renamed while its Parent Task is incomplete, whether or not it is checked.
_Avoid_: Child task, nested task

**Task Type**:
The reusable category of work. Every Planned Block and Actual Block has one; a Battle Plan Task or Recurring Task Series may have one. `unspecified` is the fixed, non-renamable and undeletable neutral category when a Block is unclassified.
_Avoid_: Task, tag, Block Name

**Unset**:
The absence of a Task Type on a Battle Plan Task or Recurring Task Series. It is not the `unspecified` Task Type.
_Avoid_: unspecified, empty type

**Task Type Path**:
The hierarchical name of a Task Type, as ancestor segments then a leaf. Each prefix is itself a Task Type.
_Avoid_: tag, folder, category tree

**Task Type Rename**:
A change to an existing Task Type's name that preserves its identity and associated work, including historical Planned Blocks and Actual Blocks, which display the new name. Renaming a Task Type Path also renames its descendants and may move the branch under another parent.
_Avoid_: Reclassification, Task Type replacement

**Block Name**:
An optional user-defined identity for one Planned Block or Actual Block, distinct from its reusable Task Type and supporting Note. It remains the Block's own identity regardless of Battle Plan Task linkage.
_Avoid_: Label, title, tag, Task Type

**Supporting Note**:
Optional freeform context attached to one Planned Block or Actual Block. It does not become part of a linked Task's Description.
_Avoid_: Task Description, journal entry

**Transient Feedback**:
A short-lived, in-application presentation that communicates feedback or offers a follow-up action. It includes general feedback, Trash undo, Task Completion undo, and in-app Task Reminders; it excludes validation, persistent errors, loading states, and native OS notifications.
_Avoid_: Toast, notification, banner

**Transient Feedback Variant**:
The semantic form of Transient Feedback: general feedback, destructive undo, recoverable failure, or navigational feedback. Variants share a visual family while retaining their own actions, urgency, lifetime, and dismissal behavior.
_Avoid_: One-size-fits-all toast

**Work Mode**:
The legacy execution surface that combines plan-following time recording with an immersive view. Its successor separates Activity Tracking from Focus Mode.
_Avoid_: Task detail, Task status, timer mode

**Activity Tracking**:
Continuous recording of time in Actual Blocks while enabled, independently of Focus Mode or a Planned Block. Starting takes its Task Type from the Planned Block covering the current instant, or otherwise from an explicit choice; it never defaults to `unspecified`. Switching activities continues the record; stopping tracking leaves subsequent time unrecorded.
_Avoid_: Work Mode, Focus session

**Offline**:
A condition in which Activity Tracking cannot reach the server. Local recording can continue.
_Avoid_: disconnected, airplane mode

**Unsynced**:
A condition in which local Activity Tracking changes have not been confirmed by the server.
_Avoid_: pending, dirty, Change not confirmed

**Synced**:
A condition in which Activity Tracking has a confirmed snapshot and no unconfirmed local changes.
_Avoid_: All caught up, sync complete

**Current Activity**:
The work or non-work activity represented by the running Actual Block, using its Block Name, Task Type, and optional Battle Plan Task linkage. It may be unnamed and unclassified.
_Avoid_: Current Task, Activity catalog

**Reporting Time Zone**:
The shared time zone used to divide recorded activity between calendar days on all devices. Changing it changes daily attribution without changing elapsed time.
_Avoid_: Device time zone

**Today**:
The calendar date in the Reporting Time Zone that contains the current instant.
_Avoid_: Device today, local today

**Now Line**:
The Day surface marker for the current instant in the Reporting Time Zone. It appears only on Today, and only while that instant falls inside the hours shown that day.
_Avoid_: Playhead, now indicator, current time line, Day view

**Inactivity Prompt**:
A persistent question about whether the Current Activity continues, triggered by available device-inactivity signals. It leaves Activity Tracking running without requiring an answer. Its web presentation is inline; its Android presentation is modal and dismissible to Check-in waiting.
_Avoid_: Periodic check-in, Transient Feedback

**Focus Mode**:
An optional device-local immersive surface for the shared Current Activity, intended to stay visible while the user works with minimal interaction. It is separate from Activity Tracking and has a deliberate, always-available exit that leaves tracking running.
_Avoid_: Work Mode, Tracking mode

**Day Planning**:
The daily activity of allocating Planned Blocks to intended activities. It is performed on the Day surface and can coexist with Activity Tracking.
_Avoid_: Plan Mode, scheduling reminder

**Day Review**:
The daily activity of reflecting on recorded and planned work from the Day surface. It is a prompt, not a separate application surface.
_Avoid_: Day Review screen, report

**Daily Reminder**:
An optional, device-local notification scheduled for a chosen local time to prompt Day Planning or Day Review. It creates no task, recurrence, work record, or overdue state; a missed prompt is skipped.
_Avoid_: Recurring Task Series, Task Reminder, notification task, Planned Block Reminder

**Task Reminder**:
An optional, one-time nudge at a user-chosen moment for an incomplete Battle Plan Task or Session Task. A Task has at most one. It is independent of the Task's deadline: deadline changes and overdue state neither move, re-arm, nor suppress it. Task Completion clears it.
_Avoid_: Deadline reminder, notification, Daily Reminder, Planned Block Reminder

**Planned Block Reminder**:
An optional, device-local notification a chosen lead time before a Planned Block starts, offering to adopt that Planned Block. It creates no work record; a missed reminder is skipped, and a delivered one is withdrawn once the Planned Block is adopted, ends, moves, or is deleted.
_Avoid_: Daily Reminder, Task Reminder, block alarm

**Recurring Task Series**:
A recurrence rule and template that produces Task Occurrences. It represents an ongoing routine and does not belong to a Project.
_Avoid_: Recurring template, recurring parent task

**Recurring Pre-planning Schedule**:
An optional configuration on a scheduled Recurring Task Series that allocates its eligible Task Occurrences to specific Planned Block slots. A slot that cannot be materialized because it is occupied creates no Planned Block; its Task Occurrence remains available for an explicit Ready to Plan choice.
_Avoid_: Automatic scheduling, recurring time block

**Task Occurrence**:
A Battle Plan Task representing one instance of a Recurring Task Series, with its own completion, Subtask state, Planned Blocks, and Actual Blocks. It does not belong to a Project.
_Avoid_: Recurring series, Quota Tracker

**Skipped Task Occurrence**:
A Task Occurrence whose recurrence period ended without a recorded Task Completion. It is historical rather than current work and is not created by a manual skip action.
_Avoid_: Deleted occurrence, missed occurrence

**Quota Tracker**:
A generated progress aggregate for a recurring quota, expressed as completed Session Tasks out of required Session Tasks. It is derived rather than explicitly completed and does not belong to a Project.
_Avoid_: Recurring Task Series, Parent Task, completable task

**Session Task**:
An individually completable unit of work that contributes to a Quota Tracker. It does not belong to a Project.
_Avoid_: Actual Block, Task Occurrence, work session

**Blocked**:
A condition indicating that an incomplete Battle Plan Task cannot currently progress. It is not a completion status and is cleared when the task completes.
_Avoid_: Blocked status

**Ready to Plan**:
A queue condition indicating that an incomplete Battle Plan Task is available to receive a Planned Block. It is explicitly chosen for every Battle Plan Task, including a Task Occurrence; recurrence generation alone never adds it, and completed Tasks are never Ready to Plan.
_Avoid_: Open status, unscheduled task

**Pending Ready to Plan Change**:
The latest, not-yet-confirmed choice to add or remove a Battle Plan Task from Ready to Plan. It is visible wherever readiness is shown, but a pending addition is not schedulable.
_Avoid_: Saved Ready to Plan state, disabled task

**Planned Block**:
An allocation of time intended for one primary item. It records the plan, not whether work occurred.
_Avoid_: Planned timebox, Task, appointment

**Actual Block**:
A record of time that occurred. It may link to a Planned Block or stand alone and is authoritative for actual time.
_Avoid_: Actual session, work session, completed block

**Task Completion**:
An explicit statement that no work remains for a Battle Plan Task. It is independent of recording or ending an Actual Block and is not inferred from Subtask checks.
_Avoid_: Time completion, session completion

### Surfaces

**Day**:
The surface for one calendar date's Planned Blocks and Actual Blocks. Day Planning, Day Review and Work Mode start from it.
_Avoid_: Today view, timeline page

**Chronicle**:
The retrospective surface: what already happened, whether recorded time or completed work. It has two views, Calendar (one past day at a time) and Trends.
_Avoid_: History, Analytics tab

**Trends**:
The Chronicle view that presents activity by Task Type over a selected preset or custom time frame. It describes activity without judging it against goals.
_Avoid_: Analytics, Insights, Reports

**Battle Plan**:
The surface for Battle Plan Tasks, Projects and Recurring Task Series, and the home of Task Types management.
_Avoid_: Board, backlog

**Task Types**:
The management page for renaming, reparenting and deleting Task Types. It is reached from Battle Plan and is not a primary navigation destination.
_Avoid_: Types, Task types page

**Assistant**:
The AI assistant's surface and the home of its conversations.
_Avoid_: Chat, AI tab
