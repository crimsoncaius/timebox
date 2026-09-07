# Issue #85: Android nearest available drag placement

Source: https://github.com/crimsoncaius/timebox/issues/85

All product questions raised in the design discussion are resolved. The user
confirmed the scope by requesting implementation on 2026-09-07.

## Confirmed decisions

- Android only.
- Keep the change within Plan mode: dragging a Ready to Plan Battle Plan Task
  from the planning queue and moving unsaved planning drafts.
- Saved Planned Block movement outside Plan mode is tracked separately in
  https://github.com/crimsoncaius/timebox/issues/90. Do not enable saved Block
  dragging inside Plan mode as part of this issue.
- Resizing is covered by a separate issue and is outside this change.
- Search the selected day's configured displayed time range without a distance
  limit, including portions outside the current scroll viewport. Exclude hours
  hidden by the day settings; never search another date. Clearly display the
  resolved placement time.
- Choose the valid start closest in time to the start implied by the drag,
  preserving the grab offset. Choose the later start on equal-distance ties.
- Preserve the full duration. Never shorten the dragged draft or push other
  Planned Blocks or drafts to make room.
- When resolving an invalid destination, permit exact alignment with
  arbitrary-minute block boundaries, such as placing a 30-minute draft in a
  10:02-10:32 gap. Keep normal five-minute drag behavior at already-valid
  positions.
- If no space fits, show "No available space in this time range" and make no
  placement change on release. A queue task stays queued; a moved draft keeps
  its original placement.
- Limit the new snapping behavior to dragging. Accessible task selection and
  tapping a time retain their existing placement behavior.
- Update the preview as availability changes during dragging. If its destination
  becomes unavailable just before release, reject the drop with "That time is
  no longer available" and preserve the original placement or queue state.
  Never commit a different position that the preview did not show.
- Keep the carried block under the user's finger and show the resolved start
  and end time on it. The timeline placement preview uses that same range.
  Keep existing top/bottom edge scrolling. Do not add a separate offscreen
  destination indicator or automatically jump to the snapped destination.

## Existing placement behavior

- New queue placements create 30-minute planning drafts. Draft moves retain
  their duration and the point at which the user grabbed the block.
- Plan mode permits draft dragging, but disables saved Planned Block gestures.
- Placement currently respects the day's configured displayed time range,
  checks saved Planned Blocks and other drafts, and permits adjacent endpoints.
  Actual Blocks do not obstruct Planned Block placement.
- New placements use five-minute start increments. Draft moves use five-minute
  movement increments, retaining any original minute offset.
- Dropping a draft on the planning queue returns it to the queue. Cancellation
  and invalid destinations otherwise leave placements unchanged.
- During an active drag, holding near the timeline viewport's top or bottom
  edge already scrolls the timeline. Snapping can select an offscreen time even
  when the pointer is not near an edge.

## Placement and interaction contract

- First retain the normal drag candidate if it is valid. Only an invalid
  destination requires nearest-available resolution.
- A valid placement must fit the full duration inside the selected day's
  configured displayed range, overlap neither saved Planned Blocks nor other
  same-date drafts, and preserve the existing minimum duration. Exclude the
  draft being moved from its own collision check. Touching endpoints is valid.
- Resolve destinations only while the pointer is in the existing timeline drop
  area. Searching the full configured range does not turn the queue or space
  outside the timeline into a scheduling destination.
- Preserve returning a draft to the queue, cancellation, long-press pickup,
  ordinary click/selection/scrolling, and the existing planning save lifecycle.
- Preview and drop must agree. Successful release creates or updates a planning
  draft at the previewed range; it does not independently save the plan.

## Acceptance scenarios

1. A queue drag at an already-valid time creates a 30-minute draft at the normal
   candidate, with no additional adjustment. A valid draft move retains its
   duration and existing five-minute movement behavior, including minute offset.
2. An occupied destination resolves to the closest fitting start. If 09:30 and
   10:30 are equally close to an intended 10:00 start and no closer start fits,
   choose 10:30.
3. A 30-minute draft can resolve into an exact 10:02-10:32 gap. A longer draft
   cannot use that gap by shortening itself or moving another Block.
4. Saved Planned Blocks and other drafts obstruct placement. Actual Blocks do
   not. The moving draft does not obstruct itself; adjacent endpoints are valid.
5. Searching an 08:00-22:00 configured day may find a destination beyond the
   scrolled viewport, but never before 08:00, after 22:00, or on another date.
6. For an offscreen destination, the carried block shows the resolved time
   range. Holding near an edge still scrolls; snapping itself does not jump the
   viewport. The timeline preview and release use the displayed destination.
7. If no space fits, show the agreed no-space message and preserve the queue
   task or original draft on release.
8. If availability changes, refresh the preview during dragging. If the last
   previewed destination becomes invalid immediately before release, reject
   the drop with the agreed availability message; do not silently choose another.
9. Returning a draft to the queue, releasing outside a scheduling destination,
   and cancelling retain their existing behavior. Tap placement, resizing,
   saved Block gestures, and web behavior remain outside this change.

## Validation and delivery

Verify nearest-fit selection and the boundary cases above with focused Android
logic tests, and verify drag feedback, release, cancellation, and edge scrolling
with appropriate UI coverage. After implementation, rebuild and launch Android
from the updated working tree and leave Plan mode available for review, as
required by AGENTS.md.

## Implementation

- Android Plan mode now resolves drag candidates against gaps between saved
  Planned Blocks and same-date drafts. It preserves duration and valid candidates,
  and chooses the later start on equal-distance ties.
- The carried block displays the resolved time range. Release uses that range;
  it does not calculate a new destination from the release event.
- Drag releases pass through a separate planning-session action that validates
  the range against the current Day and current drafts. Tap placement and resize
  actions retain their existing behavior.
- Standards and Spec reviews found no remaining actionable issues after fixing
  release-time validation to use authoritative current state.

## Verification results

- All 155 Android unit tests passed in the updated working tree.
- All 19 Plan mode emulator tests passed, including queue snapping, draft moves,
  no-space feedback, offscreen destination text, edge scrolling, and a schedule
  change after preview but before release.
- The full Android emulator suite finished with 119 passes and one unrelated
  SettingsNotificationTest activity-cleanup timeout in ActivityScenario.close.
- An isolated copy of the staged commit built successfully and passed its
  focused logic tests and all 16 selected emulator tests: its 15 Plan mode tests
  plus a successful rerun of the unchanged notification-settings test.
- Reinstalled the APK from the updated working tree, launched MainActivity,
  and opened Plan mode on emulator-5554. The current planning queue is empty.
- Visual evidence and test reports are in artifacts/issue-85/ locally.
