# Issue #141: Manual Block collision resolution

Source: https://github.com/crimsoncaius/timebox/issues/141

Status: the user confirmed the complete design and requested implementation
on 2026-09-13; the final revision removes the distance threshold from both lanes.

## Confirmed decisions

- Creating or moving a Planned Block into occupied time resolves to an
  available placement. Existing Blocks stay fixed.
- Choose the available start closest to the intended start. Choose the later
  start when distances are equal.
- Preserve the full duration when creating or moving a Block. Skip gaps that
  cannot fit it.
- Resizing stops the dragged edge at the neighboring Block's boundary.
  Creating and moving instead find an available slot.
- Apply the closest-start policy on web and Android to new placements, draft
  moves, and saved Planned Block moves, replacing web directional selection
  where it differs.
- Search the selected day's configured displayed time range, including offscreen
  hours. There is no distance threshold for either lane. The user explicitly
  removed the earlier 30-minute limit on 2026-09-13.
- Reject only when no full-duration placement fits within the allowed range.
  Show "No available space in this day", preserve a moved Block's original
  placement, and leave a new task unplaced.
- While a task is selected for placement, clicking or tapping occupied time
  places that task at the nearest fitting location. Otherwise existing Block
  clicks retain their usual behavior.
- Collision resolution may align to exact minute boundaries, including fitting
  a 30-minute Block into a 10:02-10:32 gap. Resizing likewise stops at exact
  neighboring boundaries. Preserve ordinary increments at valid destinations.
- During dragging, preview the resolved placement and show its start and end
  time. Retain edge scrolling without automatically jumping the view. After
  click/tap placement, bring the resulting Block into view.
- If the previewed destination becomes occupied before saving, preserve the
  original placement or leave the new task unplaced, refresh availability,
  and explain the conflict. Never silently save a different destination.
- Resizing either supported edge stops at the first neighbor, even if the
  pointer passes several Blocks. Keep the opposite edge fixed and respect
  existing minimum duration and configured day boundaries. Creating and moving search for a fitting gap; resizing clamps its edge.
- Saved Planned Blocks and unsaved planning drafts obstruct placement. Actual
  Blocks do not. Exclude the moving Block from its own collision check.
- Apply nearest-space behavior to timeline clicks/taps, dragging, and accessible
  movement actions. Explicitly typed start/end times retain validation behavior;
  automatic recurring placement retains its existing rules.

## Existing related decisions

Android drag placement is already specified in
[issue #85](issue-85-plan-drag-snapping.md) and
[issue #90](issue-90-saved-planned-block-drag-snapping.md).
Issue #141 extends collision handling to click/tap placement and resizing
and establishes the same closest-start policy on web and Android. The final policy retains same-day unlimited search from those earlier
Android specifications.

## Boundary examples

- A requested 14:00 start may resolve to any fitting slot in the displayed day;
  choose the closest start and prefer later starts on ties.
- Skip gaps that cannot fit the full duration. Reject when the day has no fit.
- Actual Blocks cannot extend beyond now, even when later displayed hours are empty.

## Implementation

Web and Android now resolve manual placement across the displayed day. Selected-task clicks/taps take priority over opening occupied cards.
Web queue dragging displays a resolved preview; successful release uses that
same range. Both clients preserve exact boundaries when resizing and revalidate
placements at release/save. Android draft edits validate the current saved Day,
including changes received since the planning session began.

Explicit time editing and recurring placement retain their separate policies.

## Actual Block extension

The user accepted extending the same behavior to manual Actual Block creation,
movement, and resizing on both clients. Each lane considers only its own Blocks
as obstacles. Closest-start selection, later ties,
full-duration preservation, exact neighbor resize boundaries, and preview/release
agreement apply to Actual Blocks too. Existing minimum durations remain in force.

Activity Tracking retains real timestamps. Running Actual Blocks remain fixed;
manual placement is restricted to elapsed time, excluding future dates and time
after now. Ordinary Actual Block taps still open editing, and explicitly typed
times retain validation rather than automatic adjustment. There is no Actual
Ready-to-Plan placement mode.

## Verification

- Web: 25 focused unit/component tests passed; production build and changed-file
  lint passed. Four browser integration tests passed, covering occupied-card
  clicks, queue-drag preview/release,
  nearest saved moves, exact-boundary resizing, reload persistence, and
  rejection beyond the limit.
- Android: 23 planning unit tests and 42 timeline/planning gesture tests passed,
  including occupied taps, saved moves, exact resize boundaries, rejection,
  cancellation, scrolling, and a last-second availability conflict. The final
  feedback build also passed three focused emulator smoke tests.
- Existing broad-suite failures involving legacy Work Mode and Actual Block
  identity were reproduced against unchanged commit 080c3e5. They are outside
  this placement change.

Local evidence and test reports are in `artifacts/issue-141/`.
The review web instance runs from this working tree at http://127.0.0.1:12010,
using the existing local API. Browser integration used an isolated SQLite API
on port 12011. The updated Android APK is installed on emulator-5554.


Actual extension verification: 31 focused web tests passed, production build and
changed-file lint passed. The Android planning unit suite passed (25 tests); five
final emulator gesture checks passed, including Actual nearest moves, distant
rejection feedback, exact resize boundaries, and Planned regression checks. The
existing unrelated Actual identity test remains failing in the broader web suite.
The updated app was relaunched on Pixel_9a (emulator-5554) in the Day surface.

## Threshold removal and Android release jitter

The user confirmed removing the distance threshold from both lanes. Tests now
cover distant fitting slots and full-day rejection instead of distance rejection.

A regression test reproduced an Actual move briefly reverting from 08:15 to
08:00 before returning to 08:15. DayViewModel's activity projection overwrote the
optimistic move with its older snapshot. Pending manual Actual moves now remain
visible while the activity journal catches up, and the pending override is cleared
when the operation resolves. The test covers both pending and acknowledged states.
A separate emulator check verifies timeline position at release and after saving.

Final validation: 31 focused web tests and 26 Android unit tests passed. Web
production build passed. Local reproduction command:
`scripts/android-gradle.ps1 :app:testDebugUnitTest --tests '*DayWorkModeViewModelTest.Actual drop*'`.

Five final emulator checks passed, covering stable release, distant placement in
both lanes, Actual resize boundaries, and planning edge scrolling. The final app
was relaunched on Pixel_9a (emulator-5554); the web review remains on port 12010.

## Whole-timeline jump after dropping a Block

The user clarified that the entire Android viewport jumps briefly and returns,
separate from the Actual Block position regression above. The Day pager mounted
previous, current, and next days with one shared ScrollState. A background Day
refresh could measure different displayed hours and clamp that shared scroll
position, affecting the visible timeline.

Only the visible day now owns the primary ScrollState. Neighboring pages use
independent scroll states that mirror the visible position within their own
bounds, including when a newly loaded page changes height. Their measurement can
no longer alter the current viewport.

An emulator regression drops a saved Block into occupied time, refreshes a shorter
neighboring day during the save, and checks the now-line position over 24 frames.
It fails with the old shared state and passes with independent preview states.

The final Android build passed six emulator checks: the frame-by-frame drop
regression, initial positioning, horizontal gesture arbitration, next/previous day
swipes, and canceled swipes. The updated app is open on Pixel_9a (emulator-5554).

## Unresolved reporter-confirmed viewport bug

After retesting, the user confirmed the rapid whole-timeline jump toward 10 PM
and back is still present. The preceding focused tests and fixes do not establish
that the reporter's symptom is resolved. Remaining cause is unconfirmed.
Follow-up: https://github.com/crimsoncaius/timebox/issues/175.
The user explicitly requested merging the placement work while tracking this
remaining Android bug separately.
