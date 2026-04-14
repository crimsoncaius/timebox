# Extension points

## Authentication (future)

`v1` is intentionally open: no login. To add accounts later:

1. **Database** — Add a `users` table and nullable `user_id` on `days` (or a join table). Backfill or migrate existing rows to the first user.
2. **API** — Introduce FastAPI dependencies (e.g. `get_current_user`) that load the user from a JWT/session and filter all `Day` queries by `user_id`. Keep route handlers thin; enforce ownership in the service layer.
3. **Frontend** — Store tokens securely (httpOnly cookies preferred), attach `Authorization` headers in `frontend/src/lib/api.ts`, and add login/logout routes.
4. **Deployment** — Use HTTPS, rotate secrets, and restrict CORS to known origins instead of `*`.

## Timezone

`APP_TIMEZONE` is the single source of truth for “today” and `current_hour_index`. Changing it shifts how dates and highlights behave; document any change for users.

## Object storage / backups

Not in `v1`. Optional later: nightly Postgres dumps, or export JSON per day from a dedicated endpoint.
