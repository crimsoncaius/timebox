"""No paid calls: native parser/lifecycle and DSPy bootstrap/export feasibility."""
# ruff: noqa: E402 -- MIPRO tests also require the eval extra.
import asyncio
import json
from contextlib import aclosing
from copy import deepcopy
from pathlib import Path

import pytest

dspy = pytest.importorskip("dspy")

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from app.core.config import get_settings
from app.services import assistant_agent
from scripts.presentation_compile import NativeProgram, load_policy, metric, policy_prompt, save_policy
from scripts.presentation_eval import grade, run_native
from tests.test_assistant_loop import call
from tests.test_assistant_tracking import ScriptedModel, scripted

CASES = json.loads((Path(__file__).parents[1] / "studies/presentation/pilot.json").read_text())
ORIGINAL = json.loads((Path(__file__).parents[1] / "studies/presentation/original_failures.json").read_text())["cases"]


def final(text):
    return AIMessage(content=text, response_metadata={"finish_reason": "stop"})


class CardModel(ScriptedModel):
    async def _astream(self, messages, **kwargs):
        if not self.script:
            read = json.loads(next(m.content for m in reversed(messages) if isinstance(m, ToolMessage)))
            self.script.append(final(json.dumps({"presentation": "snapshot", "snapshot_id": read["snapshot_id"]}) + "\n"))
        async for chunk in super()._astream(messages, **kwargs):
            yield chunk


def text_runner(question, prompt):
    return run_native(question, prompt, scripted(final('{"presentation":"none"}\nHello.')))


def test_bootstrap_traces_clone_and_export_round_trip(tmp_path):
    program = NativeProgram(text_runner)
    example = dspy.Example(context=CASES[0]["question"], expectation=CASES[0]["expectation"]).with_inputs("context")
    compiled = dspy.BootstrapFewShot(metric=metric, max_bootstrapped_demos=1, max_labeled_demos=0).compile(
        program, trainset=[example])
    assert len(compiled.presentation.demos) == 1
    assert program.presentation.demos == []
    path = tmp_path / "policy.json"
    save_policy(compiled, path)
    policy = json.loads(path.read_text())
    assert set(policy["presentation"]["demos"][0]) == {"context", "raw_answer"}
    assert "expectation" not in path.read_text() and "acknowledged" not in path.read_text()
    traced = compiled(context=example.context).attempt
    loaded = load_policy(path)
    assert policy_prompt(loaded) == policy_prompt(compiled)
    exported = text_runner(example.context, loaded)
    # LangGraph generates internal message IDs; they are not sent to the provider.
    def wire(call):
        return [{k: v for k, v in m.items() if k != "id"} for m in call["messages"]]
    assert wire(traced["calls"][0]) == wire(exported["calls"][0])
    assert metric(example, compiled(context=example.context))


def test_read_card_native_lifecycle_and_unknown_example_id():
    case = CASES[1]
    attempt = run_native(case["question"], "candidate marker", CardModel(script=[call()]))
    assert grade(case["expectation"], attempt)["status"] == "pass", attempt
    assert all("candidate marker" in c["messages"][0]["content"] for c in attempt["calls"])
    bad = run_native(case["question"], "Example snapshot ID: fake", scripted(call(), final(
        '{"presentation":"snapshot","snapshot_id":"fake"}\n')))
    assert bad["status"] == "interrupted" and bad["errors"]
    assert grade(case["expectation"], bad)["status"] == "fail"


def test_parser_failure_is_zero_credit_not_repaired():
    case = CASES[0]
    attempt = run_native(case["question"], "candidate", scripted(final('Hello.\n{"presentation":"none"}')))
    assert attempt["status"] == "interrupted" and not attempt["acknowledged"]
    assert attempt["errors"] and grade(case["expectation"], attempt)["status"] == "fail"


def test_read_then_proposal_uses_loaded_candidate_in_forced_final(tmp_path):
    case = CASES[2]
    model = scripted(call("read_tasks", {"mode": "search", "query": "Weekly report"}),
                     call("propose_task_changes", {"operations": [{"op": "patch_task", "target": {"id": 3},
                         "set": {"title": "Send weekly update"}}]}, 1),
                     final('{"presentation":"none"}\nReview the proposed rename.'))
    program = NativeProgram(instructions="candidate marker")
    program.presentation.demos = [{"context": "Example question", "raw_answer": "Example answer"}]
    path = tmp_path / "policy.json"
    save_policy(program, path)
    attempt = run_native(case["question"], load_policy(path), model)
    assert grade(case["expectation"], attempt)["status"] == "pass", attempt
    assert len(attempt["calls"]) == 3
    assert all("candidate marker" in c["messages"][0]["content"] for c in attempt["calls"])
    assert all("Example answer" in c["messages"][0]["content"] for c in attempt["calls"])
    assert "Read rounds remaining" not in attempt["calls"][-1]["messages"][0]["content"]


def test_scorer_negative_controls():
    attempt = text_runner(CASES[0]["question"], "candidate")
    assert grade(CASES[0]["expectation"], attempt)["status"] == "pass"
    for changes, status in [({"answer": "Goodbye."}, "unresolved"),
                            ({"answer": '{"presentation":"none"} Hello.'}, "fail"),
                            ({"tasks_unchanged": False}, "fail"),
                            ({"cards": [{"snapshot_id": "invented"}]}, "fail"),
                            ({"proposals": [{"status": "invalid", "completed": False, "receipt": None}]}, "fail")]:
        wrong = deepcopy(attempt)
        wrong.update(changes)
        assert grade(CASES[0]["expectation"], wrong)["status"] == status


def test_mipro_controlled_proposer_and_native_search():
    from dspy.utils.dummies import DummyLM

    proposer = DummyLM([{"proposed_instruction": "Candidate: start final response with a presentation JSON line."}] * 10)
    example = dspy.Example(context=CASES[0]["question"], expectation=CASES[0]["expectation"]).with_inputs("context")
    optimizer = dspy.MIPROv2(metric=metric, prompt_model=proposer, task_model=dspy.BaseLM("native-timebox"),
                            auto=None, num_candidates=2, num_threads=1,
                            max_bootstrapped_demos=1, max_labeled_demos=0)
    seen = []
    def recording_runner(question, prompt):
        seen.append(prompt)
        return text_runner(question, prompt)
    result = optimizer.compile(NativeProgram(recording_runner), trainset=[example], valset=[example],
                               num_trials=2, minibatch=False, program_aware_proposer=False,
                               data_aware_proposer=False, tip_aware_proposer=False, fewshot_aware_proposer=False)
    assert metric(example, result(context=example.context))
    assert any(p.startswith("start final response") for p in seen)


@pytest.mark.parametrize("case", ORIGINAL, ids=lambda case: case["id"])
def test_original_captured_plain_text_is_accepted_but_late_selectors_fail(case):
    from app.services.assistant_presentation import PresentationParser

    parser = PresentationParser({})
    if case["id"] == "17-dismiss-rename":
        parser.allow_empty = True
    if '"presentation"' in case["raw_answer"]:
        with pytest.raises(ValueError):
            parser.feed(case["raw_answer"])
            parser.finish(successful_terminal=True)
    else:
        events = parser.feed(case["raw_answer"]) + parser.finish(successful_terminal=True)
        assert "".join(data["text"] for _, data in events) == case["raw_answer"]


def test_failed_final_never_activates_proposal():
    malformed = '{"presentation":"invalid"}\n' + ORIGINAL[1]["raw_answer"]
    model = scripted(call("read_tasks", {"mode": "search", "query": "Weekly report"}),
                     call("propose_task_changes", {"operations": CASES[2]["expectation"]["operations"]}, 1),
                     final(malformed))
    attempt = run_native(CASES[2]["question"], assistant_agent.PRESENTATION_PROMPT, model)
    assert attempt["status"] == "interrupted" and not attempt["acknowledged"]
    assert attempt["raw_answer"] == malformed
    assert len(attempt["proposals"]) == 1
    assert not attempt["proposals"][0]["completed"]
    assert attempt["proposals"][0]["receipt"] is None
    assert attempt["tasks_unchanged"]
    assert grade(CASES[2]["expectation"], attempt)["status"] == "fail"


def test_plain_text_final_completes_proposal_without_applying_it():
    model = scripted(call("read_tasks", {"mode": "search", "query": "Weekly report"}),
                     call("propose_task_changes", {"operations": CASES[2]["expectation"]["operations"]}, 1),
                     final(ORIGINAL[1]["raw_answer"]))
    attempt = run_native(CASES[2]["question"], assistant_agent.PRESENTATION_PROMPT, model)
    assert grade(CASES[2]["expectation"], attempt)["status"] == "pass"
    assert attempt["proposals"][0]["completed"]
    assert attempt["proposals"][0]["status"] == "pending"
    assert attempt["proposals"][0]["receipt"] is None
    assert attempt["domain_unchanged"]


def test_uncertain_regression_semantics_cannot_earn_credit():
    attempt = text_runner(CASES[0]["question"], "candidate")
    attempt["answer"] = "Work 9 hours. Coding 3 hours. Admin 6 hours."
    result = grade(CASES[3]["expectation"], attempt)
    assert result["status"] == "fail"
    assert len(result["failures"]) >= 2


def test_runtime_loads_configured_dspy_state(tmp_path, monkeypatch):
    program = NativeProgram(instructions="Reviewed policy marker")
    path = tmp_path / "policy.json"
    save_policy(program, path)
    monkeypatch.setattr(get_settings(), "assistant_policy_path", str(path))
    seen = []
    original = assistant_agent.build_agent

    def build(*args, **kwargs):
        seen.append(kwargs["presentation_prompt"])
        return original(*args, **kwargs)

    monkeypatch.setattr(assistant_agent, "build_agent", build)

    async def run():
        return [event async for event in assistant_agent.agent_events(
            [HumanMessage("Hello")], model=scripted(final("Hello.")))]

    assert asyncio.run(run()) == [("text_delta", {"text": "Hello."})]
    assert seen == ["Reviewed policy marker"]


def test_policy_stream_cancellation_closes_native_execution():
    async def run():
        closed = asyncio.Event()

        async def execute(prompt):
            try:
                yield "text_delta", {"text": "Hello"}
                await asyncio.Event().wait()
            finally:
                closed.set()

        async with aclosing(NativeProgram().stream("Hello", execute)) as stream:
            assert await anext(stream) == ("text_delta", {"text": "Hello"})
        assert closed.is_set()

    asyncio.run(run())


def test_policy_stream_propagates_native_failure():
    async def run():
        async def execute(prompt):
            yield "tool_started", {}
            raise ValueError("native failure")

        with pytest.raises(ValueError, match="native failure"):
            async with aclosing(NativeProgram().stream("Hello", execute)) as stream:
                assert await anext(stream) == ("tool_started", {})
                await anext(stream)

    asyncio.run(run())
