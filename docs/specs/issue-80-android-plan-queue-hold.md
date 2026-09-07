# Issue #80: Android planning queue hold-to-pick-up

Design agreed during the issue #80 grilling session.

## Problem and target

In Android Plan mode, dragging a Ready to Plan Battle Plan Task from the
planning queue onto the timeline currently feels possible only after moving
the finger left. The report's "Left Shift" means finger movement, not a
keyboard modifier.

The queue card previously used `detectImmediateHorizontalDragGestures` in
`android/app/src/main/java/com/timebox/android/ui/day/PlanMode.kt`.
This established a horizontal start requirement even when the user intended
to drag vertically or diagonally.

## Agreed interaction

- Holding still on a queue task picks it up using Android's standard
  long-press timing.
- Pickup immediately shows the carried block and gives a small vibration,
  before the finger starts moving.
- Once picked up, the task follows movement in any direction. No initial
  leftward or horizontal movement is required.
- Movement that starts scrolling before the hold completes cancels pickup
  and keeps normal queue scrolling.
- Releasing over a valid timeline destination creates a planning draft there.
- Releasing outside a valid destination returns the task to its original
  queue position without scheduling anything. Holding and releasing without
  moving has the same result.
- Cancelling the gesture clears the carried block and destination feedback
  without scheduling anything.

## Scope and existing behavior

This changes activation for Android planning queue tasks. Keep the existing
destination validity, placement, draft duration, and accessible scheduling
rules. Use the carried-block and destination-outline presentation already
approved in issue #81.

## Acceptance scenarios

1. A stationary hold shows pickup feedback at the system long-press threshold.
2. After pickup, the initial movement can be left, right, up, down, or diagonal.
3. A quick swipe scrolls the queue without picking up a task.
4. A quick press and release does not start a drag or create a draft.
5. A valid drop creates one draft at the existing calculated destination.
6. An invalid drop, stationary release, or cancellation creates no draft and
   clears all drag feedback, leaving the task in its original queue position.

## Implementation

The queue now uses Compose's `detectDragGesturesAfterLongPress`. Its pickup
callback immediately starts the carried-block presentation and long-press
haptic. Existing placement and cancellation callbacks remain in use. The
unused horizontal-only detector was removed.

## Verification

- The vertical-first placement regression failed before the fix.
- All 146 Android unit tests passed.
- All 29 Plan mode and Day timeline gesture emulator tests passed, including
  stationary pickup, movement in six directions, cancellation, early scrolling,
  valid placement, and occupied-destination rejection.
- Rebuilt and installed the final debug APK, relaunched MainActivity, and
  opened Plan mode on the emulator. The local planning queue is currently empty.
