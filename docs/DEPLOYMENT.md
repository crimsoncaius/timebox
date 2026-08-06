# Deployment Notes

Last updated: 2026-05-15

## Live Services

### Frontend: Vercel

- Provider: Vercel
- Team scope: `caius-projects-fddd122e`
- Project: `frontend`
- Preview URL: `https://frontend-i12nvtjht-caius-projects-fddd122e.vercel.app`
- Inspect URL: `https://vercel.com/caius-projects-fddd122e/frontend/7QFFwHEkNwCxX1q5E1jmB6rCGQpQ`
- Deployment ID: `dpl_7QFFwHEkNwCxX1q5E1jmB6rCGQpQ`
- Build-time variable: `VITE_API_BASE_URL=https://api-production-238a.up.railway.app`

The frontend was deployed as a Vercel preview from `frontend/` using:

```powershell
vercel deploy frontend -y --no-wait --target preview --scope caius-projects-fddd122e -b VITE_API_BASE_URL=https://api-production-238a.up.railway.app -e VITE_API_BASE_URL=https://api-production-238a.up.railway.app
```

Vercel also created `frontend/.vercel/project.json` and updated `frontend/.gitignore`.

### Backend: Railway

- Provider: Railway
- Workspace: `Caius Chew's Projects`
- Project: `timebox`
- Project ID: `00d7890c-fba8-4a83-8e9e-9c355ccbe166`
- Environment: `production`
- Environment ID: `4879bb4d-48f9-4f5a-b839-dcf5cf5e71f6`
- API service: `api`
- API service ID: `a8113630-4188-4e1e-97fd-2979eab954ce`
- API URL: `https://api-production-238a.up.railway.app`
- Latest successful API deployment: `fe958a8a-90a7-4b83-b1b3-68d100d6e727`
- Health check: `https://api-production-238a.up.railway.app/health`

Health check response at deployment time:

```json
{"status":"ok","today":"2026-05-15","timezone":"Asia/Singapore"}
```

### Database: Railway Postgres

- Service: `Postgres`
- Service ID: `3c661ad7-8590-4047-adf2-0ad4b3f6e1cc`
- Image: `ghcr.io/railwayapp-templates/postgres-ssl:18`
- Storage: Railway-managed volume mounted at `/var/lib/postgresql/data`
- App connection variable: `DATABASE_URL=${{Postgres.DATABASE_URL}}`

Do not copy the resolved database URL into docs or commits; it contains credentials.

## Backend Configuration

Railway API service variables:

```text
DATABASE_URL=${{Postgres.DATABASE_URL}}
APP_TIMEZONE=Asia/Singapore
CORS_ORIGINS=https://frontend-i12nvtjht-caius-projects-fddd122e.vercel.app,https://frontend-8vz3kgk5d-caius-projects-fddd122e.vercel.app
```

Railway deploy settings:

```text
preDeployCommand=alembic upgrade head
startCommand=uvicorn app.main:app --host 0.0.0.0 --port $PORT
healthcheckPath=/health
healthcheckTimeout=300
```

`alembic` was moved into backend runtime dependencies in `backend/pyproject.toml`, and `backend/uv.lock` was refreshed so Railway's locked `uv sync --no-dev` build can install it.

## Reproduction Commands

Create and configure Railway resources:

```powershell
railway init --name timebox --workspace f49703d0-892d-4328-a82e-b773cbb60e1f --json
railway add --database postgres --json
railway add --service api --json
railway variable set DATABASE_URL='${{Postgres.DATABASE_URL}}' APP_TIMEZONE='Asia/Singapore' --service api --environment production
railway variable set CORS_ORIGINS='https://frontend-i12nvtjht-caius-projects-fddd122e.vercel.app,https://frontend-8vz3kgk5d-caius-projects-fddd122e.vercel.app' --service api --environment production
```

Configure API deploy commands:

```powershell
@'
{"services":{"a8113630-4188-4e1e-97fd-2979eab954ce":{"deploy":{"startCommand":"uvicorn app.main:app --host 0.0.0.0 --port $PORT","preDeployCommand":"alembic upgrade head","healthcheckPath":"/health","healthcheckTimeout":300}}}}
'@ | railway environment edit --json --message "configure api deploy commands"
```

Deploy backend:

```powershell
uv lock
railway up backend --path-as-root --service api --environment production --detach --json --message "deploy timebox api with updated lockfile"
railway domain --service api --json
```

Deploy frontend preview:

```powershell
vercel deploy frontend -y --no-wait --target preview --scope caius-projects-fddd122e -b VITE_API_BASE_URL=https://api-production-238a.up.railway.app -e VITE_API_BASE_URL=https://api-production-238a.up.railway.app
```

## Notes

- The backend is the only Railway app service; the frontend is hosted by Vercel.
- Browser traffic goes from Vercel to the Railway public API domain.
- Backend-to-database traffic uses Railway private networking through `${{Postgres.DATABASE_URL}}`.
- A Vercel deployment without `--target preview` also created `https://frontend-8vz3kgk5d-caius-projects-fddd122e.vercel.app`; it is included in CORS because it was created during deployment, but the intended frontend URL is the preview URL above.

## Client-side routing and preview CORS

The frontend includes `frontend/vercel.json`, which rewrites client-side routes (such as `/day/2026-08-06`) to the Vite entry point. Vercel continues to serve static assets normally.

Keep the stable production frontend URL in `CORS_ORIGINS`. To allow this Timebox project's Vercel preview deployments without opening CORS to arbitrary origins, set the Railway API variable below:

```bash
railway variable set \
  CORS_ORIGIN_REGEX='^https://timebox-[a-z0-9-]+-caius-projects-fddd122e\\.vercel\\.app$' \
  --service api
```

## Opt-in live smoke test

`backend/scripts/live_smoke.py` creates a namespaced task type and time block, updates both, then deletes them. It refuses to run unless the destructive-test confirmation variable is set, and it attempts cleanup in `finally` on every failure.

```bash
cd backend
TIMEBOX_API_BASE_URL='https://your-api.example.com' \
TIMEBOX_LIVE_SMOKE_CONFIRM='DELETE_TEST_DATA' \
python scripts/live_smoke.py
```

It defaults to the isolated date `2099-12-31`; override it with `TIMEBOX_LIVE_SMOKE_DATE=YYYY-MM-DD` when needed.
