# The Assistant uses bounded read rounds

## Approved round-based revision (issue #314, 2026-10-08)

The user approved replacing the three individual-read limit with five read rounds per response attempt. A read round is one model response requesting one or more read tools, followed by their results. There is no separate individual-read or batch-size ceiling. This gives dependent lookups room to proceed without making batching consume extra rounds.

A batch may mix supported read tools or repeat the same tool. Execute calls sequentially in request order, preserve each original tool-call ID, and return results in that order before the next model call. Calls requiring earlier results belong in a later round. Sequential execution avoids concurrency coordination until measured tool latency justifies it; batching already saves model round trips. Each read retains its own freshness evidence, not a shared batch snapshot.

Unknown tools and batches mixing reads with proposals are rejected before any call executes, with corrective feedback associated with every call. Rejected batches consume a round so recovery remains bounded. Proposals must be requested alone; preserve one proposal attempt, explicit user confirmation, and no reads after a proposal.

After five read rounds, allow the optional proposal and final tool-free answer: at most seven model calls per response attempt. Preserve the 120-second response deadline, per-tool argument validation, and three-card presentation ceiling. The round limit replaces both individual-call admission and any equivalent successful-read-count restriction; cards remain separately bounded.

The user also approved removing application-imposed byte allowances on Assistant read data and carried-forward data context, preferring to observe actual model failures before introducing a replacement allowance. This removes the current-turn and rolling-context 64 KiB envelopes and subsidiary read/refresh/outcome byte budgets. Do not replace these with an equivalent token allowance. Provider context limits still exist; this decision does not promise unlimited model input. Preserve pagination, the 20-exchange history window, description-excerpt limits, and proposal/review/receipt safeguards.

Within an admitted read batch, argument-validation and runtime failures return explicit per-call errors while remaining calls continue. Preserve successful results and their association with the original calls. Do not retry automatically; the model may retry within a remaining round or explain incomplete coverage. Cancellation and the overall response deadline stop execution rather than becoming recoverable per-call errors.

This approved revision supersedes the read and model-call counts and read/context byte allowances below and in the task-access handoff. The user confirmed the complete contract and authorized implementation.

## Original decision

A response may now make up to three read-only tool calls, then at most one `propose_tracking`, before a final tool-free answer. This replaces the earlier guarantee of at most two model calls and one tool call. Comparison questions ("this week vs last week", "plan vs actual on Tuesday") and read-then-propose requests ("I've been doing what I planned since 2") need more than one tool. We chose a small bounded loop so the model can combine general-purpose Day and range reads. The alternative, one call with ever-wider tool parameters, would need a new parameter for every new question shape. A response may show up to three Assistant Cards, at most one per read.

## Consequences

Worst-case latency, token use and provider cost per response rise, and the 120-second deadline has to cover the extra calls. The bound is expected to change again, so anything that relies on per-response call counts should read the limit rather than assume a number.

## Approved task-access extension

The [task-access handoff](../specs/assistant-task-access-handoff.md), approved on 2026-10-04 for later implementation, extends the same loop to saved Task reads and permits at most one `propose_task_changes` **or** one `propose_tracking` per response. The shared bounds remain three model-selected reads, one proposal, three cards, five model calls and 120 seconds; no reads follow a proposal. Deterministic known-Task refresh runs before the model within the same response deadline and bounded data context, without consuming another model-selected read. This supersedes the tracking-only proposal restriction above when the task capability is implemented.
