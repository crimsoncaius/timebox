# Issue #171: readable elapsed durations

Confirmed in the design interview: fix displays that remain in total minutes as time grows. Three hours must read `3 hours`, rather than `180m` or `180:00`.

- Use days, hours, minutes, and (on existing second-resolution timers) seconds, largest first. A day is 24 elapsed hours.
- Omit zero units; use singular/plural labels (`day`, `hour`, `min`, `sec`). Zero is `0 mins` on minute displays and `0 secs` on second-resolution timers.
- Android Focus retains seconds: three hours and seven seconds is `3 hours 7 secs`. Web retains its existing whole-minute precision.
- Include compact Current Activity controls, Focus, recorded totals, reporting-day duration shares, stop/edit previews, and legacy timers that still show total minutes. Allow wording to wrap rather than hiding units.
- Preserve displays already expressing hours, relative-action labels such as `15 min ago`, numeric inputs, clock times, dates, stored values, and existing duration calculations/rounding.

Examples: `1 min`, `1 hour`, `1 hour 30 mins`, `3 hours`, `1 day 1 hour 30 mins`, `2 days 1 min`, `3 hours 7 secs`.

This is a presentation change; it introduces no domain term or architectural decision requiring a glossary entry or ADR.
