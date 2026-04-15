# Hierarchical Task Type Paths Design

## Summary

Extend task types from flat saved labels into canonical slash-delimited paths such as `coding`, `coding/ai`, and `exercise/cardio`.

The storage model should remain simple:

- one `task_types` row per saved path
- one canonical `name` string per row
- no explicit tree table or `parent_id`

Hierarchy should be inferred from the stored path string and respected by the UI, validation rules, and rename/delete behavior.

## Product Decision

Task types should use a **materialized path** model.

This means:

- the backend stores one canonical `name` path string per task type
- paths may have unlimited depth
- `/` is reserved syntax, not decorative text
- parent-child relationships are derived from prefixes such as `coding` -> `coding/ai` -> `coding/ai/agents`

This keeps the data model close to the existing flat `TaskType` resource while enabling nested display, inline creation, and cascading rename semantics.

This design supersedes the earlier task-types decision that `Today` should not support inline task type management. Under this design, `Today` still should not manage rename or delete flows, but it should support inline creation of a missing path while editing a block.

## Goals

- support hierarchical task types such as `coding/ai` and `exercise/cardio`
- keep the database model simple and close to the current `task_types` table
- make block editing fast through searchable suggestions plus inline creation
- preserve compatibility with existing root task types like `work`, `gym`, and `unspecified`
- support deeper paths like `coding/ai/agents`
- allow branch-aware rename behavior that updates descendant paths predictably

## Non-Goals

- introducing a separate tree table or explicit `parent_id` relation
- adding colors, icons, ordering, archive state, or reporting rollups in this iteration
- supporting arbitrary freeform labels that ignore path rules
- implementing destructive subtree deletion or automatic reassignment of in-use paths
- changing time blocks to reference multiple task types

## Data Model

### TaskType

Keep the `TaskType` model flat, but redefine `name` as a canonical path:

- `id`
- `name`
- `created_at`
- `updated_at`

Examples of valid stored values:

- `coding`
- `coding/ai`
- `coding/ai/agents`
- `exercise/cardio`

The backend should treat a task type as the exact saved path value.

### Hierarchy Semantics

Hierarchy is inferred by splitting `name` on `/`.

- a root task type has one segment, such as `coding`
- a descendant task type has two or more segments, such as `coding/ai`
- any path that starts with `oldPath/` is considered a descendant of `oldPath`

Examples:

- `coding` is an ancestor of `coding/ai`
- `coding/ai` is an ancestor of `coding/ai/agents`
- `codingstuff` is unrelated to `coding`

## Canonicalization Rules

Task type paths must be normalized before create or rename.

Required rules:

- trim leading and trailing whitespace from the full input
- split on `/`
- trim whitespace around each segment
- lowercase each segment before storage
- reject empty segments
- reject leading slash and trailing slash
- collapse the stored path into `segment1/segment2/...` with no extra spaces
- enforce uniqueness on the fully canonicalized lowercase path

Examples:

- ` coding / ai ` becomes `coding/ai`
- `coding//ai` is invalid
- `/coding` is invalid
- `coding/` is invalid

## Task Type Lifecycle

### Create

Creating a path should first canonicalize the requested value.

If the path is valid and does not already exist, the backend should create the requested path and auto-create any missing ancestors.

Example:

Creating `coding/ai/agents` should ensure all of the following exist:

- `coding`
- `coding/ai`
- `coding/ai/agents`

This keeps the saved hierarchy fully materialized without requiring a dedicated tree schema.

### Rename

Renaming a task type should be branch-aware and prefix-safe.

If `coding` is renamed to `development`, the backend should update:

- `coding` -> `development`
- `coding/ai` -> `development/ai`
- `coding/personal` -> `development/personal`
- `coding/ai/agents` -> `development/ai/agents`

Rename must:

- only affect the exact path plus descendants beginning with `oldPath/`
- not affect unrelated names such as `codingstuff`
- run in one transaction
- fail if any target path would collide with an existing canonical path

### Delete

Deletion should remain conservative in v1.

A task type may be deleted only when:

- no time blocks reference the exact path
- no saved descendant paths exist below it

This means parent paths like `coding` cannot be deleted while `coding/ai` still exists.

## Block Editing UX

The block editor should replace the plain task type selector with a searchable combobox.

Behavior:

- typing filters saved paths
- exact matches and close prefix matches appear first
- suggestions are shown in a hierarchy-aware display
- the user may select an existing path or create a new canonical path inline

If the typed value does not exactly match an existing path, the suggestions should include an action like:

- `Create "coding/personal"`

Choosing that action should:

1. create the missing task type path
2. auto-create missing ancestors if needed
3. assign the created path to the current block

This keeps the `Today` flow fast and avoids forcing users to leave block editing to manage paths elsewhere.

## Display Rules

The UI should respect path structure when rendering saved task types.

Recommended behavior:

- show full canonical paths where precision matters
- group related paths visually in suggestion lists
- emphasize the leaf segment and de-emphasize ancestors for deep values
- keep root paths and descendants visually connected

Examples:

- `coding`
- `coding / ai`
- `coding / ai / agents`

The canonical stored value should still use `/` with no surrounding spaces even if some displays add spacing for readability.

## API Design

Keep the existing task type resource shape and endpoints, but change path behavior.

### `GET /task-types`

Returns a flat list of saved paths. The frontend derives hierarchy from `name`.

### `POST /task-types`

Creates a canonical path and auto-creates missing ancestors when necessary.

Validation failures should reject:

- invalid path syntax
- duplicate canonical paths

### `PATCH /task-types/{id}`

Renames the selected path.

If the path has descendants, the rename should cascade by prefix in the same transaction.

Validation failures should reject:

- invalid target path syntax
- rename collisions with existing canonical paths

### `DELETE /task-types/{id}`

Reject deletion when:

- the path is in use by one or more time blocks
- the path still has descendants

## Migration Strategy

This design should be a lightweight extension of the current task type system rather than a structural rewrite.

Migration approach:

1. keep existing flat task type values unchanged
2. reinterpret `TaskType.name` as a canonical path rather than a plain label
3. update validation, create, rename, and delete rules to use path semantics
4. update the block editor UI from select-style choice to searchable combobox with inline create

Existing values like `work`, `gym`, and `unspecified` remain valid as root paths, so no risky tree migration is required.

## Error Handling

The backend should return clear errors for:

- invalid path syntax
- duplicate canonical paths
- rename collisions
- deleting an in-use path
- deleting a path that still has descendants

The frontend should:

- keep failed input visible so the user can correct it
- show inline create and rename errors in the block editor and task-types page
- avoid silently mutating the visible input after a rejected save

## Testing Strategy

### Backend

Cover:

- canonicalization of spaced path inputs
- rejection of empty segments and malformed separators
- case-insensitive uniqueness for canonical paths
- creating deep paths such as `coding/ai/agents`
- auto-creating missing ancestors
- cascading rename by exact prefix
- collision handling during rename
- blocking deletion of in-use paths
- blocking deletion of parent paths with descendants

### Frontend

Cover:

- searching existing paths in the block editor
- hierarchy-aware rendering of suggestions
- inline creation of a missing path from typed input
- assignment of a newly created path to the current block
- persistence of deep paths after reload

### End-To-End

Cover one realistic flow:

1. create `coding/ai` from the block editor
2. confirm `coding` is available as an ancestor path
3. assign `coding/ai` to a block
4. reload and verify it persists
5. rename `coding` to `development`
6. verify `development/ai` now appears and remains assigned correctly

## Risks And Trade-Offs

- storing paths as strings keeps the schema simple, but hierarchy logic must stay consistent across API and UI
- branch-aware rename is more complex than a single-row rename
- auto-creating ancestors adds opinionated behavior, but keeps the saved hierarchy coherent
- unlimited depth is flexible, but the UI should still optimize for common shallow cases

## Recommendation

Implement hierarchical task types as canonical materialized paths stored in `TaskType.name`.

This approach preserves the simplicity of the current flat resource model while giving the app the hierarchy-aware behavior you want:

- nested task types
- searchable inline creation
- descendant-aware rename
- conservative delete safety

It is the best balance of product flexibility, implementation cost, and future extensibility for the current app.
