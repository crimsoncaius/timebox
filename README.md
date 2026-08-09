# Timebox

Dual-lane timeboxing app: **React + Tailwind** frontend, **FastAPI + Postgres** backend. Plan and track **planned** vs **actual** time blocks on a 30-minute grid; data is stored in Postgres with a **fixed app timezone** (`APP_TIMEZONE`) so every device agrees on “today”.

There is also a native **Kotlin + Compose** Android client in [android/](android/README.md), talking to the same API.

## Prerequisites

- Node.js 20+
- Python 3.11+
- [uv](https://docs.astral.sh/uv/getting-started/installation/) (recommended for the backend; manages `.venv` and `uv.lock`)
- Postgres 15+ (local or Docker)

## Environment

Copy [.env.example](.env.example) to `.env` at the repo root (or set variables in your shell). Backend reads `DATABASE_URL`, `APP_TIMEZONE`, and `CORS_ORIGINS`. The Vite dev server proxies `/api` to `http://127.0.0.1:8000`, so the frontend can call paths like `/api/days/...` without CORS issues.

## Database

Create a database and user matching `DATABASE_URL`, then run migrations from `backend/`:

```bash
cd backend
uv sync --extra dev
alembic upgrade head
```

If you previously ran an older schema (with `hour_entries`), `alembic upgrade head` applies the follow-up migration that drops `hour_entries` and creates `time_blocks`. Later migrations add `app_settings` for the global day window and `task_types` (replacing freeform block titles with `task_type_id` plus optional `note`). Always back up production data before migrating.

Without uv: `python -m pip install -e ".[dev]"` (from `backend/`).

## Run locally

**Terminal 1 — API**

```bash
cd backend
uv sync --extra dev
set DATABASE_URL=postgresql://user:pass@localhost:5432/timebox
set APP_TIMEZONE=America/New_York
uv run uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

On macOS/Linux use `export` instead of `set`.

**Terminal 2 — UI**

```bash
cd frontend
npm install
npm run dev
```

Open `http://127.0.0.1:5174`.

## Tests

```bash
cd backend && uv run pytest
cd frontend && npm test
cd frontend && npm run e2e
```

`npm test` runs Vitest (unit/component tests). `npm run e2e` starts the API and dev server with test env and runs Playwright (requires Chromium via `npx playwright install chromium`). The Playwright spec seeds task types and blocks via the HTTP API, then drives Day, Chronicle, and Settings in the browser.

**E2E note:** Playwright may reuse an API already listening on port 8000. If E2E fails after backend schema changes, stop any manual `uvicorn` on that port (or delete `backend/e2e.sqlite` when using the default Playwright SQLite URL) so a fresh server runs migrations / `AUTO_CREATE_TABLES` logic.

## Product spec

See [docs/superpowers/specs/2026-04-13-hourly-timebox-design.md](docs/superpowers/specs/2026-04-13-hourly-timebox-design.md).

## API assumptions and extensions

- **Timezone:** The backend owns `APP_TIMEZONE`; the UI uses `meta` from day responses for “today” and server time.
- **Auth:** Single-user `v1`; see [docs/EXTENSIONS.md](docs/EXTENSIONS.md) for how to add login later. Setting `API_KEY` turns on a shared-secret `X-API-Key` check for `/days`, `/settings` and `/task-types` (`/health` stays open) — used by the Android client. The web frontend does not send the header, so leave it unset while relying on the browser UI.
- **Day summary:** `GET /days/{date}/summary` returns planned/actual totals plus per-task-type minutes without creating the day. Added for the Android Review screen.
- **Day list:** `GET /days` rows carry `block_count`. Simply opening a date creates the day, so the archive is mostly empty rows; the count is how a calendar tells those from days with real entries.
- **E2E / SQLite:** Setting `AUTO_CREATE_TABLES=1` lets the API create tables on startup (used by Playwright). Do **not** use this for production Postgres; use Alembic instead.
- **Day window:** Configure the visible hours under **Settings** (`GET`/`PATCH /settings`); changes apply to all days.
- **Task types:** Manage reusable **path** categories under **Task types** (`GET`/`POST`/`PATCH`/`DELETE /task-types`). Names are canonical lowercase slash paths (e.g. `coding`, `coding/ai`, `exercise/cardio`); creating a deep path materializes ancestors; renames cascade to descendants. Each time block references a `task_type_id` and may include an optional `note`. See [docs/superpowers/specs/2026-04-15-hierarchical-task-type-paths-design.md](docs/superpowers/specs/2026-04-15-hierarchical-task-type-paths-design.md) and the earlier [task types overview](docs/superpowers/specs/2026-04-14-task-types-design.md).
