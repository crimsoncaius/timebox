---
name: launch-timebox
description: Launch the Timebox frontend, backend, and Android app.
---

# Launch Timebox

1. Use `manage-dev-ports` and confirm this checkout still owns frontend `5176`, API `8001`, Postgres `15433`, and the registered Android ports.
2. From the repository root, run `scripts\launch-timebox.ps1 -Json`. This is the source of truth for container/process identity, the local-only database target, backup-first migrations, API and proxy probes, Android installation, and foreground verification.
3. Treat a nonzero exit as a failed launch. Preserve the API-stopped state after any backup, migration, or invariant failure and report the emitted backup path and error.
4. On success, read `%LOCALAPPDATA%\Temp\timebox-launch\status.json`, open `http://127.0.0.1:5176/` for review, and report all three surfaces plus the Alembic and database fields.

When `alembicCurrent` equals `alembicHead`, report the schema invariant as satisfied and the backup as not applicable. When a migration ran, report the verified backup path, size, and SHA-256 from the launcher output.
