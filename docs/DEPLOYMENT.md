# Deployment Runbook

Last reviewed: 2026-08-21

Timebox uses Vercel for the web frontend and Railway for the FastAPI backend and PostgreSQL database. This document records durable infrastructure identity, required configuration, and repeatable deployment steps. Deployment-specific URLs and IDs returned by individual releases do not belong here.

## Infrastructure

| Surface | Provider | Identity |
| --- | --- | --- |
| Frontend | Vercel | Project `frontend`, linked in `frontend/.vercel/project.json` |
| API | Railway | Project `timebox` (`00d7890c-fba8-4a83-8e9e-9c355ccbe166`), environment `production` (`4879bb4d-48f9-4f5a-b839-dcf5cf5e71f6`), service `api` (`a8113630-4188-4e1e-97fd-2979eab954ce`) |
| Database | Railway Postgres | Service `Postgres` (`3c661ad7-8590-4047-adf2-0ad4b3f6e1cc`) with a Railway-managed volume at `/var/lib/postgresql/data` |

The API public URL is `https://api-production-238a.up.railway.app`; its health endpoint is `/health`.

Never copy a resolved `DATABASE_URL` into documentation, source control, logs, or shell history. It contains database credentials. Production schema changes must run through Alembic; `AUTO_CREATE_TABLES=1` is only for disposable SQLite development and test environments.

## Prerequisites and context checks

Install and authenticate the Railway and Vercel CLIs, then verify the accounts before deploying:

```powershell
railway --version
railway whoami --json

Push-Location frontend
vercel --version
vercel whoami
Get-Content .vercel/project.json
Pop-Location
```

The repository does not need a persistent Railway link for deployment because the backend command below passes the project, environment, and service explicitly. Commands such as `railway status`, `railway service status`, and `railway deployment list` require linked context. If operational inspection is needed, link deliberately and verify the result:

```powershell
railway link --project 00d7890c-fba8-4a83-8e9e-9c355ccbe166 --environment production --service api
railway status --json
```

## Railway API configuration

The `api` service requires these variables:

```text
DATABASE_URL=${{Postgres.DATABASE_URL}}
APP_TIMEZONE=Asia/Singapore
CORS_ORIGINS=<comma-separated stable frontend origins>
CORS_ORIGIN_REGEX=^https://frontend-[a-z0-9-]+-caius-projects-fddd122e\.vercel\.app$
```

`API_KEY` is optional. Setting it protects every application route while leaving `/health` open, but it also prevents the current web frontend from working because the browser client does not send the key.

The Railway deploy settings are:

```text
preDeployCommand=alembic upgrade head
startCommand=uvicorn app.main:app --host 0.0.0.0 --port $PORT
healthcheckPath=/health
healthcheckTimeout=300
```

Alembic must remain in `backend/pyproject.toml` runtime dependencies so Railway's production install can execute the pre-deploy migration.

## Deploy the backend

From the repository root:

```powershell
Push-Location backend
uv run pytest
Pop-Location

railway up backend `
  --path-as-root `
  --project 00d7890c-fba8-4a83-8e9e-9c355ccbe166 `
  --environment production `
  --service api `
  --detach `
  --message "<release summary>"
```

`--detach` starts the deployment but does not wait for it to become healthy. Use the deployment ID returned by Railway to inspect bounded logs if needed:

```powershell
railway logs <deployment-id> --lines 200 --json
Invoke-RestMethod https://api-production-238a.up.railway.app/health
```

If dependencies changed, run `uv lock` in `backend/` and commit the refreshed lockfile before deploying.

## Deploy the frontend

The Vercel project link lives under `frontend/`, so run Vercel commands from that directory. `VITE_API_BASE_URL` is a Vite build-time variable; a runtime `--env` value is not required.

Preview deployment:

```powershell
Push-Location frontend
npm test
npm run build
vercel deploy --yes --build-env "VITE_API_BASE_URL=https://api-production-238a.up.railway.app"
Pop-Location
```

Production deployment:

```powershell
Push-Location frontend
vercel deploy --prod --yes --build-env "VITE_API_BASE_URL=https://api-production-238a.up.railway.app"
Pop-Location
```

The deployment command prints the new URL. Ensure its stable production origin is present in `CORS_ORIGINS`. Preview URLs for the linked `frontend` project are covered by the `CORS_ORIGIN_REGEX` above.

`frontend/vercel.json` rewrites client-side routes to `/index.html`, allowing direct navigation to routes such as `/day/2026-08-06` while Vercel continues to serve static assets normally.

## Live smoke test

`backend/scripts/live_smoke.py` creates, updates, and removes namespaced test data. It refuses to run unless the destructive-test confirmation variable is set and attempts cleanup in `finally` after failures.

```powershell
Push-Location backend
$env:TIMEBOX_API_BASE_URL = "https://api-production-238a.up.railway.app"
$env:TIMEBOX_LIVE_SMOKE_CONFIRM = "DELETE_TEST_DATA"
uv run python scripts/live_smoke.py
Remove-Item Env:TIMEBOX_API_BASE_URL
Remove-Item Env:TIMEBOX_LIVE_SMOKE_CONFIRM
Pop-Location
```

The script defaults to the isolated date `2099-12-31`. Set `TIMEBOX_LIVE_SMOKE_DATE=YYYY-MM-DD` only when another date is deliberately required.

## Troubleshooting

- Confirm CLI identity and project context before inspecting or changing infrastructure.
- For a Railway build failure, fetch bounded build logs with `railway logs <deployment-id> --build --lines 200 --json`.
- For a Railway runtime failure, fetch bounded runtime logs without `--build` and check the health endpoint.
- If the frontend loads but API calls fail, check `VITE_API_BASE_URL`, the deployed frontend origin, `CORS_ORIGINS`, and `CORS_ORIGIN_REGEX` together.
- If a client-side route returns 404 on refresh, verify that `frontend/vercel.json` was included in the deployment.
