# Assistant Cards (#296)

Status: prototyping on `claude/prototype-assistant-cards` (debug-only deep link, sample data, nothing reaches the API). Parent: #292.

## Settled before prototyping (#292)

- Day Card: rows list with a Planned/Actual marker, blocks crossing midnight shown in full, deep link to Day.
- Range Card: top-level Task Types with planned and actual totals and share, expandable into children, deep link to Trends.
- Neither shows Supporting Notes or Task Descriptions. Cards are fixed as of their read time and show it. Historical plan cards keep rendering.

## Open questions

Could change what #293/#294 return: (1) how the Day Card mixes lanes, (3) midnight crossings, (6) where plan-vs-actual numbers go, (10) whether the Range Card uses `day`/`week` detail.
Presentation only: (2) lane marker, (4) running block, (5) future Days, (7) collapse count, (8) planned vs actual on Range rows, (9) child expansion, (11) filter/future-range labels, (12) header, (13) deep-link placement, (14) empty states.

## Round 1 — how does a Day Card mix both lanes?

Deep link: `timebox://prototype/assistant-cards?layout=timeline|sections|paired&scenario=review|today|future|long|empty`.

- **One timeline**: every block in start order, Planned and Actual interleaved.
- **Plan, then actual**: a Planned section, then an Actual section, 3 rows each before Show all.
- **Paired**: each Planned Block with its linked Actual Blocks nested under it ("Not recorded" / "Not yet" when none); unlinked Actual Blocks under "Not planned". Needs #293 to return each Actual Block's Planned Block link.

Stable across layouts: Assistant chrome, question and answer, header (relative day, date · read time · zone, counts), row anatomy (lane bar, lane-colored start time, PLAN/DID tag, Task Type · duration, "total · this day" for midnight crossings, "running · Xm at read"), collapsible By Task Type (plan / actual / diff, no score), Open Day stand-in.
The yellow bar is comparison-only.

Limitations: static sample data; Open Day only shows where it would go; read time and zone are fixed text.
