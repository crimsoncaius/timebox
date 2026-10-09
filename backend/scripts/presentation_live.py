"""Small, real-model development baseline. Never loads the held-out split."""
import argparse
import datetime as dt
import hashlib
import json
import os
import time
from contextlib import contextmanager
from decimal import Decimal
from pathlib import Path
from unittest.mock import patch
from uuid import uuid4

import httpx

from app.core.config import get_settings
from app.services import assistant_agent
from scripts.presentation_dataset import load_cases
from scripts.presentation_tracing import evaluate_case, start_tracing

BACKEND = Path(__file__).resolve().parents[1]
ARTIFACTS = BACKEND.parent / "artifacts/presentation-experiment"
TOTAL_CAP = Decimal("200")
PILOT_CAP = Decimal("10")


def encode(value):
    def default(item):
        if hasattr(item, "model_dump"):
            return item.model_dump(mode="json")
        if isinstance(item, (dt.datetime, dt.date)):
            return item.isoformat()
        raise TypeError(type(item).__name__)
    return json.dumps(value, default=default, ensure_ascii=False)


def save(path, value):
    temporary = path.with_suffix(".tmp")
    with temporary.open("w", encoding="utf-8") as stream:
        stream.write(encode(value) + "\n")
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)


def pricing(*, token_only=False):
    url = f"https://openrouter.ai/api/v1/models/{assistant_agent.MODEL}/endpoints"
    response = httpx.get(url, timeout=30)
    response.raise_for_status()
    data = response.json()["data"]
    endpoints = data["endpoints"]
    if not endpoints:
        raise ValueError("No verified provider pricing")
    allowed = {"prompt", "completion", "request", "input_cache_read", "input_cache_write", "discount"}
    prices = []
    for endpoint in endpoints:
        base = endpoint["pricing"]
        prices.append({k: v for k, v in base.items() if k != "overrides"})
        for override in base.get("overrides", []):
            prices.append({k: v for k, v in override.items() if k != "min_prompt_tokens"})
    for price in prices:
        for key, value in price.items():
            # This option is only for runners using local function tools, never
            # provider-hosted search. Include all long-context token price tiers.
            if key == "web_search" and token_only:
                continue
            if key not in allowed and value not in (None, "0", 0):
                raise ValueError("Unaccounted pricing field: " + key)
            if key in allowed and (not Decimal(str(value)).is_finite() or Decimal(str(value)) < 0):
                raise ValueError("Invalid provider price")
    return {"source": url, "checked_at": dt.datetime.now(dt.UTC).isoformat(), "data": data,
            "context_tokens": max(int(e["context_length"]) for e in endpoints),
            "prompt": str(max(Decimal(p[k]) for p in prices for k in ("prompt", "input_cache_read", "input_cache_write") if k in p)),
            "completion": str(max(Decimal(p["completion"]) for p in prices)),
            "request": str(max(Decimal(p.get("request", "0")) for p in prices))}


class Budget:
    def __init__(self, path, batch, batch_cap=PILOT_CAP):
        self.path, self.batch = path, batch
        if not batch_cap.is_finite() or not 0 < batch_cap <= TOTAL_CAP:
            raise ValueError("Invalid batch cap")
        self.batch_cap = batch_cap
        self.data = json.loads(path.read_text()) if path.exists() else {"cap_usd": str(TOTAL_CAP), "calls": []}
        if Decimal(self.data["cap_usd"]) != TOTAL_CAP:
            raise ValueError("Unexpected experiment cap")

    def accounted(self, batch=None):
        return sum((Decimal(c["actual_usd"] if c.get("actual_usd") is not None else c["reserved_usd"])
                    for c in self.data["calls"] if batch is None or c["batch"] == batch), Decimal(0))

    def reserve(self, amount, case):
        if self.data.get("blocked"):
            raise RuntimeError("Accounting discrepancy requires review before further requests")
        if not amount.is_finite() or amount <= 0:
            raise ValueError("Invalid reservation")
        if self.accounted() + amount > TOTAL_CAP or self.accounted(self.batch) + amount > self.batch_cap:
            raise RuntimeError("Budget cannot accommodate another request")
        call = {"id": str(uuid4()), "batch": self.batch, "case": case,
                "reserved_usd": str(amount), "actual_usd": None, "status": "reserved"}
        self.data["calls"].append(call)
        save(self.path, self.data)  # Durable before any provider request, including on process loss.
        return call

    def finish(self, call, result=None):
        if result is not None:
            cost = result.response_metadata.get("cost")
            call["usage"] = result.usage_metadata
            call["metadata"] = result.response_metadata
            if cost is not None:
                amount = Decimal(str(cost))
                if amount.is_finite() and amount >= 0:
                    call["actual_usd"] = str(amount)
                    if amount > Decimal(call["reserved_usd"]):
                        self.data["blocked"] = True
        save(self.path, self.data)


@contextmanager
def measured_calls(budget, rates, case, log):
    original = assistant_agent.call_model

    async def call(target, messages):
        base = getattr(target, "bound", target)
        # Fixture history is scripted; the target turn is always the live model.
        if base._llm_type == "presentation-fixture":
            return await original(target, messages)
        parameters = {**base._default_params, **getattr(target, "kwargs", {})}
        wire, _ = base._create_message_dicts(messages, None)
        if len(encode({"messages": wire, "parameters": parameters}).encode()) + 8192 > rates["context_tokens"]:
            raise RuntimeError("Input exceeds conservative context reservation")
        if base.max_tokens != 4096 or base.max_retries != 0:
            raise RuntimeError("Answering configuration changed")
        reserved = Decimal(rates["prompt"]) * rates["context_tokens"] + Decimal(rates["completion"]) * base.max_tokens + Decimal(rates["request"])
        record = budget.reserve(reserved, case)
        log({"kind": "model_input", "call_id": record["id"], "messages": wire, "parameters": parameters})
        started = time.monotonic()
        try:
            result = await original(target, messages)
            record["status"] = "returned"
            budget.finish(record, result)
            log({"kind": "model_output", "call_id": record["id"], "output": result})
            return result
        except BaseException as error:
            record["status"] = "interrupted"
            log({"kind": "model_error", "call_id": record["id"], "type": type(error).__name__})
            raise
        finally:
            record["seconds"] = time.monotonic() - started
            budget.finish(record)
    with patch.object(assistant_agent, "call_model", call):
        yield


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("batch", help="Fresh output directory name")
    parser.add_argument("--trace", action="store_true", help="Export to the configured Phoenix collector")
    args = parser.parse_args()
    if Path(args.batch).name != args.batch or args.batch in {".", ".."}:
        raise ValueError("Use a simple batch name")
    secret = get_settings().openrouter_api_key
    if not secret:
        raise RuntimeError("OpenRouter credential is not configured")
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    lock = ARTIFACTS / ".lock"
    with lock.open("x") as stream:
        stream.write(str(os.getpid()))
    tracing = None
    try:
        tracing = start_tracing(args.trace)
        root = ARTIFACTS / args.batch
        root.mkdir(exist_ok=False)
        rates = pricing()
        save(root / "pricing.json", rates)
        budget = Budget(ARTIFACTS / "budget.json", args.batch)
        dataset = BACKEND / "studies/presentation/dataset"
        train = load_cases(dataset, "train")
        regression = load_cases(dataset, "regression")
        # One current-prompt attempt per case. No optimization and no test-split access.
        selected = [next(c for c in train if c["family"] == family) for family in
                    ("arithmetic-only", "single-day-schedule", "tracking-now")]
        selected += regression
        source = [*sorted((BACKEND / "app").rglob("*.py")), *sorted((BACKEND / "scripts").glob("presentation_*.py")), BACKEND / "uv.lock"]
        save(root / "manifest.json", {"model": assistant_agent.MODEL, "candidate": "current-production",
             "case_ids": [c["id"] for c in selected], "batch_cap_usd": str(PILOT_CAP), "total_cap_usd": str(TOTAL_CAP),
             "files": {str(p.relative_to(BACKEND)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source},
             "dataset_manifest": json.loads((dataset / "manifest.json").read_text())})
        summaries = []
        for case in selected:
            path = root / (case["id"] + ".jsonl")
            def log(event, path=path):
                text = encode(event).replace(secret, "[redacted]")
                with path.open("a", encoding="utf-8") as output:
                    output.write(text + "\n")
                    output.flush()
            print("START " + case["id"], flush=True)
            log({"kind": "case_started", "case": case, "at": dt.datetime.now(dt.UTC)})
            model = assistant_agent.create_model()
            model.openrouter_provider = {**(model.openrouter_provider or {}), "max_price": {
                "prompt": str(Decimal(rates["prompt"]) * 1_000_000),
                "completion": str(Decimal(rates["completion"]) * 1_000_000), "request": rates["request"]}}
            with measured_calls(budget, rates, case["id"], log):
                attempt, evaluation = evaluate_case(case, assistant_agent.PRESENTATION_PROMPT, model,
                                                     batch=args.batch, phase="pilot", candidate="current", event_sink=log)
            log({"kind": "case_finished", "attempt": attempt, "grade": evaluation})
            summary = {"case": case["id"], "grade": evaluation, "status": attempt["status"],
                       "seconds": attempt["duration_seconds"], "first_visible_seconds": attempt["first_visible_seconds"],
                       "model_calls": len(attempt["calls"]), "errors": attempt["errors"]}
            summaries.append(summary)
            save(root / "summary.json", {"results": summaries, "accounted_usd": str(budget.accounted(args.batch))})
            print(encode(summary), flush=True)
            if budget.data.get("blocked"):
                raise RuntimeError("Provider charge exceeded its reservation; further requests stopped")
        print("Accounted USD: " + str(budget.accounted(args.batch)), flush=True)
    finally:
        if tracing:
            tracing.shutdown()
        lock.unlink()


if __name__ == "__main__":
    main()
