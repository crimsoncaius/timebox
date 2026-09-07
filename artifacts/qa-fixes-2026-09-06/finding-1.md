# Finding 1: Subtask checks

Implemented in the shared working tree; ready for coordinator independent verification.

Root cause: `set_subtask_checked` used an unqualified `FOR UPDATE` on a query eager-loading the optional parent with a LEFT OUTER JOIN. PostgreSQL rejects locking the nullable join side. The parent is already locked separately; `.with_for_update(of=Task)` limits the second lock to the Subtask. Existing lock order and parent completion semantics remain intact. No migration or Android source change.

Web now renders the current failure inside Task Details. The checkbox handler records the failure without an unhandled promise rejection; retry clears it and reflects the saved state.

## Reproduction and regression

- Before fix, created isolated parent 8 / Subtask 9; `Invoke-WebRequest http://127.0.0.1:8001/subtasks/9/check -Method Post -SkipHttpErrorCheck` returned HTTP 500 twice. Log: `FOR UPDATE cannot be applied to the nullable side of an outer join`.
- New backend regression captures SQL from actual check/uncheck endpoint requests and compiles the queries with PostgreSQL dialect. It failed on the unqualified join lock before the fix. SQLite behavioral tests alone had missed this production-dialect failure.
- New web test first failed with no dialog alert and an unhandled rejection, then passed after fixing feedback and retry.
- Backend (explicit isolated SQLite, never live database): `$env:DATABASE_URL='sqlite:///:memory:'; .venv/Scripts/python.exe -m pytest tests/test_actual_block_locking.py tests/test_completion_api.py tests/test_battle_plan_api.py tests/test_recurrence.py -q` from backend: **66 passed**.
- Frontend `npx vitest run src/features/battle-plan/BattlePlanPage.test.tsx`: **29 passed**.
- Frontend `npm run build`: passed; `git diff --check`: passed.
- Actual local PostgreSQL after reload: check/uncheck return HTTP 200, true/false as requested, parent remains open.
- Web desktop click updates 1/1 and persists on reload. Recurring occurrence 10 / Subtask 11 checked from web and persisted. `playwright-cli -s=fix1 run-code --filename=artifacts/qa-fixes-2026-09-06/finding-1-web-check.js` passed desktop persistence, injected HTTP 500 visible dialog feedback, retry, 390px mobile check and reload. Injection removed.
- Android emulator-5554: opened parent 8, scrolled to Subtasks, tapped Uncheck, observed 0/1 and Check, then tapped Check. Observed checked title, Uncheck and 1/1, persisted through API reread. Screenshot visually inspected: `android-fix1-checked.png`; hierarchy: `android-fix1-checked.xml`.

## Handoff / live state

- API 8001 Uvicorn normal watcher reloaded to updated code; Vite 5176 serves updated frontend and browser was reloaded. API restart attempt via repository module was automatically rejected before execution, but normal watcher subsequently completed graceful reload. No manual process stop occurred.
- No Android code change; installed app is compatible and talking to the updated shared API. Android is running at parent 8 details, scrolled to Subtasks 1/1. Coordinator now owns emulator.
- Browser session `fix1` remains open at `http://127.0.0.1:5176/battle-plan?task=8`, viewport 390x844, checked.
- Ordinary fixture: parent **8** `QA fix1 check parent`; Subtask **9** `QA fix1 checkpoint` (checked).
- Recurring fixture: series **2** `QA fix1 recurring`, `cycle_limit=1`; occurrence **10**; Subtask **11** `QA fix1 recurring checkpoint` (checked). It cannot generate more than one occurrence. Left for independent verification / coordinator cleanup after finding 4.
- Fixture JSON files preserve IDs. For ordinary cleanup use `DELETE /tasks/8`, then `DELETE /tasks/8/permanent`; do not touch pre-existing task 1 or original QA fixtures. Recurring cleanup should use the supported series-delete behavior once finding 4 is fixed.
- Preserved all unrelated changes and user data. No commit, merge, push or remote deployment. Physical devices and iOS not tested.

Coordinator verification: reviewed all five changed files, approved minimal PostgreSQL row-lock fix and dialog error feedback. Independently created parent12/subtask13 via web UI, clicked check, confirmed persisted checked after reload and board1/1. Playwright check() initially reported synchronous-state mismatch because server response updates asynchronously; API and subsequent awaited checkbox were checked. Native independently tapped Uncheck on subtask9, observed0/1; tapped Check, observed1/1 + Uncheck and persisted true on GET with parent status open. Screenshots coordinator-f1-web.png/coordinator-f1-android.png; native XML coordinator-f1-checked.xml. Finding1 accepted before starting Finding2. Cleanup IDs12/13 added to session fixtures.
