# Finding 4 — permanent deletion of ended Recurring Task Series

Implemented and verified against the running API, web app, and native Android emulator on 2026-09-06. Coordinator independent verification remains the next sequential gate.

## Diagnosis and change

The Android client already sent DELETE /recurring-templates/{id}, and its confirmation promised to preserve generated tasks and detach them. The API had no DELETE route; the existing lifecycle test explicitly expected 405. A changed API test reproduced exactly `405 {"detail":"Method Not Allowed"}` before implementation.

The route now returns 204 with an empty body after permanently deleting an ended series, 422 for an active/paused series, and 404 for an absent series. Generated Tasks and Subtasks retain their identities, checked/completion state, task roles, dates, project/type links and blocks. Their series reference is cleared. Quota Trackers retain their Session Tasks and continue deriving completion correctly. No web deletion control was added: the existing web lifecycle supports pause/resume/end; the shared API deletion is currently exposed in native Android.

Migration 020_detach_series_history makes the occurrence ledger's series reference nullable with SET NULL. This retains skipped-history classification, preventing deleted-series history from silently becoming current work. Only generation tombstones without a surviving task are deleted. Detached occurrences no longer participate in recurrence readiness synchronization. API occurrence identity is null when its series no longer exists, keeping existing client schemas valid.

Downgrade to019 works before any detached history exists. After deletion, downgrade explicitly refuses instead of destroying the ledger or inventing a deleted series. A pre-migration backup is available; no destructive rollback was attempted on user data.

## Verification

- Red: targeted lifecycle test failed with405 before the fix; expanded deletion tests were also run red.
- Green: `backend/.venv/Scripts/python.exe -m pytest tests/test_recurrence.py tests/test_series_delete_migration.py -q` —35 passed.
- Full backend: `.venv/Scripts/python.exe -m pytest -q` —198 passed in19.21s.
- Regression cases cover ended deletion, active/paused rejection, absent series, checked Subtasks and completed tasks, Planned/Actual Blocks, skipped visibility, quota Session Task completion after deletion, migration row retention, and lossless rollback/refusal behavior.
- `git diff --check` passed (only existing line-ending notices).
- Live PostgreSQL migration019→020 applied successfully. Repository database-evidence comparer confirmed identities and counts preserved. `/ready` reports current/head020 and ready.
- Native original flow: BattlePlan→list selector→Recurring→QA fix4 native deletion→End→End template→Delete→Delete permanently. App returned to Recurring and displayed `Recurring template deleted`; series3 GET returned404. Screenshot and UI XML saved.
- Web saw series3 in Active, then Ended after native End; refreshing Ended after native Delete removed it. Opening retained task19 displayed the checked checkpoint. API independently confirms series reference and occurrence identity null, with Subtask20 checked=true.

## Live state and fixtures

- Original series1 `QA 0906 web series` and series2 `QA fix1 recurring` were not changed.
- This fix created only series3 `QA fix4 native deletion`, cycle_limit1, Task19, Subtask20 `Preserve checked checkpoint`. Checked Subtask20 to protect generated work during End; native Delete removed series3. Task19/Subtask20 remain detached for coordinator inspection and later QA cleanup. No blocks or Task Types were created in the live database.
- Original user task1/projects and prior fixtures8,12,14–18/block766 were not altered.
- Emulator is available for the coordinator and remains on native Recurring. Android source is unchanged, so no reinstall/data clear was needed. API watcher loaded the updated code; live204/404 behavior verifies it. Web session `fix4` is left open at `http://127.0.0.1:5176/battle-plan?task=19`.
- Verified backup: `C:/Users/Caius/AppData/Local/Temp/timebox-backups/timebox-20260906-202519.dump`,46982 bytes, SHA256 `5E849AE2D5E8CBBE98BFB6BB702F3372C475E08D8B0371E62D6DCBD26A8CEEE9`.

## Evidence

- finding-4-before-db.json / finding-4-after-db.json / finding-4-backup.json
- finding-4-native-fixture.json / finding-4-detached-task.json
- finding-4-native-end-confirm.xml / finding-4-native-delete-confirm.xml
- finding-4-native-deleted.xml / finding-4-native-deleted.png
- finding-4-web-deleted.yml / finding-4-web-preserved-task.yml

One combined fixture-create and adb launch/capture command was rejected by automatic approval policy before execution, with a generic blocked-by-policy reason. Focused read-only inspection showed the app already foreground; safe focused API fixture creation and ordinary native input/capture then succeeded. No process killing or rejection bypass was used.

Coordinator acceptance: reviewed endpoint, service, model/serializer/visibility synchronization changes, migration and preservation tests. Independently used native series2 QA fix1 recurring→End→End template→Delete→Delete permanently. Toast confirmed deleted; GET series2=404, retained task10/subtask11 same IDs and checkedtrue, series/occurrence reference null. Web task10 details visually/DOM confirmed checked checkpoint. Evidence coordinator-f4-*. Finding4 accepted before finding5. Original ended series1 remains for final cleanup;2 already deleted. Schema020 verified /ready; backup exists.
