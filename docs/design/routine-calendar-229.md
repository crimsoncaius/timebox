# Routine calendar — issue 229

The user approved expanding upcoming dates into a month calendar of completed work and known future occurrences. Month was selected over the fortnight and agenda alternatives. The approved prototype is preserved on [codex/prototype-229-upcoming](https://github.com/crimsoncaius/timebox/tree/codex/prototype-229-upcoming), final prototype commit 5072093.

## Implemented behavior

Android routine details replace the Upcoming date list with a month calendar. Completed Task Occurrences and Session Tasks appear on their completion dates in the Reporting Time Zone, using filled circles in the Actual Block `actual` theme color. Multiple completions on a day display a count. After review, the user requested a grid-only presentation: no legend, selected-date label, or task details below the calendar. Dates retain accessibility descriptions; the month arrows are the calendar controls.

Scheduled active routines also show projected occurrence dots. The server uses the canonical recurrence iterator, excludes suppressed and already completed occurrences, and never materializes future tasks when browsing. Quota routines, paused routines, and ended routines display completion history only.

Month navigation is bounded by available history and the scheduled start/end or cycle limit. A never-ending active schedule has no domain upper bound. Late completions can extend history past a scheduled end. Empty completion history has an explicit empty state. Loading and retry states are local to the calendar.

The API is GET /recurring-templates/{id}/calendar with an optional month=YYYY-MM-01. It returns the selected month, Reporting Time Zone today, navigation bounds, completion facts and upcoming dates. No schema migration is needed. The existing web presentation is unchanged.

## Validation and review

Backend recurrence suite: 68 existing tests passed. Calendar: 8 focused tests passed, covering timezone boundaries, quota counts, excluded checklist/deleted rows, lifecycle, end dates, cycle limits, suppressed/early-completed occurrences, and read-only browsing. Android: 11 API contract tests passed; debug build passed. Inspected the production scheduled and quota screens, completion selection and quota upper-bound control.

Review instance: emulator-5588, reservation b91ce16d9d404d32b6e8f7cae35ab6bb. Isolated API uses port 12032 and artifacts/issue-229-review.sqlite. Review APK was built with -PreviewApiBaseUrl=http://10.0.2.2:12032/. No normal user database was modified.
