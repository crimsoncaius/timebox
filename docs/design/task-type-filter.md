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

## Open questions

- Which structure? Should a parent include its sub-types (provisional: yes)?
- Empty-query order: Block usage (picker, ADR-0006) vs task count on the board.
- Large text, dark theme and Few-types behaviour are not reviewed yet.
