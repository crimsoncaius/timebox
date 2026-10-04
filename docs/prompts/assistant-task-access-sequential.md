# Implement Assistant task access sequentially

Use `$sequential-issues` from `C:\Users\Caius\.codex\skills\sequential-issues\SKILL.md` to implement the complete approved Assistant task-access handoff in this Timebox repository.

The source of truth is `docs/specs/assistant-task-access-handoff.md` on `master`, together with its linked decision resolutions and ADRs. The [Wayfinder map](https://github.com/crimsoncaius/timebox/issues/300) and its children are completed planning records; use them as authorities, not as unfinished implementation tickets.

I authorize creating or updating the necessary GitHub implementation issues and their native dependencies, assigning them to the current developer, implementing them, committing the work on an isolated batch branch, and posting concise progress/validation comments. Reuse matching implementation issues if they already exist. Leave implementation issues open for user review; close them only after the user accepts the corresponding implementation. This request does not authorize a PR, deployment, or merging the implementation batch into `master`.

Delegate setup to a worker. Have it read the repository instructions and full approved handoff, establish one isolated worktree and `codex/` batch branch from current `master`, and create or reconcile a concrete issue for each of the five approved slices in this order:

1. Storage, pure saved-task reads, typed snapshots and bounded automatic context refresh.
2. Atomic Task/Subtask operations, proposal lifecycle, reviewed-state guards, durable confirmation receipts and recoverable limited Undo.
3. Android task cards, current-detail navigation, complete change previews, separate description review and receipt presentation.
4. Android readiness/activity coordination, online confirmation, durable submission recovery, reconciliation and the complete vertical path.
5. Integration, deterministic race/failure checks, bounded live-provider checks and native Android review.

Each implementation issue must link the approved handoff and relevant decision resolutions, identify its deliverable and dependencies, and carry the applicable acceptance rows. Wire native dependency relationships in the same sequence. Return a compact issue list and starting commit before dispatching implementation. Ticket setup is authorized; do not reopen settled product decisions merely to create the batch.

Act as the sequential-issues orchestrator: coordinate the ledger and use worker handoffs as evidence. Delegate repository inspection, code changes, tests, application checks and integration. Start one fresh implementation worker per issue, one at a time, passing the accumulated branch, last accepted commit, prerequisite handoffs and applicable constraints. Use the skill's three-attempt limit, safe cleanup requirements, dependency-aware skips and concise required handoff. Each worker must read the full relevant handoff sections, implement and validate its slice, launch affected applications as required, and commit before handing off.

Apply the final accepted amendment: **Android and backend advance together; older application versions are unsupported.** Add no task-specific compatibility negotiation, old-version fallback paths or mixed-version test matrix. Preserve stored records and recovery of submitted operations. Read all supported saved task kinds; only ordinary Tasks and first-level Subtasks are writable. All other scope, privacy, bounded execution, temporal and recovery requirements come from the approved handoff.

Use the existing Android emulator ownership helper and current instrumentation baseline, including its later corrections. Run real PostgreSQL concurrency checks for the transaction guarantees. After deterministic validation, the isolated live-provider verification is authorized within the handoff's ceiling of six scenarios and thirty model calls total, with no automatic provider retries. Track that budget across workers and retries; do not reset it for the final integration worker. If credentials or an external prerequisite are unavailable, report the blocked checks and continue only independent work.

After all issue workers finish, delegate the skill's focused integration check, reusing their validation evidence rather than rerunning every suite. Leave the updated application running from the batch worktree for user review. Report completed, skipped and blocked issues, remaining validation gaps, the branch/commits and the review location, attributing validation to the workers. Wait for a separate explicit instruction before creating a PR or merging this implementation batch.
