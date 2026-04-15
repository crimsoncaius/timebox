# Chronicle month calendar

## Summary

The **Chronicle of focus** route (`/history`) shows a **single-month, Monday-first** calendar in **UTC**, aligned with existing day labels elsewhere. Days returned from `GET /days?limit=500` are keyed by ISO date and styled as archived “focus” days; **every** calendar cell links to `/day/:date` so users can open any day, with or without prior history.

## Timezone

- Grid construction and month titles use **UTC** (same stability as the previous `formatDayMonth` helper).
- “This month” uses the **current UTC** month.

## Data

- The client calls `listDays(500)` (API maximum) and builds `Map<YYYY-MM-DD, DayListItem>`.
- **Cap:** Only the **500 most recent** `Day` rows (by `date` descending) are returned. Older days outside that window appear as empty cells until a date-range API exists.

## Navigation

- **Prev / next month** shifts the visible month without changing routes.
- **This month** jumps to the current UTC month.
- Initial visible month: the month of the **newest** day in the list if any rows exist; otherwise **today (UTC)**.

## Cells

- **In-month vs adjacent:** Leading/trailing cells use muted styling; all remain links to that ISO date (navigating away does not auto-change the visible month).
- **Archive vs empty:** Days present in the map use stronger surface and show a compact **window** line (`start_hour` / `end_hour`); other in-month days are quieter but still clickable.

## Accessibility

- Day targets are **`<Link>`** elements with descriptive **`aria-label`** (date, archived vs open, window when relevant) for screen readers and keyboard Tab order.

## Out of scope (follow-ups)

- `GET /days?from=&to=` for exact month slices if the 500-row cap becomes a problem.
- Week-first alternative or hybrid week strip.
