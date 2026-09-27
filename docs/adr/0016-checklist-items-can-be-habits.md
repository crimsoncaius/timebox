# Checklist Items can be Habits

A Checklist Item can be opted into habit tracking on its own or with its Recurring Task Series, and its Habit Period is Met when that Task Occurrence's Subtask for it is checked. This revises the Habits view's rule that only a Task Completion counts. People track a routine for its parts: seeing which part they keep skipping (Stretch, Journal) is what the routine's own row cannot show.

## Considered Options

- **Separate routines per item.** Works today, but loses the single routine to plan, time-block and complete.
- **Items as their own flat rows, each opted in (prototype C).** Gives control but loses the grouping; the chosen design nests tracked items under their routine and shows an untracked routine as a heading.
- **Date each Subtask check and count it on its day.** Rejected: a Checklist Item follows its Task Occurrence's recurrence period, whatever the check's date, exactly as a scheduled Habit Period does for Task Completion. No check date is stored.
- **Identify items by title.** Rejected as the ongoing rule: renames would split history and duplicate titles would merge it. Checklist Items get a stable identity, and titles are used once, to link Subtasks that existed before this decision.

## Consequences

- Editing a checklist no longer deletes and recreates its items; renaming or reordering keeps an item's identity and history, and deleting an item removes it from Habits.
- A Habit tick may check or uncheck a Checklist Item's Subtask on a completed Task Occurrence, an exception to completed Tasks' Subtasks being read-only until reopen. Other surfaces keep that rule. A check never completes a Task Occurrence and never reverses a Skipped Task Occurrence.
- A Task Occurrence without a Subtask for the item leaves that Habit Period Excused, so deleting it from one occurrence reads as "not this time", not a miss.
