# Minimal Assistant backend research

Research for #222, part of #9. Verified 2026-09-19 against primary documentation and repository revision `bf38df3c5dc189730eaa005185ee10f65fa2124f`. This is a design recommendation, not implementation or a claim of live inference testing.

## Finding

Use the existing Python FastAPI backend with LangChain `create_agent`, its LangGraph runtime, and `ChatOpenRouter(model="z-ai/glm-5.3-flash")`. Add one read-only tool and an application-owned streaming adapter. A separate agent server is unnecessary for this experiment. LangChain documents the graph-backed agent loop and short-term state; its OpenRouter integration supports tools, async streaming, and token usage. [Agents](https://docs.langchain.com/oss/python/langchain/agents), [ChatOpenRouter](https://docs.langchain.com/oss/python/integrations/chat/openrouter).

The requested model **is available in the public OpenRouter catalog**, with exact ID **`z-ai/glm-5.3-flash`**. The official model page explicitly advertises tool calling. An unauthenticated GET of the endpoint API returned that ID and 29 endpoint entries, all containing `tools` in `supported_parameters`. No model substitution is needed. Streaming is documented by the provider API and integration, but an authenticated streamed tool round trip remains an implementation acceptance check; catalog availability does not prove account credits, access, or every provider's behavior. [Model](https://openrouter.ai/z-ai/glm-5.3-flash), [endpoint API](https://openrouter.ai/api/v1/models/z-ai/glm-5.3-flash/endpoints), [streaming](https://openrouter.ai/docs/api_reference/streaming).

Pin a tested dependency set in the existing `uv.lock` when implementing. Do not assume a release from memory. The repository currently has Python >=3.11, FastAPI, SQLAlchemy and no agent framework dependency. [Repository dependencies](https://github.com/crimsoncaius/timebox/blob/bf38df3c5dc189730eaa005185ee10f65fa2124f/backend/pyproject.toml).

## Proposed minimal flow

Android sends a user message to the existing API; the backend supplies its own system instructions and conversation history; the agent either responds or requests `get_today_planned_blocks`; the backend executes that tool; the model streams an answer. Keep the provider key solely in backend environment configuration (`OPENROUTER_API_KEY`). Keep Timebox's existing API authentication on every new route and require it for a network-reachable experiment that spends provider credits. The Timebox API key is separate from the provider key. Existing authentication is a shared secret, not a multi-user identity system. [App routing](https://github.com/crimsoncaius/timebox/blob/bf38df3c5dc189730eaa005185ee10f65fa2124f/backend/app/main.py), [configuration](https://github.com/crimsoncaius/timebox/blob/bf38df3c5dc189730eaa005185ee10f65fa2124f/backend/app/core/config.py).

Use a backend-owned in-memory transcript per unguessable conversation ID, with one run at a time. Android keeps its ID and display transcript in process memory, surviving screen rotation but not process restart. New conversation cancels the old run, deletes its server state, and obtains a fresh ID. Expire abandoned conversations after a configurable idle TTL; reject expired IDs explicitly. Use a single backend worker initially. Do not use a global shared conversation.

For the smallest predictable failure behavior, pass the server's completed transcript into each agent run without a checkpointer, then commit the returned messages only after successful completion. This avoids retaining half a tool exchange after cancellation. Failed or stopped partial text may remain visible on Android, clearly marked, but is excluded from subsequent model context. LangChain also offers `InMemorySaver`; it is a valid alternative if explicit checkpoint deletion and rollback behavior are implemented. Thread IDs alone do not supply memory. [Short-term memory](https://docs.langchain.com/oss/python/langchain/short-term-memory).

## Read-only tool boundary

Resolve Reporting Time Zone from `activity_service.reporting_settings`, then compute Today using `today_in_tz` at tool execution time. Return the resolved date and timezone with the result. No date argument is needed for this single capability. Do not allow model-generated SQL, arbitrary URLs, or write tools.

**Do not call `GET /days/{date}`**: it calls `get_or_create_day`, which commits a new Day when absent. Reuse `get_day_by_date` and project the planned lane through `PlannedBlockRead`, sorted by start minute and ID. Missing Day means an empty list. `build_day_preview` is another existing non-materializing read path, but computes Actual Blocks that this tool does not need. Close the short database session before waiting on the model; use a worker-thread boundary for synchronous SQLAlchemy calls in an async run. [Day routes](https://github.com/crimsoncaius/timebox/blob/bf38df3c5dc189730eaa005185ee10f65fa2124f/backend/app/api/routes/days.py), [Day service](https://github.com/crimsoncaius/timebox/blob/bf38df3c5dc189730eaa005185ee10f65fa2124f/backend/app/services/day_service.py), [Reporting settings](https://github.com/crimsoncaius/timebox/blob/bf38df3c5dc189730eaa005185ee10f65fa2124f/backend/app/services/activity_service.py), [block schema](https://github.com/crimsoncaius/timebox/blob/bf38df3c5dc189730eaa005185ee10f65fa2124f/backend/app/schemas/time_block.py).

Return block IDs, start/end minutes, Block Name, Task Type, and available linked Task identity. Agree separately whether Supporting Notes and Task Descriptions are necessary. Treat all record text as data. Preserve tool-call IDs and tool responses in the backend transcript and traces. Read fresh data each turn that needs Today, rather than interpreting an old tool result as current.

## Proposed Android wire contract

Use `POST /assistant/conversations/{id}/turns` with JSON user text and a request ID; return `text/event-stream` using an async `StreamingResponse`. The existing Android networking uses OkHttp/Retrofit; a dedicated streaming reader can share the base URL and authentication. Its present 20-second read timeout needs explicit consideration for model latency. SSE framing is an application protocol over this POST response, with no automatic EventSource replay. [Starlette streaming response](https://starlette.dev/responses/), [Android ApiFactory](https://github.com/crimsoncaius/timebox/blob/bf38df3c5dc189730eaa005185ee10f65fa2124f/android/app/src/main/java/com/timebox/android/data/remote/ApiFactory.kt).

| Event | Minimum payload |
| --- | --- |
| `started` | protocol version, conversation ID, run ID, trace ID |
| `text_delta` | message ID, sequence, text |
| `tool_started` | call ID, tool name |
| `tool_completed` | call ID, status, block count |
| `completed` | run ID, finish status, final message ID |
| `failed` | run ID, stable error code, safe message, retryable flag |

Include a monotonic sequence and run ID on events; exactly one terminal event for a connected run. EOF without a terminal event is interruption. Send heartbeat comments while idle; avoid buffering by any proxy. Do not send raw framework events, provider credentials, stack traces, or reasoning deltas to Android. Translate framework message/progress streams into this small stable contract. Current LangChain docs recommend typed event streaming for new applications and also document `astream` with `messages`/`updates`; select the supported API after dependency pinning. [Streaming APIs](https://docs.langchain.com/oss/python/langchain/streaming).

Stop should cancel the client request and close upstream generation; an explicit idempotent cancel endpoint can provide reliable run-level cancellation during disconnect races. Always cancel outstanding async work and close streams in cleanup. No automatic replay after partial output. OpenRouter reports early failures as HTTP errors but later failures inside a successful HTTP stream, so success requires the terminal application event. Aborting generation stops billing only for supporting providers; do not promise universal cost cancellation. [OpenRouter stream lifecycle](https://openrouter.ai/docs/api_reference/streaming).

## Limits, traces, and remaining checks

Proposed experiment defaults, not provider constraints: one concurrent run per conversation; 60-minute idle TTL; 20 successful turns before New conversation; 4,000-character user input; 120-second whole-run deadline; two model calls and one tool execution per turn; 4,096 completion tokens per model call; bounded tool-result size with explicit truncation metadata. Reject excessive history rather than silently dropping context. These are adjustable design choices awaiting acceptance. Tool result errors must not be described as an empty plan.

Configure `provider.require_parameters=true` so routing honors required capabilities, and keep the model ID fixed. Provider failover within this model is distinct from model substitution. Avoid automatic application retries after output starts; record the actual served provider and model. [Provider routing](https://openrouter.ai/blog/insights/model-routing/).

Propagate conversation/run/trace IDs through HTTP, graph, model and tool spans. Capture full messages and tool inputs/results, latency, failures, served model/provider, and reported usage; do not log secrets. Missing usage on cancelled/failed runs is unknown, not zero. Await the separate observability investigation for callback/exporter choice; no LangSmith service is required by this proposal.

Before implementation is declared successful, verify an authenticated GLM 5.3 Flash streaming tool call, usage capture, tool-free conversation, missing-Day read without writes, Reporting Time Zone around midnight, malformed tool arguments, tool failure, provider 401/402/429, midstream interruption, cancellation cleanup, simultaneous-send rejection, and expired conversation behavior. No paid generation was performed for this research.

Decisions still needed: accept the proposed temporary-state behavior and limits; decide which block fields may enter prompts/traces; choose whether provider routing should be restricted to providers with documented billing cancellation; accept the proposed transport/event contract alongside Android interaction design.
