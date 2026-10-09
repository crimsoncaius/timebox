"""Experiment roots correlate native spans and grades without paid calls."""
from types import SimpleNamespace

import pytest
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from app.services.assistant_agent import PRESENTATION_PROMPT
from scripts import presentation_tracing
from scripts.presentation_replay import FixtureModel


def test_tracing_requires_explicit_flag_and_endpoint(monkeypatch):
    monkeypatch.setattr(presentation_tracing, "get_settings", lambda: SimpleNamespace(assistant_trace_endpoint=None))
    assert presentation_tracing.start_tracing(False) is None
    with pytest.raises(ValueError, match="ASSISTANT_TRACE_ENDPOINT"):
        presentation_tracing.start_tracing(True)


@pytest.mark.parametrize("raw,expected", [('{"presentation":"none"}\n15', "pass"), ("15", "pass"), ('{"presentation":"invalid"}\n15', "fail")])
def test_native_attempt_exports_correlated_grade_and_child_spans(monkeypatch, raw, expected):
    exporter = InMemorySpanExporter()
    provider = TracerProvider()
    provider.add_span_processor(SimpleSpanProcessor(exporter))
    monkeypatch.setattr(trace, "get_tracer", provider.get_tracer)
    try:
        attempt, result = presentation_tracing.evaluate_case(
            {"id": "arithmetic-smoke", "question": "What is 7 plus 8?",
             "expectation": {"cards": "unwanted", "facts": ["^15$"]}},
            PRESENTATION_PROMPT, FixtureModel(steps=[{"raw": raw}]),
            batch="test", phase="controlled-smoke", candidate="fixture")
        assert result["status"] == expected
        spans = exporter.get_finished_spans()
        root = next(s for s in spans if s.name == "presentation.case")
        assert root.attributes["experiment.grade"] == expected
        assert root.attributes["experiment.score"] == int(expected == "pass")
        assert root.attributes["experiment.case_id"] == "arithmetic-smoke"
        assert attempt["trace_id"] == format(root.context.trace_id, "032x")
        children = [s for s in spans if s.name == "assistant.model_call"]
        assert children and all(s.context.trace_id == root.context.trace_id for s in children)
    finally:
        provider.shutdown()
