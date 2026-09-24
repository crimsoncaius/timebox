# Task Type Recommendations

The picker-context extension in [Task Type recommendations inside the picker](task-type-picker-recommendations.md) supersedes the triggers, context inputs and placement below. This document retains the original implementation history.

Issue: https://github.com/crimsoncaius/timebox/issues/168

Status: implemented on web and Android. The user delegated the final design choice; variant B was selected, with recommendations in task details after committing the focused name editor. No PR requested.

## Agreed behavior

- Web and Android use the same behavior for Battle Plan Task names (including Task Occurrences), Session Task names, Recurring Task Series template names, and Planned/Actual Block Names.
- Jev from TypeSafe AI selects only existing Task Types. It may return no suitable match; category creation remains manual.
- Send only the entered name and existing Task Type paths as classification context. Do not send descriptions, Project names, or activity history.
- Request automatically after a 500 ms pause in name entry. Editing and saving never wait for a recommendation.
- Show the selected suitable match only when Jev's confidence score is at least 0.8. This uses the provider's confidence statistic, not the winning option's probability.
- Include at most 254 eligible Task Types plus an explicit none-of-the-above option. If there are more than 254 eligible Task Types, quietly omit recommendations rather than truncate the list; manual selection remains available.
- Recommendations require explicit acceptance; they never automatically assign a Task Type.
- Support creation and renaming existing work, only while unclassified and before the user explicitly chooses or clears a Task Type in the editing session.
- Present a separate suggested Task Type action beside the classification controls. Dismissal hides the recommendation until the name changes. Android task and recurring detail sheets show it after saving the focused name editor; creation and block forms show it alongside their existing controls.
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

## Selected presentation

Variant B preserves Android's focused name editor and puts the decision with task properties. The recommendation remains optional, with explicit Use and Dismiss actions. It does not delay saving the name or merge two existing editing flows.

## Placement prototypes

The throwaway branch `codex/prototype-168-task-type-recommendations` preserves browser and native Android comparisons of A (inside the name editor), B (in details after saving), and C (combined name-and-type editor). Those prototypes simulate recommendations. The implementation uses real Jev and Phoenix integrations and excludes the prototype-only routes and activities.

## Configuration and validation

Set backend `JEV_API_KEY` and optionally `TYPESAFE_MODEL` (default `jev-1.13.0`). Reuse `ASSISTANT_TRACE_ENDPOINT` and optional `ASSISTANT_TRACE_API_KEY` for Phoenix. Secrets stay in the backend environment. Missing credentials, timeouts, and invalid responses quietly suppress recommendations.

Live validation used an isolated SQLite review database: Jev classified "Practice piano scales" as `learning/music` with confidence 0.98; Phoenix received the input, selected result, confidence, model, latency, and reported usage. Android displayed the suggestion after saving the name, and explicit acceptance persisted the Task Type. A provider timeout left editing usable.

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
