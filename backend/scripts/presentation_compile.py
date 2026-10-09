"""Optimization scoring around the shared production DSPy policy."""
from app.services.assistant_policy import (  # noqa: F401
    NativeAdapter,
    NativeProgram,
    Presentation,
    export_policy,
    load_policy,
    policy_prompt,
    render_policy,
    save_policy,
)


def metric(example, prediction, trace=None):
    from scripts.presentation_eval import grade

    return grade(example.expectation, prediction.attempt)["status"] == "pass"
