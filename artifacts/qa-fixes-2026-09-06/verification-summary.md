# Timebox QA fixes — 6 September 2026

All five original findings were implemented in numbered order, with one implementation subagent per finding. The coordinator reviewed each diff and independently verified its original reproduction before starting the next. Finding 2 was returned to its original agent to correct draft loss across responsive layout changes before acceptance.

| Finding | Change | Verification |
| --- | --- | --- |
| 1. Subtasks cannot be checked | Limit PostgreSQL row locking to the Task table; show checkbox failures in the web details panel. | Web and native check/uncheck, persisted progress, recurring Subtasks; SQL dialect and endpoint regressions. |
| 2. Name save overwrites Note | Protect local drafts from older save responses; retain a single editor across desktop/mobile layouts. | Independently delayed the real Name PATCH by 1.6 seconds at 1440px and 390px, typed Note while pending, checked focus and reload persistence. Also checked resize in both directions, pending draft and Escape. Native Name/Note flow checked. |
| 3. Blocked appears Open | Render and edit the independent blocked condition consistently in cards, columns, forms and drag operations. | Save, reopen, refresh, clear, create and actual drag at desktop/mobile widths; native blocked badge confirms shared state. |
| 4. Ended series deletion fails | Add ended-only DELETE; detach generated Tasks while preserving Subtasks, blocks, quota relationships and skipped-history classification. | Native End → Delete permanently succeeds; coordinator separately repeated it. Web/API confirm removed series and retained checked Subtasks. Migration and retention regressions pass. |
| 5. Unset priorities clipped | Stack Importance/Urgency within the card and allow content-driven height and wrapping. | Final native APK installed without clearing data. Visual checks at default density/font, font 1.3, and 360dp width with font 1.3; coordinator independently inspected the screenshot. Native edit instrumentation passes. |

Detailed evidence: [finding 1](finding-1.md), [finding 2](finding-2.md), [finding 3](finding-3.md), [finding 4](finding-4.md), [finding 5](finding-5.md). Each report includes coordinator acceptance and artifact names.

## Regression results

- Backend: 198 tests passed after the final backend change, using an isolated test database.
- Web: all 209 tests in 23 files passed; production build and ESLint passed.
- Android: final-source rerun passed all 146 unit tests and lint. Application/test APK builds passed; focused native instrumentation passed (1 test).
- An initial final Android run intermittently failed the unchanged Actual-block resize test. The same full command passed on rerun. Both failing and passing XML evidence are retained in `android-existing-resize-flake.xml` and `android-resize-rerun-passed.xml`; no unrelated test change was made.
- Focused browser E2E: 13/17 passed. All four failures also reproduced on starting commit `ebf691e` in a separate baseline checkout and isolated database: recurring active-count expectation, quota automatic-readiness expectation, paired Actual/Planned label assertion after reload, and an ambiguous Today locator. See `regression-checks.txt` and current/baseline error contexts. These are remaining baseline failures, not a fully green E2E suite.
- `git diff --check` passed (line-ending notices only).

## Data, migration and review state

Schema 019 → 020 was applied to local PostgreSQL after a verified backup. The repository's database evidence comparison confirmed identities and counts were preserved. Backup: `C:/Users/Caius/AppData/Local/Temp/timebox-backups/timebox-20260906-202519.dump`, SHA256 `5E849AE2D5E8CBBE98BFB6BB702F3372C475E08D8B0371E62D6DCBD26A8CEEE9`. Downgrading to 019 intentionally refuses once detached history exists, to avoid data loss.

The database container unexpectedly exited during final checks (exit 255, not reported OOM-killed). The repository launcher resumed the same container and volume; PostgreSQL completed recovery and `/ready` again confirmed schema 020. No restore, volume replacement or data reset was performed. The cause of that local container interruption was not established.

Cleanup removed only identified session QA parent tasks 8/10/12/14–19 and their Subtasks, block 766, the original ended QA series 1, and unused QA Task Type 6. Series 2/3 had already been deleted by the verified native flow. The earlier report's trashed tasks were preserved. Before/after comparisons confirm original Task 1, projects, settings and future planned work unchanged. No Actual Block is active. Cleanup evidence is in `cleanup-user-data-before.json`, `cleanup-user-data-after.json` and `final-live-tasks.json`.

Final live web and native navigation through Day, Chronicle and Battle Plan passed after recovery and cleanup. Native Task 1 details again exposed both full priority values in the hierarchy. Screenshots from finding 5 establish visual legibility. Emulator density 420 and font scale 1.0 were restored. Web remains served from this working tree at http://127.0.0.1:5176, API at http://127.0.0.1:8001; the updated native MainActivity was relaunched. A concurrent prototype task also uses this emulator and may change its foreground activity. Its manifest, source and launch-script edits were preserved.

Temporary E2E listeners 15174/18001 were stopped. No push, merge or remote deployment occurred. No worktree merge/cleanup was applicable. Existing untracked directories were preserved. Automatic approval review blocked optional removal of the baseline snapshot's dependency junction; that link and its snapshot remain in artifacts, and current workspace dependencies remain intact.

Physical devices, iOS, landscape/tablet native layouts, TalkBack and notification delivery were not tested. Blocked drag still uses separate condition and reorder API calls; partial failure reloads persisted state and reports the error.

After writing this report, automatic approval review rejected a final combined readiness/file-existence/Android-foreground command with the generic reason `blocked by policy`, before execution. Earlier independent readiness, successful MainActivity launch and live flow checks remain the verified evidence; the rejected check was not retried. No required implementation or original reproduction was left unverified.
