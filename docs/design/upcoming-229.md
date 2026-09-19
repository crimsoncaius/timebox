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
