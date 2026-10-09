"""Budgeted exploratory MIPROv2 search. Never reads the held-out test cases."""
import argparse
import asyncio
import hashlib
import json
import os
from decimal import Decimal

import dspy
from langchain_core.messages import SystemMessage, convert_to_messages
from opentelemetry import trace

from app.core.config import get_settings
from app.services import assistant_agent
from app.services.assistant_policy import load_policy, policy_prompt
from scripts.presentation_compile import NativeProgram, metric, save_policy
from scripts.presentation_dataset import load_cases
from scripts.presentation_eval import grade
from scripts.presentation_live import ARTIFACTS, BACKEND, Budget, encode, measured_calls, pricing, save
from scripts.presentation_tracing import evaluate_case, start_tracing

MANUAL = assistant_agent.PRESENTATION_PROMPT + """
The selector belongs to the FINAL tool-free response itself. A selector in an earlier
tool-calling message does not count. Start the final response with its JSON selector,
then a real newline, then the answer. Never put the selector after the answer.
Even factual summaries and subtask lists need this prefix. If no read card is needed,
the first line is {"presentation":"none"}. Check the prefix before sending any final text.
"""


def bounded_model(rates):
    model = assistant_agent.create_model()
    model.openrouter_provider = {**(model.openrouter_provider or {}), "max_price": {
        "prompt": str(Decimal(rates["prompt"]) * 1_000_000),
        "completion": str(Decimal(rates["completion"]) * 1_000_000), "request": rates["request"]}}
    return model


class BudgetedProposer(dspy.BaseLM):
    """DSPy text-generation interface using the same metered native model boundary."""
    def __init__(self, budget, rates, log):
        super().__init__(assistant_agent.MODEL, temperature=1.0, max_tokens=4096, cache=False, num_retries=0)
        self.budget, self.rates, self.log = budget, rates, log

    def __call__(self, prompt=None, *, messages=None, **kwargs):
        if kwargs.get("n", 1) != 1:
            raise ValueError("Only one metered proposal generation per request")
        model = bounded_model(self.rates)
        model.temperature = kwargs.get("temperature", self.kwargs["temperature"])
        target = model.bind(response_format=kwargs["response_format"]) if "response_format" in kwargs else model
        inputs = convert_to_messages(messages or [{"role": "user", "content": prompt}])
        inputs.insert(0, SystemMessage(
            "You are optimizing only the presentation instructions for Timebox's native Assistant. "
            "Its real model emits a leading presentation-selector JSON line followed by user-facing text. "
            "The DSPy fields raw_answer and attempt are captured by the Python adapter automatically; "
            "never instruct the Assistant to generate those wrapper fields, diagnostics, grades or fixture IDs. "
            "Preserve existing tools, data grounding and confirmation requirements. "
            "Return your optimizer response in the format requested by the following messages."))

        async def generate():
            with model.client:
                async with model.client:
                    return await assistant_agent.call_model(target, inputs)

        with trace.get_tracer(__name__).start_as_current_span("presentation.instruction_proposal", attributes={
            "openinference.span.kind": "CHAIN", "experiment.batch": self.budget.batch,
            "experiment.phase": "instruction-proposal",
        }), measured_calls(self.budget, self.rates, "instruction-proposer", self.log):
            result = asyncio.run(generate())
        if result.response_metadata.get("finish_reason") != "stop":
            raise RuntimeError("Incomplete instruction proposal")
        output = result.text
        self.history.append({"outputs": [output], "usage": result.usage_metadata})
        return [output]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("batch")
    parser.add_argument("--trace", action="store_true", help="Export to the configured Phoenix collector")
    parser.add_argument("--baseline-from", help="Reuse retained baseline attempts, regrading them without model calls")
    parser.add_argument("--baseline-only", action="store_true")
    args = parser.parse_args()
    if not args.batch.replace("-", "").isalnum():
        raise ValueError("Use letters, digits and hyphens for the batch name")
    secret = get_settings().openrouter_api_key
    if not secret:
        raise RuntimeError("OpenRouter credential is missing")
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    lock = ARTIFACTS / ".lock"
    with lock.open("x") as stream:
        stream.write(str(os.getpid()))
    tracing = None
    try:
        tracing = start_tracing(args.trace)
        root = ARTIFACTS / args.batch
        root.mkdir(exist_ok=False)
        budget = Budget(ARTIFACTS / "budget.json", args.batch, batch_cap=Decimal("50"))
        rates = pricing()
        save(root / "pricing.json", rates)
        dataset = BACKEND / "studies/presentation/dataset"
        train, validation, regressions = [load_cases(dataset, split) for split in ("train", "validation", "regression")]
        cases = {c["question"]: c for c in train + validation}
        source = [*sorted((BACKEND / "app").rglob("*.py")), *sorted((BACKEND / "scripts").glob("presentation_*.py")), BACKEND / "uv.lock"]
        save(root / "manifest.json", {"answer_model": assistant_agent.MODEL, "proposer_model": assistant_agent.MODEL,
             "proposer_temperature": 1.0, "batch_cap_usd": "50", "total_cap_usd": "200",
             "num_candidates": 3, "num_trials": 4, "num_threads": 1, "max_bootstrapped_demos": 2,
             "train_ids": [c["id"] for c in train], "validation_ids": [c["id"] for c in validation],
             "purpose": "Exploratory search and known-regression comparison; not release approval or held-out evaluation",
             "baseline_from": args.baseline_from,
             "dataset_manifest": json.loads((dataset / "manifest.json").read_text()),
             "files": {str(p.relative_to(BACKEND)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source}})
        summaries = []
        phase = "baseline"

        def log(event):
            with (root / "events.jsonl").open("a", encoding="utf-8") as stream:
                stream.write(encode(event).replace(secret, "[redacted]") + "\n")
                stream.flush()

        def execute(case, prompt, candidate):
            index = len(summaries) + 1
            prompt_hash = hashlib.sha256(policy_prompt(prompt).encode()).hexdigest()
            log({"kind": "attempt_started", "index": index, "phase": phase,
                 "case": case["id"], "candidate": candidate, "prompt": policy_prompt(prompt), "prompt_sha256": prompt_hash})
            print(f"START {index} {phase} {candidate} {case['id']}", flush=True)
            with measured_calls(budget, rates, case["id"], log):
                attempt, result = evaluate_case(case, prompt, bounded_model(rates), batch=args.batch,
                                                phase=phase, candidate=candidate, event_sink=log)
            log({"kind": "attempt_finished", "index": index, "attempt": attempt, "grade": result})
            summaries.append({"index": index, "phase": phase, "candidate": candidate, "case": case["id"],
                              "prompt_sha256": prompt_hash, "grade": result, "status": attempt["status"],
                              "seconds": attempt["duration_seconds"], "first_visible_seconds": attempt["first_visible_seconds"],
                              "model_calls": len(attempt["calls"]), "errors": attempt["errors"]})
            save(root / "summary.json", {"attempts": summaries, "accounted_usd": str(budget.accounted(args.batch))})
            print(f"DONE {index} {result['status']} {attempt['duration_seconds']:.1f}s USD {budget.accounted(args.batch)}", flush=True)
            if budget.data.get("blocked") or budget.accounted(args.batch) + Decimal("1") > budget.batch_cap:
                raise RuntimeError("Budget guard stopped the experiment")
            return attempt

        baseline_prompts = {"current": assistant_agent.PRESENTATION_PROMPT, "manual": MANUAL}
        save(root / "baseline-prompts.json", baseline_prompts)
        reused = {}
        if args.baseline_from:
            if not args.baseline_from.replace("-", "").isalnum():
                raise ValueError("Invalid baseline batch name")
            previous = ARTIFACTS / args.baseline_from
            old_manifest = json.loads((previous / "manifest.json").read_text())
            fixture_path = BACKEND / "scripts/presentation_fixtures.py"
            old_files = {name.replace("\\", "/"): value for name, value in old_manifest["files"].items()}
            if old_files["scripts/presentation_fixtures.py"] != hashlib.sha256(fixture_path.read_bytes()).hexdigest():
                raise ValueError("Cannot reuse baseline with changed fixture state")
            started = None
            for line in (previous / "events.jsonl").open(encoding="utf-8"):
                event = json.loads(line)
                if event.get("kind") == "baseline_reused":
                    reused[(event["candidate"], event["case"])] = event
                if event.get("kind") == "attempt_started":
                    started = event
                if event.get("kind") == "attempt_finished" and started and started["phase"] == "baseline":
                    name = started["candidate"]
                    if started["prompt"] != baseline_prompts[name]:
                        raise ValueError("Baseline prompt changed")
                    reused[(name, started["case"])] = {**event, "candidate": name, "case": started["case"]}
        # Interleave equivalent candidates across the same development cases.
        for i, case in enumerate(validation):
            for name in list(baseline_prompts)[::1 if i % 2 == 0 else -1]:
                retained = reused.get((name, case["id"]))
                if retained:
                    attempt = retained["attempt"]
                    evaluation = grade(case["expectation"], attempt)
                    log({**retained, "kind": "baseline_reused", "original_grade": retained["grade"], "grade": evaluation})
                    summaries.append({"index": len(summaries) + 1, "phase": "baseline", "candidate": name,
                                      "case": case["id"], "grade": evaluation, "status": attempt["status"],
                                      "seconds": attempt["duration_seconds"], "first_visible_seconds": attempt["first_visible_seconds"],
                                      "model_calls": len(attempt["calls"]), "errors": attempt["errors"], "reused": True})
                else:
                    execute(case, baseline_prompts[name], name)
        save(root / "summary.json", {"attempts": summaries, "accounted_usd": str(budget.accounted(args.batch))})
        if args.baseline_only:
            print("BASELINE COMPLETE " + str(root), flush=True)
            return
        phase = "compilation"

        def runner(context, prompt):
            return execute(cases[context], prompt, "search")

        def example(case):
            return dspy.Example(context=case["question"], expectation=case["expectation"]).with_inputs("context")

        optimizer = dspy.MIPROv2(metric=metric, prompt_model=BudgetedProposer(budget, rates, log),
                                task_model=dspy.BaseLM("native-timebox"), auto=None, num_candidates=3,
                                num_threads=1, max_bootstrapped_demos=2, max_labeled_demos=0, max_errors=1, seed=9)
        # The wrapper's Python source and diagnostic attempt dictionaries are not
        # useful presentation demonstrations for the instruction proposer.
        optimized = optimizer.compile(NativeProgram(runner), trainset=[example(c) for c in train],
                                      valset=[example(c) for c in validation], num_trials=4, minibatch=False,
                                      program_aware_proposer=False, data_aware_proposer=True,
                                      fewshot_aware_proposer=False, tip_aware_proposer=True)
        save_policy(optimized, root / "compiled-policy.json")
        compiled = load_policy(root / "compiled-policy.json")
        phase = "known-regression-comparison"
        frozen = {**baseline_prompts, "compiled": compiled}
        for repetition in range(3):
            for case in regressions:
                names = list(frozen)
                names = names[repetition:] + names[:repetition]
                for name in names:
                    execute(case, frozen[name], name)
        print("COMPLETE " + str(root), flush=True)
    finally:
        if tracing:
            tracing.shutdown()
        lock.unlink()


if __name__ == "__main__":
    main()
