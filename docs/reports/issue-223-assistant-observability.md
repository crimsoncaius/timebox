# Issue 223: Local Assistant observability research

Researched 2026-09-19 for [issue 223](https://github.com/crimsoncaius/timebox/issues/223), part of [map 9](https://github.com/crimsoncaius/timebox/issues/9). Documentation research only; nothing provisioned and no live integration test performed.

## Recommendation and comparison

Use **local Phoenix with SQLite persistence**, exporting backend OpenTelemetry traces through OpenInference's LangChain instrumentor. This also supports LangGraph. The recommendation is an engineering judgment about the documented setup footprint, not a performance benchmark.

| Option | Documented footprint and integration | Decision |
| --- | --- | --- |
| Phoenix | One server with SQLite and a persisted working directory; PostgreSQL optional. The LangChain instrumentor also covers LangGraph. | Smallest fit for this experiment. |
| Langfuse | Web, worker, PostgreSQL, ClickHouse, Redis and blob storage; LangChain callback handler supports LangGraph. | Credible alternative, with more services to operate. |
| LangSmith | Self-hosted observability requires an Enterprise license. | Exclude unless an applicable license already exists. |

Sources: [Phoenix deployment](https://arize.com/docs/phoenix/self-hosting/deployment-options/docker), [Phoenix LangGraph](https://arize.com/docs/phoenix/integrations/python/langgraph/langgraph-tracing), [Langfuse Compose](https://github.com/langfuse/langfuse/blob/main/docker-compose.yml), [Langfuse integration](https://langfuse.com/integrations/frameworks/langchain), [LangSmith licensing](https://support.langchain.com/articles/7011309930-how-do-i-obtain-a-self-hosted-langsmith-license-key).

Phoenix's server uses **Elastic License 2.0**, not MIT. It restricts offering substantial Phoenix functionality as a hosted/managed service to third parties; its vendor explicitly supports free self-hosting. Langfuse uses MIT outside designated enterprise directories and third-party components. Private local deployment fits the documented Phoenix offering; customer-facing observability hosting would require a fresh license review. [Phoenix license](https://github.com/Arize-ai/phoenix/blob/main/LICENSE), [self-hosting policy](https://arize.com/docs/phoenix/self-hosting), [Langfuse license](https://github.com/langfuse/langfuse/blob/main/LICENSE).

## Proposed integration contract

These are implementation recommendations, not guarantees that every field appears automatically:

- Initialize `arize-phoenix-otel` with an explicit local OTLP endpoint and `openinference-instrumentation-langchain` before constructing the agent. Use one LangChain instrumentor, avoiding duplicate LLM instrumentation. [LangChain guide](https://arize.com/docs/phoenix/integrations/python/langchain/langchain-tracing), [OTEL setup](https://arize.com/docs/phoenix/tracing/how-to-tracing/setup-tracing/setup-using-phoenix-otel).
- Wrap the entire backend streaming generator in a request root span, alive until completion, cancellation or failure. Nest graph/model/tool calls beneath it. Include request ID, ephemeral conversation/session ID, Reporting Time Zone, resolved local date and terminal outcome. Return request/trace ID in the Android stream for correlation; Android need not export telemetry itself.
- Capture full root messages, each model's input/output and identity, Planned Blocks tool arguments/result, per-span times, provider usage and error status. The instrumentor source extracts messages, inputs/outputs and token data, parents spans by run, and records model/tool errors. Verify this against the selected OpenRouter adapter and pinned versions. [instrumentor source](https://github.com/Arize-ai/openinference/blob/main/python/instrumentation/openinference-instrumentation-langchain/src/openinference/instrumentation/langchain/_tracer.py).
- Record request-to-first-visible-text separately from full duration. Mark that event in the streaming loop: model thinking/tool arguments are not yet visible answer text. Phoenix provides latency/token/error metrics, while this product-specific boundary belongs in application instrumentation. [metrics](https://arize.com/docs/phoenix/tracing/llm-traces/metrics).

## Streaming, cancellation and usage

OpenRouter documents native-tokenizer counts and cost on the final streaming SSE message. Its older `usage.include` and `stream_options.include_usage` parameters are now documented as deprecated/no-op. Keep the generation ID for optional later accounting through its generation endpoint. Verify adapter handling with pinned versions. [usage accounting](https://openrouter.ai/docs/cookbook/administration/usage-accounting).

Inference: interruption before that final message can leave final usage unavailable. Record unknown usage, not zero or estimated token counts presented as fact. Preserve partial output and generation ID. Optional reconciliation must not block Android. Keep provider cost distinct from Phoenix model-price estimates.

Aborting OpenRouter's HTTP stream stops upstream processing/billing only for supported provider endpoints. Verify the actual GLM endpoint after model resolution; Stop cannot promise zero remaining charges. [OpenRouter cancellation guidance](https://openrouter.zendesk.com/hc/en-us/articles/51691588409883-How-do-I-cancel-a-streaming-request-and-which-providers-stop-billing-when-I-do).

Proposed behavior: on Stop/disconnect, close upstream streaming and cancel the loop; finalize the root span in `finally` with a cancelled outcome and partial output. Start no further tool/model rounds. Distinguish expected cancellation from failure. Finished spans are not a live token transcript; do not assume every partial chunk appears in Phoenix. Use bounded asynchronous export and graceful shutdown flushing, and let chat continue if the collector fails. Cancellation span closure needs runtime verification with the chosen versions.

## Local setup and content handling

Use one pinned image, a persisted SQLite working directory and loopback-only UI/HTTP collector mapping. Internal HTTP port 6006 serves the UI and `/v1/traces`; reserve the host port under repository policy during implementation. A containerized backend uses the private service address. No cloud account, playground model credentials or Android access to Phoenix is needed. [deployment](https://arize.com/docs/phoenix/self-hosting/deployment-options/docker), [configuration](https://arize.com/docs/phoenix/self-hosting/configuration).

Full message/tool content is authorized. OpenInference's content hiding defaults are false and configurable through `TraceConfig`. Keep content enabled while excluding environment dumps, Authorization headers, backend API keys and serialized HTTP-client configuration. Sanitize metadata/error details before export and verify a sentinel secret is absent in stored traces. Global content masking would defeat this experiment. [masking controls](https://arize.com/docs/phoenix/tracing/how-to-tracing/advanced/masking-span-attributes).

Ephemeral Android conversations do not imply ephemeral traces. Proposed default: seven-day local trace retention with an explicit wipe/reset operation; confirm this choice with the user. Model messages still travel to OpenRouter for inference independently of local observability.

## Implementation verification and remaining decisions

Inspect a successful model/tool/model trace against actual Timebox Planned Blocks, an empty-day response, provider error, tool error, interrupted stream and collector outage. Check shared trace IDs, authorized content, timing, successful usage matching the terminal OpenRouter response, unknown interrupted usage, absent credentials and usable streaming without Phoenix.

This resolves platform research. Remaining discussion: accept the proposed seven-day retention or choose another period. Runtime verification and image/package pins belong to implementation. Exact provider/model availability belongs to sibling backend research. No additional research ticket is needed for platform selection.
