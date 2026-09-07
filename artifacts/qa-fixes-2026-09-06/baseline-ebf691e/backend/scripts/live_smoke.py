#!/usr/bin/env python3
"""Run an explicitly opt-in, self-cleaning smoke test against a Timebox API."""

from __future__ import annotations

import json
import os
import sys
import uuid
from datetime import date
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen


CONFIRMATION = "DELETE_TEST_DATA"


def request(base_url: str, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
    body = json.dumps(payload).encode() if payload is not None else None
    req = Request(
        f"{base_url}{path}",
        data=body,
        method=method,
        headers={"Content-Type": "application/json"} if body is not None else {},
    )
    with urlopen(req, timeout=20) as response:  # noqa: S310 -- URL is explicitly supplied by the operator.
        raw = response.read()
        return json.loads(raw) if raw else None


def find_available_start(day: dict[str, Any]) -> int:
    planned = [block for block in day.get("time_blocks", []) if block["lane"] == "planned"]
    for start in range(0, 24 * 60, 30):
        end = start + 30
        if all(end <= block["start_minute"] or start >= block["end_minute"] for block in planned):
            return start
    raise RuntimeError("No free 30-minute planned slot is available on the requested smoke-test date.")


def main() -> int:
    if os.environ.get("TIMEBOX_LIVE_SMOKE_CONFIRM") != CONFIRMATION:
        print(
            "Refusing to write test data. Set TIMEBOX_LIVE_SMOKE_CONFIRM=DELETE_TEST_DATA to continue.",
            file=sys.stderr,
        )
        return 2

    base_url = os.environ.get("TIMEBOX_API_BASE_URL", "").rstrip("/")
    if not base_url.startswith(("https://", "http://")):
        print("Set TIMEBOX_API_BASE_URL to the target API origin, for example https://api.example.com.", file=sys.stderr)
        return 2

    smoke_date = os.environ.get("TIMEBOX_LIVE_SMOKE_DATE", "2099-12-31")
    try:
        date.fromisoformat(smoke_date)
    except ValueError:
        print("TIMEBOX_LIVE_SMOKE_DATE must be ISO-8601 (YYYY-MM-DD).", file=sys.stderr)
        return 2

    suffix = uuid.uuid4().hex[:10]
    task_type_id: int | None = None
    block_id: int | None = None
    passed = False

    try:
        task_type = request(base_url, "POST", "/task-types", {"name": f"qa/live-smoke-{suffix}"})
        task_type_id = task_type["id"]
        updated_type = request(base_url, "PATCH", f"/task-types/{task_type_id}", {"name": f"qa/live-smoke-updated-{suffix}"})
        if updated_type["id"] != task_type_id:
            raise RuntimeError("Task type update returned the wrong resource.")

        day = request(base_url, "GET", f"/days/{smoke_date}")
        start_minute = find_available_start(day)
        created_day = request(
            base_url,
            "POST",
            f"/days/{smoke_date}/blocks",
            {
                "lane": "planned",
                "task_type_id": task_type_id,
                "note": "qa live smoke",
                "start_minute": start_minute,
                "end_minute": start_minute + 30,
            },
        )
        block = next(
            block
            for block in created_day["time_blocks"]
            if block["task_type_id"] == task_type_id and block["note"] == "qa live smoke"
        )
        block_id = block["id"]

        updated_day = request(
            base_url,
            "PATCH",
            f"/days/{smoke_date}/blocks/{block_id}",
            {"note": "qa live smoke updated"},
        )
        if not any(block["id"] == block_id and block["note"] == "qa live smoke updated" for block in updated_day["time_blocks"]):
            raise RuntimeError("Time block update was not persisted.")

        request(base_url, "DELETE", f"/days/{smoke_date}/blocks/{block_id}")
        block_id = None
        request(base_url, "DELETE", f"/task-types/{task_type_id}")
        task_type_id = None
        passed = True
    except HTTPError as error:
        detail = error.read().decode(errors="replace")
        print(f"HTTP {error.code} during smoke test: {detail}", file=sys.stderr)
    except Exception as error:  # noqa: BLE001 -- cleanup still must run for every failure.
        print(f"Smoke test failed: {error}", file=sys.stderr)
    finally:
        cleanup_errors: list[str] = []
        if block_id is not None:
            try:
                request(base_url, "DELETE", f"/days/{smoke_date}/blocks/{block_id}")
            except Exception as error:  # noqa: BLE001
                cleanup_errors.append(f"block {block_id}: {error}")
        if task_type_id is not None:
            try:
                request(base_url, "DELETE", f"/task-types/{task_type_id}")
            except Exception as error:  # noqa: BLE001
                cleanup_errors.append(f"task type {task_type_id}: {error}")

        if cleanup_errors:
            print(f"Cleanup failed for {', '.join(cleanup_errors)}", file=sys.stderr)
            passed = False

    if passed:
        print("Live smoke test passed; all test data was deleted.")
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
