# Android Battle Plan Task Type filter

## Round 1: overall structure

The filter sheet's Task type section shows every Task Type as a chip, which overwhelms
with nested paths. The user wants a picker-like pattern **without** type creation
(filters only narrow existing types; ADR-0008 already exempts filters).

Stable across variants: multi-select; a chosen parent covers its whole branch
("Included via coding"); selected types appear as removable chips (`coding +8`);
rows show two-tone paths and branch task counts; an Unset row ends the list.

| Variant | Question it answers |
| --- | --- |
| Current | Baseline chip wall (exact-ID matching). |
| Inline search | Search plus a bounded ranked list inside the filter sheet. |
| Drill-in | A summary row opens a full-height Task types sheet. |
| Tree | Collapsible hierarchy (roots by task count), flat results while searching. |

Route: `timebox://prototype/type-filter?variant=inline|drill|tree|current&data=many|few`.
All state is local; nothing writes to the repository or the saved view.

## Decision

The user chose **Inline search**, and it is now the production Task type section of
the Android filter sheet (`TaskTypeFilterSection.kt`, rules in `TaskTypeFilter.kt`).

- Filtering only narrows existing types; there is no create row.
- A chosen parent covers its branch. Choosing a parent absorbs its individually chosen
  descendants; covered rows show "Included via …" and cannot be toggled on their own.
- Empty-query order follows the Task Type picker (ADR-0006); rows show branch task counts
  within the current scope, before other filters.
- No Unset row: the existing filter sheet deliberately omits Unset.
- The prototype route was removed; it remains in commit `ec70964b`.

Web matches Android (issue #290): the same branch coverage and inline search, in the
filter bar's Task types popover (`frontend/src/features/battle-plan/TaskTypeFilterMenu.tsx`).
Web keeps its Unset row, which Android's sheet omits.

## Final review

On 2026-09-26 the user reviewed the production filter sheet against nested sample data
(39 Task Types, 23 tasks) and accepted every default: branch coverage for chosen parents,
picker (Block usage) ordering, no Unset row, and no chip fallback for few types.
Web parity is tracked in issue #290. The earlier questions below are resolved.

## Earlier open questions

- Which structure? Should a parent include its sub-types (provisional: yes)?
- Empty-query order: Block usage (picker, ADR-0006) vs task count on the board.
- With few types (Few types data), search plus a list is heavier than the old chips.
  Consider chips below a type-count threshold and the search pattern above it.

## Coverage checked

- Many types (39, three levels) and Few types (5 roots), light and dark theme,
  font scale 1.3, keyboard open while searching in the filter sheet.
- The prototype switcher chips clip at 1.3 font scale; they are review controls only.
