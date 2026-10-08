# Issue 314: read rounds and mixed-tool batches

Implemented on `codex/issue-314-read-rounds`. The model may request any number of independent reads together, using repeated or different supported read tools. The backend executes them sequentially, returns each result under its original call ID, and counts the batch as one of five read rounds. A standalone proposal and final answer remain possible afterward, within seven model calls.

Unknown/disallowed tools and batches containing a proposal are rejected before execution, except for a single allowed proposal. Rejection consumes a round; the final proposal opportunity cannot reopen reads. Individual read validation/runtime errors preserve the other results and return explicit feedback, without automatic retries. Cancellation and the response deadline still stop execution. Failed tool progress is completed so the client does not remain in its reading state.

Removed current-turn, historical-context, per-task-read, refresh, and outcome-context byte allowances. Pagination, the 20-exchange history window, description excerpts, refresh identity counts, outcome counts, three displayed cards, and proposal/review/receipt safeguards remain. Existing stored snapshots marked `payload_limit` remain readable as historical evidence.

## Validation

- All Assistant backend tests: **161 passed, 14 skipped**. Skips require real PostgreSQL transaction-race/admission coverage; this run uses isolated SQLite. Command: `backend/.venv/Scripts/python.exe -m pytest backend/tests -k assistant -q`. The final explicit Assistant-file suite also passed with the same counts.
- Ruff checks on all changed Python files and `git diff --check` passed.
- Android `assembleDebug` passed with `-PreviewApiBaseUrl=http://10.0.2.2:12086/ -PreviewApplicationIdSuffix=.issue314`.
- Exact captured call lists are checked in at `backend/tests/fixtures/assistant_issue_314.json`. Replaying all three against the original graph at `4dba8fae` reproduced the unsupported-operation exception with zero executed reads. Their updated-graph tests run real tools through the conversation route, assert returned evidence and call-ID association, and verify that read-before-create saves only a proposal.
- Additional coverage checks mixed tools, a 14-read batch, five rounds plus proposal/final answer, rejected batches, remaining-round exhaustion, validation/runtime failures, cancellation, more than three captured snapshots, and large Unicode results surviving both current response and historical reload. Existing tests preserve card limits, proposal restrictions, provider retry policy, and the overall deadline.
- The provider-503 test timed out once during the initial busy build/test run; it passed alone and in both subsequent complete Assistant runs without changing that test or provider retry behavior.

## Separate live verification batch

Local evidence is in ignored `artifacts/issue-314-review/`. The review database is a copy of the original study's synthetic `09-create-task.sqlite`; original study artifacts were not changed. These are new live attempts using the current clock, not replacements for baseline failures or continuation of the paused multi-turn study.

- Android week comparison: an initial attempt executed both reads but interrupted afterward; its precise failure was not captured. An explicitly initiated retry requested both weeks in one model response and completed. Saved totals were 22,147.71692 seconds for this week and 18,000 for last week; the answer correctly rounded them to 6h 9m versus 5h, about 1h 9m more. The running synthetic Reading block means totals depend on `read_at`.
- Task prioritisation: three reads executed in one batch, correctly returning overdue work, upcoming deadlines, and ready-to-plan tasks. The answer then failed with `ValueError: Missing presentation header`, matching the separate [issue 315](https://github.com/crimsoncaius/timebox/issues/315). No prioritisation answer passed quality review; read execution alone is not an answer-quality result.
- Read-before-create: both choice reads executed together and returned Project 1 (`Timebox`) and Task Type 3 (`Work/Coding`). A subsequent standalone proposal correctly requested `Check export on Android`, due 2026-10-09. The response completed and the proposal was pending; no task was created or confirmation submitted.

The temporary diagnostic wrapper was removed. The review backend now runs the ordinary updated `app.main:app` from this worktree. The missing-header limitation remains outside this change.

## Pending user review

- Android package: `com.timebox.android.issue314`, showing the completed week comparison.
- Device: `emulator-5588`.
- Reservation token: `3478e2d9220c46be8cb77801181cb77d`.
- Emulator storage root: `C:/Users/Caius/TimeboxRuntime/emulators`.
- Backend: `http://127.0.0.1:12086`, with PID and environment configuration in the ignored review-artifact directory. OpenRouter credentials are inherited from the local environment, not written into the report or review environment file.
- Screenshot: `artifacts/issue-314-review/assistant.png`.

Keep this worktree and backend running during review. Resume with `python scripts/android-emulator.py resume TOKEN` before revising the device, then mark it for review again. After explicit review completion or a successful user-requested merge, release this reservation with `python scripts/android-emulator.py release TOKEN --review-done`. No pull request or merge is part of this implementation request.
