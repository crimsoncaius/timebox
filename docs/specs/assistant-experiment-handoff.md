# Minimal Android Assistant experiment

Approved in discussion for the [Assistant planning map](https://github.com/crimsoncaius/timebox/issues/9). This document records the agreed implementation handoff. See [implementation and setup](../assistant.md) for the resulting experiment.

## Agreed design

- Existing FastAPI backend with LangChain/LangGraph and OpenRouter model `z-ai/glm-5.3-flash`. Provider key stays on the backend; no silent model substitution.
- Android Assistant surface with streamed responses over SSE and temporary backend-owned conversation state.
- One read-only tool fetching Today's Planned Blocks in the Reporting Time Zone. Include times, Block Name, Task Type, and linked Task identity/title. Exclude Supporting Notes and Task Descriptions.
- Local self-hosted Phoenix with SQLite. Capture full messages, model calls, tool inputs/results, timing, reported token usage, and errors; exclude credentials.
- One response at a time, 20 completed exchanges per conversation, 4,000 characters per user message, two-minute response timeout, and expiry after 60 idle minutes. Expiry asks the user to start fresh.
- Stop cancels generation where supported; provider billing cancellation is not guaranteed.
- Seven-day local trace retention with an explicit clear operation. New conversation clears context but does not delete observability traces.

## Acceptance criteria

1. Ask “What have I planned for today?” against known data on Android. Observe compact tool progress and a grounded streamed answer, then ask a follow-up using the completed conversation context.
2. Resolve Today in the Reporting Time Zone at tool execution. An empty or missing Day returns an empty plan without creating or modifying any Timebox record. Read failure is reported as failure, not an empty plan.
3. Supply only the agreed fields to the model. No mutation tools are available.
4. Follow the approved interaction: messages above a bottom composer, New conversation in the header, and the starter prompt. Switching tabs preserves the conversation and response while the app remains open. App restart starts fresh.
5. Stop and interrupted streams retain partial text marked Stopped or Interrupted. Retry is explicit, never automatic. Incomplete exchanges are excluded from later context. New conversation cancels the old run and resets context; late output cannot enter the new conversation.
6. Enforce the agreed concurrency, history, input, timeout, and expiry limits with understandable feedback.
7. Demonstrate an authenticated streaming tool round trip with the selected model. Verify authentication, credit/rate-limit, tool, timeout, and midstream failures are surfaced without leaking secrets or claiming success.
8. Inspect a correlated end-to-end trace in local Phoenix containing the agreed messages, model/tool calls, timing, reported usage, and errors. Missing usage for an interrupted run is unknown, not zero.
9. Verify seven-day trace retention and explicit clearing. New conversation must not clear historical traces.
10. Launch the updated Android application and leave it reviewable using the repository's emulator reservation workflow.

## References

- [Backend research](https://github.com/crimsoncaius/timebox/issues/222)
- [Observability research](https://github.com/crimsoncaius/timebox/issues/223)
- [Approved Android interaction and prototype](https://github.com/crimsoncaius/timebox/issues/224)
- [Acceptance decision](https://github.com/crimsoncaius/timebox/issues/225)

Web support, mutation tools, additional tools, saved conversation history, and production observability hosting are outside this experiment.
