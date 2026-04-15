# Task Types And Optional Block Notes Design

## Summary

Replace freeform block titles with a first-class `TaskType` resource.

Each time block should store:

- a required selected `task type`
- an optional free-text `note`

Task types are managed from a dedicated top-level `Task Types` page, while the timeline uses those saved task types when creating and editing blocks.

## Product Decision

The app should treat task types as reusable saved records, not ad hoc strings entered independently on each block.

This design introduces:

- a `task_types` collection managed centrally
- a required `task_type_id` relationship on each time block
- an optional `note` field on each time block

The old block `title` field should be removed from the API and UI after migration.

## Goals

- make block categorization consistent and reusable
- let users manage a saved list of task types such as `work`, `coding`, `travelling`, `exercise`, and `gym`
- allow a short optional note on each block without making that note the block identity
- keep the data model flexible for later additions like colors, ordering, or archive behavior
- preserve existing block data during migration from freeform titles

## Non-Goals

- adding colors, icons, priorities, or tags to task types in this iteration
- supporting per-day custom task type lists
- supporting inline task type management directly from `Today`
- implementing delete-and-reassign flows for in-use task types
- preserving both the old `title` field and the new task-type model long-term

## Information Architecture

The app should have four primary screens:

- `Today`
- `History`
- `Task Types`
- `Settings`

### Today

`Today` remains the main timeline editing screen for a selected day.

Each block should edit two separate values:

- `task type` via a saved task type selector
- `note` via an optional text input

The primary visible block label should be the selected task type name. The optional note is secondary and should not replace the task type as the block identity.

### History

`History` continues to show past day summaries using the task type names returned from the backend.

It does not need task type management controls, but it should reflect renamed task types whenever the backend returns updated block data.

### Task Types

Add a new top-level page at `/task-types`.

This page should be the only place where users manage the saved task type list. It should support:

- listing existing task types
- creating a task type
- renaming a task type
- deleting a task type when unused

### Settings

`Settings` remains responsible for global day-window configuration only.

Task type management should not be folded into `Settings`, because task types are part of the day-planning workflow rather than a general app preference.

## Backend Design

### Data Model

Introduce a `TaskType` model with:

- `id`
- `name`
- `created_at`
- `updated_at`

Update `TimeBlock` so it stores:

- `task_type_id` required
- `note` optional

Remove the old `title` field from the public API after migration is complete.

### Read Shapes

Block responses should include both the relationship key and enough nested data for the frontend to render without a second lookup pass.

Each block response should include:

- `task_type_id`
- `task_type` object with at least `id` and `name`
- `note`

This keeps the storage normalized while making timeline rendering straightforward.

### API

Add task type endpoints:

- `GET /task-types`
- `POST /task-types`
- `PATCH /task-types/{id}`
- `DELETE /task-types/{id}`

Update block APIs so:

- block creation requires `task_type_id`
- block patch accepts `task_type_id` and `note`
- block read returns `task_type_id`, nested `task_type`, and `note`

### Validation Rules

The backend should enforce:

- task type names must be non-empty after trim
- task type names must be unique case-insensitively
- block create/patch requests must reference an existing task type
- `note` is optional and may be empty
- deleting a task type that is still used by one or more blocks is rejected

### Delete Behavior

For v1, deleting an in-use task type should fail with `409 Conflict`.

The response should use a clear message such as:

`Task type is still used by existing blocks`

This avoids accidental data loss and keeps deletion logic simple. Reassignment UX can be added later if needed.

## Frontend Design

### Routing And Navigation

Add a new route at `/task-types`.

Update the main navigation to include:

- `Today`
- `History`
- `Task Types`
- `Settings`

### Today Page Behavior

Replace the current freeform block title editing with:

- a task type selector bound to the saved task type list
- an optional note editor

Changing the selected task type should save `task_type_id`.

Changing the optional note should save `note`.

Existing resize and delete block interactions should continue to work unchanged.

### New Block Creation

When creating a new block:

- if one or more task types exist, assign a stable default selected task type
- if no task types exist yet, block creation gracefully and direct the user to the `Task Types` page

The frontend should not invent ad hoc task types during block creation.

### Task Types Page Behavior

The `Task Types` page should:

- load the saved task type list
- allow adding a new task type
- allow renaming a task type
- allow deleting an unused task type
- show inline create, rename, and delete errors clearly

The page can use the same lightweight save feedback style already used elsewhere in the app.

## Migration Strategy

This feature changes the data model, so rollout should happen in one coherent migration sequence.

The migration should:

1. create the `task_types` table
2. add `task_type_id` and `note` columns to `time_blocks`
3. create a task type for each distinct existing non-empty block title
4. assign each existing block to the matching `task_type_id`
5. set migrated block `note` values to empty
6. create a default task type such as `unspecified` for blocks whose existing title is empty
7. make `task_type_id` required after backfill
8. stop exposing or editing `title` in the API and UI

This preserves existing user-entered block data while moving the app fully to the new model.

## Error Handling

### Backend

The backend should return clear errors for:

- empty task type names
- duplicate task type names
- references to missing task types
- attempts to delete a task type that is still in use

Recommended status codes:

- `422` for validation failures
- `409` for deleting an in-use task type

### Frontend

The UI should:

- show save-state feedback when changing a block task type or note
- keep failed edits visible instead of silently discarding them
- show create, rename, and delete errors inline on the `Task Types` page
- provide an obvious path to recovery when no task types exist yet

## Testing Strategy

### Backend

Cover:

- listing, creating, renaming, and deleting task types
- duplicate-name validation
- preventing deletion of in-use task types
- creating blocks with `task_type_id`
- patching block `task_type_id`
- patching block `note`
- reading blocks with nested `task_type`
- migration from existing `title` values to task types

### Frontend

Cover:

- navigation to the new `Task Types` route
- rendering and editing the saved task type list
- timeline block editing with task type selector plus optional note
- blocked block creation when no task types exist
- preserving existing resize and delete timeline behavior

### End-To-End

Cover one realistic flow:

1. create a task type on `Task Types`
2. navigate to `Today`
3. create a block
4. assign the task type
5. add an optional note
6. reload and verify both values persist

## Risks And Trade-Offs

- normalizing task types adds more backend and migration work than a plain string list
- renaming a task type updates how existing blocks are displayed, which is desirable for consistency but means names are not historical snapshots
- blocking deletion of in-use task types keeps data safe, but may feel restrictive until reassignment UX exists
- requiring at least one saved task type introduces a new empty-state flow that must be handled clearly

## Recommendation

Implement task types as a first-class backend resource with `task_type_id` on blocks and a separate optional `note`.

This best matches the desired “task type” model, gives the app a clean dedicated management page, and keeps the data model strong enough for future enhancements without overcomplicating v1.
