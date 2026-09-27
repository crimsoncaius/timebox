# Checklist Item Habits

Lets a routine's Checklist Items be Habits in Chronicle › Habits, and lets an item be tracked without its routine. Domain terms (Checklist Item, Habit, Habit Period) are in `CONTEXT.md`. The decision and the rejected alternatives are in ADR 0016. This builds on the Habits view from issue 214 (`habits-214.md`).

## Prototype

Question: should a routine's items sit nested under its row, or should each opted-in item get its own row?

Variants, on the debug route `timebox://prototype/habits?checklist=b|c|d`:
- **B · Nested:** items hang under their routine's row behind a chevron, and tracking the routine tracks every item.
- **C · Own rows:** each item has its own Track as habit switch, and an opted-in item gets a full row with "↳ routine" under its name.
- **D · Nested + choose:** B's layout with C's per-item switches. A routine that isn't tracked shows as a heading over its tracked items.

**Decision: D.** The user liked B's instant nesting but sometimes wants to track one item without its whole routine. D keeps the grouping and adds that choice.

The prototype lives only on the throwaway branch `prototype/habit-checklist-items` (commits `200e1d35` and `c1bd18b1`). It never merges to `master`.

## Decisions from the grilling session

- **Only routine-level items count.** Only a Checklist Item can be a Habit; a Subtask added to one occurrence can't.
- **Met follows the occurrence, not a date.** An item's day is Met when that occurrence's Subtask is checked, whenever it was checked. No check date is stored.
- **Ticks:**
  - A tick checks the Subtask, even on a completed occurrence.
  - It never completes the routine and never reverses a skip.
  - Checking every item doesn't complete the routine either.
- **Excused days:** an occurrence without the item's Subtask shows Excused. An item's row appears only in weeks where some occurrence has its Subtask.
- **The routine switch:**
  - Switching the routine on tracks all its items, including items added later, except those switched off individually.
  - Switching it off turns off every item.
  - Switching it on again clears the individual opt-outs.
- **Identity:** renaming or reordering an item keeps its history; deleting an item ends it. Existing Subtasks were linked to items once, by title.
- **Grouping:** groups are expanded by default, and each group's collapsed state is remembered on the device. A collapsed group shows its item count.
- **Routine sheet:** the per-item switches sit under Track as habit, as "N of M checklist items tracked". They're kept out of checklist editing.
- **Scope:** Android and the backend only. The web app has no habit features.

## Production

Backend:
- Migration 037 adds:
  - `recurring_checklist_items.track_as_habit`
  - `tasks.checklist_item_id` (`ON DELETE SET NULL`)
- 037 also links existing Subtasks by title once and copies each tracked routine's flag onto its items. It skips columns that already exist, as 036 does.
- Clients edit a checklist as plain lines, so the server infers identity:
  - A title that is still present keeps its item wherever it moved.
  - A line edited in the same place is a rename.
- Occurrence generation and the rebuild of upcoming Subtasks carry the item link.
- `PATCH /recurring-templates/{id}` accepts `habit_checklist_item_ids`, the complete tracked set. A change to `track_as_habit` sets every item.
- `GET /habits` returns each routine's tracked `items`. A routine that isn't tracked comes back with `tracked: false`, as a heading.
- `POST` and `DELETE /habits/{template_id}/items/{item_id}/days/{date}` check or uncheck that occurrence's Subtask and protect the occurrence from checklist rebuilds.

Android:
- The Habits view nests item rows (30dp cells behind a guide line) under their routine row or heading, with a chevron.
- Each group's collapsed state is stored in DataStore (`habits_collapsed_templates`).
- The routine sheet gains the tracked-item summary with per-item switches.
- Add habit offers untracked routines and untracked items.

## Validation

- **Backend:** 12 new tests in `test_checklist_item_habits.py`. The full suite passes: 542 passed, 6 skipped.
- **Android:** 5 new unit tests. The full unit suite passes: 368 tests.
- **Migration on Postgres:** checked in a throwaway container on port 15499:
  - A database migrated to 036 with the pre-feature code was seeded through its API (`artifacts/checklist-habits/seed.py`), then upgraded to 037.
  - 154 generated Subtasks were linked.
  - A Subtask renamed on one occurrence before the feature, and one added to a single occurrence, stayed unlinked.
  - The tracked routine's items started tracked.
  - Downgrading to 036 and upgrading again worked.
- **Emulator:** checked on emulator-5588 against that database through a backend on port 12216:
  - The nested rows and the untracked-routine heading render as prototyped.
  - An item ticked on a skipped Thursday shows Met while the routine stays Missed.
  - An item ticked on a completed day leaves the routine completed.
  - Collapsing a group shows "1 item", and reopening restores it.
  - The routine sheet's per-item switch saves without closing the sheet.
  - Add habit tracked a single item under the untracked routine's heading.
- **Not exercised:** instrumentation tests, dark mode, font scale above 1.0, and process death.
