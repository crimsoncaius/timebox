# Recurring Task Series create/edit hierarchy

Progressive prototype for GitHub issues #189 and #191. Throwaway debug UI; does not save.

## Accepted

- Create and edit share one field order. Optional details come after rhythm, not in a leading Definition block.
- End date is not duplicated in extras.
- Keep-unfinished-overdue lives with “when it runs” (a recurrence rule, not metadata).
- Mode cannot change after creation and is visibly disabled on edit.
- **Adaptive extras (round 1):** notes, subtasks, Task Type, and priorities stay collapsed while empty and open when any of them is already set.

## Round 2 — Recurring Pre-planning Schedule

**Question:** Where does Recurring Pre-planning Schedule sit once extras are Adaptive? On edit it currently lives in core and pushes notes off the screen.

**What varies:** placement of Recurring Pre-planning Schedule (scheduled only).
**What stays stable:** Adaptive extras, shared field order, mode lock, pinned save.

| Variant | Label | Behavior |
|---|---|---|
| A | With rhythm | Pre-planning always sits with start/end. |
| B | With extras | Pre-planning sits inside Adaptive extras with notes and subtasks. |
| C | Own toggle | Pre-planning has its own Adaptive disclosure: collapsed until configured or opened. |

**Launch:** `timebox://prototype/recurring-details?flow=details&layout=core&mode=scheduled`

Switcher: Create/Edit/**Details**, With rhythm / With extras / Own toggle, Scheduled/Quota, Reset.

**Details** is Recurring Task Series details (#188): current work first, then upcoming, then the same pre-planning and Adaptive extras family as create/edit. Lifecycle and opening a Task Occurrence are stubbed.

Quota has no Recurring Pre-planning Schedule in any variant.

### Open

- Round 2 pre-planning placement, now judged on create, edit, **and details**.
- Subtask entry widget (#183) — still newline text here.
- Date chrome, summary card, and preview fidelity.
- Opening a Task Occurrence / Session Task from details.

### Coverage this round

Create (no slots), edit (one Monday slot), and details (current work first) × scheduled. Quota confirms pre-planning is absent and Session Tasks replace Task Occurrences. Adaptive extras remain in force.

### Prototype

`android/app/src/main/java/com/timebox/android/ui/battleplan/RecurringDetailsHierarchyPrototype.kt`
