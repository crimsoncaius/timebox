"""Retained-response wording regressions and adjacent false-positive controls."""
import pytest

from scripts.presentation_dataset import families
from scripts.presentation_eval import grade
from scripts.presentation_scoring import factual_checks


def score(family, answer):
    expectation = next(c["expectation"] for c in families() if c["family"] == family)
    attempt = {"status": "completed", "acknowledged": True, "tasks_unchanged": True,
               "domain_unchanged": True, "answer": answer, "cards": [], "snapshots": {}, "proposals": [],
               "calls": [{"output": {"tool_calls": [{"name": "read_activity"}]}}]}
    return grade(expectation, attempt)["status"]


@pytest.mark.parametrize("answer", [
    "This week, you've spent **9 hours** on Work: **6 hours on Coding** and **3 hours on Admin**.",
    "This week you've logged **9 hours of Work** in total. Of that, **6 hours were Coding** and **3 hours were Admin**.",
    "This week you've tracked **9h of Work** in total. Of that, **6h was Coding** and **3h was Admin**.",
    "Work: 9 hours, Coding: 6 hours, Admin: 3 hours.",
    "Work: nine hours; Coding: 360 minutes; Admin: 180 minutes.",
    "9 hours of Work in total. Of that, 6 hours were Coding and 3 hours were Admin — a 2:1 split in favor of coding.",
    "9h of Work in total. Of that, 6h was Coding and 3h was Admin — so two-thirds coding, one-third admin.",
    "Work: 9h. Coding: 6h. Admin: 3h. The plan is 15h of Work (10h Coding, 5h Admin).",
])
def test_inline_duration_associations(answer):
    assert score("hierarchy-totals", answer) == "pass"


@pytest.mark.parametrize("answer", [
    "Work: 9 hours, Coding: 3 hours, Admin: 6 hours.",
    "9 hours on Work: 3 hours on Coding and 6 hours on Admin.",
    "Work: 18 hours, Coding: 6 hours, Admin: 3 hours.",
])
def test_incorrect_inline_values_fail_even_with_correct_cards(answer):
    expectation = next(c["expectation"] for c in families() if c["family"] == "hierarchy-totals")
    cards = [{"types": [{"task_type": f["label"], "actual_seconds": f["seconds"]}
                         for f in expectation["durations"]]}]
    assert factual_checks(expectation, answer, cards)[0]


@pytest.mark.parametrize("answer", [
    "Work, Coding and Admin: 9 hours, 6 hours, 3 hours.",
    "Work: maybe 9 hours; Coding: perhaps 6 hours; Admin: 3 hours?",
    "Work was not 9 hours. Coding was not 6 hours. Admin was not 3 hours.",
    "I couldn't retrieve the activity data.",
])
def test_ambiguous_or_missing_durations_do_not_pass(answer):
    assert score("hierarchy-totals", answer) != "pass"


@pytest.mark.parametrize("answer", [
    "Yes. There’s no saved block from 16:00 to 16:30; the next one starts at 16:30.",
    "Yes. Your plan is clear from 4:00 to 4:30 pm today; reading ends at 4:00 and the quarterly report starts at 4:30.",
    "No. The reading block ends at 4:00, and the next planned block starts at 4:30.",
])
def test_gap_paraphrases(answer):
    assert score("plan-free-gap", answer) == "pass"


@pytest.mark.parametrize("answer", [
    "You're not free from 16:00 to 16:30.",
    "Your plan isn't clear at that time.",
    "Reading ends at 4:15 and the next planned block starts at 4:30.",
    "Reading ends at 4:00 and the next planned block starts at 4:15.",
    "I couldn't retrieve the schedule.",
])
def test_gap_negative_controls(answer):
    assert score("plan-free-gap", answer) != "pass"


@pytest.mark.parametrize("answer", [
    "It's now planned for 6:00 to 7:00pm.",
    "Quarterly report is scheduled for 6:00–7:00 pm today.",
    "The report is now scheduled for six to seven this evening.",
    "Quarterly report starts at 18:00.",
])
def test_start_time_paraphrases(answer):
    assert score("stale-plan-refresh", answer) == "pass"


@pytest.mark.parametrize("answer", [
    "Quarterly report starts at 16:00.",
    "It runs from 5:00 to 7:00pm.",
    "It is scheduled from 6:00 to 7:00am.",
    "It starts at six in the morning.",
    "It still starts at 4:30.",
])
def test_wrong_start_time_does_not_pass(answer):
    assert score("stale-plan-refresh", answer) != "pass"


def test_number_only_answer_ignores_outer_whitespace_not_extra_content():
    assert score("arithmetic-only", "\n\n15\n") == "pass"
    assert score("arithmetic-only", "15 or maybe 16") != "pass"
    assert score("arithmetic-only", "\n16\n") != "pass"
