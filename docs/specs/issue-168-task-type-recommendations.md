# Task Type Recommendations

Issue: https://github.com/crimsoncaius/timebox/issues/168

Status: behavior and observability decisions agreed; Android presentation deferred to a later prototype. Implementation has not started.

## Agreed behavior

- Web and Android use the same behavior for Battle Plan Task names (including Task Occurrences), Session Task names, Recurring Task Series template names, and Planned/Actual Block Names.
- Jev from TypeSafe AI selects only existing Task Types. It may return no suitable match; category creation remains manual.
- Send only the entered name and existing Task Type paths as classification context. Do not send descriptions, Project names, or activity history.
- Request automatically after a 500 ms pause in name entry. Editing and saving never wait for a recommendation.
- Show the selected suitable match only when Jev's confidence score is at least 0.8. This uses the provider's confidence statistic, not the winning option's probability.
- Include at most 254 eligible Task Types plus an explicit none-of-the-above option. If there are more than 254 eligible Task Types, quietly omit recommendations rather than truncate the list; manual selection remains available.
- Recommendations require explicit acceptance; they never automatically assign a Task Type.
- Support creation and renaming existing work, only while unclassified and before the user explicitly chooses or clears a Task Type in the editing session.
- Present a separate "Suggested: ..." action beside the Task Type field. Dismissal hides the recommendation until the name changes. Android placement across its separate name-editing sheet remains deferred as described below.
- Preserve the existing Task Type picker and its ranking, as required by ADR-0006 and ADR-0008.
- Suppress recommendations wherever a Block's Task Type cannot be edited independently. Preserve ADR-0005: accepting a Task recommendation does not reclassify existing Blocks, and linked Actual Blocks cannot diverge from their Planned Blocks.
- Use one installation-wide Jev API key, held exclusively by the backend. No client key entry.
- Empty names and unavailable model service leave manual classification available. A response for an earlier name must not be shown or applied to a newer name.
- Opening existing unclassified work does not trigger a request; its name must change first.
- Model failures and timeouts quietly omit recommendations. A later name edit can trigger a new request; there is no automatic retry loop.
- Recurring work preserves existing classification propagation rules. Subtasks have no independent Task Type and are outside scope.
- Connect recommendation requests to Arize Phoenix for backend request/result observability.
- Traces include the entered name, candidate Task Type paths, selected result, confidence, model version, latency, reported usage, and failure or suppression reason. Never include credentials. Do not invent usage values when the provider does not report them.
- Defer acceptance and dismissal telemetry until the prototype settles the interaction.
- Phoenix export failures must not interrupt recommendations or editing.

## Remaining design decisions

- Android recommendation placement is explicitly deferred to a later prototype. The proposed placement after committing the name is not accepted and must not be treated as settled.

## Implementation context

The existing backend tracing infrastructure in `backend/app/services/assistant_tracing.py` provides asynchronous OTLP/HTTP export, optional collector authentication, credential scrubbing, and startup/shutdown integration. Reuse this mechanism for explicit Jev spans; direct HTTP calls are not covered by its existing LangChain instrumentation. Add the Jev key to the secrets scrubbed by the exporter.

Existing backend trace endpoint/key configuration supports local and remote Phoenix collectors. The documented local and production setups already use seven-day retention. Reuse the configured collector; this design session does not change or deploy infrastructure. Trace project naming is an implementation detail, not a new product decision.

## Provider verification

TypeSafe describes Jev as selecting predefined structured outcomes rather than generating strings:
https://typesafe.ai/blog/introducing-system-one-models-and-jev

Verified official references:

- https://docs.typesafe.ai/api — POST https://api.typesafe.ai/v1/systemone with Bearer authentication and model/state/questions fields.
- https://docs.typesafe.ai/primitives/choice — Choice accepts up to 255 options, returns the selected choice and probability distribution, and supports an explicit none-of-the-above option.
- https://docs.typesafe.ai/confidence — confidence is a statistic derived from the probability distribution, distinct from the winning option's probability.

An 80% threshold is an initial product setting, not an established accuracy guarantee.
