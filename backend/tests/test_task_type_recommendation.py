import json

import httpx
import pytest
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from app.core.config import Settings
from app.services import task_type_recommendation as service

HTTP_CLIENT = httpx.Client


def provider(monkeypatch, answer, status=200):
    requests = []
    def handle(request):
        requests.append(request)
        return httpx.Response(status, json={"answers": {"task_type": answer}, "model": "jev-1.13.0", "usage": {"input_tokens": 42}})
    monkeypatch.setattr(service.httpx, "Client", lambda **kwargs: HTTP_CLIENT(transport=httpx.MockTransport(handle), **kwargs))
    return requests


@pytest.mark.parametrize("confidence,choice,reason,id", [
    (0.8, "type_7", "recommended", 7), (0.799, "type_7", "low_confidence", None),
    (0.99, "none", "no_match", None), (0.99, "type_999", "invalid_response", None),
    (True, "type_7", "invalid_response", None), (1.1, "type_7", "invalid_response", None),
])
def test_provider_boundary(monkeypatch, confidence, choice, reason, id):
    requests = provider(monkeypatch, {"type": "choice", "choice": choice, "confidence": confidence,
                                      "probabilities": {"type_7": 0.99, "none": 0.01}})
    result = service.recommend("Practise piano", [(7, "learning/music"), (1, "unspecified")], Settings(jev_api_key="test-secret"))
    assert (result.reason, result.task_type_id) == (reason, id)
    sent = json.loads(requests[0].content)
    assert sent["state"] == {"name": "Practise piano"}
    assert sent["questions"]["task_type"]["criteria"] == {"type_7": "learning/music", "none": "None of these Task Types suitably classifies the entered name."}
    assert requests[0].headers["Authorization"] == "Bearer test-secret"


@pytest.mark.parametrize("name,count,key,reason", [("", 1, "key", "empty_name"), ("name", 0, "key", "no_categories"), ("name", 255, "key", "category_limit"), ("name", 1, None, "unavailable")])
def test_suppression_makes_no_provider_request(monkeypatch, name, count, key, reason):
    monkeypatch.setattr(service.httpx, "Client", lambda **kwargs: pytest.fail("unexpected request"))
    assert service.recommend(name, [(i, f"type{i}") for i in range(count)], Settings(jev_api_key=key)).reason == reason


def test_trace_contains_result_usage_and_failure_without_credentials(monkeypatch):
    exporter = InMemorySpanExporter()
    tracing = TracerProvider()
    tracing.add_span_processor(SimpleSpanProcessor(exporter))
    tracer = tracing.get_tracer("test")
    monkeypatch.setattr(service.trace, "get_tracer", lambda name: tracer)
    provider(monkeypatch, {"type": "choice", "choice": "type_7", "confidence": .91})
    service.recommend("Practise piano", [(7, "learning/music")], Settings(jev_api_key="test-secret"))
    attributes = exporter.get_finished_spans()[0].attributes
    assert attributes["recommendation.reason"] == "recommended"
    assert attributes["llm.token_count.prompt"] == 42
    assert "llm.token_count.completion" not in attributes
    assert "test-secret" not in str(attributes)
    assert json.loads(attributes["input.value"]) == {"name": "Practise piano", "task_types": ["learning/music"]}
    provider(monkeypatch, {}, 503)
    assert service.recommend("Name", [(7, "learning/music")], Settings(jev_api_key="test-secret")).reason == "unavailable"
    tracing.shutdown()


def test_endpoint_reads_catalog_and_never_assigns(client, monkeypatch):
    created = client.post("/task-types", json={"name": "learning/music"}).json()
    received = []
    def recommend(name, candidates, settings, **context):
        received.append((name, candidates, context))
        return service.Recommendation(reason="recommended", task_type_id=created["id"], confidence=.9)
    monkeypatch.setattr("app.api.routes.task_type_recommendations.recommend", recommend)
    before = client.get("/task-types").json()
    result = client.post("/task-types/recommendation", json={"name": "Practise piano", "picker_query": "learning", "linked_task_name": "Daily practice"})
    assert result.status_code == 200
    assert received[0][2] == {"picker_query": "learning", "linked_task_name": "Daily practice"}
    assert (created["id"], "learning/music") in received[0][1]
    assert client.get("/task-types").json() == before


def test_combined_context_is_one_request(monkeypatch):
    requests = provider(monkeypatch, {"type": "choice", "choice": "type_7", "confidence": .93})
    result = service.recommend("Scales", [(7, "learning/music")], Settings(jev_api_key="test"),
                               picker_query="learning", linked_task_name="Practice piano")
    assert result.task_type_id == 7
    assert len(requests) == 1
    assert json.loads(requests[0].content)["state"] == {
        "name": "Scales", "picker_query": "learning", "linked_task_name": "Practice piano"}


def test_query_only_and_linked_name_only(monkeypatch):
    requests = provider(monkeypatch, {"type": "choice", "choice": "type_7", "confidence": .93})
    for context in ({"picker_query": "piano"}, {"linked_task_name": "Practice piano"}):
        assert service.recommend("", [(7, "learning/music")], Settings(jev_api_key="test"), **context).task_type_id == 7
        assert json.loads(requests[-1].content)["state"] == context
