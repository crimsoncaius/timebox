# Assistant redesign implementation handoff

Status: proposed handoff, awaiting live confirmation in [Agree the Assistant implementation handoff and acceptance criteria](https://github.com/crimsoncaius/timebox/issues/238). This document does not authorize implementation or merge.

## Scope and authorities

Implement the Android Conversation layout with the approved A inline schedule from [the prototype](../design/assistant-236/README.md). This expressly adds one agent-selected plan card to the original design-refinement scope. Backend changes are limited to the contract, snapshot memory, and completion handling needed for that card. Rewrite production components; do not ship the prototype.

Authoritative product decisions live in their tickets: [presentation](https://github.com/crimsoncaius/timebox/issues/233), [freshness and memory](https://github.com/crimsoncaius/timebox/issues/234), [streaming and retry](https://github.com/crimsoncaius/timebox/issues/235), [visual selection](https://github.com/crimsoncaius/timebox/issues/236), and [response contract](https://github.com/crimsoncaius/timebox/issues/237). The details below make those decisions implementable; new technical defaults are proposed for handoff approval.

Exclude mutations, additional reading tools/card types, arbitrary generated UI, saved history, web UI, provider replacement, and broad backend redesign. Keep credentials backend-only and preserve existing tool field exclusions and tracing retention.

## Concrete protocol proposal

- Preserve the existing application protocol header. Negotiate Assistant separately at conversation creation: optional `capabilities: ["plan_card_v1"]`; echo the accepted capability list. Persist the accepted mode for that conversation. Missing capability means legacy text mode. A new client receiving no capability echo also uses text mode, without retrying a model request. Deploy the compatible backend before the new Android client.
- Add one SSE event, `plan_card`, with the existing `run_id` and contiguous `sequence` envelope. Payload: `schema_version: 1`, `snapshot_id`, ISO calendar `date`, IANA `reporting_timezone`, UTC RFC3339 `read_at`, and ordered `planned_blocks`. Read time is captured by the server at snapshot creation; render it in the snapshot's Reporting Time Zone.
- Each row preserves the existing projection: `start_minute`, `end_minute`, nullable `name`, `task_type`, and nullable `task_id`/`task_title`. Preserve returned order and values; enforce the repository's existing valid time ranges rather than inventing stricter domain constraints. No Supporting Notes or Task Descriptions. No Planned Block ID or click navigation is required. Render row labels as text, never markup. Use Block Name, then linked Task title, then Task Type as the display-title fallback.
- The server owns an opaque snapshot ID and verifies conversation ownership and eligibility. The eligible set consists of this run's successful read and acknowledged completed-history snapshots. Historical data is labeled with its original date/time/zone; current-plan requests require the run's fresh read. Reading is independent of presentation.
- Model control line: exactly `{"presentation":"none"}\n` or `{"presentation":"snapshot","snapshot_id":"<id>"}\n`, followed by answer text. Accept JSON whitespace within the object and LF or CRLF termination; no leading prose, fences, duplicate/extra keys, other discriminator values, or non-string IDs. Cap the header at 512 UTF-8 bytes excluding the terminator. Handle arbitrary chunk boundaries. Consume only the first line as control; do not interpret later answer text as control. Never expose the header to Android.
- Buffer the tool-capable first call until its terminal tool/no-tool decision. Discard its prose if it requests the read. On the no-read path, validate and release the buffered answer. On the second, tool-free call, validate the complete header, emit the atomic card if selected, then forward text deltas. Unexpected tools or invalid terminal outcomes fail the attempt.
- For card clients, event order is `started`, optional read progress, optional single `plan_card`, zero or more `text_delta`, then exactly one terminal outcome. Text-only answers need nonblank text; card-only answers need a validated card. Neither visible content nor a closed header implies completion. Progress cannot render a card. Unknown required events, invalid card schemas, second cards, cards after answer text, and sequence violations make the attempt interrupted and ineligible for acknowledgement.
- Legacy mode emits no card event or selector syntax and never completes with blank text. If the model selects a snapshot, the server produces a deterministic textual schedule from it before any accompanying answer, without another model call. No-card answers use normal text. This is a negotiated presentation mode, not recovery from malformed model output.
- Stage completed text, presentation metadata, and all successful read snapshots as one pending exchange. A repeatable acknowledgement atomically commits the exchange before the next question. Do not duplicate snapshots when referencing history. Count exchanges directly, not internal context messages. Unacknowledged data never becomes history; cancelled/replaced runs discard it. Reset, expiry and process lifetime remain temporary; displayed expired transcripts persist until user reset or app process restart.
- Preserve two model calls, one read, 4096 output tokens per model call, the 120-second response deadline, 20 completed exchanges, 60-minute idle expiry, one active run per conversation, and no automatic retries. Keep existing conversation/input limits. Limit exhaustion must fail explicitly, never silently drop historical snapshots.

## Android implementation boundaries

Use A's compact user bubbles, open Assistant answers, inline card before interpretation, dated metadata and expandable long plans. Show three rows initially and expand the rest in place. Support paragraphs, emphasis and lists in answer text; escape raw HTML and do not introduce embedded remote content. Keep card row content literal. Starter prompts populate the editable composer. Preserve multiline drafting and explicit Send/Stop; avoid stealing scroll position when reading earlier content and offer Jump to latest.

Keep validated cards and partial text visible after Stop/interruption, with the incomplete-memory explanation. Explicit Retry appends a fresh attempt using the same question. Once expired/capped, preserve the transcript but replace continuation/Retry with New conversation. Reset invalidates all late text and card events. Tab navigation does not cancel generation.

## Acceptance matrix

| Scenario | Observable pass condition |
| --- | --- |
| Explicit current-plan request | Fresh server read; one grounded dated card in A; no invented or altered rows. |
| Read without display | Agent selects none; only text appears; completed read remains historical context after acknowledgement. |
| Narrow follow-up | Text by default; no mechanically repeated card. |
| Card-only | No dummy prose; completion and acknowledgement succeed only after confirmed terminal success. |
| Empty plan / read failure | Successful empty snapshot has dated empty state; failed read has explanation and Retry, never an empty or stale-current card. |
| Plan edit / midnight / zone change | Old cards unchanged; new current-plan reads use current date/zone; historical questions can use original snapshots. |
| Streaming | Validated full card precedes text; preliminary tool-call prose and selector never leak. No-read buffered delivery is acceptable. |
| Stop / disconnect / invalid terminal | Existing card/text remain marked incomplete; no late additions, memory commit, or automatic retry. |
| Explicit Retry | Old attempt remains; new attempt appends, with fresh data for current-plan questions. |
| Invalid prefix / payload | Fail visibly; no guessed IDs, partial rows, duplicate card, or false completion. |
| Acknowledgement loss | Repeat acknowledgement commits once, including card-only and unseen read snapshots; no regeneration. |
| Reset / tab navigation | Reset isolates generations; navigation preserves the ongoing response. |
| Expiry / limit / restart | Correct ended controls and visible transcript; 20 completed exchanges regardless of internal records; app restart begins fresh. |
| Compatibility | Old client/new backend and new client/old backend use meaningful text; no blank completed card-only response. |
| Native usability | Small Android display, dark theme, system large text, long names/plans/answers, IME opening and multiline input retain usable controls without overlap. |
| Accessibility | TalkBack identifies speakers/card/date/status/actions; logical focus order, readable contrast, adequate touch targets, restrained progress announcements, no per-token announcement flood. |

## Implementation sequence and release gates

1. Backend: typed snapshots, prefix parser, capability negotiation, event ordering, explicit exchange storage/counting, atomic acknowledgement and legacy text projection. Add focused deterministic checks for chunk boundaries/UTF-8, malformed/duplicate keys, wrong references, run cancellation, terminal validation, and compatibility.
2. Android: transport validation and exchange state, then production A layout and lifecycle presentation. Add controller/transport tests for completion, Retry, reset, sequence failures and acknowledgement loss.
3. Integration: exercise the acceptance matrix with deterministic fixtures, then run a bounded live provider smoke check using the pinned adapter/SDK and routing settings. Initial smoke suite: one no-card answer, one fresh read with card/text, one card-only answer, and one historical-reference answer (at most eight model calls total, no automatic retries). Verify actual prefix compliance and budgets. Failures block release; investigate and seek an explicit decision if encoding or budget must change.
4. Validate native Android IME/TalkBack and representative screen/text-size combinations on reserved managed devices, following emulator ownership instructions. Interpret instrumentation against the documented baseline; do not label existing failures as regressions. Run relevant backend and Android suites.
5. Launch the updated application from its implementation branch and retain it for user review. Report actual validation and remaining failures. Merge only on explicit instruction; create no PR unless explicitly requested. Deploy backend compatibility support before rolling out the Android client.

The prototype and [research](../research/assistant-presentation-encoding.md) establish design intent and documented feasibility, not native or live-provider validation. This handoff introduces no automatic deployment or spending authorization beyond a later authorized implementation task's agreed smoke-check scope.
