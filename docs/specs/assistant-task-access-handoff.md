# Assistant access to Battle Plan Tasks — implementation handoff

Status: **approved and implementation-ready, 2026-10-04**. The user accepted the final handoff, with one amendment: they are the sole developer and user, so older Android/backend versions do not need support. This document completes [Agree the Assistant task-access specification and acceptance criteria](https://github.com/crimsoncaius/timebox/issues/305). Implementation, deployment, PR creation and merge require a later request.

## Authorities and scope

The authoritative product decisions are [task discovery and reads](https://github.com/crimsoncaius/timebox/issues/301#issuecomment-5975824468), [ordinary changes and side effects](https://github.com/crimsoncaius/timebox/issues/302#issuecomment-5975905998), [confirmation and recovery](https://github.com/crimsoncaius/timebox/issues/303#issuecomment-5975957264), and [approved presentation](https://github.com/crimsoncaius/timebox/issues/304#issuecomment-5976404278). This handoff supplies their schemas, bounds, integration seams, and acceptance criteria; it does not supersede their product semantics.

Target Android's existing Assistant and the backend. Read saved ordinary Tasks, Subtasks, Task Occurrences, Quota Trackers and Session Tasks. Propose changes only to active ordinary Tasks and their first-level Subtasks. Assign existing Projects and Task Types. Exclude recurring writes/generation, Project/Task Type management, Trash/deletion/restore/archive, Subtask removal/reordering/reparenting, task ordering, direct Block editing, offline task confirmation, general/batch Undo, web Assistant and external-agent access.

The model proposes. An explicit user confirmation executes an immutable, finite set in one server transaction. Complete-now retains existing Task Completion effects; earlier completion leaves all Blocks alone. One response may propose one task-change set **or** one Tracking Proposal. Reads can cover both domains.

Approved [prototype and review guide](https://github.com/crimsoncaius/timebox/blob/3e6e3a73/docs/design/assistant-tasks-304/README.md) are design evidence, not production code. Rewrite the interaction in Compose. Historical Task Cards, proposed changes, and authoritative receipts are separate records.

## Existing implementation and required seams

| Current seam | Required change |
| --- | --- |
| `backend/app/api/routes/assistant.py`, `services/assistant_agent.py` | Add task access directly to the current Android/backend protocol, with typed read/proposal tools and stream events; retain bounded loop and existing tracking behavior. |
| `services/assistant_storage.py`, `models/assistant.py` | Typed snapshot replay; capture automatic current-state/outcome inputs; persist immutable task proposals and independent execution results. Existing replay incorrectly assumes every snapshot above version 1 is an activity read. |
| `services/battle_plan/tasks.py`, `task_completion_service.py` | Extract transaction-neutral operations from helpers that currently commit individually. Existing manual route wrappers retain their commits and semantics. |
| `db/activity_admission.py`, `db/session.py`, `api/activity_gate.py` | Admit short task execution/lifecycle transactions separately from SSE; preserve cutover, paused-recovery and protocol gates. |
| Android `ui/assistant/AssistantTransport.kt`, `AssistantController.kt`, `AssistantCard.kt` | Typed task cards/proposals, response-completion eligibility, separate execution state and durable recovery metadata. Existing controller and transcript are process-local. |
| Android `ReadyToPlanCoordinator`, `ActivityRepository`, `ui/taskcompletion/TaskCompletion.kt` | Add scoped confirmation barriers, result reconciliation and adoption of sole-complete-now Undo. Existing readiness has no drain/reservation API; activity `refresh()` alone is not a successful synchronization guarantee. |

Keep one API worker for the existing Assistant generation coordinator. PostgreSQL is the production concurrency target; SQLite remains single-process for supported local use. No multi-worker Assistant redesign is included.

## Accepted bounds

Issue #314 revised read orchestration and removed read/context byte budgets on 2026-10-08; see [ADR 0017](../adr/0017-assistant-bounded-read-loop.md). Proposal and receipt byte safeguards remain unchanged.

Remaining proposal and receipt byte sizes below mean UTF-8 serialized JSON. Validate before making a proposal eligible; never truncate a mutation or its reviewed consequences. Centralize constants alongside `assistant_limits.py`, with corresponding client validation limits.

| Resource | Bound and behavior at the limit |
| --- | --- |
| Shared model loop | Allow 5 read rounds with any number of independent calls per round, 1 standalone proposal of either kind, 3 cards, at most 7 model calls, 4,096 output tokens per call, 4,000 input characters, 120 seconds total, and no automatic provider retries. Rejected batches consume a round. A proposal attempt consumes its single allowance even when validation fails. No reads follow either proposal tool. |
| Rolling context | Keep at most 20 acknowledged completed exchanges and their historical snapshots. No application-imposed byte or token budgets on historical reads, current-turn reads, task refresh, or outcome context. Provider context limits and ordinary conversation exchange/input bounds still apply. |
| Search | 20 rows per page; at most 1,000 identities in one saved search manifest. Inspect up to 1,001 eligible matches to distinguish exact count from a lower bound. A larger result is partial and requires narrowing for complete coverage. |
| Individual read | No byte cap; up to 10 explicit Task IDs in a detail call. Nested Subtasks and Session Tasks have 20-row pages. Omitted rows have counts and a continuation; description excerpts remain explicitly truncated. |
| Automatic refresh | At most 40 distinct known Tasks per response; no byte budget for current projections. No extra model call. Remaining known identities are explicitly unverified. |
| Outcome context | At most 20 operation summaries without a byte budget. An explicitly referenced operation has priority; count overflow is declared, not presented as no changes. |
| Task-change set | At most 5 distinct parent Tasks, including created parents, and 20 operations total, including Subtask operations. Up to 64 KiB for the complete canonical review payload; up to 64 KiB for a receipt. Oversize requests produce no confirmable proposal; ask the user to narrow or choose separately confirmed sets. |
| Description edit | Proposed text at most 8,000 characters and 16 KiB. Full current and proposed text must fit the review payload. Existing descriptions have no domain maximum: if the full current value cannot fit, direct the user to Task Detail; never approve an unseen truncation. This limit affects Assistant edits only. |
| Explicit description read | Keep the existing 2,000-character per-item model limit and explicit truncation flag. Reading a capped excerpt cannot authorize a claim that the full text was reviewed. |
| Proposal life | 15 minutes from server proposal creation, enforced at execution admission. Relative dates use the separate saved request-time anchor. |
| Execution | No provider call under a write lock. At most 5 seconds admission wait and 10 seconds transaction work, with a 20-second server request budget and 30-second client call deadline. If outcome cannot be established, recover it rather than infer failure. |
| Recovery | At most 20 unresolved local submission records. Read-only recovery batches accept up to 20 IDs. One foreground recovery cycle makes at most 3 status calls, at 0, 1 and 3 seconds; then expose Check result. No background polling loop, automatic mutation retry or automatic task execution. |

Search pages are read-only presentation/discovery. Five Tasks/twenty operations constrain one atomic confirmation, not the number of saved Tasks the Assistant can inspect over multiple responses. A collection cannot be silently split into separately committed sets.

## Read contracts

Expose `read_tasks` and `read_task_choices` as read-class tools. One model response requesting one or more independent reads consumes one of five read rounds. Mixed read tools execute sequentially in request order, preserving original call IDs. Dependent calls need another round. Reject unknown-tool and mixed read/proposal batches before execution with feedback for every call; rejection consumes a round. Per-call validation and runtime failures return explicit errors while the remaining reads continue, with no automatic retry. Cancellation and the overall deadline stop execution. After five rounds, allow a standalone proposal attempt and a final tool-free answer within seven total model calls. Strict schemas reject extra fields and invalid combinations. IDs use existing integer saved IDs; opaque snapshot, query, proposal and operation IDs are server-issued UUIDs. The client/model must never synthesize a saved Task ID.

`read_tasks` is a discriminated union:

| `mode` | Arguments |
| --- | --- |
| `search` | `query` (optional, at most 500 characters); `search_in: titles|descriptions`; `scope: current|history|saved_future`; `kinds`; filters; optional opaque cursor for an unchanged query. Default current/titles, incomplete saved work, including Blocked. |
| `get` | `task_ids` (1–10); `include_description` (default false); optional nested collection cursor. |
| `children` | `parent_id`; `kind: subtasks|sessions`; optional cursor. Do not reinterpret quota Sessions as Subtasks. |
| `outcomes` | Up to 20 explicit operation IDs belonging to this conversation; returns authoritative outcome summaries, not Task snapshots. |

Filters: existing Project ID or explicitly unassigned; Task Type ID/subtree or explicitly Unset; completion `incomplete|completed|skipped|any`; Blocked and Ready to Plan booleans; urgency/importance values or explicitly unset; deadline/reminder ranges; completed-at range for explicit history. Date ranges use inclusive start/exclusive end in the captured Reporting Time Zone. Date-only deadline comparisons use calendar dates; timed deadline/reminder comparisons use resolved instants. Reject inappropriate field/kind combinations rather than silently ignore them. `history` with `any` is explicit; changing scope never accidentally broadens the default query.

Search Task and Subtask titles case-insensitively using literal substring matching, with SQL wildcard escaping. A matched Subtask yields its parent plus matched Subtask identities, not a new independently mutable Task. Quota Tracker rows remain aggregates; default root discovery groups their current saved Session Tasks under the tracker. Explicit Session requests/search may return typed Session rows. Parent and child matching does not duplicate the same parent row in one root search. Description search requires the current explicit description request/clear follow-up and searches saved text; excerpts remain capped. No raw SQL or user-defined sort expression is accepted.

`read_task_choices({kind: projects|task_types, query?, cursor?})` returns existing IDs and names/paths in pages of 20, sorted case-insensitive name/path then ID, under the same per-read bounds. It creates nothing. Choice results have their own `kind: choices`, are captured/replayed as reads, and are not Task Cards. Ambiguous names require clarification. A selected Task Type must still exist with the reviewed identity/path at execution; merge-successor rewriting must not silently rebind it.

### Saved search identity and pagination

Create a server-owned query manifest with normalized filters, Reporting Time Zone, captured `query_at`, ordered eligible saved IDs and count metadata. Default order is `(position, id)` as in the saved collection; Task/session kinds are explicit. No implicit relevance score or changing priority order. Store at most 1,000 IDs, marking a 1,001st match as a lower bound and query-limit truncation. This is a manifest of saved matches, not recurrence generation.

Cursor contains an opaque reference to manifest and offset, bound to the same conversation/query. It cannot accept client-supplied filters or arbitrary IDs. Cursor reuse is allowed for 15 minutes, after which a new query is required. Retained manifest evidence follows conversation retention; cursor expiry does not erase past results.

Each page receives a **new immutable snapshot ID** and its own read time. It preserves manifest order. A Task removed/excluded between pages is represented as unavailable at its existing position. Counts describe membership at `query_at`; later page fields describe `read_at`. State changes/new membership require a new search. Never claim cross-page values were all read simultaneously. Already loaded card rows expand locally; fetching another page is a new bounded read and never rewrites the earlier snapshot. Ordinals always refer to the particular original result, not a later query.

### Task snapshot schema

Add strict snapshot schema version 4 with `kind: tasks`. Do not change schemas 1–3. Store the normalized source tool/arguments for typed replay.

```text
TaskSnapshotV4 {
  schema_version: 4, kind: "tasks", snapshot_id, read_at, reporting_timezone,
  source: {tool, mode, normalized_arguments},
  query_id?, query_at?, matching_count?, count_relation: exact|at_least|unknown,
  next_cursor?, completeness: complete|partial,
  limitations: [saved_only|query_limit|payload_limit|unverified_readiness|unavailable],
  rows: ordered TaskProjection[]
}
TaskProjection {
  id, kind: ordinary|occurrence|quota_tracker|session,
  availability: available|unavailable|unverified,
  title?, lifecycle?: incomplete|completed|skipped,
  project?: {id,name}, task_type?: {id,path},
  ready_to_plan?, blocked?, blocking_reason?, urgency?, importance?,
  deadline?: {kind: date,date}|{kind: instant,instant}, reminder_at?,
  completed_at?, completion_precision?: date|instant|unknown,
  completion_local_date?, completion_timezone?,
  parent_id?, series_id?, period?: {start,end},
  quota?: {required,completed,saved_session_count},
  subtasks?: [{id,title,checked}], subtasks_count?, subtasks_next_cursor?,
  planned_dates?: [...], planned_dates_complete?,
  description?: {text,truncated}, projection_hash?, field_coverage
}
```

Fields not applicable to a kind are omitted, never fabricated. Unavailable identities include only ID/kind/availability; no archived/trashed details or reason revealing excluded content. `field_coverage` distinguishes fetched, intentionally excluded and truncated fields. Missing description is not empty description. Task Cards use a separate allowlist excluding description and internal hashes/tokens/source arguments; they show identity, useful classification/state, dates and limitations. No Supporting Notes enter task projection.

Use dedicated allowlisted SQL projections with autoflush disabled. Do not serialize existing `TaskRead` wholesale because it always includes descriptions. Never call `list_tasks`, recurrence synchronization or Trash cleanup. Compute only current relevance of existing records using existing period and carry-over rules: scheduled expired unfinished work carries when the series permits it or a saved Planned Block is on a Day on/after Today; quotas do not carry. Choose the oldest actionable saved occurrence per series for current root discovery. Expose current/future/history classification without persisting skip/readiness changes. Preserve the distinction between this day-based carry-over rule and complete-now's strict instant-based future-plan cutoff.

### Freshness and context budget

Before every model response, collect known Task identities from snapshots still eligible for the bounded context, including seen child/session identities and referenced proposal targets. A seen Subtask maps to its parent projection with explicit per-child refreshed/unavailable/unverified coverage; Subtasks are not a fifth root Task kind. A truncated parent collection never verifies an omitted known child. Count each parent and explicitly covered child/session identity toward the 40-identity refresh bound. Deterministically prioritize the current pending set, then most recently seen identities, then ID. Recompute the full allowlisted projection from Task, children, Project/Type labels, recurring period information and relevant planned dates; do not trust only Task.version. Compare canonical projections/hashes. Never fetch descriptions as part of automatic refresh; a current authorized read must request them explicitly.

Capture `task_refresh_v1` on the attempt, containing `read_at`, requested/refreshed IDs, current projections, changed-field names, and unavailable/unverified IDs. This is an injected system data section with untrusted-data framing, not a fabricated model tool call. Historical descriptions remain historical and never become verified by this refresh. If count or time bounds prevent verification, mark the identity/affected fields unverified; a narrower explicit detail read can supply them.

Retain historical snapshots and their replay references for up to 20 acknowledged transcript exchanges, without byte-based eviction. Refresh known identities from that retained context; identities beyond refresh count or time bounds remain explicitly unverified. Preserve all durable snapshots/cards. No false empty result or false freshness on omission.

Model-selected reads have no cumulative byte allowance. Read rounds, rather than individual calls or successful snapshots, bound model iteration. Preflight and reads share the 120-second deadline, with preflight at most 5 seconds and each saved-data query at most 5 seconds. Timeout markers are captured. Current collection questions still require a fresh search, because refreshing known identities cannot discover new members. If an ordinal's original snapshot has left context, ask which Task or let the user reselect it; never bind the ordinal to newly sorted data.

## Proposal and execution contracts

`propose_task_changes({operations})` consumes the single shared proposal slot. It performs no domain writes. It resolves all IDs and dependencies, reads current relevant state, validates the whole set, computes the canonical review and saves a draft proposal. The proposal tool result describes the draft and its effects; a model cannot choose its identities, preconditions or receipt.

```text
ProposalV1 {
  schema_version: 1, proposal_id, revision: 1, operation_id,
  conversation_id, originating_run_id, created_at, expires_at,
  request_anchor: {received_at, reporting_timezone},
  content_hash, operations, review_targets, side_effects,
  description_review_available, status
}
```

Re-review/replacement creates a fresh immutable proposal and operation ID, optionally `supersedes_proposal_id`; it never increments a revision in place under an existing executable identity. Version 1 reserves `revision` and requires exactly 1. The server retains old proposed content and lifecycle events. A content hash is a comparison/integrity check, not authorization. The canonical record is server-owned: model tool results and SSE use a safe review summary without full private before-text, guards or Undo tokens. Full description before/after text is fetched only for the dedicated explicit review, and any model-visible description excerpt retains the 2,000-character cap.

### Operations

| `op` | Strict payload |
| --- | --- |
| `create_task` | `ref` unique within the set; `title`; optional explicit creation fields from the patch vocabulary. |
| `patch_task` | `target: {id}` or `{ref}`; nonempty `set` with explicitly requested fields only. |
| `add_subtask` | `parent: {id}|{ref}`; unique `ref`; `title`. |
| `rename_subtask` | Existing Subtask ID; expected parent ID; `title`. |
| `set_subtask_checked` | Existing Subtask ID or earlier created Subtask ref; expected parent; boolean `checked`. |
| `complete_now` | Ordinary Task target. Server captures one completion instant for the whole set at confirmation after admission. |
| `complete_at` | Ordinary Task target; `{precision: date, local_date, reporting_timezone}` or `{precision: instant, instant, reporting_timezone}`. |
| `reopen_task` | Ordinary Task target, explicitly requested by the user. |

Patch fields: `title`, `description`, `project_id`, `task_type_id`, `urgency`, `importance`, `deadline`, `reminder_at`, `blocked`, `blocking_reason`, `ready_to_plan`. Omitted means unchanged; `null` clears only nullable fields. Title/booleans cannot be null; description null clears to empty. Normalize blocking reason according to the agreed reason/Blocked semantics. Reject contradictory deadline or blocking input, unknown fields and unsupported operations. Reference only earlier creates; no cycles, forward references, title lookup or replacement of whole child lists. Preserve operation order and validate the simulated intermediate state; explicit reopen must precede editing a completed Task. Canonicalize repeated scalar edits into their final reviewed value, but do not hide lifecycle transitions or side effects. Reject contradictory complete/reopen cycles on one Task within a set.

Keep domain validation: trimmed nonblank title ≤500 characters, blocking reason ≤1,000, priority `low|medium|high|null`, existing reference eligibility. Description limits above are Assistant-specific. Unset is null Task Type, distinct from the `unspecified` Task Type. All saved recurring kinds are rejected as write targets even if their storage uses the same table. Creating a new ordinary Task never acts as an unrequested workaround for a recurring change.

Relative dates are anchored to the server-saved request reception time and Reporting Time Zone. Proposal displays the resolved date/time. Past date-only completion uses local noon internally, stores UTC plus precision/local-date/zone intent, and displays only the date. Add nullable completion-intent metadata to the Task (and receipt), not only transient chat text; existing manual completions have unknown/instant-compatible legacy presentation and reopening clears that metadata. Earlier today requires an explicit time. Earlier completion may precede creation; future completion is rejected. DST gaps are invalid; ambiguous local times require an explicit offset/occurrence instead of guessing. A Reporting Time Zone change affecting interpreted dates requires re-review. Reminders must still be future at confirmation; no automatic shifting.

The operation table describes canonical server output, not model-authored UTC arithmetic. Tool input uses a strict `DateIntent` union: `{kind:absolute,date}`, `{kind:day_offset,days}`, or `{kind:weekday,weekday:1..7,week_offset}` relative to the request's Monday-based Calendar Week. Offsets are integers bounded to ±366 days or ±53 weeks; dates outside that relative window can use explicit ISO dates. A timed value is `{date:DateIntent, local_time, utc_offset?}`, or `{kind:relative_minutes,minutes}` bounded to ±525,600 minutes from request reception. The server resolves these to canonical date/instant/precision, validates offset against the captured IANA zone and rejects ambiguous/nonexistent local times without disambiguation. The model interprets wording into this small intent vocabulary; materially ambiguous wording requires clarification. Preserve the intent with the proposal so confirmation never re-anchors it. Apply this resolver consistently to deadline, reminder and earlier-completion fields.

### Review-sensitive guards

The server creates guards; clients cannot submit arbitrary expected-state patches. Every target guard covers saved ID/kind, active lifecycle, parent relationship, and displayed identity title. Requested field groups include their normalized before-values; unchanged unrelated Task fields are not overwritten or guarded. Reference assignment guards include ID and reviewed Project name/Task Type path. Type merge/delete or Project deletion requires re-review, not implicit rebinding.

Complete-now additionally guards the linked running Actual identity/start/link, the eligible future Plan set and its material values, Actual correspondence affected by plan removal, flags/reason/reminder/delivery bookkeeping, and relevant Undo inputs. Compare side-effect membership using the actual completion instant: a plan crossing from future to already-started can change the set and requires re-review. Normal elapsed tracking time alone is not a conflict. Backdated completion guards the flags it clears and linked-activity identity used in the review; it never alters Blocks. Reopening guards completion and explicitly requested subsequent fields without promising restoration. Subtask changes guard parent eligibility, child identity and changed title/check fields. Compare description content by server hash/full value without exposing it for unrelated edits.

Preview must enumerate every target and identify/count every affected plan, with expandable details within the bounded payload. If it cannot fit, no confirmable proposal is emitted. A broad query becomes a fixed ID list; newly matching Tasks are never added at execution.

### HTTP API and lifecycle

All routes use existing API authentication and conversation/operation association checks. The current service is single-account; UUIDs are not access control. Do not add new unauthenticated recovery URLs or broaden tenancy assumptions.

| Route | Contract |
| --- | --- |
| `GET /assistant/task-proposals/{id}` | Full immutable review plus current authoritative lifecycle; full description review only for a proposal explicitly editing descriptions. Bounded response; no model invocation. |
| `POST /assistant/task-proposals/{id}/confirm` | Body `{revision, operation_id, submission_id}` only. Submission UUID identifies one client confirmation attempt, not new mutation content. Enforce identity match, activated source eligibility, active proposal, expiry, domain gates and guards. Return original receipt for already-applied identity before checking expiry/closed conversation. |
| `POST /assistant/task-proposals/{id}/dismiss` | Idempotent cancellation of an unconfirmed proposal. If execution won the race, return its result instead of claiming cancellation. |
| `POST /assistant/task-proposals/{id}/refresh` | Revalidate the same explicit request/targets and return a fresh reviewed proposal; no mutation or model call. Preserve request time anchor and already-resolved dates. Invalid targets or contradictory intent require a new request. This successor inherits the completed source-response eligibility, is captured separately, and still needs a new user tap. |
| `POST /assistant/task-operations/status` | Status-only body `{operations:[{operation_id,submission_id?}]}`; per-operation and per-submission lifecycle/result, including closed-conversation operations. Reject cross-association requests. Never executes task changes. |
| `POST /assistant/task-operations/{id}/undo` | Body `{undo_operation_id,submission_id}` using the server-issued operation identity returned with an eligible receipt. Repeated success returns the original Undo result. |

Successful status reads return `200`. Submitted duplicate/in-flight checks that cannot yet acquire a conclusive view return `202` with `checking`, never a failure assertion. `409` carries stale/replaced/cancelled/ineligible/conflicting-Undo details and no mutation; `410` expired; `422` invalid arguments; `503` unavailable admission/service. Domain guards/preconditions are checked before any mutation. A transport error or bare HTTP error after submission does not prove rollback; the client obtains an authoritative operation status.

Persistent proposal lifecycle: `draft`, `pending`, `applied`, `stale`, `cancelled`, `replaced`, `expired`, `invalid`. Submission status has `submission_id`, `operation_id`, `state: not_seen|executing|rolled_back|rejected|applied`, recorded timestamps, safe reason code and optional receipt reference. `not_seen` is a query observation, not a durable terminal state. A definite transient rollback records a failed attempt while leaving the same proposal retryable only while eligible; it does not mint a new operation. Client-only states add `sync_blocked`, `submitting`, `unknown`, `checking`. Do not conflate a client not knowing with a server-declared failure.

Draft becomes pending only in the transaction that successfully captures the originating response as completed. That transaction replaces any prior pending set in the conversation and permanently records `source_completed_at` on the activated proposal. Failed/stopped/unsaved attempts invalidate their drafts. Response acknowledgement controls rolling prose/read history, not execution eligibility or durable results. Stopping an already completed response must not retroactively revoke an activated pending, submitted or applied task proposal; do not re-derive its eligibility from a later mutable attempt status. Explicit dismissal/closure, replacement and expiry govern pending proposals instead.

Unrelated turns/navigation preserve pending state. Explicit New conversation closes the old conversation and invalidates its unconfirmed set atomically with new conversation creation (extend create with optional `previous_conversation_id`). Submitted execution and results remain recoverable. Persist the prior conversation ID locally only to finish closure after reconnect/restart; do not persist its transcript as a new history interface. If offline, show a fresh local view but do not claim server cancellation until acknowledged. Server expiry is an independent final guard. Dismissal and replacement follow the same authority rule.

### Atomic execution and writer admission

Use the existing request admission mechanism in **exclusive mode selected up front** for short Assistant confirmation/Undo transactions. PostgreSQL session advisory admission then excludes all existing shared-admitted domain writers; SQLite uses its supported single-process mutex. Never acquire shared admission then upgrade. Never hold admission around provider calls or the SSE lifetime. Enforce activity protocol/cutover/paused-recovery gates on these new routes separately; the current Assistant router intentionally bypasses ordinary long-lived DB dependencies.

Proposal activation/replacement/dismissal/refresh/closure and startup draft recovery must also participate in admission and durable conversation/proposal locking. Existing Assistant storage uses direct Sessions, so exclusive domain admission alone is insufficient until these lifecycle writers join. Acquire and release admission per short storage transaction, not around an entire streamed response. Future background writers must use admission; direct fixture/migration writers are unsupported concurrently with a serving database.

Under exclusive admission, re-read and lock durable proposal state, establish the one completion instant, then acquire domain locks in established global order across **all** targets: Activity State, Tasks (ascending ID), Days, Planned Blocks, Actual Blocks, domain operation records. Do not interleave per-Task lock orders across the set. Validate all guards and intermediary constraints. Invoke transaction-neutral primitives and persist the successful receipt, created ID mapping, canonical activity-journal entries and applicable Undo record in the same database commit. Preserve the existing `task-completion` journal path and Actual/Plan link behavior. A later Subtask or Task failure rolls back everything.

No asynchronous execution worker/lease system is necessary for these bounded synchronous transactions. Persist a small submission claim (`submission_id`, operation association, `executing`) before domain execution while retaining the same exclusive admission across that claim commit and the following domain transaction. Success commits its submission outcome with the domain effects and receipt. After rollback, persist a terminal rolled-back/rejected submission outcome before releasing admission. A repeated submission ID returns that outcome and never executes again. A new explicit retry after a definite rolled-back attempt uses a new submission ID for the **same** proposal/operation; it revalidates and never changes the mutation content.

A status read that acquires admission and finds a claimed submission still marked executing but no committed receipt can settle that abandoned submission as rolled back: no executor can still hold the exclusive admission. By contrast, `not_seen` or pending without a terminal submission outcome is **inconclusive**: the original HTTP request may arrive late. Keep its recovery barrier; the user can retry the same submission ID if still eligible. An operation-level terminal cancellation/expiry/stale state also fences late execution. A mere status read must never release the barrier on the assumption that a delayed request will not arrive. Successful duplicate confirmations return the original committed operation receipt, even across different submission IDs. Client-local submission alone does not prove the server accepted execution; close/cancel races report whichever outcome the server establishes. Do not introduce an abort button that claims to undo an already submitted change.

Status recovery and immutable review remain available during paused activity recovery or a maintenance pause on new task writes. Explicitly exempt these status/review routes from the blanket POST-as-mutation gate while preserving authentication and association/protocol checks. Status may reconcile abandoned submission metadata under admission but never applies a Task change. Confirmation/Undo remain gated. Admission timeout reports checking. Database commit is the only success authority, and no proposal/operation identity is released for a duplicate mutation.

This scoped admission can briefly delay unrelated writes. The finite wait/work budgets bound that cost. It avoids claiming correctness from Task.version or incomplete row-lock coverage. Do not loosen it without PostgreSQL race evidence and a reviewed alternative.

### Receipts, Undo and retention

```text
TaskReceiptV1 {
  schema_version: 1, operation_id, proposal_id, revision, content_hash,
  conversation_id, originating_run_id, committed_at,
  outcome: applied, created: {within_set_ref: saved_id},
  changes: [{target_id, kind, changed_fields, approved_after_values}],
  effects: {completion_instants, stopped_actual_ids, removed_plan_ids,
            detached_actual_links, cleared_fields},
  undo?: {undo_operation_id, eligible: true, limitations},
  status_events: [{event_id, at, kind, related_operation_id?}]
}
```

Full description text belongs only to the dedicated explicit review, not a Task Card, general receipt or automatic context. Its receipt identifies the changed field and hashes/size; subsequent current text requires a newly authorized read. No Undo token or internal guard is sent to the model. Safe failure receipts identify the failing operation/reason without exposing unrelated private data.

Retain proposals, execution results, refresh evidence and outcome events without automatic expiry alongside retained Assistant conversations. Proposal expiry prevents new execution, not result lookup. The original applied receipt remains immutable after Undo; append an Undo outcome with its own identity and timestamp. New context may state that completion was applied and subsequently undone without rewriting old prose/cards. No conversation-history UI or implicit cross-conversation memory is introduced.

Only a set whose sole operation is one `complete_now` receives Undo. Map its private existing completion token to a server-owned Undo operation identity; apply through the existing conflict checks under the same admitted atomic receipt boundary. Do not invent a time-based expiry absent from the domain service. When conflict makes Undo unavailable, explain it. Restore prior eligible state/plans, never restart tracking. UI receipt may offer Undo after recovery; once requested, its submission is durable and recovery is idempotent exactly like confirmation. This extends recovery of the affordance without creating general Undo semantics.

For each response, inject authoritative compact status/outcome data for referenced/current and recent in-context operations belonging to that conversation, independently of acknowledgement. Capture that exact `task_outcomes_v1` input on the attempt; historical attempt inputs remain unchanged. Unknown/unavailable recovery is explicit. A fresh conversation does not automatically receive the old conversation's outcomes as model memory; its recovery surface can still show them to the user. Reading an explicitly attached receipt in a new request requires a deliberate UI attachment, not silent cross-conversation context.

## Android integration

### Current-version protocol and stream

Android and backend are updated together for the single developer/user. Add the task tools, cards, proposals and recovery endpoints directly to the current protocol. Do not add `task_access_v1` negotiation, old-client/old-server fallback paths, mixed-version test matrices, or a staged capability rollout. An incompatible client/server pair is unsupported and may fail visibly; it must never imply a successful task mutation.

Keep existing authentication, activity protocol/cutover guards, stream validation and schema tags needed to validate current messages and retained records. Preserving already-stored conversations, snapshots and operation receipts is a data-retention requirement, not a promise to run older application versions. The existing unrelated capability machinery need not be redesigned or removed as part of this feature.

Add `task_proposal` and `task_card` SSE events with the existing run/sequence envelope. At most one task or tracking proposal comes before up to three read cards, then text, then one terminal outcome. Task card payload is the safe schema-4 projection. It never contains descriptions. The existing selector format still picks zero to three distinct eligible snapshot IDs; the server dispatches them by type. Each read contributes at most one card. Unknown required events, duplicated proposal kinds, mixed kinds, invalid identity/shape, or bad ordering interrupt the attempt. Preserve valid partial output but disable task confirmation unless completed/saved eligibility is established.

A proposal is not a receipt. A successful `completed` event makes a validated pending proposal available; HTTP confirmation/status updates the separately keyed execution state. Card-only/proposal-only completed replies are permitted only with validated content and terminal success; text-only completion still needs nonblank text. The prompt must not invite confirmation without an actual eligible proposal or claim success from proposal generation/submission. Follow a bounded tool error with truthful text, never an invented card or success.

### Local barriers and reconciliation

Add a narrowly scoped reservation API around the existing readiness coordinator and activity repository. Acquire activity coordination first when complete-now/Undo can affect tracking, then affected Task lifecycle/readiness reservations in ascending Task ID order. Drain pre-existing relevant intents; wait for confirmed outcomes. A failed latest readiness intent is **not** synced merely because its pending flag cleared. Require explicit retry or abandonment/reconciliation of that failed intent. Activity drain must return a verified empty outbox/no unresolved recovery state, not rely on `refresh(): Unit` or a swallowed error.

While the reservation is held, route new relevant local intents after the submitted operation or keep affected controls briefly disabled with accessible status. Do not deadlock manual Task Completion by acquiring locks in the reverse order. Revalidate server preview after draining. Hold the barrier through confirmation and initial result reconciliation. For **any** unknown submitted task change, preserve a scoped recovery barrier for overlapping Task fields/lifecycle changes until its submission outcome is established or execution is authoritatively fenced out. Include activity coordination only when complete-now/Undo can affect tracking. Even a Ready to Plan edit needs this protection: a delayed older confirmation must not overwrite a newer local choice. Show Check result and the affected controls' reason. Do not hold a coroutine mutex indefinitely across offline time. On restart, recreate these barriers from durable metadata before allowing conflicting local mutations. This is a task-confirmation restriction, not a change to ordinary standalone Tracking Proposal offline support.

All mutations remain online-only; a local submission record is a recovery pointer, never an offline work queue. Before HTTP, durably store `{server_identity, conversation_id, proposal_id, revision, operation_id, submission_id, kind, affected_task_ids, affected_fields, affects_tracking, submitted_at}`. For Undo include the related original operation. Persist atomically in app-private storage scoped to the backend/account identity, without API keys, description text or whole transcripts. If this write fails, do not send confirmation. Changing server identity never sends old IDs/metadata to another server.

On disconnect/process restart, recover via status only. Never automatically POST the mutation from the saved pointer. An inconclusive not-seen submission may offer an explicit retry using the same operation **and submission** IDs while retaining the barrier. A terminal rolled-back submission can release its barrier after reconciliation and offer a newly confirmed submission ID under the same still-valid proposal/operation. Keep unresolved pointers until conclusively resolved; if the bound of 20 is reached, require recovery before another task confirmation. Cache resolved receipts needed for the current recovery/Undo surface within 64 KiB per item, dropping only acknowledged resolved local entries oldest-first. Server receipts remain authoritative and retained.

Successful result reconciliation refreshes affected Tasks/Days/current Activity, merges readiness through its coordinator and invokes existing task-change/reminder scheduling hooks. Do not overwrite unrelated newer local fields with a stale whole-Task response. Release the corresponding recovery barrier only after authoritative state is reconciled. Expose a compact “Task changes need checking” surface in Assistant after restart even though the conversation transcript starts fresh. It provides operation status, receipt, Open Task and eligible Undo, without browsing old conversations.

### Presentation and native checks

Use the approved three-row dated Task Cards with expansion and current-detail navigation through `AppRoutes.taskDetail(id)`. Keep original values/order/as-of labels. Task Detail is current and may report unavailable; opening it never updates the old card. Partial counts and saved-recurring/readiness limitations must survive card formatting.

Inline review identifies all targets, before/after fields, material effects and one confirmation. Description edits open the dedicated full-height review; render all text literally, show complete before/after content and place confirmation there. Large text must not clip essential differences or hide controls. Dismiss/back from description review returns to the pending proposal unless the user explicitly dismisses it. Receipt/status is distinct from the proposed diff and earlier answer.

TalkBack must identify speakers, historical/current data, target identity, preview versus saved result, before/after values, eligibility errors and confirmation actions in logical order. Announce meaningful state changes, not every streamed token or polling attempt. Test small screens, dark/light, system large text, long titles/text, Back, IME, navigation and focus restoration. Browser prototype success is not evidence of native success.

## Acceptance matrix

Every row is an implementation release criterion. Use deterministic fixtures/fault injection except the explicitly bounded live-provider and native manual checks.

| Scenario | Observable pass condition / validation seam |
| --- | --- |
| Current default | Incomplete saved work includes Blocked; explicit history expands completed/skipped/past Sessions. No archive/Trash details. Read tests assert zero inserts/updates/deletes and no synchronization/purge calls. |
| Saved recurrence | Carry-over and quota/session distinctions use existing records only, including Day ≥ Today carry-over. Missing records never mean nothing is due. No generated IDs, plans or readiness writes. |
| Identity ambiguity | Same title across Tasks/Projects/Types and Subtask-title matches require clarification. “Second one” stays bound to original snapshot; a pruned reference is not guessed. |
| Paging and limits | Fixed manifest membership/order; new immutable page snapshots; unavailable positions; count lower bound beyond 1,000; cursor expiry; large Unicode read payloads without byte pruning; preserved proposal/receipt byte limits; no false completeness or silently truncated confirmation. |
| Automatic refresh | Project/Type rename, Subtask edit, planned date change and clock transition detected without Task.version change. Old cards unchanged; no extra model call; captured injected inputs reproduce the actual response context. |
| Refresh failure/overflow | Covered/current, unavailable and unverified are distinguishable; descriptions not auto-fetched; current collection does fresh search. Context pruning preserves durable history and typed replay has no activity-field assumptions. |
| Privacy and injection | Explicit description request/clear follow-up only, 2,000-character excerpts with truncation; malicious text treated as data. Cards/ordinary receipts exclude descriptions and Supporting Notes; full edit text appears only in explicit review. |
| Create/edit | Neutral defaults; omitted versus clear distinct; valid existing references; title/reason/description limits; no inferred extra fields unless requested organization/classification. Existing Blocks keep their Task Types. |
| Parent/Subtask | Add/rename/check/uncheck only, checked rename allowed, no automatic parent completion; completed parent needs explicit reopen; no whole-child replacement workaround for deletion. |
| Complete-now | Linked Actual ends once; only strictly future eligible plans removed; running-associated plan preserved; historical Actual linkage handled; flags/reminder bookkeeping cleared; child checks unchanged. |
| Earlier completion | Prior date stores noon plus date precision without displaying invented time; earlier-today asks time; future rejects, before-creation accepted; Blocks untouched. DST gap/ambiguity and zone-change re-review covered. |
| Reopen/reminder | Reopen restores none of old side effects. Date-only deadlines and timed deadlines are exclusive. Reminder requires time and must remain future at execution; unrelated deadline edits do not re-arm it. |
| Atomic dependencies | Create parent + children and explicit reopen + edit commit as one. Fault after later step leaves no Tasks/children/domain journal changes/partial success receipt. Existing manual endpoints retain their behavior. |
| Duplicate create / lost reply | Two same-identity confirms create once and return same IDs/receipt. Commit followed by lost response and server/client restart recovers original outcome. Altered revision/identity rejected. |
| Delayed HTTP admission | Status arrives before original confirmation: not-seen remains inconclusive and its barrier persists. Duplicate same-submission arrival after terminal rollback cannot execute; explicit retry uses a new submission ID under the same operation. Status recovers during paused write gates. |
| Relevant concurrency | PostgreSQL races against manual patch/delete, Plan insert/move, activity switch, Project delete, Type rename/merge and reminder claim either serialize or cause fresh review. Unrelated fields survive without rejection. |
| Lifecycle races | Confirm versus close/dismiss/replacement/expiry has one winner. New eligible proposal supersedes old; unsaved/unfinished drafts never executable. Applied result survives expiry, stop, acknowledgement loss and conversation closure. |
| Local synchronization | Deferred readiness write, failed latest intent, queued activity and a new edit during drain cannot be bypassed. Unknown completion recreates barriers after restart. Reconciliation never overwrites newer intent. |
| Offline/recovery | No mutation POST from an offline queue; metadata persisted before send; only bounded GET/status recovery; retry uses same identity. Endpoint switching isolates records; unresolved cap never drops them silently. |
| Undo | Only sole complete-now offered; domain conflicts preserved, tracking stays stopped; lost Undo response/restart returns original Undo receipt and never retries raw one-shot token blindly. Original completion remains historical. |
| Current protocol | The matched current Android/backend pair supports the complete task contract; incompatible versions have no fallback guarantee. Mixed proposal kinds, unknown events, malformed selector/sequence and save failure never produce confirmable task UI. |
| Truthful model context | Applied, pending, cancelled, stale, rolled back and unknown derive from server evidence; receipt independent of acknowledgement. Fresh conversation has no implicit old chat memory. No model-prose success without receipt. |
| Native review | Current-detail navigation preserves snapshot, description review is complete/private, TalkBack and focus work, large text/IME/Back have reachable controls, recovery surface works after forced process death. |

## Implementation slices and release ordering

1. **Storage, pure reads and bounded context.** Add typed Task projections/query manifests, privacy filters, current protocol contracts, capture/replay and deterministic preflight. Validate no-write saved recurrence and retained-record replay. Integrate the complete vertical path before release.
2. **Atomic domain operations and durable execution.** Extract transaction-neutral primitives with existing wrappers preserved; add proposal lifecycle, relevant guards, admission, receipts and recoverable sole-completion Undo. Validate fault injection and real PostgreSQL races before enabling writes.
3. **Android read/review integration.** Add transport/DTOs, cards/current-detail navigation, complete review and receipt state. Keep confirmation gated while durable recovery and synchronization are unfinished.
4. **Android synchronization/recovery and complete vertical path.** Add scoped barriers, minimal durable pointers, restart status surface, reminder/Task/Day refresh, Undo adoption and truthful outcome context. Exercise end-to-end deterministic scenarios.
5. **Release verification and user review.** Run focused backend tests (`test_assistant*`, relevant battle-plan/completion/recurrence/Habits/locking tests), targeted PostgreSQL concurrent integration tests, Android controller/card/readiness/activity/completion units and focused Assistant instrumentation. Build with `scripts/android-gradle.ps1 testDebugUnitTest assembleDebug`; compile instrumentation before device execution. Use managed emulator ownership helper and run instrumentation without competing backend/frontend suites. Read the baseline's later corrections; do not treat its original “19 failures” as an unchanged current baseline, and do not excuse new Assistant failures as baseline.

After deterministic checks, run at most **six live-provider scenarios and thirty model calls total**, with no automatic retries: (1) current task discovery/card, (2) ambiguous title clarification, (3) known-task edit followed by automatic-refresh answer, (4) simple ordinary edit proposal, (5) create with Subtasks proposal, (6) explicit description read/edit with separate review. Use isolated seeded data and test confirmation where appropriate; record model-call/read counts, captured preflight, current protocol and actual terminal outcomes. Provider failure blocks that release check and requires diagnosis; do not spend beyond the bound or silently swap model/encoding. No live provider calls are authorized or performed during this planning session.

Release the updated backend and Android app together once the entire vertical path passes; run the required database migrations for that release. Retain stored conversations, snapshots and operation receipts and preserve current manual/tracking behavior. Status recovery must remain available during a maintenance pause on new task writes. Do not add support for running older binaries against the new contract or migration schema.

Finally launch the updated application from the implementation branch and leave it running for user review, following `docs/agents/android-emulators.md` and `docs/agents/android-instrumentation-baseline.md`. Create a PR only on explicit request; merge into master only on explicit instruction. These approved slices define the sequence for a later implementation request. No implementation tickets are created by this planning handoff.

## Decision record

The user accepted all final recommendations on 2026-10-04, amending version support: “I am solo dev right now with me as only user so ignore this. don't support old versions”. This replaces the draft's capability negotiation and backward-compatible rollout recommendation. Accepted bounds, atomic admission, durable recovery, unknown-outcome barriers and the implementation/validation sequence remain unchanged. All prior product and prototype decisions remain accepted.

[ADR 0017](../adr/0017-assistant-bounded-read-loop.md) records one task or tracking proposal within the existing bounded loop. [ADR 0019](../adr/0019-assistant-task-confirmation-is-atomic-and-recoverable.md) records atomic server confirmation, review-sensitive guards and durable receipts. ADR 0014 continues to govern direct Tracking Proposals; ADR 0004 continues to govern ordinary manual readiness. ADRs 0012, 0015 and 0018 remain in force. These are approved design decisions for implementation, not a claim that task access is shipped.

Planning validation: inspected the current backend/Android code and resolved tickets; no production code, tests, emulator or provider execution was performed. Documentation whitespace and consistency checks passed. The implementation must produce the evidence specified above.
