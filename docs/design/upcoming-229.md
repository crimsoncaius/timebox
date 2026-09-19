# Upcoming routine dates — issue 229 prototype

Question: does a month calendar communicate upcoming recurrence better than a fortnight strip or agenda inside routine details?

Throwaway native Android variants on the existing debug recurring-details route:
- A: month calendar, date selection and month navigation.
- B: two-week strip, date selection and fortnight navigation.
- C: next-five agenda, selectable occurrences or weekly periods.

Use the bottom arrows to compare; Try quota switches from Mondays to three sessions per week. Calendar shading represents quota periods, not scheduled sessions. Sample today is 19 September 2026. State is in memory; no backend is needed. This host mirrors routine details with a compact read-only definition so the upcoming area is visible.

Run from the repository root: `./scripts/prototype-upcoming-229.ps1`.
Deep link: `timebox://prototype/recurring-details?flow=details&layout=upcoming&mode=scheduled&variant=A` (also B/C and mode=quota).

Verdict: pending user review. No design has been selected or promoted to production.
Source branch: `codex/prototype-229-upcoming`.

## Round 2 — completed work and future occurrences

User explicitly approved expanding issue 229 beyond upcoming dates. Scheduled routines show completed Task Occurrences on their completion dates alongside upcoming occurrence dates. Quota routines show only completed Session Tasks on completion dates; no future quota markers or period shading. Multiple completions on one day display a count. The section is now Calendar. All three comparison variants follow these semantics.

The sample includes a Monday occurrence completed on Tuesday 15 September, and quota days with two completed sessions. Tap a date to inspect its completion count or upcoming state. Month navigation reveals earlier sample completions in August. Final visual direction remains pending review.

Round 2 verification: debug build and whitespace check passed. Visually inspected scheduled and quota month calendars on emulator-5588; selected September 5 and verified the detail reads '2 sessions completed'. Retained scheduled month view for review (token b91ce16d9d404d32b6e8f7cae35ab6bb).
