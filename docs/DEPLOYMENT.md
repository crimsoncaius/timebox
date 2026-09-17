# Deployment

Last reviewed: 2026-09-17

Timebox deploys directly from GitHub through the Vercel and Railway Git integrations. GitHub Actions are not part of the deployment path.

## Release flow

The production branch is `master`.

1. A push or merge to `master` triggers the Vercel frontend deployment and the Railway API deployment independently.
2. Vercel builds the `frontend/` Vite application and publishes the production site at [timebox-umber.vercel.app](https://timebox-umber.vercel.app).
3. Railway builds the `backend/` FastAPI service and publishes the API at [api-production-db7f.up.railway.app](https://api-production-db7f.up.railway.app).
4. Railway runs the database migration command before starting the API. The separate Railway Postgres service and its volume are not rebuilt from GitHub.

Other branches and pull requests receive Vercel preview deployments. Railway production follows its configured `master` trigger branch.

## Provider configuration

### Vercel

- Project: `timebox`
- Root directory: `frontend`
- Framework: Vite
- Build command: `npm run build`
- `VITE_API_BASE_URL` is configured for production, preview, and development and points to the Railway API.
- `frontend/vercel.json` rewrites client-side routes to `/index.html`.

### Railway

- Project: `timebox`
- Environment: `production`
- Service: `api`
- Root directory: `/backend`
- Builder: Railpack Python with `uv`
- Pre-deploy command: `alembic upgrade head`
- Start command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
- Health check: `/health` with a 300-second timeout

The API service requires the production `DATABASE_URL`, `APP_TIMEZONE`, and CORS configuration in Railway variables. Never copy resolved database credentials into documentation, source control, logs, or shell history. Production schema changes must use Alembic; `AUTO_CREATE_TABLES=1` is only for disposable local SQLite environments.

## Troubleshooting

- Check the deployment for the matching commit in the Vercel project and Railway `api` service.
- If the frontend loads but API requests fail, check Vercel `VITE_API_BASE_URL`, Railway CORS settings, and the API health endpoint together.
- If a client-side route returns 404 after refresh, verify that `frontend/vercel.json` was included in the Vercel deployment.
- For a database or migration issue, inspect Railway pre-deploy logs before retrying a release.
