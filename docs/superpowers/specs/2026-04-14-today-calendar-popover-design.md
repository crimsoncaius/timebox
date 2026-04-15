# Today Calendar Popover Design

## Summary

Replace the native browser `type="date"` control on `Today` with a custom calendar popover that visually belongs to the app's editorial design system.

The current browser-provided popup looks detached from the rest of the interface because it ignores the project's typography, tonal surfaces, spacing, and "no-line" component rules. This design introduces a lightweight in-house date picker that preserves fast day navigation while matching the visual language defined in `docs/DESIGN.MD`.

## Product Decision

`Today` should keep a popup calendar interaction, but the popup must be rendered and styled by the app rather than delegated to the browser.

This design chooses:

- a custom trigger button instead of a native date input
- a custom floating month-view popover
- no third-party date picker dependency for this iteration
- explicit day selection only, with no empty or cleared state

## Goals

- make calendar navigation feel consistent with the rest of the app
- remove browser-default calendar chrome from the `Today` page
- preserve fast navigation to any day from the day header
- keep the implementation small and understandable without adding a date library
- ensure the component still works cleanly in dark mode

## Non-Goals

- adding range selection, time selection, or multi-date selection
- supporting a blank "no day selected" state
- introducing a reusable cross-app date picker system beyond `Today`
- adding year-jump dropdowns or advanced keyboard shortcuts in this iteration

## Current Problem

`TodayPage` currently renders a native `<input type="date">`.

This causes two problems:

- the closed field can be styled only partially
- the opened calendar is browser UI and does not follow the app's surfaces, typography, or interaction design

As a result, the calendar looks foreign next to the custom `Prev` and `Next` controls and breaks the calm "Monastic Archive" feel.

## Frontend Design

### Placement

Keep the day navigation cluster in `TodayPage`:

- `Prev` button
- date trigger
- `Next` button

The middle control should become a custom trigger button that displays the currently selected date in a refined editorial format. It should read as a navigational control, not as a form field.

### Trigger Styling

The trigger should follow the design system more closely than the current input:

- no standard input border box
- use soft surface contrast rather than visible control framing
- use headline typography for the primary date value
- keep the control compact enough to sit comfortably between `Prev` and `Next`
- on hover, become more present through a background shift rather than a stronger shadow

The trigger may keep a very subtle ghost boundary only if needed for accessibility and hit-area clarity.

### Popover Styling

The calendar should open in a floating popover aligned to the trigger.

The popover should reflect the design rules in `docs/DESIGN.MD`:

- `surface-container-lowest`-leaning background with light transparency
- backdrop blur to create a high-end glass effect
- ambient depth instead of a heavy drop shadow
- no hard 1px section borders as primary structure
- separation through spacing, tonal shifts, and alignment rather than visible grid lines

The overall feeling should be quiet, premium, and integrated with the page rather than resembling a browser utility widget.

### Calendar Structure

The popover should include:

- a month label with previous/next month controls
- a weekday header row
- a month grid of day buttons
- a small `Today` action in the footer

Do not include a `Clear` action, because the route model always represents a concrete day.

### Day Cell Styling

Day cells should avoid the boxed-grid look from native calendars.

Instead:

- default days sit on the popover background with generous spacing
- hover uses a tonal fill or subtle presence shift
- selected day uses the strongest quiet emphasis in the component
- today's date gets a secondary visual cue when it is not the selected date
- out-of-month days remain visible but subdued

Day numbers should use the app's headline font so the calendar feels like part of the editorial system rather than a generic utility control.

Out-of-month days shown in the leading and trailing grid slots should remain directly selectable so users can move across month boundaries with a single click.

### Interaction Model

The component should support the following behavior:

- clicking the trigger opens and closes the popover
- clicking a day navigates to `/day/:date`
- the displayed month initially reflects the currently selected date
- `Prev` and `Next` month controls update the visible month without navigating until a day is chosen
- the footer `Today` action navigates to the app's canonical current day route using the server-provided current day value when available
- clicking outside the popover closes it
- pressing `Escape` closes it

After selecting a day, the popover should close immediately.

## Data And Date Logic

The app already uses `YYYY-MM-DD` strings and UTC-safe helpers for calendar math. The custom picker should keep that model.

This design should reuse existing date utilities where practical and add small helper functions only for:

- month grid generation
- month-label formatting
- weekday labeling
- deriving the visible month view from an ISO day string using the same UTC-safe date model as the rest of the app

No new date library should be introduced unless implementation complexity proves materially higher than expected.

## Accessibility

The custom calendar should preserve solid baseline usability:

- the trigger is a real button with an accessible label
- day cells are real buttons
- the selected day is communicated semantically
- month navigation buttons have clear labels
- keyboard users can close the popover with `Escape`

This iteration does not require full roving-tabindex calendar semantics, but the structure should not block that upgrade later.

## Dark Mode

The component should follow the same hierarchy reversal described in `docs/DESIGN.MD`:

- the popover remains distinct without becoming a bright glowing panel
- tonal differences, not neon contrast, define hover and selection
- gradients are not necessary for this component

The date picker should feel calm in dark mode rather than "inverted browser chrome."

## Testing Strategy

### Frontend Unit Tests

Cover:

- rendering the custom date trigger with the current selected day
- opening and closing the popover
- rendering the correct visible month for the selected day
- navigating when a day is selected
- closing on outside click or `Escape`

### End-To-End

Cover one realistic flow:

1. open `Today`
2. open the calendar popover
3. move to a different month if needed
4. select a day
5. verify the route and visible date update

## Risks And Trade-Offs

- a custom calendar adds interaction code that the browser previously handled for free
- keeping the implementation in-house improves visual consistency but requires careful testing around month math and dismissal behavior
- limiting the feature to `Today` keeps scope tight, but the component may later attract reuse pressure from other screens

## Recommendation

Implement a small custom calendar popover in `TodayPage` with app-owned styling and date-grid logic.

This best satisfies the request because it removes the browser-controlled visual mismatch while preserving the fast popup workflow the user wants.
