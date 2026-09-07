# Finding 3 — Saved Blocked condition appears Open on web

Implemented in the shared working tree; ready for coordinator review and independent reproduction. Findings1–2 and all pre-existing data/artifacts preserved.

Cause: board grouping and editor initialization read only `status`, while the API correctly stores the independent `is_blocked` condition. The compatibility input `status: blocked` normalizes to `status: open, is_blocked: true`. Plain non-completed status writes do not clear `is_blocked`, so just fixing display would leave moving/unblocking behavior incorrect.

Changes (7 files):

- `frontend/src/lib/battlePlan.ts`: shared presentation mapping; Completed takes priority, then Blocked condition. A Blocked selection changes only the condition, preserving underlying progress; selecting Open/In progress explicitly clears it.
- `frontend/src/lib/api/types.ts`: expose existing API `is_blocked` write field.
- `frontend/src/features/battle-plan/BattlePlanPage.tsx`: group by mapped board state, apply the condition on cross-column moves, preserve canonical status values during reorder, exclude completed rows from the reorder request, reload authoritative state after success or partial failure. Existing finding1 error changes retained.
- `frontend/src/features/battle-plan/TaskDetailPanel.tsx`: initialize and compare selected state using the same mapping; unrelated edits preserve Blocked. Existing finding1 error display retained.
- `frontend/src/features/battle-plan/BattlePlanCard.tsx`: visible Blocked badge.
- `frontend/src/features/battle-plan/TaskComposer.tsx`: create directly in Blocked with the independent condition.
- `frontend/src/features/battle-plan/BattlePlanPage.test.tsx`: four new behavioral regressions and faithful API normalization in fixtures; existing full-draft assertion now expects the condition write. Existing finding1 test retained.

## Verification

- Exact original live repro command: `playwright-cli -s=fix3 run-code --filename=artifacts/qa-fixes-2026-09-06/finding-3-repro.js`. Red before implementation: successful blocked PATCH, then `Blocked tasks` heading wait timed out. Same flow passes after fix (script resets fixture to Open first when already Blocked so it is rerunnable).
- Four new integration cases failed before implementation: save Blocked then board/reopen/reload; clear to Open; clear to In progress; create directly in Blocked. All pass. Coverage also preserves an underlying In progress status and unrelated description updates while blocked.
- `npx vitest run src/features/battle-plan/BattlePlanPage.test.tsx src/lib/battlePlan.test.ts` in `frontend`: **48 passed**, including33 BattlePlanPage tests. An old expectation asserted the legacy blocked status request; updated to the independent condition request and reran green.
- `npm run build`: passed. Focused ESLint on all7 changed files: passed. `git diff --check`: passed (line-ending notices only).
- `finding-3-web-check.js`: **1440x1000 and390x844** passed set/clear Blocked, proper column and card badge, reopen, unrelated description save and refresh. Visually inspected desktop board and mobile detail PNGs.
- `finding-3-drag-check.js`: actual pointer drag **In progress→Blocked→Open**, persisted through reload. The moving task retains underlying In progress when becoming blocked; Open explicitly clears it. A separate blocked In progress peer retains both fields. A completed peer does not cause reorder rejection. No console errors during final checks (only React development notices).
- Android `emulator-5554`, existing native app relaunched, Battle Plan opened and scrolled: **task14 “QA fix3 blocked task” displays BLOCKED**. Accessibility label is `QA fix3 blocked task is blocked`; screenshot `android-fix3-blocked.png` visually inspected, hierarchy `android-fix3-blocked.xml`. Backend/native source unchanged; updated native build not required for this web-only change.

## Review state / fixtures

- Existing Vite watcher served updated code, browser reloaded. No listener restart or allocation change. Browser session `fix3` and native Android left running. Emulator released to coordinator.
- Main fixture **14**, `QA fix3 blocked task`, statusOpen + blockedtrue, no Subtasks or Blocks. Description `QA finding3 verified at390px` (with spaces around390 in actual value).
- Drag fixtures **15** `QA fix3 drag task` (Open, unblocked), **16** `QA fix3 blocked peer` (In progress, blocked), **17** `QA fix3 completed peer` (Completed). Created only for this finding; creation responses in `finding-3-drag-fixtures.json` and `finding-3-completed-fixture.json`. Left for independent review; cleanup may trash then permanently delete only14–17. No timers/Actual Blocks created. Preserve task1 `x` (already blocked with reason before this work), all projects, prior fixture tasks8/10/12, and Planned Block766.
- Drag changes condition and ordering using two existing API calls. If ordering fails after the condition succeeds, UI reloads persisted state and shows the error; the condition may have succeeded while the new order did not. This avoids silently reverting the displayed blocked condition after partial success.
- No commits, push, merge, deployment, or later-finding work. Physical devices/iOS not tested.

Coordinator acceptance: reviewed7-file diff plus tests, including independent condition mapping and actual drag group usage. Fresh web-created task18 QA coordinator finding3: Status Blocked Save returned statusopen/is_blockedtrue; appeared Blocked column; reopened and refreshed retained Blocked at1440/390. Independent native hierarchy and screenshot confirm web-set task14 BLOCKED badge. Evidence coordinator-f3-*; script coordinator-finding3.js. Finding3 accepted before finding4. Added cleanup task18.
