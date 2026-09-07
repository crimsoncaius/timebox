# Issue #90: Android saved Planned Block nearest-available dragging

Source: https://github.com/crimsoncaius/timebox/issues/90

## Confirmed decisions

- Android only. Apply this behavior to saved Planned Block movement on the
  ordinary Day timeline, outside Plan Mode. Do not change Actual Block movement,
  resizing, web behavior, or saved Block gesture availability in Plan Mode.
- A saved Planned Block preserves its full duration and the user's grab offset.
  A valid ordinary drag destination remains unchanged. For an invalid
  destination, search the selected day's full configured displayed range,
  including offscreen hours but excluding hidden hours and other dates. Choose
  the closest valid start; choose the later start on an equal-distance tie.
- A valid placement stays in the displayed range and overlaps no other Planned
  Block. Adjacent endpoints are valid. Actual Blocks do not obstruct placement,
  and the moving Planned Block is excluded from its own collision check. Never
  move another Block to make room.
- Normal valid movement continues to use five-minute increments. Conflict
  resolution may align exactly with arbitrary-minute Block boundaries.
- The dragged Block stays under the finger and displays its resolved start and
  end time. Do not jump the timeline to an offscreen result; retain ordinary
  top/bottom edge scrolling.
- Preview and release must agree. If the previewed range becomes unavailable or
  the save fails, reject the move, preserve the original Planned Block, refresh
  the Day as appropriate, and explain the failure. Never silently choose a new
  range at release.
- A Planned Block linked to an Actual Block remains movable. Moving the plan
  never changes the Actual Block, which remains the durable record of work.
- Accessible saved-Block movement actions use this same nearest-available
  policy. An occupied five-minute target resolves as it would for dragging,
  rather than failing or producing an overlap.
