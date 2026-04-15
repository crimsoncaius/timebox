# Hourly Timebox Design

## Summary

This project is a browser-based application for planning a day in hourly blocks and reflecting on how closely the day matched the plan. The first version targets desktop web use, with a React and Tailwind frontend, a FastAPI backend, and Postgres for durable storage across devices.

The product is intentionally structured around two primary screens (**Today** and **History**), covering:

- planning the day quickly
- checking in during the day with minimal friction
- looking back at past days from History or the current day on Today

The app is a private single-user product in `v1`. It uses a dedicated backend and database, but does not yet include a full authentication system.

## Product Goals

The main goal of `v1` is to improve daily reflection, not just daily planning. The product should make it easy to answer questions like:

- Did I do what I planned for each hour?
- Which parts of the day were mostly on track?
- Where did I drift off plan?
- Which hours did I leave unplanned or forget to rate?

Secondary goals:

- daily planning should be fast enough to do every morning
- in-day updates should be lightweight enough to use without breaking focus
- data should be available across devices through the backend

## Non-Goals For V1

The following are explicitly out of scope for the first version:

- reminders or notifications
- automatic rescheduling when the day slips
- rich analytics dashboards
- heavy journaling or long-form reflection
- multi-user collaboration
- public sharing
- full user signup and login flows

## Users And Access Model

The intended user for `v1` is a single private user. The backend exists to support reliable persistence and multi-device use, but the application is not yet designed as a full multi-tenant SaaS product.

Implications:

- the system can assume one logical owner of all data
- the backend does not need a full account lifecycle yet
- the data model and API should still be organized so a future `user_id` can be added cleanly

## Core User Flow

### 1. Plan

The user opens the app to `today` and configures a day window such as `8am-8pm`. The app shows only those hours by default, with an option to expand to the full 24-hour day.

For each visible hour, the user can enter a planned task. Some hours may be left blank intentionally.

### 2. Track

As the day progresses, the app highlights the current hour. The user can quickly rate each hour as:

- `on track`
- `partly`
- `off track`

Each hour may also include an optional short note for extra context, but the experience should remain lightweight.

### 3. Reflect

Later, the user can review a completed day. The app summarizes the day with:

- counts of `on track`, `partly`, and `off track` hours
- indication of unplanned hours
- indication of planned hours that were never rated

The reflection view should make patterns visible without turning the app into a full analytics or journaling system.

## Information Architecture

The product should have two primary screens.

### Today

This is the main working screen. It includes:

- current date
- selected day window
- control to expand to full 24-hour view
- hourly rows for the selected date
- inline editing for planned tasks
- fast status controls for `on track`, `partly`, and `off track`
- optional per-hour note field
- clear indication of save state and error state
- visual highlight for the current hour

Reflection on how the day went happens on this same screen (timeline and statuses), without a separate read-only review route.

### History

This screen lists previous days and their high-level summaries. It should make it easy to scan recent days and open a specific day in **Today** (`/day/:date`) for planning and tracking.

Each history row should include enough summary information to spot patterns at a glance, such as:

- date
- day window
- counts by status
- whether the day has missing ratings

## Technical Architecture

### Frontend

The frontend should be built with:

- `React`
- `Tailwind CSS`

Responsibilities:

- render planning and day views (Today and per-day timeline)
- manage client-side loading and error states
- call backend APIs for reads and writes
- highlight the current hour based on client time
- provide low-friction inline editing interactions

### Backend

The backend should be built with:

- `FastAPI`

Responsibilities:

- create or initialize a day record when needed
- fetch day data by date
- update day-level settings
- update individual hour entries
- return recent history summaries
- validate request data

### Database

The database should be:

- `Postgres`

Responsibilities:

- store day records durably
- store hourly entries for each day
- support future growth to multiple users without forcing that complexity into `v1`

## Data Model

The model should remain simple and explicit.

### Day

One record per calendar date.

Suggested fields:

- `id`
- `date`
- `start_hour`
- `end_hour`
- `show_full_day`
- `created_at`
- `updated_at`

Future-compatible field:

- optional `user_id` in a later release

### Hour Entry

One record per hour for a given day.

Suggested fields:

- `id`
- `day_id`
- `hour_index`
- `planned_task`
- `status`
- `note`
- `created_at`
- `updated_at`

`hour_index` should map to the hour in the local day, using a stable `0-23` representation.

`status` should be an enum with:

- `unrated`
- `on_track`
- `partly`
- `off_track`

Including `unrated` makes the difference between "not yet reviewed" and "reviewed negatively" explicit.

## API Shape

`v1` only needs a small set of endpoints.

### Day Retrieval

- `GET /days/{date}`

Behavior:

- return the day and its hourly entries if it exists
- if it does not exist, initialize it automatically and return the new record

### Day Initialization

This may be folded into `GET /days/{date}` if automatic initialization is used. A separate explicit creation route is optional, but not necessary if retrieval can initialize safely.

### Day Settings Update

- `PATCH /days/{date}`

Used for:

- updating `start_hour`
- updating `end_hour`
- updating `show_full_day`

### Hour Entry Update

- `PATCH /days/{date}/hours/{hourIndex}`

Used for:

- updating `planned_task`
- updating `status`
- updating `note`

### History Listing

- `GET /days`

Supports:

- recent day list
- summary data per day for the history screen

The response should include enough data to avoid loading every day in full just to render the history list.

## UX Requirements

The app should feel fast and lightweight.

Important interaction requirements:

- editing a planned task should be quick and inline
- status changes should require minimal clicks
- the current hour should be easy to identify
- the selected day window should reduce noise by default
- full-day expansion should be available without losing the saved custom window
- save and error states should be visible to the user

The UI should optimize for repeated daily use rather than maximum configurability.

## Error Handling

`v1` should favor clarity and resilience over complex offline behavior.

Rules:

- invalid input should be rejected with clear API validation messages
- if a requested day does not exist, the backend should initialize it automatically
- if a save fails, the frontend should surface the error clearly
- failed edits should be retryable
- the UI should never silently discard the user's changes

The frontend does not need full offline sync in `v1`, but it should distinguish clearly between:

- loading
- saved
- saving
- failed to save

## Testing Strategy

Testing should focus on the core value of the product rather than on exhaustive UI details.

### Backend Tests

Cover:

- automatic day initialization on fetch
- correct creation and retrieval of hour entries
- updates to day settings
- updates to single hour entries
- validation of invalid hours or statuses
- history summaries

### Frontend Tests

Cover:

- rendering the `Today` screen with a day window
- current-hour highlighting
- inline edit behavior for planned tasks
- status updates for hourly entries
- loading states
- save error states

### End-To-End Test

Cover one realistic flow:

1. open `today`
2. create or load the day
3. enter planned tasks for several hours
4. set statuses for some hours
5. open history
6. return to Today (or open a day from History) and confirm the timeline and saved entries are shown correctly

## Future Evolution

The design should leave room for later additions without forcing them into `v1`.

Likely future features:

- real authentication
- user ownership of day records
- reminders
- richer reflection summaries
- weekly pattern reporting
- export and backup
- mobile-focused layouts

The current architecture should make these possible without requiring a rewrite of the basic day and hour-entry model.

## Recommended V1 Boundary

The first implementation should prioritize:

- a polished `Today` view
- a simple `History` list that links into per-day views
- a small and reliable API surface
- clear persistence in Postgres

Anything that slows down delivery without materially improving daily planning or reflection should be deferred.
