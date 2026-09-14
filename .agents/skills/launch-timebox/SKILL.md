---
name: launch-timebox
description: Launch the Timebox frontend, backend, Android app, and local emulator dashboard.
---

# Launch Timebox

1. Use `manage-dev-ports` and confirm this checkout still owns frontend `5176`, API `8001`, Postgres `15433`, and the registered Android ports.
2. From the repository root, run `scripts\launch-timebox.ps1 -Json`. This is the source of truth for container/process identity, the local-only database target, backup-first migrations, API and proxy probes, Android installation, and foreground verification.
3. Treat a nonzero exit as a failed launch. Preserve the API-stopped state after any backup, migration, or invariant failure and report the emitted backup path and error.
4. On success, read `%LOCALAPPDATA%\Temp\timebox-launch\status.json`, open `http://127.0.0.1:5176/` for review, and report all three surfaces plus the Alembic and database fields.
5. Start or reuse the local emulator dashboard as described below, open it alongside Timebox, and include its URL in the launch result. Report a dashboard failure separately from the application launch result.

When `alembicCurrent` equals `alembicHead`, report the schema invariant as satisfied and the backup as not applicable. When a migration ran, report the verified backup path, size, and SHA-256 from the launcher output.

## Local emulator dashboard

The dashboard lives in the Git-ignored `.emulator-dashboard/` directory. Use this checkout's copy if present; from another worktree, use `C:\Users\Caius\Desktop\timebox\.emulator-dashboard\server.py`. If neither exists, report that the local dashboard is unavailable and complete the application launch.

Read `PORT` from that `server.py` and use `http://127.0.0.1:<PORT>/`. Reuse a running dashboard when `/api/status` returns the expected reservation JSON with a `slots` array. Otherwise, start `python` with the absolute server path using `Start-Process -WindowStyle Hidden`, set the working directory to its repository root, and redirect stdout and stderr into `.emulator-dashboard/`. Keep the configured port; inspect an occupied port before attempting another process.

Verify `/api/status` succeeds, then open the dashboard in a visible browser tab, reusing an existing matching tab. Verify reservation cards appear and retain the tab for user review. Leave the server running. Launching the dashboard does not acquire, resume, or release emulator reservations; those controls remain available for the user's review workflow. Keep its files ignored by Git.
