# Android Battle Plan Parity Design

## Status

Proposed specification for bringing the native Kotlin and Jetpack Compose Android client to functional parity with the web application's Battle Plan, Admin, Projects, Recurring, reminders, and Ready to Plan workflows.

This document defines product behavior, Android architecture, API integration, edge cases, testing expectations, and a five-phase delivery sequence. It does not authorize or include implementation changes.

## Summary

The web application treats Battle Plan as a connected planning system rather than an isolated task list. It includes:

- four task statuses: Open, In Progress, Blocked, and Completed
- All Tasks, Admin, and project-specific scopes
- project creation, editing, deadlines, and deletion
- task creation and rich task details
- urgency, importance, task type, deadline, reminder, and Ready to Plan metadata
- subtasks
- manual ordering, status changes, sorting, and filtering
- completed-task archiving
- Trash, restore, undo, permanent deletion, and retention feedback
- scheduled and quota-based recurring templates
- reminder delivery
- a Ready to Plan workflow that links Battle Plan tasks to Planned time blocks

The Android client currently provides Day, Chronicle, Task Types, Settings, and Review. It does not define Battle Plan screens, domain models, API methods, repository methods, reminder infrastructure, deep links, or task-to-time-block linkage.

The backend already implements almost all required server behavior. The parity effort should therefore focus on the Android client and preserve the backend as the shared source of truth.

## Product Decision

The Android client should reach functional parity with the web Battle Plan while adapting the interaction design to a phone-sized native interface.

Functional parity means that a user can perform the same meaningful operations on either client and observe the same server-backed state. It does not require pixel-for-pixel duplication of the web board.

The mobile design should use status tabs or a swipeable status pager rather than fitting four narrow Kanban columns on one screen. Cross-status moves should be available from task actions and task details. Reordering within a status may use drag and drop where it remains reliable and accessible.

## Goals

- expose Battle Plan as a first-class Android destination
- let users work with All Tasks, Admin, and Projects
- support the complete active-task lifecycle and rich task metadata
- support subtasks, archive, Trash, restore, and permanent deletion
- support the complete recurring-template lifecycle
- link Ready to Plan tasks to Planned blocks on the Day timeline
- deliver native Android reminders and open the relevant task from a notification
- preserve server timezone, validation, and recurrence behavior
- keep task and template changes consistent across web and Android
- add sufficient automated coverage to prevent contract drift between the backend and Android

## Non-Goals

- rewriting or replacing the existing backend Battle Plan implementation
- introducing a second mobile-specific task database
- full offline editing or conflict resolution
- multi-user accounts, collaboration, or task assignment
- push-notification infrastructure in the first parity release
- duplicating the desktop four-column board exactly on a phone
- changing recurrence-generation semantics
- changing the 30-day Trash retention rule
- adding more than one subtask level
- redesigning unrelated Day, Chronicle, Review, or Settings behavior

## Current State

### Web

The web client exposes Battle Plan as a top-level destination. Its active view:

- groups tasks into Open, In Progress, Blocked, and Completed
- supports manual reordering and status movement
- filters by All Tasks, Admin, or Project
- sorts by manual order, deadline, urgency, or importance
- filters by urgency, importance, and task type
- optionally hides completed tasks
- archives completed tasks in bulk
- opens rich task details
- persists view preferences locally

The web Day screen loads active Battle Plan tasks, shows the ones marked Ready to Plan, and sends their `task_id` when creating a Planned block.

### Backend

The backend already provides:

- project list, create, patch, and delete
- task list by active, archived, or trash collection
- task create and patch
- task reorder and status placement
- archive-completed and unarchive operations
- Trash, restore, and permanent-delete operations
- due-reminder list and delivered acknowledgement
- recurring-template preview, list, create, read, patch, pause, resume, end, and delete
- task linkage on time blocks
- recurrence generation and Ready to Plan behavior

Admin is not a separate entity. A task or recurring template belongs to Admin when `project_id` is null.

### Android

Android currently:

- has no Battle Plan destination
- has no project, Battle Plan task, reminder, or recurrence models
- has no Retrofit methods for Battle Plan or recurring endpoints
- has no repository surface for those endpoints
- does not retain `task_id` or task summaries in time-block responses
- cannot send or clear `task_id` on time-block writes
- cannot display Ready to Plan tasks on Day
- has no notification permission, channel, worker, scheduler, or task deep link
- only understands block usage when deleting task types
- uses an in-memory screen enum rather than route-backed navigation
- is intentionally online-only

## Information Architecture

### Primary Navigation

The Android bottom navigation should contain:

1. Day
2. Chronicle
3. Battle Plan
4. Types

Settings should move from the bottom navigation to a persistent top-bar action or an overflow destination. Review remains owned by Day.

### Route Model

Replace the local screen enum with Navigation Compose routes that support saved state and deep links.

Suggested destinations:

- `day/{date}`
- `chronicle`
- `battle-plan`
- `battle-plan/task/{taskId}`
- `battle-plan/project/new`
- `battle-plan/project/{projectId}`
- `battle-plan/recurring`
- `battle-plan/recurring/new`
- `battle-plan/recurring/{templateId}`
- `types`
- `settings`
- `review/{date}`

Collection, scope, sort, and filter state should be stored as saved state or DataStore preferences rather than encoded into every route. Task and recurring-template identifiers must remain route arguments so notifications and internal links can open a specific item.

### Battle Plan Sections

Battle Plan needs the following sections:

- All Tasks
- Admin
- Recurring
- one entry per Project
- Archive
- Trash

On phones, expose these through a modal navigation drawer or a top-level scope selector. On larger Android layouts, the same content may remain visible in a permanent side rail.

## Android Domain Model

Add Android domain enums matching backend wire values:

- `TaskStatus`: `open`, `in_progress`, `blocked`, `completed`
- `PriorityLevel`: `low`, `medium`, `high`
- `TaskCollection`: `active`, `archived`, `trash`
- `RecurrenceMode`: `scheduled`, `quota`
- `RecurrenceStatus`: `active`, `paused`, `ended`
- `RecurrenceFrequency`: `daily`, `weekly`, `monthly`

Add domain models for:

- `Project`
- `BattleTask`
- `BattleTaskList`
- `DueReminder`
- `RecurringTemplate`
- `RecurringChecklistItem`
- `RecurringTaskLink`
- `RecurrenceWindow`
- `RecurrencePreview`

`BattleTask` must retain all server fields needed by the web behavior, including:

- parent relationship and parent title
- project and task-type relationships
- recurrence relationship and occurrence metadata
- quota parent/session metadata
- title and description
- Ready to Plan state
- status
- urgency and importance
- date-only or timestamp deadline
- reminder timestamp and delivery timestamp
- position
- archive and deletion timestamps
- overdue state
- subtasks

Android should use `java.time` types at the domain boundary:

- `LocalDate` for date-only deadlines and recurrence dates
- `Instant` or `OffsetDateTime` for absolute timestamps
- the backend-provided timezone for display and local date/time editing

Wire DTOs may retain ISO strings, but conversion should occur before values reach the UI.

## API Client Design

### Projects

Add Retrofit calls for:

- `GET /projects`
- `POST /projects`
- `PATCH /projects/{projectId}`
- `DELETE /projects/{projectId}`

### Tasks

Add Retrofit calls for:

- `GET /tasks?state={active|archived|trash}`
- `POST /tasks`
- `PATCH /tasks/{taskId}`
- `POST /tasks/reorder`
- `POST /tasks/archive-completed`
- `POST /tasks/{taskId}/unarchive`
- `DELETE /tasks/{taskId}`
- `POST /tasks/{taskId}/restore`
- `DELETE /tasks/{taskId}/permanent`

### Reminders

Add Retrofit calls for:

- `GET /reminders/due`
- `POST /reminders/{taskId}/delivered`

### Recurring Templates

Add Retrofit calls for:

- `POST /recurring-templates/preview`
- `GET /recurring-templates?status={active|paused|ended}`
- `POST /recurring-templates`
- `GET /recurring-templates/{templateId}`
- `PATCH /recurring-templates/{templateId}`
- `POST /recurring-templates/{templateId}/pause`
- `POST /recurring-templates/{templateId}/resume`
- `POST /recurring-templates/{templateId}/end`
- `DELETE /recurring-templates/{templateId}`

### Time Blocks

Extend Android time-block response, create, and patch DTOs to support:

- `task_id`
- the optional task summary returned by the backend

The Android `TimeBlock` domain model must retain the linked task so a Planned block can show its Battle Plan source and navigate to task details.

## PATCH Presence Semantics

The backend distinguishes between an omitted PATCH field and a field explicitly set to null:

- omitted means leave the existing value unchanged
- a value means replace the existing value
- explicit null means clear the existing value

Android's current JSON configuration omits null properties. A conventional nullable DTO therefore cannot clear:

- project
- task type
- urgency
- importance
- date-only deadline
- timestamp deadline
- reminder
- linked time-block task

Battle Plan PATCH requests must use an explicit presence representation. Acceptable approaches include:

- a sealed `PatchField` type with absent, value, and null states plus a custom serializer
- request-specific `JsonObject` construction that includes only deliberately changed keys

Do not enable global explicit-null serialization for ordinary nullable DTOs. Doing so could unintentionally clear fields that were merely omitted by a caller.

## Error Contract

The existing Android API error envelope assumes `detail` is a string. Recurrence backfill uses a structured `409` detail containing:

- `code = backfill_confirmation_required`
- `past_cycles`
- `past_tasks`

Replace string-only error parsing with a tolerant error model that supports:

- string details
- structured validation details
- structured backfill confirmation
- unknown server error bodies

The repository should expose actionable typed errors where the UI needs a decision, especially:

- task-type reference conflict
- recurrence backfill confirmation
- duplicate project name
- reminder without a valid earlier deadline
- restore-parent-first conflict
- invalid recurrence rule

Generic network, authorization, not-found, and validation messages should continue to use the existing common error treatment.

## Battle Plan Active View

### Status Navigation

Use four status tabs or a horizontal pager:

- Open
- In Progress
- Blocked
- Completed

Each tab shows a count and a vertically scrolling task list. The selected scope and filters apply to every status.

### Task Cards

Each card should display, when present:

- title
- project name or Admin
- task type
- urgency and importance
- relative or formatted deadline
- overdue state
- Ready to Plan state
- recurring-template badge
- quota-session context
- completed-subtask count

The entire card must open task details. Separate accessible actions may toggle Ready to Plan, expand subtasks, or move status.

### Create Task

Allow creation inside the selected status. The compact composer should support at minimum:

- title
- description
- project or Admin
- task type
- urgency
- importance
- date-only or timestamp deadline

Default project behavior depends on scope:

- Admin scope fixes `project_id` to null
- Project scope fixes `project_id` to that project
- All Tasks lets the user choose Admin or a project

After successful creation, refresh the authoritative active-task list and keep the user in the same status and scope.

### Status Changes And Reordering

Users must be able to move a task to any status from task details or a card action.

Manual ordering should be supported within a status. Dragging between status pages is not required. When a status changes, submit the complete relevant placement set through `/tasks/reorder` so server positions remain canonical.

When sorting by deadline, urgency, or importance:

- same-status manual reordering is disabled
- moving to another status remains available

On request failure, restore the prior local list and surface an error.

### Sorting And Filtering

Support the web options:

- manual order
- deadline
- urgency
- importance
- hide completed
- urgency filter including Unset
- importance filter including Unset
- task-type filter including Unset

Persist scope, sort, hide-completed, and filter preferences in DataStore. Preferences are device-local UI state, not server state.

## Task Details

Task details should be a full-screen destination on compact devices and may be a sheet or side panel on expanded devices.

The user must be able to edit:

- title
- description
- status
- project or Admin
- task type
- urgency
- importance
- deadline mode: none, date only, or date and time
- reminder date and time
- Ready to Plan state

Behavior requirements:

- changes remain local until Save
- leaving with unsaved changes prompts for confirmation
- a reminder requires a deadline
- a reminder must precede the deadline boundary
- clearing a deadline also clears its reminder
- date/time editing and display use the backend app timezone
- moving a task to Trash requires confirmation
- after save, the authoritative task list is refreshed
- if a deep-linked task is a subtask, open its parent details and focus the subtask

## Subtasks

Support one level of subtasks only.

Users must be able to:

- expand and collapse a task's subtasks
- create a subtask
- open a subtask from the parent details
- toggle a subtask between Open and Completed quickly
- assign any supported status from details
- move a subtask to Trash

Subtasks inherit the parent's project on creation. They cannot contain their own subtasks. Completing every subtask does not automatically complete the parent.

Archive and Trash operations on a parent act on its subtasks according to backend behavior. Restoring a deleted subtask while its parent remains deleted must show the restore-parent-first error.

## Ready To Plan And Day Integration

### Battle Plan

Every active task and subtask should expose an independent Ready to Plan toggle. This state is separate from task status.

### Day

Add a Ready to Plan selector to the Android Day screen. It should:

- load active Battle Plan tasks without preventing Day from loading if the task request fails
- flatten eligible parent tasks and subtasks
- show only tasks with `ready_to_plan = true`
- support title search when the list is long
- show task type or a warning that a task type still needs to be chosen
- allow one selected task at a time
- apply only when creating a Planned block

When the user chooses a Planned slot with a Ready to Plan task selected:

- prefill the task's task type when available
- retain the selected `task_id` in the block draft
- require the user to choose a task type if the task has none
- send `task_id` when creating the block
- refresh Battle Plan tasks after creation
- clear the Ready to Plan selection after success

The backend clears the task's Ready to Plan state when it receives a linked Planned block. Android should render that server result rather than clearing unrelated task fields locally.

### Existing Blocks

For a time block linked to a Battle Plan task:

- show the task title in block details
- provide an action to open the task
- retain the task relationship when completing Planned as Actual
- allow the task relationship to be cleared with an explicit-null PATCH

## Admin And Projects

### Admin

Admin is a scope containing tasks whose `project_id` is null. Creating a task from Admin must send `project_id = null` or omit it for creation.

### Project List

Projects should be sorted using the server order and appear in the Battle Plan section selector.

### Project Editor

Support:

- create project
- edit project
- unique non-empty name
- description
- no deadline, date-only deadline, or date/time deadline
- delete project

Date/time project deadlines use the backend timezone.

### Project Deletion

Project deletion is destructive and requires a confirmation that explains:

- all tasks belonging to the project are permanently deleted, including archived and trashed tasks
- linked subtasks are deleted with their parents
- recurring templates are not deleted; they are moved to Admin

Before confirmation, Android should count project tasks across active, archived, and trash collections. If template counts are not available from the current API, the confirmation must still state that recurring templates move to Admin without claiming an exact template count.

After deletion:

- refresh projects and tasks
- switch a deleted active project scope back to All Tasks
- close project details

## Archive And Trash

### Archive

Support bulk archiving of completed parent tasks visible in the current scope and filters.

Archived tasks should:

- appear in Archive
- retain project/Admin metadata and subtasks
- support Restore

Restoring an archived parent restores its subtasks.

### Trash

Moving an item to Trash should:

- require confirmation in task details
- close details when the parent being viewed was trashed
- show an Undo affordance
- refresh active tasks

Trash should show:

- task title
- project or Admin
- remaining retention days derived from `deleted_at` and server time
- Restore
- Delete permanently

Permanent deletion must require a second destructive confirmation. Only trashed tasks can be permanently deleted.

The client should not implement its own purge. The backend remains responsible for the 30-day retention rule and expired-item removal.

## Recurring Templates

### Recurring Overview

Provide status tabs for:

- Active
- Paused
- Ended

Each template row should show:

- title
- project or Admin
- cadence
- next occurrence or current window
- status

Template details should show:

- recurrence mode and cadence
- next five windows
- current and overdue generated tasks
- links into the generated task details
- Edit
- lifecycle actions allowed for the current status

### Create And Edit

Support both recurrence modes:

- Scheduled: creates dated tasks on recurrence occurrences
- Quota: creates a period parent plus the requested number of Ready to Plan session tasks

Support frequencies:

- Daily
- Weekly
- Monthly

Support rule fields:

- interval
- weekly weekdays for scheduled weekly rules
- day of month for scheduled monthly rules
- times per period for quota rules
- start date
- never end, inclusive end date, or cycle limit

Support template task fields:

- title
- description
- project or Admin
- task type
- urgency
- importance
- checklist titles

Editing an existing template must preserve its immutable mode and follow backend behavior for existing generated tasks and field overrides.

### Preview And Backfill

Use `/recurring-templates/preview` while editing a valid rule. Show:

- upcoming windows
- count of past cycles
- count of tasks that a backfill would create

If creation or editing returns `backfill_confirmation_required`, show a confirmation using the structured counts. Resubmit the same request with `confirm_backfill = true` only after explicit user confirmation.

### Lifecycle

Support:

- Pause for active templates
- Resume for paused templates
- End for active or paused templates
- Delete permanently for ended templates

Confirm End because pristine future generated tasks may be removed.

Confirm permanent deletion because the template is removed while already generated tasks remain as ordinary Battle Plan tasks.

Recurrence generation remains entirely server-owned. Android must not create recurrence occurrences locally.

## Reminders And Notifications

### Permission And Channel

Add Android notification support with:

- `POST_NOTIFICATIONS` permission on Android 13 and newer
- a Battle Plan reminder notification channel
- a permission request triggered when the user first enables a reminder or from a clear settings affordance

Declining permission must not prevent saving the reminder on the server. The UI should explain that the reminder exists but this device cannot display notifications until permission is granted.

### Foreground Delivery

While the application is active:

- poll due reminders at startup and approximately once per minute
- avoid duplicate notification display within the current process
- acknowledge a reminder only after it has been handed to the Android notification system
- open `battle-plan/task/{taskId}` when the notification is tapped

### Background Delivery

For the first parity release:

- schedule local work from active tasks that contain future `reminder_at` values
- resynchronize scheduled work on app startup, task refresh, reminder changes, device boot, and a periodic WorkManager job
- use unique work names keyed by task ID so edits replace earlier schedules
- cancel scheduled work when the reminder is cleared, the task is completed, archived, trashed, or no longer returned as active
- confirm with `/reminders/due` before displaying background reminders where practical

Android background execution may delay delivery under battery restrictions. Exact cross-device delivery would require future push infrastructure and is outside this release.

### Delivery Ownership

The backend's `reminder_delivered_at` remains the cross-client deduplication source. Because web and Android can race to acknowledge the same reminder, both clients must tolerate a reminder disappearing between list and acknowledgement.

## Task Type Integration

Extend the Android task-type DTO and domain model to retain:

- block usage count
- Battle Plan task usage count
- recurring-template usage count

Extend delete calls to support:

- cascading block deletion
- migrating blocks to another task type
- clearing task and recurring-template references

Deletion UI must explain all affected references. When a type is used by Battle Plan tasks or recurring templates, Android must not offer only block deletion and then leave the user at an unresolvable `409` error.

The existing hierarchy and descendant-deletion behavior remains unchanged.

## State Management And Repository

Add feature-specific ViewModels rather than putting Battle Plan state in the root app composable.

Suggested ViewModels:

- `BattlePlanViewModel`
- `TaskDetailViewModel`
- `ProjectViewModel` or project editor state owned by Battle Plan
- `RecurringViewModel`
- `RecurringEditorViewModel`

Repository requirements:

- expose typed project, task, reminder, and recurrence operations
- keep network work on the IO dispatcher
- return the existing `Result`-based error surface or a consistently typed successor
- refresh authoritative collections after mutations
- preserve the current online-only policy

Do not add a Room cache solely for this work. DataStore should hold only UI preferences and existing connection/theme settings, not authoritative tasks or projects.

When independent data can load separately, partial failure should remain isolated. For example, failure to load Ready to Plan tasks should not prevent the Day timeline from loading.

## Loading, Empty, And Failure States

Every new screen must define:

- initial loading state
- pull-to-refresh or explicit retry
- empty state
- recoverable validation error
- network error
- authorization error
- destructive-action confirmation
- mutation-in-progress state that prevents duplicate submission

Optimistic updates are appropriate for reorder and quick status toggles when rollback is implemented. Destructive actions and rich edits should wait for server success before presenting the operation as complete.

## Accessibility

- every icon-only action needs a content description
- status tabs must expose selected state and task counts
- drag-and-drop cannot be the only way to reorder or change status
- swipe actions must have equivalent visible or menu actions
- task priority cannot be communicated by color alone
- deadline and overdue states need text labels
- confirmation dialogs must identify the exact destructive consequence
- notification content must not expose more than the task title
- controls must remain usable with large font scaling

## Testing Strategy

### Android Unit Tests

Cover:

- DTO serialization and deserialization for every new resource
- ISO date/time and timezone conversion
- tri-state PATCH encoding, especially explicit null
- task grouping, scoping, sorting, and filtering
- status placement and reorder payloads
- relative deadline and Trash retention calculations using server time
- recurrence rule construction and client-side form validation
- backfill error parsing
- Ready to Plan selection and time-block payload construction
- notification scheduling and cancellation decisions
- task-type usage and deletion-resolution decisions

### Repository Tests

Use a fake or mock HTTP server to cover:

- all project endpoints
- all task lifecycle endpoints
- recurrence preview and lifecycle endpoints
- string and structured error bodies
- retries and network failures
- explicit-null PATCH bodies
- reminder list and acknowledgement

### ViewModel Tests

Cover:

- initial loading and refresh
- scope, collection, status, sort, and filter transitions
- optimistic reorder rollback
- create/edit success and failure
- unsaved detail drafts
- project deletion refresh and scope reset
- recurrence backfill confirmation and resubmission
- notification deep-link destination creation

### Compose UI Tests

Cover:

- bottom navigation and Settings relocation
- four status tabs and counts
- All Tasks, Admin, and Project scopes
- task creation and detail editing
- subtask expansion and completion
- Ready to Plan toggle
- archive and Trash actions
- project editor and destructive confirmation copy
- recurring status tabs and editor modes
- notification-permission explanation
- accessibility labels for non-text controls

### End-To-End Tests

Run against the real FastAPI test backend and an emulator. At minimum cover:

1. create a project and project task on Android, then observe it through the API
2. mark a task Ready to Plan, link it to a Planned block, and verify readiness clears
3. move a task across all statuses and preserve order
4. create and complete a subtask without completing its parent
5. archive, restore, trash, undo, and permanently delete a task
6. create a scheduled recurring template and observe generated tasks
7. create a quota template and observe Ready to Plan session tasks
8. trigger backfill confirmation
9. receive and acknowledge a reminder
10. delete a project and verify its tasks are deleted while its templates move to Admin
11. resolve deletion of a task type used by blocks, tasks, and templates

### Existing Backend Coverage

Existing backend tests remain the contract authority for project cascade behavior, subtasks, Ready to Plan, task placement, archive and Trash, reminders, recurrence generation, backfill, quota behavior, and lifecycle operations. Android contract tests should use representative payloads from those behaviors to detect client drift.

## Delivery Phases

The work must be delivered in five phases. A phase is complete only when its exit criteria pass; incomplete foundation work should not be hidden by beginning later UI work.

### Phase 1 — Contracts, Navigation, And Shared Foundations

Scope:

- add Navigation Compose route-backed app navigation
- add Battle Plan to bottom navigation and relocate Settings
- add all Battle Plan, Project, Reminder, Recurrence, and extended Task Type DTOs and domain models
- add Retrofit and repository methods
- implement tri-state PATCH encoding
- implement tolerant structured error parsing
- extend time-block models with task linkage
- add contract and serialization tests

Exit criteria:

- every existing Battle Plan and recurring endpoint is callable from Android repository tests
- explicit-null clearing is proven by tests
- a task deep link resolves to a stable destination
- current Day, Chronicle, Types, Settings, and Review navigation still works

### Phase 2 — Core Battle Plan, Admin, Projects, And Day Linkage

Scope:

- add active Battle Plan status tabs or pager
- add All Tasks, Admin, and Project scopes
- add core task cards and task creation
- add task details and status changes
- add project create, edit, and delete
- add Ready to Plan toggles
- add the Day Ready to Plan selector
- create and inspect task-linked Planned blocks
- persist Battle Plan view preferences

Exit criteria:

- a user can create and edit Admin and project tasks
- a user can move a task through every status
- a user can create, edit, and delete a project with correct destructive behavior
- a Ready to Plan task can be linked to a Planned block and clears readiness on the server
- linked blocks can open their Battle Plan task

### Phase 3 — Task Management Parity

Scope:

- add subtasks and subtask actions
- add urgency, importance, task-type, deadline, and reminder editing
- add sorting, filtering, hide-completed, and manual reorder
- add archive-completed and Archive restore
- add Trash, undo, retention feedback, restore, and permanent delete
- update task-type usage and deletion-resolution flows
- complete compact and expanded Android layouts for these features

Exit criteria:

- every non-recurring task behavior available on the web has a usable Android equivalent
- destructive actions have accurate confirmations and recovery where supported
- task-type deletion cannot strand the user on a known reference conflict
- sorting and filtering persist across app restarts

### Phase 4 — Recurring Templates

Scope:

- add Active, Paused, and Ended recurring lists
- add template details and generated-task links
- add scheduled and quota editors
- add daily, weekly, and monthly rules
- add start/end/cycle controls, preview, checklist, priorities, project, and task type
- add structured backfill confirmation
- add pause, resume, end, and permanent-delete lifecycle actions

Exit criteria:

- Android can create every valid recurrence rule supported by the backend
- preview and backfill counts match backend responses
- lifecycle behavior matches web behavior
- generated tasks open correctly in Battle Plan and Ready to Plan quota sessions appear on Day

### Phase 5 — Native Reminders, Hardening, And Release Verification

Scope:

- add notification permission and channel
- add foreground due-reminder polling
- add background synchronization and local scheduling
- add reminder notification deep links and acknowledgement
- finish accessibility, loading, error, and large-font behavior
- add Compose UI and end-to-end coverage
- run regression verification for all existing Android features

Exit criteria:

- due reminders display once per backend delivery state and open the correct task
- permission denial has a clear non-blocking experience
- all listed Android unit, repository, ViewModel, UI, and end-to-end critical paths pass
- Day, Chronicle, Types, Settings, and Review regressions are cleared
- a manual web-to-Android parity checklist passes against the same backend database

## File Impact Map

Expected Android areas include:

- `ui/TimeboxApp.kt` and `ui/components/Chrome.kt` for route-backed navigation and tabs
- new `ui/battleplan/` screens, components, and ViewModels
- new `ui/recurring/` screens and ViewModels
- `ui/day/` for Ready to Plan and task-linked block details
- `ui/types/` for extended usage and deletion resolution
- `data/Models.kt` or new feature-specific domain model files
- `data/remote/Dto.kt` or feature-specific DTO files
- `data/remote/TimeboxApi.kt`
- `data/TimeboxRepository.kt`
- `data/AppPreferences.kt` for view preferences if DataStore remains centralized
- `AndroidManifest.xml` for notification and boot behavior
- Gradle/version catalog entries for WorkManager and notification test support
- Android unit and instrumentation test directories

Backend changes are not expected for baseline parity. Optional future server changes may add push-notification registration or a dedicated single-task read endpoint, but neither is required by this specification.

## Risks And Trade-Offs

- Battle Plan is large enough that copying all web UI literally would produce an unusable phone layout; native status navigation is the deliberate adaptation.
- Android background limits mean local reminder delivery may be delayed. This is acceptable for the first parity release and should be documented.
- The existing online-only repository keeps scope controlled but means task edits cannot be queued offline.
- Explicit-null PATCH behavior is easy to get wrong and could leave fields impossible to clear or clear fields accidentally. Contract tests are mandatory before UI work depends on it.
- Project deletion is permanently destructive for tasks. Confirmation copy and end-to-end verification are mandatory.
- Recurrence edits have historical preservation and override rules owned by the backend. Android should display server results rather than attempting to reproduce generation locally.
- Loading all active tasks and filtering on device matches the web but could become expensive with a very large task set. Server-side task filtering is a future optimization, not a parity blocker.
- Reminder acknowledgement is shared across clients, so web and Android may race. Both clients must treat already-delivered or disappearing reminders as normal.

## Acceptance Criteria

Parity is complete when all of the following are true:

- Battle Plan is reachable from Android primary navigation
- All Tasks, Admin, Projects, Recurring, Archive, and Trash are accessible
- tasks support all four statuses and server-backed ordering
- task creation and details support all web task fields
- projects support create, edit, deadlines, and correct deletion behavior
- subtasks support create, edit, status change, completion, and Trash
- Ready to Plan tasks can be scheduled into Android Day with a persisted task link
- sorting, filtering, and local view preferences match the web options
- completed tasks can be archived and restored
- trashed tasks can be restored or permanently deleted with retention feedback
- scheduled and quota recurring templates support the complete backend rule set and lifecycle
- structured backfill confirmation works
- task-type deletion resolves block, task, and template references
- native reminders can be displayed, acknowledged, and opened
- web and Android show consistent task/project/template state against the same backend
- automated tests cover contracts and the critical end-to-end flows

## Recommendation

Implement parity as the five phases above, beginning with API contracts and route-backed navigation before introducing new UI. The most valuable vertical slice is Phase 2: it delivers usable Admin, Projects, core tasks, and the Ready to Plan connection to Day. Task-management completeness, recurrence, and native reminder reliability should then build on that tested foundation without requiring backend or database redesign.
