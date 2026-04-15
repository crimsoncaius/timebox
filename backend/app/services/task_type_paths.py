from __future__ import annotations


def canonicalize_task_type_path(name: str) -> str:
    raw = name.strip()
    if not raw:
        raise ValueError("Task type path is required")

    raw_segments = raw.split("/")
    if any(segment.strip() == "" for segment in raw_segments):
        raise ValueError("Task type path is invalid")

    return "/".join(segment.strip().lower() for segment in raw_segments)


def path_prefixes(path: str) -> list[str]:
    segments = path.split("/")
    return ["/".join(segments[:idx]) for idx in range(1, len(segments) + 1)]


def is_descendant_path(ancestor_path: str, candidate_path: str) -> bool:
    return candidate_path.startswith(f"{ancestor_path}/")
