from decimal import Decimal
from types import SimpleNamespace

import pytest

from scripts.presentation_live import Budget


def test_price_reservation_includes_long_context_tiers(monkeypatch):
    from scripts import presentation_live as live

    response = SimpleNamespace(raise_for_status=lambda: None, json=lambda: {"data": {"endpoints": [{
        "context_length": 1000000, "pricing": {"prompt": "0.0000001", "completion": "0.0000005",
        "web_search": "0.01", "overrides": [{"min_prompt_tokens": 272000,
        "prompt": "0.0000002", "completion": "0.00000075", "input_cache_write": "0.00000025"}]}}]}})
    monkeypatch.setattr(live.httpx, "get", lambda *args, **kwargs: response)
    with pytest.raises(ValueError, match="web_search"):
        live.pricing()
    rates = live.pricing(token_only=True)
    assert Decimal(rates["prompt"]) == Decimal("0.00000025")
    assert Decimal(rates["completion"]) == Decimal("0.00000075")


def test_reservation_is_durable_and_unknown_usage_keeps_full_reservation(tmp_path):
    path = tmp_path / "budget.json"
    budget = Budget(path, "first")
    call = budget.reserve(Decimal("0.50"), "case")
    assert Budget(path, "second").accounted() == Decimal("0.50")
    call["status"] = "interrupted"
    budget.finish(call)
    assert Budget(path, "second").accounted() == Decimal("0.50")
    budget.finish(call, SimpleNamespace(response_metadata={"cost": 0.003}, usage_metadata={"total_tokens": 100}))
    assert Budget(path, "second").accounted() == Decimal("0.003")


def test_per_pilot_and_total_caps_apply_before_requests(tmp_path):
    path = tmp_path / "budget.json"
    Budget(path, "first").reserve(Decimal("10"), "case")
    with pytest.raises(RuntimeError, match="Budget"):
        Budget(path, "first").reserve(Decimal("0.001"), "case")
    for i in range(19):
        Budget(path, str(i)).reserve(Decimal("10"), "case")
    with pytest.raises(RuntimeError, match="Budget"):
        Budget(path, "new").reserve(Decimal("0.001"), "case")


def test_charge_above_reserved_maximum_blocks_further_spending(tmp_path):
    path = tmp_path / "budget.json"
    budget = Budget(path, "first")
    call = budget.reserve(Decimal("0.50"), "case")
    budget.finish(call, SimpleNamespace(response_metadata={"cost": 1}, usage_metadata=None))
    with pytest.raises(RuntimeError, match="discrepancy"):
        Budget(path, "second").reserve(Decimal("0.50"), "case")
