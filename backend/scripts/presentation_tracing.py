"""Opt-in experiment traces using the application's redacting OTLP exporter."""
import hashlib
import json

from opentelemetry import trace

from app.core.config import get_settings
from app.services.assistant_policy import policy_prompt
from app.services.assistant_tracing import setup_tracing
from scripts.presentation_eval import grade, run_native


def start_tracing(enabled):
    if not enabled:
        return None
    if not get_settings().assistant_trace_endpoint:
        raise ValueError("--trace requires ASSISTANT_TRACE_ENDPOINT")
    # Initialize once, before run_native patches fixture settings/credentials.
    return setup_tracing(project_name="timebox-presentation-experiments")


def evaluate_case(case, prompt, model, *, batch, phase, candidate, event_sink=None):
    with trace.get_tracer(__name__).start_as_current_span("presentation.case", record_exception=False) as span:
        span.set_attributes({"openinference.span.kind": "CHAIN", "input.value": case["question"],
                             "experiment.batch": batch, "experiment.phase": phase,
                             "experiment.candidate": candidate, "experiment.case_id": case["id"],
                             "experiment.prompt_sha256": hashlib.sha256(policy_prompt(prompt).encode()).hexdigest(),
                             "experiment.expectation": json.dumps(case["expectation"]),
                             "experiment.scripted_history_turns": len((case.get("setup") or {}).get("history", []))})
        try:
            attempt = run_native(case["question"], prompt, model, setup=case.get("setup"), event_sink=event_sink)
            result = grade(case["expectation"], attempt)
            span.set_attributes({"output.value": attempt["raw_answer"],
                                 "experiment.grade": result["status"],
                                 "experiment.score": int(result["status"] == "pass"),
                                 "experiment.failures": json.dumps(result["failures"]),
                                 "experiment.unresolved": json.dumps(result["unresolved"]),
                                 "experiment.errors": json.dumps(attempt["errors"]),
                                 "experiment.domain_unchanged": attempt["domain_unchanged"],
                                 "experiment.completed": attempt["status"] == "completed"})
            span.set_status(trace.Status(trace.StatusCode.ERROR if result["status"] == "fail" else trace.StatusCode.OK))
            context = span.get_span_context()
            if context.is_valid:
                attempt["trace_id"] = format(context.trace_id, "032x")
                attempt["span_id"] = format(context.span_id, "016x")
            return attempt, result
        except BaseException as error:
            span.set_attribute("experiment.error_type", type(error).__name__)
            span.set_status(trace.Status(trace.StatusCode.ERROR, "Experiment execution failed"))
            raise
