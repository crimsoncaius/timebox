# Task + Timeboxing Product Specification

## 1. Purpose

This document specifies the product semantics and core interaction model for a to-do application combined with a timeboxing system.

The application supports:

- Tasks
- Subtasks
- Planned timeboxes
- Actual time tracking
- Work Mode
- Non-task scheduled items such as events, appointments, meetings, dinners, gym sessions, commutes, etc.
- Recurring tasks
- Multiple timeboxes for a single task
- Live and retrospective time tracking

The central design goal is to keep the user-facing workflow low-friction while preserving clean internal semantics.

A major product principle throughout this specification is:

> **Couple the interaction when it reduces cognitive load; decouple the underlying state when the concepts are semantically different.**

In particular, the product may automate related records while preserving the semantic distinction between planned work, Actual time, and Task Completion.

---

# 2. Core Product Model

The product distinguishes among four major concepts:

1. **Task**
2. **Subtask**
3. **Planned timebox**
4. **Actual work session**

There are also **non-task scheduled items** that can appear on the timeline.

These concepts must not be collapsed into a single `completed` concept.

---

# 3. Task

A task is a first-class unit of work.

A task represents an independently meaningful outcome or commitment.

Examples:

- Write proposal
- Prepare investor presentation
- Submit expense report
- Buy groceries
- Finish landing page

A task can:

- Exist without any timebox
- Have one timebox
- Have multiple timeboxes
- Be completed independently of any individual timebox
- Contain subtasks
- Be recurring
- Be reopened after completion

A task is the unit that is independently schedulable.

## Product invariant

> **If something needs independent scheduling or independent completion, it should be a task.**

This rule should be used to guide future product decisions.

---

# 4. Subtask

A subtask is **not** a first-class independently schedulable work item.

Subtasks are lightweight execution checkpoints inside a parent task.

Examples:

Task:

> Prepare investor presentation

Subtasks:

- Pull revenue numbers
- Make charts
- Fix title slide
- Add Q3 numbers
- Proofread

Subtasks are intended to help the user execute the parent task while in Work Mode.

## Important semantic rule

> **Subtasks are a non-exhaustive working checklist.**

They do not necessarily represent every action required to complete the parent task.

Therefore:

- Completing all subtasks must **not** automatically complete the parent task.
- The parent task may still require additional work not represented by subtasks.
- The parent task has its own explicit completion decision.

## Subtasks are not independently schedulable

The user should **not** be able to put an individual subtask directly onto the timeline.

For example:

Task:

> Prepare presentation

Subtask:

> Make charts

`Make charts` should not have its own independent timebox while it remains a subtask.

If something deserves independent scheduling, it should instead be created as a separate task.

## No "Promote subtask to task" feature for now

A possible feature considered was:

> Convert subtask to task

This was intentionally rejected for the current design.

Reasons:

- It introduces linking questions.
- It creates ambiguity around whether the new task remains linked to the original parent.
- It raises questions about completion propagation.
- It raises questions about historical timeboxes.
- It raises questions about whether the original subtask remains.
- It raises questions about reversibility / demotion.
- The effort-to-impact ratio is not currently worthwhile.

If a user discovers that a subtask should really be independently scheduled, they can manually create a new task.

This is acceptable because that case is less common than the primary workflow and keeps the product model simpler.

---

# 5. Work Mode

The application should have a dedicated **Work Mode** for present-tense execution. Work Mode follows the current time and today's Planned Blocks; it is not owned by a Battle Plan Task or entered through a Planned Block.

Web and Android should expose a persistent app-level **Start Work Mode** action. While Work Mode is active, that affordance becomes **Return to Work Mode**. The Planned Block inspector should remain a plan-editing surface and should not own a Work Mode start action.

Starting Work Mode always means:

> **I am working now.**

It cannot be started retrospectively or scheduled to begin later.

## 5.1 Entry guard

Work Mode opens directly when either:

- A Planned Block is active now, or
- Today's next Planned Block begins within ten minutes, including exactly ten minutes

If neither condition is true, entry remains allowed but the application warns that there is no planned work for the immediate future. The warning offers:

- **Plan something first** — open Plan Mode at the current time
- **Continue** — enter Work Mode without changing the plan

Finishing the planning flow automatically enters Work Mode only when the newly placed Planned Block is active or begins within ten minutes. Work Mode's entry time is the time planning finishes, not the time the original start action was pressed.

When no Planned Block is active, Work Mode shows today's next Planned Block as **Up next**. If today has no later Planned Block, it shows **No more planned work today**. Work Mode does not use tomorrow's plan as today's next work.

## 5.2 Active presentation

When a task-backed Planned Block is active, Work Mode exposes:

- The Battle Plan Task title
- Its Task Type
- Its notes
- Its Subtasks
- Controls to check and uncheck Subtasks

A taskless Planned Block participates normally and shows its Task Type as the primary label with its optional note as detail. **Up next** uses a compact title, time, and countdown presentation.

Work Mode assumes the active Planned Block is being performed. It does not provide **Skip this block**. If that assumption is wrong, the user can explicitly exit Work Mode. Work Mode also does not provide a combined Task Completion action in the current scope.

Example:

> **Prepare investor presentation**  
> 2:00–3:30 PM  
>
> Progress: 2 / 4
>
> - ✓ Get numbers
> - ✓ Make charts
> - ○ Write conclusion
> - ○ Proofread

The primary purposes of Work Mode are:

- Surface the work planned for the current time
- Show what is up next today
- Let the user tick Subtasks during execution
- Derive Actual Blocks from sustained work against active Planned Blocks
- Let the user explicitly exit present-tense work

## 5.3 Local lifecycle

Entering Work Mode initially creates client-side presentation state only. The backend does not need a Work Mode record or start event.

The local state includes the Work Mode entry time and survives ordinary navigation, backgrounding, refresh, and accidental application restart on the same device. Navigation away from the screen does not end Work Mode. Only **Exit Work Mode** clears that state and ends any active Actual Block.

If the application was absent for more than ten minutes, restoring Work Mode asks whether the user was still working:

- Confirming backfills Actual Blocks for applicable Planned Blocks
- Declining ends recorded work at the last confirmed point

Before an Actual Block exists, Work Mode state is device-local. Once an Actual Block exists, another device can detect it and offer **Return to Work Mode**. If an active Actual Block already exists when the user starts Work Mode, the application returns to Work Mode around that record instead of creating a conflicting Actual Block.

At midnight, Work Mode remains open and begins evaluating the new current day. If the final Planned Block for a day ends without another current block, Work Mode stays open in its no-work state until explicitly exited.

Changes to Planned Blocks take effect immediately for future assumptions in Work Mode. Already-recorded Actual time remains unchanged.

The UI does not need to be a literal workflow diagram unless future requirements justify that complexity.

A compact expanded panel or dedicated working screen is sufficient.

---

# 6. Planned Time vs Actual Time

Planned time and actual time are **separate records**.

This is a foundational design decision.

Example:

Planned:

> 10:00–11:00 — Write proposal

Actual:

> 10:12–11:18 — Write proposal

The actual record must not overwrite the planned record.

The system should be able to preserve both:

- What the user intended
- What actually happened

This enables future analytics such as:

- Planned duration
- Actual duration
- Started 12 minutes late
- Ended 18 minutes late
- Planned vs actual variance
- Total actual time spent on a task
- Number of sessions used to complete a task

## Conceptual model

```text
Task
  ├── Planned timebox A
  ├── Planned timebox B
  ├── Actual session A
  ├── Actual session B
  └── Completion state
```

The exact storage schema can differ, but these are semantically separate facts.

---

# 7. One Task Can Have Multiple Timeboxes

A task can be worked on across multiple sessions.

Example:

> Write proposal

- Monday 10:00–11:00
- Tuesday 14:00–15:00
- Wednesday 09:00–09:30

The first session can end without completing the task.

The second can also end without completing the task.

The third can end and also complete the task.

Therefore:

> **A timebox represents one planned session of work, not the task itself.**

Relationship:

```text
Task 1 ----> 0..N PlannedTimeboxes
Task 1 ----> 0..N ActualSessions
```

---

# 8. Actual Time Tracking Is Hybrid

Actual time can be recorded in two ways:

## 8.1 Live tracking

Live tracking is derived from Work Mode and the current Planned Block.

Example:

The user enters Work Mode at 10:12 during a Planned Block that began at 10:00. Entry creates local Work Mode state but no backend record.

After the Planned Block has been current during Work Mode for one uninterrupted minute, the application creates an Actual Block retroactively:

Internally:

```text
actual_start = max(work_mode_entry, planned_block_start)
             = 10:12
```

If Work Mode opens at 9:55 for a Planned Block beginning at 10:00, the block remains **Up next** and assumed work begins at 10:00. Its one-minute confirmation period begins only when the Planned Block becomes current.

Exiting before confirmation creates no Actual Block. After confirmation, partial Actual time is valid and remains independent of Task Completion.

At a Planned Block boundary:

- End the current Actual Block at the boundary
- Record nothing during a schedule gap
- Begin a fresh one-minute confirmation when the next Planned Block becomes current
- After confirmation, create that next Actual Block retroactively from its boundary or the later Work Mode entry

Taskless Planned Blocks follow the same behavior using their Task Type and note. Work Mode never records Actual time when no Planned Block is current.

When the user selects **Exit Work Mode**, the application ends any active Actual Block at the current time and clears the local Work Mode state. Exiting Work Mode does not complete its Battle Plan Task.

Internally:

```text
actual_end = work_mode_exit
task_completion = unchanged
```

## 8.2 Retrospective editing

The user can also enter or correct actual time after the fact.

Examples:

- Forgot to press Start
- Forgot to press Finish
- Started late
- Ended early
- Was interrupted
- Reconstructed the day later
- Wants to correct inaccurate tracking

The actual timeline should be editable.

## Source-of-truth principle

> **Start/Finish helps construct the actual timeline; the actual timeline is the source of truth.**

The timer is a convenience mechanism, not an immutable authority.

This avoids bad analytics when the user forgets to stop a timer.

---

# 9. Unresolved Planned Blocks / Skipped State

For now, the system does **not** need an explicit state for:

- Missed
- Skipped
- Forgotten
- Unresolved

Example:

Plan:

> 10:00–11:00 — Write proposal

Actual:

> No record

This can simply remain as a planned block with no corresponding actual session.

The product should not currently force the user to classify why no actual work occurred.

This can be revisited later when daily retrospective and reporting features are built.

Potential future retrospective questions could include:

- Did this happen later?
- Was it skipped?
- Was it rescheduled?
- Did you forget to track it?

But none of this is required for the current core implementation.

---

# 10. Exiting Work Mode vs Completing a Task

These are separate actions and remain separate in the current Work Mode scope.

## Exit Work Mode

Meaning:

- Clear the local Work Mode state
- End any active Actual Block
- Record its Actual end time
- Leave the Battle Plan Task open

This expresses:

> "I am done working for now, but the task itself is not finished."

## Complete Task

Task Completion remains an explicit global domain action available outside the redesigned Work Mode. Work Mode does not currently provide an action that both ends the Actual Block and completes the Battle Plan Task, or special handling for completing the active Task early.

This keeps the Work Mode lifecycle tied to present-tense execution without collapsing it into Task Completion.

Internally:

```text
Exit Work Mode
  work_mode.local_state = cleared
  active_actual_block.end = now, if one exists
  task.completion = unchanged
```

---

# 11. Task Completion Is Explicit

The parent task must **never** auto-complete merely because every subtask is checked.

Example:

Task:

> Prepare presentation

Subtasks:

- ✓ Pull numbers
- ✓ Make charts
- ✓ Proofread

The parent may still require additional work that was never represented as a subtask.

Therefore:

> **All subtasks complete ≠ parent task complete**

The parent task requires an explicit completion action by the user.

Examples:

- Complete task

---

# 12. Parent Completion Resolves Remaining Subtasks

When the user explicitly completes a parent task, they should not be forced to manually check every remaining subtask.

Example:

Task:

> Prepare presentation

Subtasks:

- ✓ Add charts
- ○ Proofread
- ○ Add citations

The user may have completed the real-world task without interacting with every checkbox.

If the user explicitly chooses:

> Complete task

then the task is complete and its remaining subtasks are considered **resolved**.

## Important behavior

The product should **not** warn the user merely because some subtasks remain unchecked.

This was a considered design alternative and was rejected.

Reason:

The parent completion action is a stronger user statement than the checklist state.

If the user says:

> "This task is complete"

the application should trust that statement.

A warning like:

> "2 subtasks are still incomplete. Are you sure?"

would create unnecessary bookkeeping and contradict the intended lightweight semantics of subtasks.

---

# 13. Preserve Explicit Subtask Check State Internally

When a parent task is completed, unresolved subtasks do not necessarily need to be mutated to `checked = true`.

Instead, preserve the actual historical check state.

Example before completion:

```text
Add charts      checked = true
Proofread       checked = false
Add citations   checked = false
Parent          completed = false
```

After explicit parent completion:

```text
Add charts      checked = true
Proofread       checked = false
Add citations   checked = false
Parent          completed = true
```

Because the parent is complete, all subtasks are effectively closed / resolved even if some remain unchecked internally.

Why preserve this distinction?

Because the task may later be reopened.

---

# 14. Reopening a Completed Task

If a completed task is reopened:

- The parent becomes active again.
- Previously checked subtasks remain checked.
- Previously unchecked subtasks become actionable again and remain unchecked.

Example:

Before original completion:

- ✓ Add charts
- ○ Proofread
- ○ Add citations

After completion, the task is frozen.

If reopened:

- ✓ Add charts
- ○ Proofread
- ○ Add citations

This is considered a relatively low-consequence decision, but it is the cleanest behavior and preserves the user's prior work state.

---

# 15. Completed Task View

A completed task is effectively **frozen**.

The default completed-task view should be read-only.

If the user opens a completed task, they may still see historical subtask states.

Example:

> **Prepare presentation — Completed**
>
> - ✓ Add charts
> - ✓ Fix title slide
> - ○ Check appendix

The unchecked item does **not** mean the user still owes the work.

It is simply historical information about what was or was not explicitly checked before the parent was completed.

If the user wants to make the task actionable again, they should use:

> Reopen task

Then the previous subtask states become active again.

## Product rule

> **Completion freezes the task. Reopening makes it actionable again.**

---

# 16. Task Completion Has the Same Semantics Everywhere

Completing a task should behave consistently regardless of where the action originates.

Possible locations:

- Task list
- Search
- Task detail screen
- Any future completion surface

Task completion always means:

- Mark task complete
- Resolve subtasks
- Remove future planned timeboxes for that task occurrence
- Preserve historical plan and actual data

## Rule

> **Task completion is a global domain action with the same meaning wherever it is offered.**

Do not create different definitions of "complete" depending on the screen.
The redesigned Work Mode is not currently a Task Completion surface.

---

# 17. Future Planned Timeboxes After Task Completion

A task can have multiple future planned timeboxes.

Example:

Task:

> Write proposal

Planned:

- Today 10:00–11:00
- Tomorrow 14:00–15:00
- Thursday 10:00–11:00

If the user unexpectedly finishes the task today, the future timeboxes become stale.

## Chosen behavior

> **Completing a task automatically removes its future planned timeboxes.**

The application should not require a confirmation dialog every time.

Instead, use an automatic action with Undo.

Example feedback:

> Task completed · 2 future timeboxes removed  
> Undo

## Why

Task completion means:

> "I no longer need to work on this task."

A future planned timebox means:

> "I intend to work on this task later."

These are contradictory states.

The app is justified in automatically reconciling that contradiction.

## Preserve historical data

Do not remove:

- Past planned timeboxes
- Past actual sessions
- The current session being completed
- Historical task data

Only remove **future planned timeboxes** associated with the completed task occurrence.

Example:

Before:

```text
Monday
Plan:   10:00–11:00 Write proposal
Actual: 10:08–11:14 Write proposal

Tuesday
Plan:   14:00–15:00 Write proposal
Actual: 14:03–14:47 Write proposal
Task completed here

Wednesday
Plan:   10:00–11:00 Write proposal

Thursday
Plan:   15:00–16:00 Write proposal
```

After completion:

```text
Monday   preserved
Tuesday  preserved
Wednesday future plan removed
Thursday  future plan removed
```

## V1 interpretation of planned schedule

For V1, the active planned timeline represents the user's **current plan**, not an immutable historical planning document.

In the future, the product may support:

```text
Original plan
Revised plan
Actual
```

But that is out of scope for now.

---

# 18. Non-Task Timeline Items

Not everything scheduled on the timeline is a task.

Examples:

- Dinner with someone
- Meeting
- Appointment
- Gym
- Commute
- Event
- Social activity

These can still have:

- Planned start/end
- Actual start/end

But they do **not** have a task completion state.

Example:

> Dinner with Alex

Planned:

> 18:30–20:00

Actual:

> 18:40–20:15

There is no separate "task completed" concept.

## Interaction difference

For a task-backed Planned Block, Work Mode can surface:

- The Battle Plan Task title and notes
- Its Subtasks
- Its Task Type

For a taskless Planned Block, Work Mode can surface:

- Its Task Type
- Its note

Both use the same **Exit Work Mode** action. Taskless work has no Task Completion state.

---

# 19. One Timebox = One Primary Item for V1

A possible future question is whether one timebox can contain multiple tasks.

Example:

> 10:00–12:00 Project Alpha

During that block:

- Fix login
- Reply to client
- Update documentation

Two philosophies exist:

## Strict model

One timebox = one work item.

Benefits:

- Cleaner analytics
- Clearer attribution
- Simpler UI
- Simpler domain model

## Flexible model

One timebox can contain multiple tasks.

Benefits:

- More faithful to how some people actually work

Costs:

- More complex time attribution
- More complex completion semantics
- More complex reporting

## Current recommendation

> **For V1, one timebox should have one primary item.**

Do not build multi-task timeboxes unless future user needs justify the added complexity.

---

# 20. Recurring Tasks

Recurring tasks introduce an important distinction:

> **A recurring task is a series. Each occurrence is an individual task instance.**

Example recurring series:

> Weekly review — every Friday

Conceptually:

```text
RecurringTaskSeries: Weekly review
  ├── Aug 28 occurrence
  ├── Sep 4 occurrence
  └── Sep 11 occurrence
```

Each occurrence should have its own:

- Completion state
- Planned timeboxes
- Actual sessions
- Subtask checked state

The recurring series itself is not completed when one occurrence is completed.

---

# 21. Recurring Task Completion

Completing a recurring task occurrence applies only to that occurrence.

Example:

> Weekly review — Aug 28

Completing Aug 28 must **not** complete:

> Weekly review — Sep 4

The recurring series continues according to its recurrence rule.

## Important rule

> **Never mark the recurring series itself complete when a single occurrence is completed.**

A recurring series ends because:

- The user disables recurrence
- The user deletes/stops the series
- The recurrence rule reaches its configured end date
- A future recurrence-management feature changes the rule

Not because one occurrence is completed.

---

# 22. Future Timebox Cleanup for Recurring Tasks

The previous rule—

> completing a task removes future planned timeboxes

—must be scoped carefully for recurring tasks.

When an occurrence is completed, remove only future planned timeboxes associated with **that occurrence**.

Example:

Series:

> Weekly review

Occurrences:

```text
Aug 28 occurrence
  14:00–15:00
  16:00–16:30

Sep 4 occurrence
  14:00–15:00
```

If the user completes the Aug 28 occurrence after the first session:

- Remove remaining future timeboxes for Aug 28
- Preserve Sep 4
- Preserve all later occurrences

Do not treat future occurrences as stale simply because the current occurrence is complete.

---

# 23. Recurring Subtasks

Recurring subtasks should behave as templates that reset for each occurrence.

Recurring task:

> Weekly review

Subtask definitions:

- Review metrics
- Clear inbox
- Plan next week

Aug 28 occurrence:

- ✓ Review metrics
- ✓ Clear inbox
- ✓ Plan next week

Sep 4 occurrence:

- ○ Review metrics
- ○ Clear inbox
- ○ Plan next week

Therefore:

- Subtask definitions can come from the recurring series/template.
- Checked state belongs to the occurrence.

Conceptually:

```text
RecurringTaskSeries
  title
  recurrence_rule
  subtask_templates

TaskOccurrence
  occurrence_date
  status
  generated_from_series_id

OccurrenceSubtask
  task_occurrence_id
  template_reference
  checked
```

The implementation does not have to use these exact tables, but it should preserve these semantics.

---

# 24. Editing Recurring Tasks

Recurring tasks eventually introduce the familiar edit-scope question.

Example:

The series currently contains:

- Review metrics
- Clear inbox

The user adds:

- Update forecast

Possible scopes:

- This occurrence only
- This and future occurrences

Potentially also:

- Entire series

Do **not** overbuild recurring edit semantics unless needed.

However, the architecture should preserve the conceptual distinction between:

- Editing one occurrence
- Editing the recurring series/template

This distinction matters even if the initial UI supports only a subset of recurrence-editing options.

---

# 25. Suggested Domain Semantics

A possible conceptual model is:

```text
Task
  id
  title
  status
  recurrence_series_id?   // nullable
  occurrence_key?         // nullable
  completed_at?

Subtask
  id
  task_id
  title
  checked
  checked_at?

PlannedTimebox
  id
  item_id
  item_type
  planned_start
  planned_end

ActualSession
  id
  item_id
  item_type
  actual_start
  actual_end
  source                 // live | retrospective

RecurringTaskSeries
  id
  title
  recurrence_rule
  active

RecurringSubtaskTemplate
  id
  series_id
  title
```

This is only illustrative.

The implementation should optimize for the existing architecture while preserving the semantic invariants in this specification.

---

# 26. Suggested Domain Actions

Prefer domain-level operations rather than duplicating behavior in UI components.

Examples:

```text
startSession(itemId)
endSession(sessionId)

completeTask(taskId)
reopenTask(taskId)

finishSession(sessionId)
finishSessionAndCompleteTask(sessionId, taskId)

checkSubtask(subtaskId)
uncheckSubtask(subtaskId)

removeFuturePlannedTimeboxes(taskOccurrenceId)
restoreRemovedFutureTimeboxes(operationId)
```

`completeTask(taskId)` should own the completion semantics.

Conceptually:

```text
completeTask(taskId):
    mark task completed
    freeze/resolve subtasks
    remove future planned timeboxes for this task occurrence
    preserve historical planned timeboxes
    preserve historical actual sessions
```

For recurring tasks, the `taskId` should refer to a specific occurrence instance, not the recurrence series.

---

# 27. Suggested Work Mode Exit Flow

## In Work Mode

User is actively working on:

> Prepare presentation

Current state:

```text
Work Mode active
Actual Block active
Task open
Some subtasks checked
Some subtasks unchecked
```

User chooses:

### Exit Work Mode

Result:

```text
Work Mode local state cleared
Actual Block ended
Actual time recorded
Task remains open
Subtask states preserved
Future planned timeboxes remain
```

Work Mode does not offer a Task Completion action in the current scope.

---

# 28. Suggested Completion Flow Outside Work Mode

User checks a task as complete from the task list.

Result:

```text
Task completed
Subtasks resolved
Future planned timeboxes for this task occurrence removed
Historical plan preserved
Historical actual preserved
```

Completion semantics must be identical across every surface where Task Completion is offered. Behavior for completing the active Task early elsewhere while Work Mode remains active is deferred.

---

# 29. Reopen Flow

User selects:

> Reopen task

Result:

```text
Task status = open
Previously checked subtasks remain checked
Previously unchecked subtasks remain unchecked
Subtasks become actionable again
```

Do not automatically recreate removed future timeboxes unless a separate future product decision explicitly adds that behavior.

The user can schedule new timeboxes as needed.

---

# 30. Undo Philosophy

For cleanup operations that are predictable but potentially destructive to the user's visible plan, prefer:

> **Automatic action + Undo**

over:

> **Confirmation before every action**

Example:

Task completed.

System:

> Task completed · 2 future timeboxes removed  
> Undo

This supports low cognitive load while preserving recoverability.

---

# 31. Explicitly Rejected / Deferred Ideas

The following ideas were considered and intentionally not chosen for the current implementation.

## 31.1 Automatically complete parent when all subtasks are done

Rejected.

Reason:

Subtasks are non-exhaustive.

All checked subtasks do not prove that the parent outcome is complete.

---

## 31.2 Require every subtask to be checked before parent completion

Rejected.

Reason:

The user may be in flow and not update every checkbox.

The parent completion action should be trusted.

---

## 31.3 Warn every time a parent has unchecked subtasks

Rejected.

Reason:

Creates bookkeeping friction.

Contradicts lightweight subtask semantics.

---

## 31.4 Automatically mutate all remaining subtasks to checked on parent completion

Not required.

Preferred behavior:

Preserve their explicit checked state internally and treat them as resolved because the parent is complete.

---

## 31.5 Independently schedule subtasks

Rejected for current model.

Reason:

It blurs the distinction between tasks and subtasks and adds scheduling complexity.

---

## 31.6 Promote subtask to task

Deferred / rejected for current scope.

Reason:

High implementation and semantic complexity relative to expected value.

---

## 31.7 Explicit skipped/missed/unresolved states for untracked planned blocks

Deferred.

Reason:

Not currently needed.

Can be revisited with retrospective/reporting features.

---

## 31.8 Immutable historical planned schedule

Deferred.

Current plan is allowed to evolve.

Future analytics may distinguish original plan, revised plan, and actual.

---

## 31.9 Multiple tasks in one timebox

Deferred.

Use one primary item per timebox for V1.

---

# 32. Key UX Principles

## 32.1 Minimize bookkeeping

Do not force the user to manually synchronize:

- Session completion
- Task completion
- Subtask cleanup
- Future timebox cleanup

when the product can infer the normal case.

---

## 32.2 Do not make the user maintain the data model

The internal system may contain:

- PlannedTimebox
- ActualSession
- TaskStatus
- SubtaskState
- RecurrenceSeries
- TaskOccurrence

The user should not have to understand or manipulate each of these separately.

---

## 32.3 Preserve history without exposing complexity

Plan and actual should remain separate.

Historical sessions should remain available.

Subtask check history can remain internally accurate.

But the UI should stay simple.

---

## 32.4 Prefer strong, understandable invariants

The most important invariant is:

> **Task = independently meaningful and independently schedulable work.**

> **Subtask = execution checkpoint inside a task.**

> **Planned Block = planned allocation of time to one primary item.**

> **Actual Block = authoritative record of time that occurred.**

> **Work Mode = present-tense execution surface for today's current or next Planned Block.**

---

# 33. Agent Implementation Guidance

When implementing or modifying features related to this system, the agent should preserve the following invariants.

## MUST

- Keep planned time separate from actual time.
- Allow one task to have multiple timeboxes.
- Support live and retrospective actual-time entry.
- Keep task completion independent from subtask completion.
- Never auto-complete the parent when all subtasks are checked.
- Treat subtasks as non-schedulable execution checkpoints.
- Surface subtasks in Work Mode.
- Expose Work Mode through an app-level action rather than a Planned Block-owned action.
- Keep pre-confirmation Work Mode state local to the starting device.
- Preserve Work Mode across navigation, backgrounding, refresh, and accidental restart until explicit exit.
- Warn without blocking when no Planned Block is current or begins within ten minutes.
- Limit **Up next** to the current day.
- Wait one uninterrupted minute of current planned work before creating an Actual Block.
- Start an automatically derived Actual Block at the later of Work Mode entry and Planned Block start.
- Record no Actual time during schedule gaps.
- End any active Actual Block when Work Mode is explicitly exited.
- Keep Task Completion independent from exiting Work Mode.
- Apply task completion semantics consistently across all UI surfaces.
- Resolve subtasks when the parent is explicitly completed.
- Preserve explicit subtask checked state if practical.
- Remove future planned timeboxes when a non-recurring task is completed.
- Remove only future planned timeboxes for the current occurrence when a recurring task occurrence is completed.
- Preserve historical planned timeboxes and actual sessions.
- Keep recurring series completion separate from occurrence completion.
- Reset recurring subtask checked state for each occurrence.
- Make completed tasks non-actionable by default until reopened.

## SHOULD

- Provide Undo after automatic future-timebox removal.
- Keep actual time editable.
- Avoid confirmation dialogs for normal completion.
- Keep Work Mode lightweight.
- Offer **Return to Work Mode** when Work Mode or an active Actual Block already exists.
- Ask whether work continued after more than ten minutes of application absence before backfilling Actual Blocks.
- Avoid overbuilding recurrence editing until needed.
- Preserve enough history for future retrospective/reporting features.
- Use domain actions for completion semantics instead of duplicating logic across screens.

## SHOULD NOT

- Schedule subtasks independently.
- Auto-complete parent tasks based on subtask state.
- Force users to manually check every subtask before completing the parent.
- Ask users to classify skipped/missed blocks in V1.
- Require a Battle Plan Task before entering Work Mode.
- Show tomorrow's Planned Blocks as today's **Up next** work.
- Provide **Skip this block** in the current Work Mode scope.
- Auto-complete a Battle Plan Task from elapsed planned or Actual time.
- Delete historical actual data when task state changes.
- Complete future recurring occurrences when one occurrence is completed.
- Make recurring-series completion equivalent to occurrence completion.

---

# 34. Acceptance Criteria

## Task + multiple timeboxes

Given a task with three planned timeboxes:

- Ending the first session does not complete the task.
- Ending the second session does not complete the task.
- The user can complete the task during the third session.
- Historical sessions remain available.

---

## Planned vs actual

Given:

```text
Plan:   10:00–11:00
Actual: 10:12–11:18
```

The system stores both records separately.

Editing Actual must not overwrite Plan.

---

## Hybrid time tracking

The user can:

- Enter and exit Work Mode live.
- Create an Actual Block retrospectively.
- Edit actual start/end after recording.

---

## Work Mode entry

Given no current Planned Block and today's next Planned Block beginning eleven minutes from now:

- Starting Work Mode warns without blocking entry.
- Choosing **Continue** shows that Planned Block as **Up next**.
- Choosing **Plan something first** opens Plan Mode at the current time.

Given today's next Planned Block beginning exactly ten minutes from now:

- Starting Work Mode enters directly without the warning.

Given **Plan something first** produces a Planned Block beginning more than ten minutes from now:

- Planning completes normally.
- Work Mode does not enter automatically.

Given no remaining Planned Block today:

- Continuing into Work Mode shows **No more planned work today**.
- Tomorrow's Planned Blocks are not shown as **Up next**.

---

## Work Mode recording

Given Work Mode entered at 10:12 during a Planned Block from 10:00 to 10:30:

- No backend Work Mode start record is created.
- Exiting before 10:13 creates no Actual Block.
- Remaining in Work Mode through 10:13 creates an Actual Block beginning at 10:12.
- Exiting Work Mode ends that Actual Block without completing its Battle Plan Task.

Given Work Mode entered at 9:55 for a Planned Block beginning at 10:00:

- The Planned Block is **Up next** until 10:00.
- Its one-minute confirmation begins at 10:00.
- Confirmation creates an Actual Block beginning at 10:00.

Given adjacent Planned Blocks from 10:00–10:30 and 10:30–11:00:

- The first Actual Block ends at 10:30.
- The second block gets a fresh one-minute confirmation.
- Confirmation creates a separate Actual Block beginning at 10:30.

Given a schedule gap:

- Work Mode records no Actual time during the gap.
- Work Mode remains open and shows today's next Planned Block or the no-work state.

Given a taskless Planned Block with a Task Type and optional note:

- Work Mode presents that Task Type and note.
- It derives Actual time using the same confirmation and boundary rules.

---

## Work Mode lifecycle

Given locally active Work Mode:

- Navigation, backgrounding, refresh, and an accidental restart preserve its entry time.
- The app-level action reads **Return to Work Mode**.
- Explicit exit clears the local state and ends any active Actual Block.

Given the application was absent for more than ten minutes:

- Restoration asks whether work continued.
- Confirming backfills Actual Blocks for applicable Planned Blocks.
- Declining ends recorded work at the last confirmed point.

Given pre-confirmation Work Mode on one device:

- Another device does not discover that local state.
- After an Actual Block exists, another device can offer **Return to Work Mode**.

Given Work Mode remains active across midnight:

- It stays open.
- It begins evaluating the new current day's Planned Blocks.

---

## Subtask behavior

Given:

```text
Prepare presentation
  ✓ Add charts
  ○ Proofread
  ○ Add citations
```

Completing `Add charts` must not complete the parent.

Completing all three subtasks must not complete the parent.

Completing the parent must be an explicit user action.

---

## Parent completion

When the user explicitly completes the parent:

- Parent becomes complete.
- Remaining subtasks are resolved.
- No warning is required simply because subtasks remain unchecked.
- Explicit checked state may be preserved internally.
- Future planned timeboxes for the task occurrence are removed.
- Historical records remain.

---

## Reopening

After reopening:

- Previously checked subtasks remain checked.
- Previously unchecked subtasks remain unchecked.
- Subtasks become actionable again.

---

## Future timeboxes

Given:

```text
Today      10:00–11:00
Tomorrow   14:00–15:00
Thursday   10:00–11:00
```

If the task is completed today:

- Today's historical data remains.
- Tomorrow and Thursday's future planned blocks are removed.
- Undo is available.

---

## Non-task item

Given:

> Dinner with Alex

The user can track planned and actual time.

The system must not require or create task-completion semantics.

---

## Recurring occurrence

Given:

> Weekly review — every Friday

Completing the Aug 28 occurrence:

- Completes Aug 28 only.
- Does not complete Sep 4.
- Does not stop the recurrence series.
- Removes only remaining future timeboxes associated with Aug 28.

---

## Recurring subtasks

Given template subtasks:

- Review metrics
- Clear inbox
- Plan next week

Completing them in one occurrence must not carry checked state into the next occurrence.

---

# 35. Deferred Product Questions

These are intentionally not blockers for the current implementation.

- Original-plan vs revised-plan history
- Daily retrospective UX
- Skipped/missed classification
- Advanced reports
- Multiple tasks per timebox
- Subtask promotion
- Rich recurring-series edit scopes
- Dependencies between tasks
- Project hierarchy beyond task/subtask
- Whether reopening should optionally restore removed future timeboxes
- Whether recurring-series template edits affect current, future, or all occurrences
- Whether non-task event types need custom completion semantics
- How Work Mode should react when its active Battle Plan Task is completed early from another surface
- Whether Work Mode should support adding subtasks during a live session
- Whether Work Mode should optionally highlight a subset of subtasks as the focus of the current session

These can be addressed only when concrete product needs emerge.

---

# 36. Final Product Philosophy

The product should remain easy to reason about.

The user is not managing database state. They are expressing intent and recording reality.

The system should maintain the following distinctions:

> **Task hierarchy = decomposition of work.**

> **Timeline = allocation of attention.**

> **Actual time = what really happened.**

> **Task completion = explicit statement that the outcome is done.**

> **Subtasks = lightweight execution checkpoints, not miniature independently scheduled tasks.**

> **Recurring series = template/rule that creates or represents individual occurrences.**

And the UX should follow this guiding principle:

> **Infer and automate the common case, preserve recoverability, and expose complexity only when the underlying facts genuinely diverge.**
