---
name: launch-timebox
description: Launch the Timebox frontend, backend, and Android app.
---

# Launch Timebox

1. Use `manage-dev-ports`; reuse this project’s registered ports: frontend `5176`, API `8001`, and Postgres `54330`. Reuse healthy matching processes.
2. If needed, start the API from the repo root with `backend\.venv\Scripts\uvicorn.exe --app-dir backend app.main:app --reload --reload-dir backend --host 127.0.0.1 --port 8001`. Verify `GET /health`.
3. If needed, set `VITE_API_PROXY_TARGET=http://127.0.0.1:8001`, then start the frontend from `frontend/` with `npm run dev -- --host 127.0.0.1 --port 5176`. Verify HTTP 200 and open it for the user.
4. For mobile, start the `Pixel_9a` AVD using the SDK in `android/local.properties`, wait for boot, run `android\gradlew.bat :app:installDebug` with Android Studio’s JBR, then launch `com.timebox.android/.MainActivity` with `adb`.
5. Verify the app is foregrounded and report the three running surfaces plus any issue.
