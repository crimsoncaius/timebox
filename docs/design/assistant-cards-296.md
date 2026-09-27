# Assistant Cards (#296)

Status: prototyping on `claude/prototype-assistant-cards` (debug-only deep link, sample data, nothing reaches the API). Parent: #292.

## Settled (#292, revised 27 Sep 2026)

- One read tool selects `lane` (planned / actual / both), a date or range, an optional Task Type filter, and `group_by` (blocks / task_type).
- Block Card: one date's blocks, as planned, actual or both. Blocks crossing midnight are shown in full. Deep link to Day.
- Task Type Card: time by Task Type for one date or a range, as planned, actual or both. Expandable into children. Deep link to Trends.
- Up to 3 cards per response.
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

Verdict (27 Sep 2026): **superseded.** The combined Day Card was the wrong model. The read now selects a lane, and the card shows exactly what was read. "Plan, then actual" was dropped; the by-Task-Type footer became its own Task Type Card.

## Round 2 — how does a Block Card show "both"?

Deep link: `timebox://prototype/assistant-cards?lane=planned|actual|both&layout=timeline|lanes|paired&scenario=review|today|future|long|empty`.

The first bar row picks what the read selected. Planned-only and Actual-only use the plain list, without PLAN/DID tags. The layout row appears only for Both:

- **List**: interleaved in start order, with PLAN/DID tags.
- **Two lanes**: a mini Day with Plan and Actual columns on one time scale, clipped to the date (0.42 dp/min collapsed; "Show larger" goes to 1.1 dp/min). Blocks shorter than ~40 minutes lose their labels when collapsed.
- **Paired**: Planned Blocks with their linked Actual Blocks nested, then "Not planned".

On a future date, Actual and Both fall back to the plan with an explanation.
