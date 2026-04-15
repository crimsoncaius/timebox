from __future__ import annotations

from app.services.task_type_paths import canonicalize_task_type_path, is_descendant_path, path_prefixes


def test_canonicalize_task_type_path_trims_whitespace_and_lowercases():
    assert canonicalize_task_type_path(" Coding / AI / Agents ") == "coding/ai/agents"


def test_canonicalize_task_type_path_rejects_empty_segments():
    try:
        canonicalize_task_type_path("coding//ai")
    except ValueError as exc:
        assert str(exc) == "Task type path is invalid"
    else:
        raise AssertionError("Expected ValueError")


def test_path_prefixes_returns_each_materialized_ancestor():
    assert path_prefixes("coding/ai/agents") == [
        "coding",
        "coding/ai",
        "coding/ai/agents",
    ]


def test_is_descendant_path_uses_slash_boundaries():
    assert is_descendant_path("coding", "coding/ai")
    assert is_descendant_path("coding/ai", "coding/ai/agents")
    assert not is_descendant_path("coding", "codingstuff")
