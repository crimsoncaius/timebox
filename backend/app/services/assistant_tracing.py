"""Opt-in OpenInference tracing with credentials scrubbed at export."""

from __future__ import annotations

import copy
import re

from openinference.instrumentation.langchain import LangChainInstrumentor
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import Event, TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, SpanExporter

from app.core.config import get_settings


class RedactingExporter(SpanExporter):
    def __init__(self, exporter, secrets):
        self.exporter = exporter
        self.secrets = tuple(value for value in secrets if value)

    def clean(self, value):
        if isinstance(value, str):
            for secret in self.secrets:
                value = value.replace(secret, "[redacted]")
            return re.sub(r"sk-or-v1-[a-zA-Z0-9_-]+", "[redacted]", value)
        if isinstance(value, (list, tuple)):
            return tuple(self.clean(v) for v in value)
        return value

    def export(self, spans):
        safe = []
        for source in spans:
            # Copy at the exporter boundary; never mutate spans used by other processors.
            span = copy.copy(source)
            span._attributes = {k: self.clean(v) for k, v in (source.attributes or {}).items()}
            span._events = [Event(e.name, {k: self.clean(v) for k, v in (e.attributes or {}).items()}, e.timestamp)
                            for e in source.events]
            span._status = trace.Status(source.status.status_code, self.clean(source.status.description))
            safe.append(span)
        return self.exporter.export(safe)

    def shutdown(self):
        self.exporter.shutdown()


def setup_tracing():
    settings = get_settings()
    if not settings.assistant_trace_endpoint:
        return None
    provider = TracerProvider(resource=Resource.create({"openinference.project.name": "timebox-assistant"}))
    provider.add_span_processor(BatchSpanProcessor(RedactingExporter(
        OTLPSpanExporter(
            endpoint=settings.assistant_trace_endpoint,
            headers={"Authorization": f"Bearer {settings.assistant_trace_api_key}"}
            if settings.assistant_trace_api_key else None,
            timeout=3,
        ),
        [settings.openrouter_api_key, settings.jev_api_key, settings.api_key, settings.assistant_trace_api_key],
    )))
    trace.set_tracer_provider(provider)
    LangChainInstrumentor().instrument(tracer_provider=provider)
    return provider
