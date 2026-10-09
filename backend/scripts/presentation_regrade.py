"""Regrade retained native evidence without generating any model responses."""
import argparse
import hashlib
import json

from scripts.presentation_dataset import load_cases
from scripts.presentation_eval import grade
from scripts.presentation_live import ARTIFACTS, BACKEND, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("batch")
    args = parser.parse_args()
    if not args.batch.replace("-", "").isalnum():
        raise ValueError("Use a batch name")
    root = ARTIFACTS / args.batch
    output = root / "regrade-v4.json"
    if output.exists():
        raise FileExistsError(output)
    dataset = BACKEND / "studies/presentation/dataset"
    cases = {c["id"]: c for split in ("train", "validation", "regression") for c in load_cases(dataset, split)}
    rows = []
    started = {}
    with (root / "events.jsonl").open(encoding="utf-8") as stream:
        for line in stream:
            event = json.loads(line)
            kind = event.get("kind")
            if kind == "attempt_started":
                started = event
            if kind not in {"attempt_finished", "baseline_reused"}:
                continue
            info = event if kind == "baseline_reused" else started
            case = cases[info["case"]]
            result = grade(case["expectation"], event["attempt"])
            rows.append({"index": len(rows) + 1, "case": case["id"],
                         "phase": "baseline" if kind == "baseline_reused" else info["phase"],
                         "candidate": info.get("candidate", "compiled"),
                         "old_grade": event["grade"], "grade": result})
    source = [BACKEND / "scripts" / (name + ".py") for name in
              ("presentation_eval", "presentation_scoring", "presentation_dataset", "presentation_regrade")]
    save(output, {"dataset": "dataset-v4", "source_batch": args.batch, "new_model_calls": 0,
                  "files": {str(p.relative_to(BACKEND)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source},
                  "attempts": rows})
    print(f"Regraded {len(rows)} retained attempts; {sum(r['grade'] != r['old_grade'] for r in rows)} grades changed")
    print(output)


if __name__ == "__main__":
    main()
