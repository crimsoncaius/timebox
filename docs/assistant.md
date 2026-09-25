# Android Assistant

Android streams a conversation from the existing FastAPI backend. Conversations
and response attempts are captured in the application's database without
automatic expiry. There is no conversation-history interface yet.
The LangGraph graph makes at most two model calls and one read-only Today tool
call per response. LangChain's OpenRouter adapter uses `z-ai/glm-5.3-flash`;
there is no model fallback or automatic retry. A response has a 120-second
deadline. Only the latest 20 explicitly acknowledged completed exchanges enter later context.
Android acknowledges the previous completed response before sending the next
message. A lost acknowledgement can be repeated without regenerating an answer.

## Plan cards and compatibility

Conversation creation accepts `capabilities: ["plan_card_v1"]` and echoes the
accepted capabilities. The mode belongs to the conversation. Clients without
card support get a server-rendered textual schedule; a new client talking to an
older server also stays in text mode. Roll out the compatible backend first.

The model selects either no card or a server-owned snapshot using a bounded
512-byte JSON control line. The backend validates and consumes that line before
emitting a single `plan_card` event followed by any answer text. It buffers the
first tool-capable call, discarding preliminary prose when a read is requested.
Only the final tool-free call streams answer text incrementally. Snapshot IDs,
read times, dates, time zones and rows come from the server. Reading a plan does
not force a card. Completed reads, including undisplayed reads, join temporary
response context only on acknowledgement; stopped and interrupted attempts never do.

Android uses the approved Conversation layout with a dated inline card, three
initial rows and expansion for longer plans. Retry appends an attempt. Idle time
and exchange count do not end a conversation. Malformed or out-of-order card events cannot become completed
context. Card-only answers require a valid selector and confirmed completion.

The implementation handoff is in `docs/specs/assistant-redesign-handoff.md`.
Live card-only generations omitted the original mandatory trailing newline.
The user approved a narrow amendment: after a confirmed normal model completion,
a complete valid snapshot selector alone can produce the card without a newline.
The newline is still required before any answer text. No exception applies to
EOF, cancellation, truncation, malformed selectors or unauthorized references.
The eight-call smoke budget was exhausted during diagnosis; regression replay
checks cover the captured output pattern without additional provider calls.

The tool resolves Today in the Reporting Time Zone at execution. It selects
stored Planned Blocks directly, including times, Block Name, Task Type and linked
Task ID/title. It does not materialize missing Days or recurring work. Supporting
Notes and Task Descriptions never enter the tool result.

## Run

From the repository root, start local Phoenix:

```powershell
docker compose -f compose.assistant.yaml up -d
```

Configure `OPENROUTER_API_KEY` and optionally
`ASSISTANT_TRACE_ENDPOINT=http://127.0.0.1:12022/v1/traces` in the ignored backend
environment file (`backend/.env`, also loaded by the repository launcher). Start
the backend from `backend/`:

```powershell
cd backend
uv sync --extra dev --locked
uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1
```

Use the project's existing `DATABASE_URL` and Android API connection settings.
The implementation review instead uses port 12023 and an isolated SQLite database
under `artifacts/assistant/`; its debug APK points to `http://10.0.2.2:12023/`.
Provider credentials belong only on the backend. Android's optional Timebox API
key is a separate existing setting.

Run `uv run alembic upgrade head` before starting the updated backend; revision
`033_assistant_conversations` adds `assistant_conversations` and `assistant_attempts`.
This implementation still requires **one API worker** for active-run coordination.
Restarting the backend retains captured conversations and marks unfinished runs
interrupted. Restarting the Android process begins with a fresh view and context.
New conversation cancels the old run and closes its conversation without deleting
the captured record (the existing DELETE endpoint now means close).

Each attempt stores the submitted message, model, timestamps, accumulated answer,
read snapshots, displayed plan, completion status, safe error message and receipt
acknowledgement. Messages are saved before generation; response text and plan
events are checkpointed as they stream, including partial and stopped attempts.
If saving the message fails, generation does not start. A response-save failure
is reported in the stream, keeping any visible answer and excluding it from later
context. Messages that never reach the server cannot be captured. A process crash
can lose output since the most recent successful checkpoint.

There are 4,000 characters per input and one active response per conversation.
Only the most recent 20 acknowledged completed exchanges and their associated
snapshots are used for later answers; older records remain stored and visible in
the active Android session. Idle context can be evicted from the bounded RAM cache
and reloaded from the database. There is no cross-conversation memory or summary.
Stop closes the upstream stream where supported; provider billing cancellation
is not guaranteed. Retry is always explicit.

## Local traces

Production uses the authenticated [Railway Phoenix dashboard](https://phoenix-production-6691.up.railway.app).
The backend exports over Railway private networking with `ASSISTANT_TRACE_ENDPOINT`
and `ASSISTANT_TRACE_API_KEY`. The latter is optional locally and is scrubbed from
exported content along with backend and provider keys. See
[deployment instructions](DEPLOYMENT.md#phoenix-observability) for access, retention,
key rotation, and disabling production export. Local traces are not migrated.

Open [Phoenix](http://127.0.0.1:12022) and choose `timebox-assistant`.
Root, graph, model and tool spans share a trace ID. SSE's `started` event includes
that ID. Full message and tool content is captured; the exporter scrubs backend
and provider keys. Model spans include reported token usage. An interrupted
call without reported usage has no usage count, rather than a fabricated zero.
Export is asynchronous with a bounded queue; an unavailable collector does not
block a response. Omitting the trace endpoint disables tracing.

Phoenix persists SQLite in the Compose `traces` volume. Its default retention
policy is seven days; cleanup follows Phoenix's scheduled retention sweep.
New conversation does not remove traces or captured conversations. Clearing traces
does not delete the conversation records in the application database. To clear the Assistant
project's traces, run from `backend/`:

```powershell
uv run python scripts/assistant_traces.py --clear
```

Stopping Compose preserves traces. The UI and collector bind to loopback only.
See [Phoenix retention documentation](https://arize.com/docs/phoenix/settings/data-retention).

## Verification

```powershell
cd backend
uv run python -m pytest
```

Android unit tests cover interrupted streams, Stop/Retry, reset isolation and
completed response acknowledgement. Run with `scripts/android-gradle.ps1
:app:testDebugUnitTest` from the root. Follow `docs/agents/android-emulators.md`
before device work. A live model check requires the backend provider credential
and sends message/tool content to OpenRouter.

### Verified on 2026-09-19

- Backend: 335 tests passed, six skipped. Android: the 304-test unit suite passed,
  followed by five focused Assistant tests after adding the acknowledgement-loss case.
- A real GLM 5.3 Flash request streamed a tool call and a grounded answer from an
  isolated known plan. Android retained the response across tab navigation, and
  a follow-up correctly identified the block's 60-minute duration.
- Real invalid-key authentication and cancellation after partial text were checked.
  Credit/rate errors, tool failures, timeout, missing terminal output, limits and
  expiry were checked deterministically; credits were not deliberately exhausted.
- Phoenix contained correlated root/model/tool spans, full messages, empty tool
  arguments and filtered results, reported usage on completed calls and unknown
  usage on cancellation. Credential and excluded-field sentinels were absent.
  Its stored default retention rule was seven days; explicit clearing removed
  the test traces. Resetting a conversation preserved historical traces.
- The Android debug review uses the isolated review API and seeded data, not the
  main Timebox database. Phoenix and the review API must stay running for review.

### Durable capture verified on 2026-09-25

- 54 focused backend tests passed, covering durable records, database reopen,
  startup recovery, rolling context and snapshot eviction, historical cards,
  partial/cancelled attempts, duplicate runs and storage failures. The new
  migration was tested against a file-backed SQLite database.
- The full backend suite had 471 passes and six skips. Two recurrence tests failed
  because their fixed 01:00 UTC Actual Block start was still in the future; both
  failures reproduced on the unchanged `df4c0ec1` baseline.
- All 327 Android unit tests passed, including fresh-process state and visible
  answer retention after a save failure. Instrumentation compilation was blocked
  by unrelated unresolved trash-undo references in `BattlePlanScreenTest`.
- A real Android request through the updated backend displayed a plan card and
  answer. The database retained both before acknowledgement; New conversation
  reset the screen, closed the conversation and preserved the completed attempt.
- The isolated review API is `http://127.0.0.1:12063/`, with database
  `artifacts/assistant-review/review-current.sqlite`. The review APK points to
  `http://10.0.2.2:12063/`. No production database was changed.
