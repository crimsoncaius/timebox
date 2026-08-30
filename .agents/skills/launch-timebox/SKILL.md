---
name: launch-timebox
description: Launch the Timebox frontend, backend, and Android app.
---

# Launch Timebox

1. Use `manage-dev-ports`; reuse this project’s registered ports: frontend `5176`, API `8001`, and Postgres `54330`. Reuse healthy matching processes.
2. Start or reuse the verified `timebox-postgres` container on `54330`; require `pg_isready` to accept connections.
3. Before starting the API, compare `backend\.venv\Scripts\alembic.exe -c backend\alembic.ini current` with `heads`. Also confirm `DATABASE_URL` targets only `postgresql://timebox:timebox@127.0.0.1:54330/timebox`. If the registered local database is behind:
   - Read [the cutover runbook](../../../docs/DEFINITIVE_CUTOVER_RUNBOOK.md).
   - Capture Task and Planned Block counts and identifier digests plus Actual/Undo counts.
   - Create a timestamped custom-format `pg_dump` in `C:\Users\Caius\AppData\Local\Temp\timebox-backups`, verify it with `pg_restore -l`, and record its path, size, and SHA-256.
   - Stop a verified Timebox API writer, run `backend\.venv\Scripts\alembic.exe -c backend\alembic.ini upgrade head`, then prove Task and Planned Block identities match and the runbook’s post-cutover invariants return zero. Invoking this launcher authorizes that backed-up migration for this registered local database only; never apply it to another or production database.
   - If backup, migration, or any invariant fails, keep the API stopped and report the failure and backup path.
4. If needed, start the API from the repo root with `backend\.venv\Scripts\uvicorn.exe --app-dir backend app.main:app --reload --reload-dir backend --host 127.0.0.1 --port 8001`. Verify `GET /health` and `GET /days/{today}`; the launch is not healthy unless both succeed.
5. If needed, set `VITE_API_PROXY_TARGET=http://127.0.0.1:8001`, then start the frontend from `frontend/` with `npm run dev -- --host 127.0.0.1 --port 5176`. Verify HTTP 200 and verify `/api/days/{today}` through the Vite proxy before opening it for the user.
6. For mobile, start the `Pixel_9a` AVD using the SDK in `android/local.properties`, wait for boot, run `scripts\android-gradle.ps1 :app:installDebug`, then launch `com.timebox.android/.MainActivity` with `adb`.
7. Verify the Android app is foregrounded and report the three running surfaces, Alembic revision, database invariant result, and backup path when a migration ran.
