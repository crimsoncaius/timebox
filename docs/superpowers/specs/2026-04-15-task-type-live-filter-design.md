# Task Type Live Filter Design

## Summary

Add live filtering to the `Task types` page so the existing composer input also acts as a search box for saved task type paths.

Today, the input above the list is only used to create a new task type. This makes the page slower to scan once many saved paths exist, even though the typed value already expresses the user's intent. This design keeps one input, uses it to narrow the saved list as the user types, and preserves the existing add flow for creating a missing path.

## Product Decision

The `New task type` input should have a dual role:

- typing filters the saved task types list immediately
- clicking `Add` still creates the typed canonical path when no exact match exists

This should remain one shared interaction rather than splitting create and search into separate fields.

## Goals

- make large saved task type lists easier to scan
- preserve the current single-field composer interaction
- keep add behavior available while filtering
- reuse the existing path-aware matching rules for slash-delimited task type paths

## Non-Goals

- adding a second dedicated search field
- redesigning the task type page layout
- changing backend API behavior
- changing rename or delete semantics

## Interaction Model

### Input Behavior

The existing `New task type` textbox remains the only input in this area.

As the user types:

- the saved task types list updates immediately
- filtering is derived from the raw textbox value
- the textbox content is not auto-rewritten during typing

The `Add` button continues to submit the current value through the existing create flow.

### Filtering Rules

Filtering should use the same canonical path semantics already used for task type suggestions:

- trim outer whitespace before matching
- normalize path segments when a valid canonical query can be derived
- treat `/` as path structure, not plain decoration
- favor exact and close path-prefix relationships over looser matches

Examples:

- `cod` should keep `coding` and `coding/ai`
- `coding/a` should keep `coding` and `coding/ai`
- `development` should keep `development` and descendants such as `development/ai`

If the typed value cannot be canonicalized into a valid path query, the page should fall back to the unfiltered saved list rather than hiding everything.

### List Behavior

The `Saved types` section should continue to use the existing grouped visual presentation.

Only the underlying rows change:

- no query: show all saved task types grouped by root
- query with matches: show only matching task types grouped by root
- query with no matches: show a small empty state such as `No matching task types.`

Filtering does not change edit and delete behavior for rows that remain visible.

### Add Behavior

The create action remains available from the same input.

When the typed value is a valid new canonical path and is not already an exact saved task type:

- `Add` should create it as today
- the saved list should refresh after success
- the textbox should clear after success
- the list should return to the full unfiltered state after the textbox clears

If the typed value exactly matches an existing saved path, current duplicate-handling behavior remains unchanged.

## State Model

The page should derive three display states from `newName`:

1. `empty query`
2. `active query with matches`
3. `active query with no matches`

This is a presentational state model only. It does not require new server state or route state.

## Accessibility

- the existing input label remains unchanged
- filtering should not remove the existing page headings
- the no-match message should be visible text in the saved list region
- keyboard add behavior on `Enter` should continue to work

## Testing Strategy

### Frontend Unit Or Component Tests

Cover:

- typing in the composer filters visible saved task types live
- filtered results preserve the existing hierarchy-aware row labels
- a query with no matches shows the no-match message
- adding a new task type from the same input still works
- clearing after a successful add restores the full list

## Risks And Trade-Offs

- a dual-purpose input is slightly less explicit than separate search and create controls
- invalid partial path text may not behave like a freeform substring search, which is acceptable because task types are path-structured data
- the page should avoid implying that filtering changes what will be created; the input still represents the create value

## Recommendation

Implement live saved-list filtering on the existing `New task type` input and keep `Add` behavior unchanged.

This is the smallest and clearest improvement because it makes task types easier to browse without adding UI clutter or diverging from the path-aware model already established elsewhere in the app.
