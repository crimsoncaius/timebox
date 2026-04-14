# Timebox

Dual-lane timeboxing app: **React + Tailwind** frontend, **FastAPI + Postgres** backend. Plan and track **planned** vs **actual** time blocks on a 30-minute grid; data is stored in Postgres with a **fixed app timezone** (`APP_TIMEZONE`) so every device agrees on “today”.

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

If you previously ran an older schema (with `hour_entries`), `alembic upgrade head` applies the follow-up migration that drops `hour_entries` and creates `time_blocks`. Always back up production data before migrating.

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

Open `http://127.0.0.1:5173`.

## Tests

```bash
cd backend && uv run pytest
cd frontend && npm test
cd frontend && npm run e2e
```

`npm test` runs Vitest (unit/component tests). `npm run e2e` starts the API and dev server with test env and runs Playwright (requires Chromium via `npx playwright install chromium`). The Playwright spec seeds sample blocks via the HTTP API, then drives Today, History, and Review in the browser.

## Product spec

See [docs/superpowers/specs/2026-04-13-hourly-timebox-design.md](docs/superpowers/specs/2026-04-13-hourly-timebox-design.md).

## API assumptions and extensions

- **Timezone:** The backend owns `APP_TIMEZONE`; the UI uses `meta` from day responses for “today” and server time.
- **Auth:** Single-user `v1`; see [docs/EXTENSIONS.md](docs/EXTENSIONS.md) for how to add login later.
- **E2E / SQLite:** Setting `AUTO_CREATE_TABLES=1` lets the API create tables on startup (used by Playwright). Do **not** use this for production Postgres; use Alembic instead.
