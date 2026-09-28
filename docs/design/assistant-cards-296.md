# Assistant Cards (#296)

Status: design settled 27 Sep 2026; sample routes removed after approval on 28 Sep 2026. Historical prototype on `claude/prototype-assistant-cards` (debug-only deep link, sample data, nothing reaches the API). Parent: #292.

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

Verdict (27 Sep 2026): **Two lanes** for Both, collapsed by default. Open: whether to trim the collapsed scale to the waking/planned part of the day (the pre-midnight Sleep stretches it back to 00:00).

## Round 3 — Task Type Card

Deep link: `timebox://prototype/assistant-cards?card=type&lane=planned|actual|both&both=bars|marker|numbers&detail=total|day&scenario=last-week|yesterday|this-week|next-week|filtered`. "→ Block Card" / "→ Task Type Card" in the bar switches study.

- Single lane: one bar per top-level type with duration and share of that lane's total.
- Both as **Paired bars** (plan outline over actual fill), **Actual + plan mark** (actual fill with a plan tick), or **Numbers** (Plan | Actual | Diff).
- **Detail**: Total, or By day, which adds a 7-column strip per row. This decides whether the card needs #294's `day` detail.
- Rows expand into child Task Types. Scenarios cover one date, a partial current week (actual stops at the read time), a future week (plan only), and a Task Type filter.

Limitations: sample data; Sleep has no plan, so its difference is all "+"; the day strip scales per row, not across rows.

Verdict (27 Sep 2026): **Paired bars** for Both; **Total** only. The card never shows per-day detail, and #294's `day`/`week` detail serves the Assistant's text answers only. An unplanned type shows "not planned" instead of a difference.
Also decided: collapsed Two lanes start at the first Planned Block's hour (not by Task Type name); "Show larger" shows the full range.

## Round 4 — several cards in one conversation

Deep link: `timebox://prototype/assistant-cards?card=conversation`. Four turns: one Block Card (both), one Task Type Card (both), three Actual Block Cards (Tue/Wed/Thu), and a planned Block Card with a Task Type Card.

Question: for a response with 2–3 cards, **Stacked** (all cards in reading order before the answer) or **Swipe between** (one card at a time with labelled tabs and page dots)? A one-card response is identical in both.

Verdict (27 Sep 2026): **Swipe between.** Responses with 2–3 cards show one card at a time with labelled tabs and page dots; the answer text stays close to the question.

## Direction

- **Block Card**: shows exactly the lane read. A single lane is a plain row list (lane-coloured start time, Task Type · duration, "total · this day" for midnight crossings, "running · Xm at read"). Both is **Two lanes**: a mini Day with Plan and Actual columns, collapsed by default and starting at the first Planned Block's hour, with "Show larger" for the full date. Future dates show the plan, with a line saying recurring work isn't included. Open Day.
- **Task Type Card**: one bar per top-level Task Type, expandable into children, with duration and share of the lane. Both is **Paired bars** (plan outline over actual fill) with "actual / plan · difference", or "not planned". Totals only. Notes for a Task Type filter, a partial current range, and future ranges. Open Trends.
- **Several cards**: swipeable, with a tab per card labelled by its date or range.

## Limitations and coverage gaps

Not yet exercised: large font scale and TalkBack order across swiped cards, dark theme, empty Task Type ranges, historical cards from stored conversations, and a running block in Two lanes beyond the "· now" label. Carry these into #296's implementation checks.


## Production implementation

Android negotiates `activity_cards_v1` for schema 1–3 and up to three ordered cards. Historical schema-1 plan cards retain their renderer. Cards keep their captured read time; neither descriptions nor Supporting Notes enter the Android display model. Task Type cards sum period buckets by path and use the returned hierarchy totals without adding descendants twice.

The collapsed two-lane view starts at the first Planned Block hour and explicitly notes hidden earlier time. Show larger reveals the full date and full textual rows, including tiny and midnight-crossing Blocks. Offscreen carousel pages are excluded from accessibility traversal; date tabs provide an alternative to swiping.

Open Day selects the captured date. Open Trends selects the captured custom range. Trends itself still only accepts dates through Today, so future-containing cards explain this existing destination limitation. Future actual-only reads retain their explicit unavailable state, as returned by the read contract, rather than inventing Planned Blocks that were not read.

After review approval, both sample destinations and their debug-only fixtures were removed. The production-card regression fixtures live only in `androidTest`; the historical deep links above are design evidence and no longer resolve in the application.
