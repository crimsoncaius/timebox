"""Evaluate a saved DSPy policy with a selected model; no training."""
import argparse
import hashlib
import json
import os
from decimal import Decimal
from pathlib import Path
from unittest.mock import patch

from app.core.config import get_settings
from app.services import assistant_agent
from app.services.assistant_policy import load_policy, policy_prompt
from scripts.presentation_dataset import load_cases
from scripts.presentation_live import ARTIFACTS, BACKEND, Budget, encode, measured_calls, pricing, save
from scripts.presentation_tracing import evaluate_case, start_tracing


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("batch")
    parser.add_argument("--model", default=assistant_agent.MODEL)
    parser.add_argument("--policy", type=Path, required=True)
    parser.add_argument("--reasoning", choices=["none", "low", "medium", "high"])
    parser.add_argument("--trace", action="store_true", help="Export to the configured Phoenix collector")
    args = parser.parse_args()
    if not args.batch.replace("-", "").isalnum():
        raise ValueError("Use letters, digits and hyphens for the batch name")
    secret = get_settings().openrouter_api_key
    if not secret:
        raise RuntimeError("OpenRouter credential missing")
    prompt = load_policy(args.policy)
    prompt_hash = hashlib.sha256(policy_prompt(prompt).encode()).hexdigest()
    dataset = BACKEND / "studies/presentation/dataset"
    selected = [("validation", 1, c) for c in load_cases(dataset, "validation")]
    regressions = load_cases(dataset, "regression")
    selected += [("regression", repetition, c) for repetition in range(1, 4) for c in regressions]
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    lock = ARTIFACTS / ".lock"
    with lock.open("x") as stream:
        stream.write(str(os.getpid()))
    tracing = None
    try:
        tracing = start_tracing(args.trace)
        root = ARTIFACTS / args.batch
        root.mkdir(exist_ok=False)
        reasoning = {"effort": args.reasoning} if args.reasoning else None
        with patch.object(assistant_agent, "MODEL", args.model):
            rates = pricing(token_only=True)
            save(root / "pricing.json", rates)
            budget = Budget(ARTIFACTS / "budget.json", args.batch)
            source = [*sorted((BACKEND / "app").rglob("*.py")),
                      *sorted((BACKEND / "scripts").glob("presentation_*.py")), BACKEND / "uv.lock"]
            save(root / "manifest.json", {"model": args.model, "reasoning": reasoning,
                 "prompt_sha256": prompt_hash, "policy": json.loads(args.policy.read_text(encoding="utf-8")),
                 "max_tokens": 4096, "batch_cap_usd": "10", "total_cap_usd": "200",
                 "purpose": "Frozen-policy model comparison; no training or held-out evaluation",
                 "dataset_manifest": json.loads((dataset / "manifest.json").read_text()),
                 "files": {str(p.relative_to(BACKEND)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source}})

            def log(event):
                with (root / "events.jsonl").open("a", encoding="utf-8") as stream:
                    stream.write(encode(event).replace(secret, "[redacted]") + "\n")
                    stream.flush()

            summaries = []
            for phase, repetition, case in selected:
                index = len(summaries) + 1
                log({"kind": "attempt_started", "index": index, "phase": phase,
                     "repetition": repetition, "case": case["id"]})
                print(f"START {index}/39 {phase} {case['id']}", flush=True)
                model = assistant_agent.create_model()
                model.reasoning = reasoning
                model.openrouter_provider = {"require_parameters": True, "max_price": {
                    "prompt": str(Decimal(rates["prompt"]) * 1_000_000),
                    "completion": str(Decimal(rates["completion"]) * 1_000_000), "request": rates["request"]}}
                with measured_calls(budget, rates, case["id"], log):
                    attempt, result = evaluate_case(case, prompt, model, batch=args.batch, phase=phase,
                                                    candidate="compiled", event_sink=log)
                log({"kind": "attempt_finished", "index": index, "attempt": attempt, "grade": result})
                summaries.append({"index": index, "phase": phase, "repetition": repetition, "case": case["id"],
                                  "grade": result, "seconds": attempt["duration_seconds"],
                                  "first_visible_seconds": attempt["first_visible_seconds"], "errors": attempt["errors"]})
                save(root / "summary.json", {"attempts": summaries, "accounted_usd": str(budget.accounted(args.batch))})
                print(f"DONE {index} {result['status']} {attempt['duration_seconds']:.1f}s", flush=True)
                if budget.data.get("blocked"):
                    raise RuntimeError("Accounting discrepancy")
                if index == 1 and attempt["errors"]:
                    raise RuntimeError("First-case failure; inspect provider compatibility before continuing")
            print("COMPLETE " + str(root), flush=True)
    finally:
        if tracing:
            tracing.shutdown()
        lock.unlink()


if __name__ == "__main__":
    main()
