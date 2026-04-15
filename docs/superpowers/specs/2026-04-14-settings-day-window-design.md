# Global Day Window Settings Design

## Summary

Move `Day window` configuration out of `Today` and into a dedicated `Settings` page.

This is not only a UI relocation. The change also redefines day-window controls as a single global application setting that applies to all existing and future days. The frontend should expose those controls at `/settings`, while the backend should own a canonical settings record and apply updates across stored day data.

## Product Decision

The app should treat the following values as global settings:

- `start_hour`
- `end_hour`
- `show_full_day`

When a user updates these settings, the change should:

- persist as the canonical day-window configuration for the app
- update all existing day records
- affect all newly initialized days

There are no per-day overrides in this design.

## Goals

- make `Today` more focused on planning and tracking
- give configuration a clear home in a dedicated `Settings` page
- ensure the day window behaves consistently across devices
- keep the backend as the source of truth for day-window settings

## Non-Goals

- introducing user-specific settings or multi-user preferences
- adding unrelated settings controls to the new page
- preserving per-day custom day-window overrides
- storing the setting only in local browser state

## Information Architecture

The app should have three primary screens:

- `Today`
- `History`
- `Settings`

### Today

`Today` remains the main working screen for the selected day timeline.

It should no longer render the `Day window` accordion or any inline controls for:

- start hour
- end hour
- full-day visibility

Instead, `Today` should render the timeline using the day data returned by the backend.

### History

`History` continues to show summaries for past days. It does not need dedicated settings UI, but it should naturally reflect updated day-window values when the backend returns updated history data.

### Settings

Add a new top-level route at `/settings`.

This page should be reachable from the main sidebar and should become the only place where the user edits:

- default start hour
- default end hour
- show full 24 hours

The settings UI can reuse the current inline editing pattern from `Today`, including visible save feedback states.

## Backend Design

Introduce a global settings resource separate from day records.

Suggested persisted fields:

- `start_hour`
- `end_hour`
- `show_full_day`
- `updated_at`

The backend remains the authority for validation and persistence.

### API

Add:

- `GET /settings`
- `PATCH /settings`

`GET /settings` returns the current global day-window configuration.

`PATCH /settings` should:

1. validate the incoming values
2. persist the canonical settings record
3. update all existing days to match the new values
4. return the saved settings payload

### Day Initialization

When a day is created or auto-initialized, its `start_hour`, `end_hour`, and `show_full_day` should be seeded from the global settings record rather than hard-coded defaults.

### Existing Day Reads

Day responses may continue to include `start_hour`, `end_hour`, and `show_full_day` for rendering convenience. The difference is that these values are no longer user-edited from `Today`; they are managed centrally through settings updates.

## Data Migration

Add a migration that creates the app-level settings record and seeds it from the current default behavior.

After the migration:

- the settings record exists for the app
- future day creation uses the settings record
- updating settings bulk-updates all existing day rows

This keeps existing API consumers simple and avoids forcing the frontend to reconcile global settings separately from day rendering logic.

## Frontend Design

### Routing

Add a `SettingsPage` route at `/settings`.

Update the sidebar in `Layout` to include a `Settings` navigation item alongside `Today` and `History`.

### Settings Page Behavior

The page should:

- load the current settings with `GET /settings`
- render inputs for start hour, end hour, and show full day
- save changes through `PATCH /settings`
- show clear `saving`, `saved`, and `error` states

The interaction model should stay lightweight and consistent with the existing app feel.

### Today Page Behavior

Remove the existing `Day window` details block from `TodayPage`.

`TodayPage` should continue to:

- fetch the selected day
- render the timeline
- show save and error states for timeline edits

It should no longer own day-window editing responsibilities.

## Validation Rules

The backend should reject invalid day-window combinations with clear validation errors.

At minimum:

- `start_hour` must be within `0-23`
- `end_hour` must be within `1-24`
- `start_hour` must be strictly less than `end_hour`

`show_full_day` remains a boolean flag.

## Error Handling

If loading settings fails, the `Settings` page should show a clear error state.

If saving settings fails:

- the user should see that the save failed
- the failed change should not be silently discarded
- the user should be able to retry

Because the backend updates all days on settings change, the save should be treated as one authoritative operation rather than many client-managed per-day updates.

## Testing Strategy

### Backend

Cover:

- `GET /settings`
- `PATCH /settings`
- validation errors for invalid hour ranges
- propagation of updated settings to existing days
- initialization of new days from the global settings record

### Frontend

Cover:

- rendering the `Settings` page with loaded values
- saving updated settings and showing save-state feedback
- removal of the `Day window` controls from `TodayPage`
- navigation to the new `Settings` route

### End-To-End

Cover one realistic flow:

1. open `Settings`
2. change the day-window values
3. confirm the save succeeds
4. return to `Today`
5. verify the timeline reflects the updated window

## Risks And Trade-Offs

- bulk-updating all day rows on every settings change is simple and explicit, but it couples settings edits to a wider database write
- keeping day-level window fields on each day row preserves current response shapes, but duplicates data intentionally for rendering convenience
- this design prioritizes a clean single-source-of-truth model over future support for per-day customization

## Recommendation

Implement a server-backed `Settings` page with a single canonical day-window record and immediate propagation to all days.

This best matches the user request, keeps behavior consistent across devices, and removes settings clutter from `Today` without relying on fragile client-side conventions.
