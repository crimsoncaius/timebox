# Trends

Approved first implementation for GitHub issue #10, based on the reviewed Android prototype A.

## Agreed decisions

- Trends presents activity neutrally, without interpreting it against goals or assigning productivity scores.
- The initial breakdown uses Actual Block time only, grouped by the Blocks' Task Types. Planned time is excluded.
- Each Task Type shows duration and its percentage of all recorded time in the selected range.
- The headline shows recorded time only; the elapsed-range comparison was tried and removed at the user's request. Task Type bars and percentages use recorded time as their denominator.
- Presets are calendar Day, Week, and Month, with previous/next navigation. Custom ranges have inclusive start and end dates.
- Date boundaries use the Reporting Time Zone.
- Parent Task Types show totals including descendants and can expand to show children. Time assigned directly to a parent remains visible in its breakdown. Each duration contributes only once to the overall total.
- The selected presentation is prototype A: ranked horizontal bars, including bars for expanded child Task Types and directly assigned parent time.
- All bars share the same left edge, full track width, and range-total scale. Child labels remain indented to show hierarchy.
- As settled in #210, Trends lives beside Calendar in Chronicle and owns its date range independently of Calendar. Task Type figures drill through to Calendar, highlighting contributing days.

## Reporting and interaction rules

- Open on the current Monday-based week. Keep the chosen range while switching Calendar/Trends; a new application session starts on the current week. Calendar month navigation remains independent.
- Rank siblings by recorded duration descending, with path order breaking ties. Direct parent time participates in the child ranking. Support any depth of Task Type path; unrecorded categories are omitted and `unspecified` is an ordinary category.
- Sum authoritative Actual Block intervals, clipped to the selected range and the server's captured current instant. Running Actual Blocks contribute through that instant. Split contributing days at zoned midnight using elapsed instants, including daylight-saving transitions. Refresh while Trends is visible, approximately once per minute; reports use server-confirmed data.
- Keep seconds during aggregation. Display whole-minute durations, with `<1m` for a positive subminute duration. Display percentages to one decimal place; rounding may keep displayed percentages from summing to exactly 100%.
- Tapping a duration opens Calendar on the latest contributing day's month. Highlight all contributing days, including those in other months when navigated to, and show the selected Task Type and its duration in the cells. Direct-parent drill-through includes only directly assigned time. Clearing removes the highlight; returning to Trends preserves its range.
- While highlighting, Calendar shows a breadcrumb back to the source range (for example "Trends › Week · Sep 21 – 27") and one sentence: the Task Type, its contributing days out of the range's days, and its total, as in "meetings on 2 of 7 days, 2h 5m in total." Parent path segments are muted. "Clear" removes the highlight.
- A Day range offers no drill-through, because it always has exactly one contributing day. Its durations are shown but not tappable.
- An empty range displays zero recorded time and an explicit empty state. Loading, retryable failure, and successful zero totals are distinct.
- This version has no time-series chart, goals, interpretation, planned-time comparisons, or elapsed-range denominator.

## Source and validation

The throwaway comparison remains on `codex/prototype-10-trends` (latest reviewed source: `3abbc26`). Production implementation is on `codex/issue-10-trends`; no prototype gates, samples, or variant switcher are included.

The shared read-only `/trends` endpoint supplies Android and web. No schema migration is needed. Tests cover zoned clipping, daylight-saving durations, running intervals, inclusive ranges, hierarchy totals, minute rounding, navigation, and drill-through. Review instances use an isolated database with seeded Actual Blocks.