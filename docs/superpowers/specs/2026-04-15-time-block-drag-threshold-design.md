# Time Block Drag Threshold Design

## Summary

Fix the same-lane time block move interaction so a dragged block no longer oscillates between adjacent legal landing zones. Introduce a stable preview outline and a hysteresis threshold so the dragged block only switches to a new landing position after the pointer clearly commits to crossing into that new zone.

## Problem

When a user drags one time block past another block in the same lane, the moving block currently resolves to the nearest legal start on every pointer move. Near the boundary between two legal landing zones, tiny pointer movements can alternate which zone is "nearest", causing the block to visibly jump back and forth.

This produces two UX problems:

- The drag target feels unstable and unpredictable.
- The user cannot clearly tell where the block will land before release.

## Root Cause

The current move flow computes a raw candidate start from pointer Y, then immediately resolves that candidate to a legal start using same-lane collision rules. That nearest-valid resolution runs on every pointer move and directly drives the rendered block position.

Because the render position is coupled to nearest-valid resolution without any hysteresis, the block can repeatedly flip between:

- the last valid landing zone above a blocker, and
- the next valid landing zone below that blocker

when the pointer hovers around the boundary.

## Goals

- Eliminate left-right or up-down jitter when dragging past blockers in the same lane.
- Show a clear anticipated landing position while the drag is in progress.
- Preserve the current rule that blocks may not overlap other blocks in the same lane.
- Keep the interaction responsive and slot-based.
- Handle too-small gaps without letting the preview appear to fit where the block cannot actually land.

## Non-Goals

- Changing resize behavior.
- Allowing overlaps during drag.
- Supporting partial-fit drops or automatic duration shrinking.
- Reworking cross-lane dragging.

## Proposed Interaction

### High-Level Behavior

During a move drag, separate the user's raw pointer position from the block's rendered landing preview.

The system will track:

- `rawCandidateStart`: slot-aligned start derived from pointer Y.
- `previewStart`: the currently committed legal landing start shown on screen.
- `previewRange`: the valid landing zone that owns the current preview.

The block outline shown during drag uses `previewStart`, not `rawCandidateStart`.

### Hysteresis Threshold

When the pointer approaches a boundary between the current legal landing zone and another legal landing zone, do not switch immediately. Instead, require the pointer to move past the boundary by a small threshold before the preview commits to the new zone.

Recommended threshold:

- Use a pixel threshold equal to roughly 25% of a timeline slot height.
- With the current slot height, this is approximately 11-12 px.

This creates a dead-band around the switching boundary:

- Inside the dead-band, keep the current preview stable.
- Beyond the dead-band, switch to the next legal landing zone.

## Preview Model

### Stable Outline

While dragging:

- Keep the dragged block visually attached to the pointer interaction.
- Render a stable outline or ghost at the committed `previewStart`.
- Do not let micro-movements change the outline position unless the threshold has been crossed.

The current block shell can continue to act as the moving visual, but its position should now reflect the committed preview target rather than the instantly re-resolved nearest-valid slot.

### Anticipated Placement

The preview should answer: "If I release now, where will this block land?"

That means:

- release commits `previewStart`
- preview changes are discrete and intentional
- pointer motion alone does not imply a valid switch until threshold rules are satisfied

## No-Fit Gap Behavior

If the pointer is over a gap that is too small to fit the block's full duration:

- do not render the preview inside that gap
- keep the preview snapped to the nearest valid landing zone
- still require threshold crossing before switching from the current preview zone to another valid zone

This preserves the user's chosen behavior:

- nearest valid landing remains the model
- unstable instant switching is removed

If there is only one legal landing zone, keep the preview there for the full drag.

If the pointer moves through an invalid region between two valid zones:

- remain on the current preview zone until the pointer crosses the target zone's threshold boundary
- then switch once, cleanly

## Decision Rules

For same-lane move drag:

1. Compute `rawCandidateStart` from pointer Y and slot-flooring.
2. Compute the set of valid start ranges for the dragged block's duration.
3. Determine which range currently owns `previewStart`.
4. If `rawCandidateStart` stays within the current range plus hysteresis margin, keep `previewStart` unchanged.
5. If `rawCandidateStart` crosses beyond the margin toward an adjacent valid range, switch `previewStart` to the nearest start in that adjacent range.
6. On release, patch the block using `previewStart` and `previewStart + duration`.

Tie-breaking rules:

- Favor staying in the current preview zone until the threshold is definitely crossed.
- Only consider adjacent valid ranges in the drag direction for switching.
- If no valid range exists in the drag direction, keep the current preview.

## Data and Code Shape

### Time Helpers

Add move-preview-specific helpers near the existing same-lane move logic:

- a helper that returns valid start ranges for a block duration
- a helper that determines whether the current preview should stay or switch
- a helper that applies hysteresis based on current preview, raw candidate, and drag direction

Keep the existing collision and slot rules centralized in `frontend/src/lib/time.ts`.

### Component State

Update move drag state in `frontend/src/components/TimeBlockCard.tsx` so it can represent:

- the raw pointer-derived candidate
- the committed preview start
- the previous preview start for direction-aware switching

The rendered block position during move drag should come from committed preview state, not from instantaneous nearest-valid resolution.

## Visual Design

The preview should remain visually calm and consistent with the current timeline language:

- retain the existing drag emphasis treatment
- add an outline/ghost interpretation through stable positioning rather than flashy decoration
- avoid introducing loud invalid colors for too-small gaps since the behavior stays snapped to a legal target

The important visual change is stability, not ornament.

## Testing

Add unit coverage in `frontend/src/lib/time.test.ts` for:

- staying in the current zone while candidate motion remains inside hysteresis margin
- switching downward only after crossing threshold
- switching upward only after crossing threshold
- moving through a too-small gap without previewing an impossible placement
- behavior when only one valid landing zone exists

Add interaction coverage in component or E2E tests for:

- dragging near a blocker boundary does not oscillate
- dragging far enough past the blocker switches once to the next legal landing zone
- release commits the stable preview location

## Risks

- If the threshold is too small, jitter may still be visible.
- If the threshold is too large, users may feel that the block is lagging behind intent.
- Mixing raw candidate and preview state incorrectly could cause release behavior to differ from the visible outline.

## Recommendation

Implement hysteresis-based switching with a stable committed preview. This solves the current oscillation at its source, gives the user a clear anticipated landing position, and handles too-small gaps without ever previewing an impossible fit.
