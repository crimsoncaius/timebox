# Timebox

Timebox combines a dual-lane daily planner with a task and project backlog. The **React + Tailwind** web app and native **Kotlin + Compose** Android app share a **FastAPI + Postgres** backend.

Plan and track **planned** vs **actual** time blocks on a 30-minute grid, pull Ready to Plan tasks onto the day, and manage projects, subtasks, deadlines, reminders, recurring work, archives, and trash. The backend uses a **fixed app timezone** (`APP_TIMEZONE`) so every device agrees on “today”.

There is also a native **Kotlin + Compose** Android client in [android/](android/README.md), talking to the same API.

## Prerequisites

- Node.js 20+
- Python 3.11+
- [uv](https://docs.astral.sh/uv/getting-started/installation/) (recommended for the backend; manages `.venv` and `uv.lock`)
- Postgres 15+ (local or Docker)

## Environment

[.env.example](.env.example) documents all supported variables. Environment files are resolved relative to the process working directory:

- When starting the API from `backend/`, put backend values in `backend/.env` or export them in the shell.
- When starting Vite from `frontend/`, put `VITE_*` values in `frontend/.env` or export them in the shell.
- A repo-root `.env` is not loaded automatically by those commands.

The backend reads `DATABASE_URL`, `APP_TIMEZONE`, `CORS_ORIGINS`, optional `CORS_ORIGIN_REGEX`, and optional `API_KEY`. Vite uses `VITE_API_BASE_URL` for direct API requests, or proxies `/api` to `VITE_API_PROXY_TARGET` during development.

## Database

Create a database and user matching `DATABASE_URL`, then run migrations from `backend/`:

```bash
cd backend
uv sync --extra dev
alembic upgrade head
```

If you previously ran an older schema (with `hour_entries`), `alembic upgrade head` applies the follow-up migration that drops `hour_entries` and creates `time_blocks`. Later migrations add the global day window, hierarchical task types, linked planned/actual blocks, Battle Plan projects and tasks, Ready to Plan scheduling, recurring templates, and blocked-task state. Always back up production data before migrating.

Without uv: `python -m pip install -e ".[dev]"` (from `backend/`).

## Run locally

**Terminal 1 — API**

```bash
cd backend
uv sync --extra dev
uv run uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

Set `DATABASE_URL`, `APP_TIMEZONE`, and `CORS_ORIGINS=http://localhost:5176,http://127.0.0.1:5176` in `backend/.env` or the shell first. In PowerShell, temporary values use `$env:NAME="value"`; on macOS/Linux, use `export NAME="value"`.

**Terminal 2 — UI**

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5176
```

Set `VITE_API_PROXY_TARGET=http://127.0.0.1:8001` in `frontend/.env` or the shell, then open `http://127.0.0.1:5176`.

These commands use the workspace's registered Timebox ports. The Android emulator's debug build also defaults to API port `8001`.

## Tests

```bash
cd backend && uv run pytest
cd frontend && npm test
cd frontend && npm run lint
cd frontend && npm run build
cd frontend && npm run e2e
```

`npm test` runs Vitest unit/component tests. `npm run e2e` starts isolated API and web servers with a SQLite test database, then runs Playwright across Day, Chronicle, Settings, Battle Plan, recurring work, and responsive layouts. Install its browser once with `npx playwright install chromium`. `npm run screenshots` regenerates the visual screenshot set.

**E2E note:** Playwright uses the dedicated ports declared in [frontend/playwright.config.ts](frontend/playwright.config.ts) and may reuse listeners already occupying those exact ports outside CI. If a stale E2E server or schema causes failures, stop that listener or delete `backend/e2e.sqlite` so Playwright can start a fresh API using `AUTO_CREATE_TABLES=1`.

Android unit tests and builds are available after Android Studio has generated the Gradle wrapper. On Windows, use the repository launcher so Gradle automatically uses an installed JDK 17+ without changing the machine-wide Java configuration:

```powershell
.\scripts\android-gradle.ps1 testDebugUnitTest assembleDebug
```

On macOS or Linux, set `JAVA_HOME` to JDK 17+ and run `cd android && ./gradlew testDebugUnitTest assembleDebug`.

## Design and behavior references

The visual source of truth is [DESIGN.md](DESIGN.md). Native Android setup and behavior are documented in [android/README.md](android/README.md). The retained technical notes under `docs/superpowers/specs/` cover the Chronicle calendar limit, hierarchical task-type semantics, and time-block drag hysteresis.

## Product surfaces

- **Day:** Planned and actual lanes, Ready to Plan scheduling, linked completions, notes, and overlap validation.
- **Chronicle:** Browse and open recorded days.
- **Battle Plan:** Projects and admin tasks, subtasks, status and priority metadata, deadlines, reminders, manual ordering, archive, and trash.
- **Recurring:** Scheduled and quota-based templates with preview, pause, resume, end, and deletion workflows.
- **Task types:** Reusable hierarchical slash-path categories shared by blocks, tasks, and recurring templates.
- **Settings:** Global day window and client appearance preferences.

## API assumptions

- **Timezone:** The backend owns `APP_TIMEZONE`; the UI uses `meta` from day responses for “today” and server time.
- **Auth:** The application is single-user. Setting `API_KEY` turns on a shared-secret `X-API-Key` check for every application route, including days, settings, task types, projects, tasks, reminders, and recurring templates. `/health` stays open. The Android client sends the key; the web frontend does not, so leave it unset while relying on the browser UI.
- **Day summary:** `GET /days/{date}/summary` returns planned/actual totals plus per-task-type minutes without creating the day. The old Android Day Review UI was removed while reporting is reconsidered; this endpoint remains as a possible reporting primitive.
- **Day preview:** `GET /days/{date}/preview` returns renderable day data without creating a missing day. The Android client uses it for adjacent pages during an interactive swipe.
- **Day list:** `GET /days` rows carry `block_count`. Simply opening a date creates the day, so the archive is mostly empty rows; the count is how a calendar tells those from days with real entries.
- **E2E / SQLite:** Setting `AUTO_CREATE_TABLES=1` lets the API create tables on startup (used by Playwright). Do **not** use this for production Postgres; use Alembic instead.
- **Day window:** Configure the visible hours under **Settings** (`GET`/`PATCH /settings`); changes apply to all days.
- **Task types:** Manage reusable **path** categories under **Task types** (`GET`/`POST`/`PATCH`/`DELETE /task-types`). Names are canonical lowercase slash paths (e.g. `coding`, `coding/ai`, `exercise/cardio`); creating a deep path materializes ancestors; renames cascade to descendants. Every stored Block references a Task Type. Planned Block creation may omit `task_type_id` when it links a Battle Plan Task: the backend uses the Task's type or atomically materializes `unspecified`. Taskless Planned Blocks still require an explicit Task Type. Blocks may also include an optional `note`. See the [hierarchical task-type design](docs/superpowers/specs/2026-04-15-hierarchical-task-type-paths-design.md).
- **Battle Plan:** `/projects`, `/tasks`, and `/reminders` provide project organization, nested tasks, lifecycle actions, Ready to Plan state, and reminder delivery.
- **Recurring work:** `/recurring-templates` supports previews and the complete template lifecycle. Generated tasks retain their recurrence metadata and can enter Ready to Plan like ordinary tasks.
