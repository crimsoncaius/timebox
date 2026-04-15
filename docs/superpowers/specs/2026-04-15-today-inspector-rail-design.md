# Today Inspector Rail Design

## Summary

Replace the current conditional right-side block editor on `Today` with a persistent desktop inspector rail.

The current interaction opens block details inside the normal page flow, which changes the width of the timeline area when a block is selected. That layout shift weakens spatial continuity and makes the timeline feel less stable. This design keeps the calendar geometry fixed and treats details as a permanent inspector region whose contents update in place.

## Product Decision

`Today` should use a persistent inspector rail on desktop.

This design chooses:

- a stable two-column desktop layout with the timeline on the left and the inspector on the right
- a minimal empty state when no block is selected
- content swapping inside the rail instead of opening and closing a layout-affecting panel
- continued sheet-style behavior on smaller screens where a permanent rail would be too cramped

## Goals

- preserve the timeline's width and position when selecting a block
- improve flow by making block selection feel like inspection rather than opening a new panel
- keep the interface aligned with the app's quiet editorial design language
- make it faster and calmer to move between blocks
- avoid turning the empty state into another dashboard

## Non-Goals

- redesigning the block form fields themselves in this iteration
- adding summary analytics, charts, or rich day metrics to the empty rail state
- changing the mobile interaction into a permanent two-column layout
- introducing a floating popover editor for block details

## Current Problem

The current detail editor appears only when a block or draft is active and participates in the same layout flow as the timeline content.

This causes a visible horizontal shift in the day view:

- the timeline area becomes narrower when the editor appears
- the clicked block no longer feels anchored in place
- moving between "browse day" and "edit block" feels more disruptive than necessary

The problem is not that a detail view exists. The problem is that opening it changes the geometry of the main object the user is working with.

## Layout Model

### Desktop

Desktop should use a fixed two-column composition:

- left: timeline region
- right: inspector rail

The inspector rail should always occupy space in the layout, whether or not a block is selected. This prevents the timeline from resizing when the interaction state changes.

The rail should feel like part of the page architecture rather than an overlay or utility drawer. It should inherit the same quiet asymmetry and whitespace-driven composition described in `docs/DESIGN.MD`.

### Empty State

When nothing is selected, the rail should remain visually light.

Recommended empty-state content:

- a small section label such as `Details`
- a short line such as `Select a block to edit`
- one secondary hint such as `Click an empty slot to create`

Do not fill the empty state with stats, cards, or secondary workflows in this iteration. The purpose of the empty rail is to preserve layout stability without stealing focus from the timeline.

### Active State

When a block or draft is active, the inspector keeps the same width, frame, and padding. Only its contents change.

This means:

- no desktop slide-in from the page edge
- no change to the timeline column width
- no page reflow caused by selection

The interaction should read as "the inspector is now showing details for this block," not "a panel has opened."

## State Model

The inspector rail should support three states:

1. `empty`
2. `selected existing block`
3. `new draft block`

### `empty`

No selected block and no active draft.

The rail shows only the minimal placeholder content.

### `selected existing block`

An existing time block is selected from the timeline.

The rail shows:

- lane label and time range
- editable task type
- editable note
- relevant actions such as save, cancel, complete, and delete

### `new draft block`

The user clicked an empty slot and created a draft placement.

The rail uses the same shell and structure as the selected state, but the framing is creation-focused rather than edit-focused.

This keeps creation and editing within one mental model rather than treating draft creation as a separate interface.

## Interaction Model

### Selection

When the user clicks a block:

- the timeline stays fixed
- the clicked block receives a clear selected state
- the inspector content updates in place

When the user clicks a different block:

- the inspector updates directly to the new block
- the previous block loses its selected styling
- the rail does not collapse or reset in between

Clicking the same already-selected block again should do nothing in this iteration.

### Closing

Closing the inspector does not remove the rail from the page. It only clears the active selection or draft and returns the rail to the empty state.

This keeps the layout stable and reinforces that the rail is part of the page structure.

### Draft Creation

When the user clicks an empty slot in the timeline:

- a draft block is created as today
- the inspector immediately enters the `new draft block` state
- the timeline remains fixed in place

The new-draft flow should feel like a normal selection flow, not a separate overlay mode.

### Unsaved Changes

If the user has unsaved changes and attempts to select another block or dismiss the draft, the app must not silently discard edits.

Implementation may choose one of these behaviors:

- autosave safe field edits
- show a confirmation step before discarding local edits

This design does not prescribe the final discard strategy, but it does require explicit handling.

## Motion And Transition

The rail itself should feel persistent. Only the contents should transition.

Recommended behavior:

- use a subtle content fade when changing between empty, selected, and draft states
- if any movement is used, keep it extremely small and vertical rather than a lateral drawer motion
- keep transitions restrained, around `120-180ms`

Avoid:

- large slide-in animations from the right edge
- dramatic drawer movement
- auto-scrolling the page on selection
- aggressive autofocus that makes mouse-based selection feel jumpy

The intended feeling is calm content replacement, not panel choreography.

## Responsive Behavior

This should be a desktop-first pattern.

On larger screens:

- keep the persistent inspector rail
- keep the timeline width stable

On smaller screens:

- keep or adapt the current sheet or drawer interaction
- do not force a permanent two-column layout if it would significantly compress the timeline

The design principle remains the same across breakpoints: selecting a block should reveal details in a way that feels orderly and spatially coherent.

## Visual Relationship Between Timeline And Inspector

The selected block should feel visibly connected to the inspector content.

Recommended cues:

- a clearer selected state on the chosen block
- consistent tonal emphasis rather than loud borders
- no heavy modal backdrop on desktop

The goal is to guide the eye from the selected block to the inspector without introducing a theatrical modal effect.

## Accessibility

The inspector should preserve baseline clarity and keyboard usability:

- the inspector region should retain a clear accessible label
- the selected block state should remain identifiable through semantics, not color alone
- keyboard users should be able to move focus into the inspector and close back to the empty state
- responsive mode changes should not remove access to block editing on smaller screens

This iteration does not require a deeper focus-management redesign beyond preventing disruptive surprises.

## Testing Strategy

### Frontend Unit Or Component Tests

Cover:

- desktop layout renders an inspector rail even when no block is selected
- selecting a block updates inspector contents without unmounting the rail shell
- closing returns the rail to the empty state
- clicking an empty slot enters the draft state
- switching between selected blocks updates the inspector in place

### End-To-End

Cover one realistic desktop flow:

1. open `Today`
2. record the timeline width
3. select a block
4. verify block details appear in the inspector rail
5. verify the timeline width remains unchanged
6. switch to another block and confirm the inspector updates in place
7. close selection and confirm the empty rail returns without layout shift

## Risks And Trade-Offs

- a permanent rail reduces the maximum width available to the timeline on desktop
- if the empty state is too rich, it will compete with the timeline and feel like wasted ornament
- if the empty state is too visually heavy, the rail may read as dead space rather than quiet structure
- responsive behavior must be handled carefully so the desktop improvement does not degrade smaller screens

## Recommendation

Implement `Today` as a stable desktop two-column layout with a persistent inspector rail and a minimal empty state.

This is the best fit for the product because it preserves spatial continuity in the timeline, improves editing flow, and matches the app's calm editorial layout language better than a conditional in-flow panel.
