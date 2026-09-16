# Recurring Task Series create/edit hierarchy

Progressive prototype for GitHub issues #189, #191, and companion #188. Throwaway debug UI; does not save.

## Accepted

- Create and edit share one field order. Optional details come after rhythm, not in a leading Definition block.
- End date is not duplicated in extras.
- Keep-unfinished-overdue lives with “when it runs” (a recurrence rule, not metadata).
- Mode cannot change after creation and is visibly disabled on edit.
- **Adaptive extras (round 1, revised):** on Create/Edit, notes, Task Type, and priorities stay collapsed while empty and open when any of them is already set. Subtasks are not extras.
- **Details is a view, not a third form (round 3 split):** current work first, then upcoming, then quieter series settings. Shared language with Create/Edit; not the same page structure.
- **Own toggle for Recurring Pre-planning Schedule on Create/Edit (round 3):** collapsed until configured or opened. Details still shows a configured schedule as read-only series settings.
- **Add rows for Subtasks on Create/Edit (round 4):** reuse Battle Plan `TaskSubtasks` the way task creation does — title rows, remove, and an Add subtask sheet. No one-per-line field. No checkboxes; these are series definition items, not occurrence completion.
- **Subtasks always visible:** on Create/Edit they sit after “when it runs”, not behind Notes & more. Details lists them under Series settings, not mixed into notes extras. They are not first on the page.

## Combined

Create, Edit, and Details now use those accepted choices together. Switcher is only flow (Create/Edit/Details) and mode (Scheduled/Quota).

**Launch:** `timebox://prototype/recurring-details?flow=edit&mode=scheduled`

### Open / deferred

- Date chrome, summary card, and preview fidelity.
- Opening a Task Occurrence / Session Task from Details (stubbed; #188).
- Production implementation of #189 / #191 / #188 visual hierarchy, including Battle Plan `TaskSubtasks` on Create/Edit. Full #183 CRUD/history remains out of this pass.

### Prototype

`android/app/src/main/java/com/timebox/android/ui/battleplan/RecurringDetailsHierarchyPrototype.kt`
