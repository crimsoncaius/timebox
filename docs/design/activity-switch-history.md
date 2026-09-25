# Activity switches across recorded history

Status: Behavioral decisions agreed. User requested the prototype; three browser variants are available for review. Production implementation awaits the interaction choice.

## Settled decisions

- Android and web support choosing a switch time earlier than the Current Activity's start.
- The new activity replaces Actual time from the chosen instant onward, including earlier recorded activities and unrecorded gaps. Earlier portions remain intact.
- Example: A ran 09:00–10:00 and B is running from 10:00. Switching to C at 09:30 leaves A at 09:00–09:30 and C running from 09:30; B is replaced entirely.
- Dragging the switch line near the viewport edge should continue scrolling into earlier time, including previous days. Display the date clearly across midnight.
- Activity switches offer Undo through the existing Undo presentation mechanism and its existing availability lifecycle.
- Undo atomically restores every affected record, gap, and original detail. The former Current Activity continues as though the mistaken switch never happened, including time elapsed before Undo.
- Only the latest switch is undoable. A subsequent switch or a change that conflicts with restoration invalidates that opportunity; ordinary passage of time does not.
- Switching and Undo both work offline. Offline Undo must restore the local state immediately and synchronize correctly later.
- Expanded historical selection applies only to switching. Stopping tracking retains its existing bounds.
- Switching must remain a quick flow. The replacement preview and confirmation interaction remains open for a later prototype; an additional dialog is not an accepted requirement.

## Open decisions

- Prototype treatment of historical replacement and its confirmation.

## Existing behavior

The Android switch timeline has a fixed three-hour viewport and two-hour navigation steps. Both clients and the backend also enforce historical switch bounds. Historical crossing therefore requires changes beyond the timeline UI.

Record Actual from a Planned Block already supports atomic replacement Undo that restores affected records and tracking continuity. Its separate overlap-confirmation policy is documented in `docs/specs/issue-173-record-actual-as-planned.md`; the switch flow's interaction is still undecided.

The shared Undo presentation offers one opportunity with ten seconds of visible exposure, platform accessibility/pause behavior, and Retry after recoverable failures. Navigation or restart does not retain the opportunity. Existing recorded-Actual restoration is server-backed; offline switch Undo requires extending the activity journal behavior, not merely reusing that endpoint.

## Throwaway prototype

Branch: `codex/prototype-activity-switch-history`.

Run `npm --prefix frontend run prototype:switch` and open <http://127.0.0.1:12064/day/2026-09-25?variant=A>. Use the floating arrows (or Left/Right outside fields) to compare:

- A — Live preview: Planned/After timeline, short inline impact summary, optional details, one Switch action.
- B — Before / after: wider Recorded/After comparison with the affected-record list always visible, one Switch action.
- C — Review step: choose the boundary, then review affected records before confirming historical replacement. Ordinary switches remain one action.

The prototype lives beside Activity Tracking, uses the existing Day route and Layout, and reuses `UndoNotice`. It uses only in-memory sample records. Its dedicated Vite mode prevents API calls from reaching a real backend. Production builds exclude the prototype route and bundle.

Try the long-activity/gap and overnight scenarios. Drag the line and hold near an edge, or enter an exact start time. Switch, advance the sample clock, then Undo within the existing notice window. The state disclosure shows records, preview, and Undo availability. Switching variants resets the fixture.

Verification: production build; browser interaction checks for held-edge autoscroll beyond the initial window, historical replacement, gap fill, exact metadata restoration and running continuity after elapsed time; all three variants; desktop and 390px layouts. Offline is a local simulation, not a persistence or reconciliation test. This is a responsive browser prototype, not an Android build.

Verdict: pending user review. A is the initial recommendation because it keeps one confirmation while showing the effect during dragging. No variant has been selected or promoted to production.
