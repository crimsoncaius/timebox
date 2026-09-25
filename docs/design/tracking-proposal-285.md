# Assistant Tracking Proposal card (#285)

Status: implemented on `codex/tracking-proposals-285`, pending review. Design settled 25 Sep 2026 with the in-app prototype on `codex/prototype-tracking-proposal` (local; its deep links and bar are not part of this branch). Prototype on `codex/prototype-tracking-proposal`.

## Settled (from #285 and ADR 0014)

- One Tracking Proposal per reply, in the Assistant Conversation; nothing applies without a tap.
- Action label (Start / Switch / Stop), live elapsed time and impact summary derive from local tracking state.
- Change = correct in chat, or open the prefilled Switch/Start sheet. Choice chips fill the card; they do not apply it.
- Invalid states include "You're already tracking X." Cards expire 15 minutes after proposal.
- Applied cards follow the Actual Block, then freeze; View in Day. Undo lives only in Transient Feedback.
- Retained as Applied, Expired or Dismissed.

## Round 1 — where does the proposal live, and how much does it show?

Deep link: `timebox://prototype/tracking-proposal?variant=A|B|C&scenario=switch|start|now|stop|choices|already|invalid|history`.

- **A · Inline card**: text-only card in the plan-card slot, before the answer. Kicker, activity, time + live elapsed, impact lines, Dismiss / Change / primary action.
- **B · Timeline card**: A plus a Now/After strip over the affected window, echoing the switch sheet's preview.
- **C · Docked tray**: the conversation holds a one-line reference; the live proposal docks above the composer until resolved, then the reference becomes the retained receipt.

Stable across variants: Assistant chrome, scenarios, data, Undo toast, stand-in Change sheet.
The yellow bar is comparison-only: variant, scenario, +5 min (drives expiry), Change elsewhere
(stops/starts tracking to show the live label and applied freeze), Reset, and the current tracking state.

Limitations: local state only; minutes-of-day sample (no midnight crossing); View in Day and
Change are stand-ins; Undo toast is a lookalike, not `UndoNoticeHost`.

Verdict (25 Sep 2026): **A, with B's Now/After strip only when the proposal reaches back** — it replaces
earlier recorded time, moves the Current Activity's start earlier, or fills unrecorded time. Most proposals
are "from now" or a few minutes back, where one impact line suffices; the strip earns its space only when
history is overwritten, mirroring the switch sheet's preview. C was rejected: a permanent tray above the
composer splits the proposal across two places. B and C remain in commit `9aebb9c7`; the prototype now
renders the decided card for every scenario.

## Round 2 — Undo and expiry (decided 25 Sep 2026)

- After Undo the card returns to **Pending** if it has not expired, so it can be confirmed again; the
  action label and impact recompute from tracking state at that moment. Undo means "as if it never happened".
- Expiry shows only in the last five minutes ("Expires in 4 min"); earlier it competes with the ticking elapsed time.

## Round 3 — View in Day highlight

Finding: the block reached from View in Day is almost always tiny — seconds tall for "from now", a sliver for
"10 min ago". Any in-block treatment is clipped away, so emphasis draws outside the block with a minimum height.

Day opens at the Now Line (existing scroll-to-now). Comparison, all in the real `DayScreen` with sample state
via a null-by-default `LocalBlockEmphasis` hook in `BlockCard`:

1. **Pulse** — ring and tint pulse twice, then fade (~3 s).
2. **Hold until touch** — ring stays until the first touch anywhere in Day.
3. **Hold + callout** — as 2, plus a "Meals · from 12:40" label above the block's start.

Deep link adds `&highlight=pulse|hold|callout`. Limitation: Day's tracking control reads the real (isolated,
offline) activity repository, so its header shows an attention state unrelated to the sample.

Verdict (25 Sep 2026): **3 · Hold + callout.** The block is usually too small to recognise by shape, so the
label ("Meals · from 12:40") carries the identification; holding until the first touch means it never
outlasts attention and needs no dismissal. The callout may briefly overlap the block above; accepted as transient.

## Round 4 — manual Start sheet with an earlier instant

The production switch timeline already handles nothing running: records are cut at the selected instant and the
preview fills through now. The Start sheet reuses `SwitchActivityTimeline` (new `afterLabel`, "AFTER START") and
`ActivitySelectionFields`; the summary names cut or replaced records, filled unrecorded time, and "You can Undo."
Change on a Start proposal opens it prefilled; Change on a Switch proposal opens the production Switch sheet.

Open trade-off: today's Start is quick (type → Start). Compared via `&start=timeline|collapsed` or the bar:

- **A · Timeline shown** — parity with the switch sheet, as requested in #285.
- **B · Collapsed** — "Starts now · Started earlier?" expands the same timeline. Prefilled earlier starts open expanded.

Verdict (25 Sep 2026): **B · Collapsed.** Most Starts are "now", so starting stays two taps; the earlier-start
flow is one tap away and opens automatically when a proposal carries an earlier instant. The switch sheet keeps
its always-visible timeline because choosing when is the point of switching.

## Integration walkthrough (25 Sep 2026)

Walked choices → confirm, Switch from now, Start since 12:00 → Change (prefilled, expanded), manual Start
(collapsed), confirm → View in Day → back, confirm → Undo, and retained history. Resolved:

- A "from now" Switch must name the running activity in its impact ("Timebox ends at 12:50"), not "Starts at".
- Choice chips stay visible, the picked one selected, until confirmation, so a wrong pick is corrected in place.
- View in Day opens at the Now Line only while the resulting block runs; for a frozen card it scrolls to the
  block. Not prototyped (sample Day has no scroll-to-block); carried into implementation.

## Remaining limitations

Local sample state; no midnight crossing in the card sample (the production timeline handles it); Undo toast is a
lookalike; Stop via Change is a stand-in; Day's tracking control reads the isolated repository.
- Manual Start sheet with earlier instant: check the nothing-running preview (later round).

## Build and review

`./scripts/android-gradle.ps1 :app:assembleDebug '-PreviewApplicationIdSuffix=.proposalprototype' '-PreviewApiBaseUrl=http://10.0.2.2:9/'`
Package `com.timebox.android.proposalprototype`; its API target is a dead port, isolating real data.
Screenshots: `artifacts/tracking-proposal/`.

## Implementation

- **Server.** Start accepts an earlier instant with switch-style replacement and the same `switch_undo` restore data;
  snapshots advertise `start_history_ready`. Clients declaring `tracking_proposal_v1` get `propose_tracking`
  (flat arguments; Task Type Paths offered as the only allowed values). The server resolves stated times at sending
  in the Reporting Time Zone, returns a `tracking_proposal` event before the answer, and stores it with the attempt
  (migration 035) so later turns see it as unconfirmed context. Malformed tool calls reach the model as an error to
  explain. After a proposal the answer is plain text; a none-selector glued to text is accepted.
- **Android.** `StartTrackingSheet` is collapsed ("Starts now · Started earlier?") and reuses the switch timeline
  ("AFTER START"). Proposal cards derive action, impact and strip from local tracking state (`deriveProposal`) and
  confirm through `ActivityRepository.command`, so they work offline and share Undo. The Assistant route is an
  Undo context. `TrackingHandoff` carries prefilled sheet requests, their results, and Day landings; Day marks the
  landed block (hold + callout, below the block when there is no room above) and scrolls to a finished one.
- **Real-model findings.** The flash model omitted paths, nested or flattened time fields, attached paths to Stop,
  and sometimes claimed a proposal without calling the tool. Enumerated paths, flat arguments, tolerant Stop,
  worked examples and a "never claim without calling" rule fixed every case in a ten-phrase sweep.

## Validation

Backend suite (all activity, assistant and proposal tests) and 341 Android unit tests pass. Live review against an
isolated API (port 12085, `artifacts/tracking-285.sqlite`) with the real model: earlier manual Start accepted and
offered Undo; "I've been eating for 10 min" produced a live Switch card; confirm → Undo restored the exact record and
returned the card to pending; View in Day marked a zero-minute block; a reaching-back proposal showed the strip and
its replacements; Change opened the prefilled Switch sheet. The instrumentation APK still has the known unrelated
`BattlePlanScreenTest` compile failure, so device tests were not run.
