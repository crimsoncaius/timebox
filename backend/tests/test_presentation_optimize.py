from contextlib import nullcontext
from types import SimpleNamespace

import pytest

dspy = pytest.importorskip("dspy")

from langchain_core.messages import AIMessage  # noqa: E402

from scripts import presentation_optimize as optimize  # noqa: E402


def test_dspy_proposer_adapter_uses_native_boundary(monkeypatch):
    class Client:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

    model = SimpleNamespace(client=Client(), temperature=None)
    captured = []

    async def call(target, messages):
        captured.append(messages)
        return AIMessage(content="[[ ## proposed_instruction ## ]]\nStart with the selector.\n[[ ## completed ## ]]",
                         response_metadata={"finish_reason": "stop"})

    monkeypatch.setattr(optimize, "bounded_model", lambda rates: model)
    monkeypatch.setattr(optimize, "measured_calls", lambda *args: nullcontext())
    monkeypatch.setattr(optimize.assistant_agent, "call_model", call)
    proposer = optimize.BudgetedProposer(SimpleNamespace(batch="test"), {}, lambda event: None)
    with dspy.context(lm=proposer):
        result = dspy.Predict("context -> proposed_instruction")(context="Improve presentation")
    assert result.proposed_instruction == "Start with the selector."
    assert len(captured) == 1
    assert "captured by the Python adapter" in captured[0][0].content
    assert model.temperature == 1.0
