"""Collector authentication and failure isolation at the export boundary."""
import threading
from types import SimpleNamespace

import pytest
from opentelemetry.sdk.trace.export import SpanExportResult

from app.services import assistant_tracing


@pytest.mark.parametrize("key", [None, "phoenix-secret"])
def test_export_authentication_and_redaction(monkeypatch, key):
    exported = []
    options = {}

    class Exporter:
        def __init__(self, **kwargs):
            options.update(kwargs)

        def export(self, spans):
            exported.extend(spans)
            return SpanExportResult.SUCCESS

        def shutdown(self):
            pass

    monkeypatch.setattr(assistant_tracing, "get_settings", lambda: SimpleNamespace(
        assistant_trace_endpoint="http://collector/v1/traces",
        assistant_trace_api_key=key, openrouter_api_key="provider-secret", api_key="app-secret",
    ))
    monkeypatch.setattr(assistant_tracing, "OTLPSpanExporter", Exporter)
    monkeypatch.setattr(assistant_tracing.trace, "set_tracer_provider", lambda provider: None)
    monkeypatch.setattr(assistant_tracing, "LangChainInstrumentor", lambda: SimpleNamespace(instrument=lambda **kwargs: None))
    provider = assistant_tracing.setup_tracing()
    try:
        with provider.get_tracer(__name__).start_as_current_span("response") as span:
            span.set_attribute("input.value", "provider-secret app-secret " + (key or "local"))
        assert provider.force_flush()
        assert options == {"endpoint": "http://collector/v1/traces", "timeout": 3,
                           "headers": {"Authorization": "Bearer phoenix-secret"} if key else None}
        assert exported[0].attributes["input.value"] == "[redacted] [redacted] " + ("[redacted]" if key else "local")
    finally:
        provider.shutdown()


def test_no_endpoint_disables_tracing(monkeypatch):
    monkeypatch.setattr(assistant_tracing, "get_settings", lambda: SimpleNamespace(assistant_trace_endpoint=None))
    monkeypatch.setattr(assistant_tracing, "TracerProvider", lambda **kwargs: pytest.fail("tracing enabled"))
    assert assistant_tracing.setup_tracing() is None


def test_collector_failure_does_not_block_response():
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor

    entered, release = threading.Event(), threading.Event()

    class UnavailableCollector:
        def export(self, spans):
            entered.set()
            assert release.wait(5)
            return SpanExportResult.FAILURE

        def shutdown(self):
            pass

    provider = TracerProvider()
    provider.add_span_processor(BatchSpanProcessor(
        assistant_tracing.RedactingExporter(UnavailableCollector(), []),
        max_export_batch_size=1, max_queue_size=2,
    ))
    try:
        with provider.get_tracer(__name__).start_as_current_span("response"):
            pass
        assert entered.wait(2)
        # Ending more responses succeeds while the collector is stalled, even
        # when the bounded queue is full. Failed export stays off the request.
        for _ in range(5):
            with provider.get_tracer(__name__).start_as_current_span("response"):
                pass
        release.set()
        assert provider.force_flush()
    finally:
        release.set()
        provider.shutdown()
