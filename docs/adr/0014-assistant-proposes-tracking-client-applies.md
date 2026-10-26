# Assistant tracking uses the activity journal with explicit execution modes

## Voice-only Quick Tracking presentation (2026-10-26)

The system assistant gesture and Quick Settings tile share a compact voice-only overlay. This supersedes the typed fallback in the Android quick tracking section below. Listening starts on invocation and final recognition submits automatically. No transcript or conversational answer is displayed or spoken. A brief success indicator dismisses the overlay; failures retain explicit Retry and relevant permission/settings recovery. Review remains the place to inspect and correct results.

Closing before submission discards capture; closing afterward does not cancel process-owned execution. The existing activity uses a translucent window, preserving the interpretation and journal model. See [the agreed design](../design/quick-tracking-overlay.md).

## Tracking execution

In the default confirmation mode, the Assistant never writes Activity Tracking data. Its `propose_tracking` tool accepts only a Track (Task Type Path candidates, optional Block Name, optional start) or Stop (optional end) intent; the server validates the paths against existing Task Types, resolves stated times to instants in the Reporting Time Zone, and returns a Tracking Proposal without persisting any activity change. The client decides at confirmation whether a Track intent starts or switches, renders the live impact from local state, and applies the confirmed change through the ordinary activity journal, the same path as manual tracking.

This keeps the model's job to language (what and when), keeps date arithmetic and tracking-state reasoning deterministic, and preserves offline confirmation, journal reconciliation and the existing switch Undo without a second write path. The default retains a confirmation tap. The optional Review later mode below uses the same journal on the server.

## Assistant task-confirmation scope

Scope clarification approved with [ADR 0019](0019-assistant-task-confirmation-is-atomic-and-recoverable.md): this decision continues to govern direct Tracking Proposals. A separately confirmed Task Completion uses the existing server Task Completion behavior, including its inherited linked-activity stop and canonical activity-journal updates. It does not add a direct server tracking command or change the Tracking Proposal's offline journal path.

## Considered Options

- A presentation-line `"tracking"` kind instead of a tool: rejected because schema-validated tool arguments are more reliable on the flash model.
- Converting the Assistant to an open ReAct loop: rejected as unnecessary; the respond → one tool → finish graph fits a single proposal per response.
- Deterministic server-side Task Type matching: rejected because mapping "eating" to `Meals` needs the model; the server only rejects paths that do not exist.

## Review later mode (2026-10-23, issue #318)

The user selected Assistant -> Review (variant B) on web and Android, and limited immediate execution to Activity Tracking. Task and Subtask changes keep ADR 0019 confirmation.

An explicitly opted-in command lets the model propose its best existing Task Type match. After generation completes, deterministic server code chooses Start, Switch, or Stop against the current activity, then applies that command through the canonical activity journal. The activity operation, immutable execution receipt, and response result commit in one transaction. An incomplete response cannot authorize execution; a completed transaction remains discoverable even when its reply is lost. The server does not queue unexecuted commands for later application.

Review receipts preserve the original command and original recorded interval. Assistant -> Review shows their current records across conversations and dates. Mark reviewed acknowledges the displayed revision and never executes tracking. A concurrent change rejects the batch atomically. Corrections use ordinary journal range edits with stable retry IDs and preserve original receipts. As approved on 2026-10-24, saving a correction acknowledges the resulting revision in the same transaction; it no longer reopens the corrected receipt. A removed or split record links to Day instead of selecting a guessed fragment. Reviewed receipts remain available and correctable; later activity changes are identified as changed since review.

This trades offline execution of Review later commands for one authoritative cross-device receipt. The existing client-confirmed tracking path retains its offline journal behavior. Widgets and other quick command surfaces can opt into the same per-message mode in later work; this change provides the Assistant entry point.

## Android quick tracking (2026-10-24)

The Quick Settings tile and system assistant gesture share microphone-first capture. Listening starts on invocation, final speech recognition submits automatically, and text is a fallback. These commands execute without confirmation and are corrected in Assistant → Review; this flow deliberately has no Undo. Task changes retain confirmation.

Quick tracking uses concurrent d1-ultra activity and timing decisions, calling GLM only when an actionable request needs time extraction. Request arrival fixes “now”; deterministic validation accepts only Today through that instant in the Reporting Time Zone. Same-activity explicit later starts leave a gap. The lab's standalone timeline and Undo snapshots are not production storage.

Unlike online conversation Review later, quick interpretation does not execute on the server. Android saves the resolved command and local review metadata together in its existing durable activity journal. The canonical server transaction admits the operation and shared review receipt atomically, using the interpretation ID as operation identity. Unsynced results stay visible locally; rejected or superseded changes must not be presented as confirmed. This preserves offline recording after interpretation and ordinary journal reconciliation. Uninterpreted drafts require explicit Retry and never run automatically on reconnection. A local activity change during interpretation requires Retry, so a slow answer cannot silently act on changed context.

## Review event correction (2026-10-24)

Android review rows show the spoken request alongside the interpreted activity and event time. A Start/Switch/start-correction receipt exposes category, optional name, and Started at. Its eventual end is preserved. A Stop receipt exposes only Stopped at, preserving start, category, name and linkage. Stop events that removed their affected record or no longer have one identifiable ended record link to Day. This is event correction, not a general interval editor; the existing web interval editor remains compatible.

Corrections require connectivity and a synced local journal. They use the canonical range-edit path with revision checks, overlap rejection and stable retry identity. Moving a start later leaves a gap rather than stretching its predecessor. Historical records retain their dates; capture's Today restriction does not prohibit later correction of older records. Future instants are rejected. A lost reply keeps the exact request available for Retry, while field editing is disabled until the result is known. Failed transactions do not acknowledge review. Original receipts remain immutable.
