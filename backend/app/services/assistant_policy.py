"""Shared DSPy policy and native streaming adapter for application and optimization."""
import asyncio
from contextlib import suppress
from pathlib import Path

import dspy


def export_policy(signature, examples):
    demos = []
    for demo in examples:
        attempt = demo.get("attempt", {})
        demos.append({"context": attempt.get("demo_context", demo["context"]),
                      "raw_answer": demo["raw_answer"]})
    return {"instructions": signature.instructions, "demos": demos}


def render_policy(policy):
    text = policy["instructions"]
    if policy["demos"]:
        text += "\nPresentation examples only; these are not conversation history or eligible snapshots."
        for demo in policy["demos"]:
            text += "\nExample context:\n" + demo["context"] + "\nExample final response:\n" + demo["raw_answer"]
    return text


def save_policy(program, path):
    saved = program.deepcopy()
    saved.presentation.demos = export_policy(program.presentation.signature, program.presentation.demos)["demos"]
    saved.save(str(path), save_program=False)


def load_policy(path):
    if Path(path).suffix != ".json":
        raise ValueError("Policy must be a DSPy JSON state file")
    program = NativeProgram()
    program.load(str(path), allow_pickle=False)
    return program


def policy_prompt(policy):
    if isinstance(policy, str):
        return policy
    return render_policy(export_policy(policy.presentation.signature, policy.presentation.demos))


class Presentation(dspy.Signature):
    context: str = dspy.InputField(desc="User question; the native runner supplies the isolated account and real tools.")
    raw_answer: str = dspy.OutputField(desc="Unmodified final model response, including any presentation selector.")
    attempt: dict = dspy.OutputField(desc="Native interaction evidence for scoring; never presentation example content.")


class NativeAdapter(dspy.Adapter):
    def __init__(self, runner):
        super().__init__()
        self.runner = runner

    def __call__(self, lm, lm_kwargs, signature, demos, inputs):
        policy = export_policy(signature, demos)
        attempt = self.runner(inputs["context"], render_policy(policy))
        return [{"raw_answer": attempt["raw_answer"], "attempt": attempt}]

    async def acall(self, lm, lm_kwargs, signature, demos, inputs):
        attempt = await self.runner(inputs["context"], render_policy(export_policy(signature, demos)))
        return [{"raw_answer": attempt["raw_answer"], "attempt": attempt}]


class NativeProgram(dspy.Module):
    def __init__(self, runner=None, instructions=None):
        super().__init__()
        from app.services.assistant_agent import PRESENTATION_PROMPT
        self.presentation = dspy.Predict(Presentation.with_instructions(
            PRESENTATION_PROMPT if instructions is None else instructions))
        self.runner = runner

    def forward(self, context):
        # Local context leaves MIPRO's instruction proposer on its own adapter/LM.
        # This marker LM is never called: NativeAdapter invokes the real graph.
        with dspy.context(adapter=NativeAdapter(self.runner), lm=dspy.BaseLM("native-timebox")):
            return self.presentation(context=context)

    async def stream(self, context, execute):
        """Execute the predictor while relaying native events and cancellation."""
        queue = asyncio.Queue()

        async def runner(context, prompt):
            raw = []
            async for event in execute(prompt):
                if event[0] == "text_delta":
                    raw.append(event[1]["text"])
                queue.put_nowait(event)
            return {"raw_answer": "".join(raw), "attempt": {}}

        async def predict():
            try:
                with dspy.context(adapter=NativeAdapter(runner), lm=dspy.BaseLM("native-timebox")):
                    return await self.presentation.acall(context=context)
            finally:
                queue.put_nowait(None)

        task = asyncio.create_task(predict())
        try:
            while (event := await queue.get()) is not None:
                yield event
            await task
        finally:
            if not task.done():
                task.cancel()
            with suppress(asyncio.CancelledError):
                await task


