# Deployment

Last reviewed: 2026-09-19

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

## Phoenix observability

The production [Phoenix dashboard](https://phoenix-production-6691.up.railway.app)
runs as `phoenix` in the existing Railway `timebox` production environment in
Singapore. It uses `arizephoenix/phoenix:version-20.14.0`, one replica, and a
separate private `phoenix-postgres` service (`postgres:17.6`) with a persistent
volume at `/var/lib/postgresql/data`. Phoenix connects using the Railway reference
`PHOENIX_SQL_DATABASE_URL=${{phoenix-postgres.DATABASE_URL}}`.

Authentication is enabled, with secure cookies and the HTTPS dashboard origin
configured as trusted. Sign in as `admin@localhost`; retrieve the initial password
from the `phoenix` service's `PHOENIX_DEFAULT_ADMIN_INITIAL_PASSWORD` Railway
variable. This variable only initializes the account; later password changes must
be made in Phoenix. Signing and database secrets remain in Railway variables.

The API exports asynchronously to
`ASSISTANT_TRACE_ENDPOINT=http://phoenix.railway.internal:6006/v1/traces` using
`ASSISTANT_TRACE_API_KEY`, a Phoenix system API key sent as Bearer authorization.
The `timebox-assistant` project keeps full messages and tool results for seven
days (`PHOENIX_DEFAULT_RETENTION_POLICY_DAYS=7`); expired data is removed by the
scheduled retention sweep. Keep one API worker: conversations are held in memory.

To disable export during recovery, remove `ASSISTANT_TRACE_ENDPOINT` from the
API service and redeploy it. Preserve the Phoenix database and volume. To rotate
the ingestion key, create a replacement system key in Phoenix, update the API
variable and redeploy, verify a new trace, then revoke the old key. Restarting or
redeploying Phoenix preserves accounts, keys, and traces in PostgreSQL.

## Troubleshooting

- Check the deployment for the matching commit in the Vercel project and Railway `api` service.
- If the frontend loads but API requests fail, check Vercel `VITE_API_BASE_URL`, Railway CORS settings, and the API health endpoint together.
- If a client-side route returns 404 after refresh, verify that `frontend/vercel.json` was included in the Vercel deployment.
- For a database or migration issue, inspect Railway pre-deploy logs before retrying a release.
