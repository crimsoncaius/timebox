"""Validate development fixtures; never run a candidate on held-out cases here."""
import json
from collections import Counter
from copy import deepcopy
from pathlib import Path

import pytest

from app.services.assistant_agent import PRESENTATION_PROMPT
from app.services.assistant_plan import ReadActivityArgs
from app.services.assistant_task_intents import ProposeTaskChangesArgs
from app.services.assistant_tasks import ReadTasksArgs
from scripts.presentation_dataset import families, load_cases, write_dataset
from scripts.presentation_eval import grade, run_native
from scripts.presentation_replay import FixtureModel
from scripts.presentation_scoring import factual_checks

CASES = families()
DEVELOPMENT = [c for c in CASES if c["split"] != "test"]


def test_split_integrity_and_fixture_schemas(tmp_path):
    assert Counter(c["split"] for c in CASES) == {"train": 60, "validation": 30, "test": 30}
    assert len({c["id"] for c in CASES}) == len({c["question"] for c in CASES}) == 120
    owners = {}
    schemas = {"read_tasks": ReadTasksArgs, "read_activity": ReadActivityArgs,
               "propose_task_changes": ProposeTaskChangesArgs}
    for case in CASES:
        assert owners.setdefault(case["family"], case["split"]) == case["split"]
        for step in case["probe"]:
            if step.get("tool") in schemas:
                schemas[step["tool"]].model_validate(step["args"])
    assert len(owners) == 24
    root = tmp_path / "dataset"
    write_dataset(root)
    assert len(load_cases(root, "train")) == 60
    with pytest.raises(FileExistsError):
        write_dataset(root)
    (root / "train.json").write_text("[]")
    with pytest.raises(ValueError, match="Dataset changed"):
        load_cases(root, "train")


def test_checked_in_dataset_matches_authored_cases():
    root = Path(__file__).parents[1] / "studies/presentation/dataset"
    for split in ("train", "validation", "test"):
        assert load_cases(root, split) == [c for c in CASES if c["split"] == split]
    assert len(load_cases(root, "regression")) == 3


@pytest.mark.parametrize("case", DEVELOPMENT, ids=lambda case: case["id"])
def test_development_fixture_with_controlled_answer(case):
    attempt = run_native(case["question"], PRESENTATION_PROMPT,
                         FixtureModel(steps=deepcopy(case["probe"])), setup=deepcopy(case["setup"]))
    result = grade(case["expectation"], attempt)
    assert result["status"] == "pass", {"grade": result, "errors": attempt["errors"],
                                         "proposals": attempt["proposals"], "tracking": attempt["tracking_proposal"],
                                         "cards": attempt["cards"], "answer": attempt["answer"]}
    assert attempt["domain_unchanged"]
    if case["category"] == "unacknowledged-history":
        assert not any(m["type"] == "ai" for m in attempt["calls"][0]["messages"])
    if case["category"] == "acknowledged-history":
        assert any(m["type"] == "ai" and '"presentation"' in m["content"] for m in attempt["calls"][0]["messages"])


@pytest.mark.parametrize("answer,expected", [
    ("Work: 9 hours.\nCoding: 6 hours.\nAdmin: 3 hours.", "pass"),
    ("9h of Work\n360 minutes Coding\nAdmin: three hours", "pass"),
    ("Work: 9 hours.\nCoding: 3 hours.\nAdmin: 6 hours.", "fail"),
    ("Work: 18 hours.\nCoding: 6 hours.\nAdmin: 3 hours.", "fail"),
    ("Nine of Work, six of Coding, three of Admin.", "unresolved"),
])
def test_duration_associations(answer, expected):
    expectation = next(c["expectation"] for c in CASES if c["family"] == "hierarchy-totals")
    failures, unresolved = factual_checks(expectation, answer, [])
    assert ("fail" if failures else "unresolved" if unresolved else "pass") == expected


def test_checked_state_is_not_just_title_presence():
    expectation = {"checked": [{"title": "Patch export", "value": False}]}
    assert factual_checks(expectation, "Patch export", [])[1]
    assert factual_checks(expectation, "Patch export: checked", [])[0]
    assert factual_checks(expectation, "⬜ Patch export", []) == ([], [])
    assert factual_checks(expectation, "Patch export: unchecked", []) == ([], [])


@pytest.mark.parametrize("case_id", ["07-work-breakdown", "18-check-subtask"])
def test_corrected_regression_text_is_checkable_through_native_boundary(case_id):
    root = Path(__file__).parents[1] / "studies/presentation"
    case = next(c for c in json.loads((root / "pilot.json").read_text()) if c["id"] == case_id)
    original = next(c for c in json.loads((root / "original_failures.json").read_text())["cases"] if c["id"] == case_id)
    # Keep the original answer wording; only relocate/add the missing selector.
    answer = original["raw_answer"].replace('{"presentation":"none"}', "").strip()
    steps = ([{"tool": "read_activity", "args": {"when": "this_week", "lane": "actual", "group_by": "task_type", "task_type": "Work"}}]
             if case_id == "07-work-breakdown" else [
                 {"tool": "read_tasks", "args": {"mode": "search", "query": "Fix calendar export"}},
                 {"tool": "read_tasks", "args": {"mode": "children", "parent_id": 1, "kind": "subtasks"}}])
    steps.append({"answer": answer})
    attempt = run_native(case["question"], PRESENTATION_PROMPT, FixtureModel(steps=steps))
    assert grade(case["expectation"], attempt)["status"] == "pass"


def test_truncated_terminal_cannot_pass():
    attempt = run_native("Reply Hello.", PRESENTATION_PROMPT,
                         FixtureModel(steps=[{"answer": "Hello.", "finish": "length"}]))
    assert attempt["status"] == "interrupted"
    assert not attempt["acknowledged"] and attempt["domain_unchanged"]
    assert grade({"cards": "unwanted"}, attempt)["status"] == "fail"


def test_text_only_delivery_does_not_satisfy_required_card():
    case = next(c for c in CASES if c["family"] == "single-day-schedule")
    steps = deepcopy(case["probe"])
    steps[-1] = {"raw": "Here is today's schedule."}
    attempt = run_native(case["question"], PRESENTATION_PROMPT, FixtureModel(steps=steps), setup=case["setup"])
    assert attempt["status"] == "completed" and attempt["acknowledged"]
    assert attempt["answer"] == "Here is today's schedule."
    assert "required card absent" in grade(case["expectation"], attempt)["failures"]


def test_truncated_plain_text_cannot_complete():
    attempt = run_native("Reply Hello.", PRESENTATION_PROMPT,
                         FixtureModel(steps=[{"raw": "Hello.", "finish": "length"}]))
    assert attempt["status"] == "interrupted" and not attempt["acknowledged"]


def test_correct_card_cannot_excuse_wrong_answer():
    spec = {"durations": [{"label": "Work", "seconds": 32400}]}
    cards = [{"types": [{"task_type": "Work", "actual_seconds": 32400}]}]
    assert factual_checks(spec, "Work: 18 hours.", cards)[0]
    assert factual_checks(spec, "", cards) == ([], [])


def test_card_coverage_tracking_and_operations_negative_controls():
    case = next(c for c in CASES if c["family"] == "same-day-lane-cards")
    attempt = run_native(case["question"], PRESENTATION_PROMPT, FixtureModel(steps=deepcopy(case["probe"])))
    wrong = deepcopy(attempt)
    wrong["cards"].pop()
    assert "required card coverage absent" in grade(case["expectation"], wrong)["failures"]
    wrong = deepcopy(attempt)
    wrong["cards"][0]["lane"] = "actual"
    assert "ungrounded card" in grade(case["expectation"], wrong)["failures"]
    case = next(c for c in CASES if c["family"] == "tracking-now")
    attempt = run_native(case["question"], PRESENTATION_PROMPT, FixtureModel(steps=deepcopy(case["probe"])))
    attempt["tracking_proposal"]["task_types"][0]["path"] = "Meals"
    assert "incorrect tracking proposal" in grade(case["expectation"], attempt)["failures"]
    case = next(c for c in CASES if c["family"] == "rename-proposal")
    attempt = run_native(case["question"], PRESENTATION_PROMPT, FixtureModel(steps=deepcopy(case["probe"])))
    attempt["proposals"][0]["review"]["operations"][0]["set"]["title"] = "Wrong title"
    assert "incorrect proposed operations" in grade(case["expectation"], attempt)["failures"]
