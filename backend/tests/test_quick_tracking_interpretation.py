import asyncio
import datetime as dt

import pytest

from app.services import quick_tracking_interpretation as lab

NOW = dt.datetime(2026, 10, 24, 8, 0, tzinfo=dt.UTC)
def state():
    return {"timezone": "Asia/Singapore", "task_types": ["Work", "Meals"], "blocks": []}


@pytest.mark.parametrize("action", [None, "stop"])
def test_extraction_requires_action_and_failed_model_never_defaults_to_now(monkeypatch, action):
    calls = []
    async def choice(*args):
        return lab.Decision(action=action), {"model_calls": 1}, {"choice": "stop" if action else "no_change"}
    async def timing(*args):
        return "extract", {"model_calls": 1}, {}
    async def extraction(*args):
        calls.append("extract")
        raise TimeoutError()
    monkeypatch.setattr(lab, "interpret_choice", choice)
    monkeypatch.setattr(lab, "interpret_timing", timing)
    monkeypatch.setattr(lab, "extract_time", extraction)
    if action:
        from fastapi import HTTPException
        with pytest.raises(HTTPException):
            asyncio.run(lab.interpret_workflow(state(), "request", NOW))
        assert calls == ["extract"]
    else:
        decision, _, validation = asyncio.run(lab.interpret_workflow(state(), "request", NOW))
        assert decision.action is None and "effective_at" not in validation and not calls

@pytest.mark.parametrize(
    "seconds,accepted", [(0, True), (300, True), (8 * 3600, True), (-1, False), (86400, False)]
)
def test_today_limits_use_reporting_zone(seconds, accepted):
    received = dt.datetime(2026, 10, 24, 0, 5, tzinfo=dt.UTC)  # 08:05 Singapore
    value = lab.ExtractedTime(kind="relative", seconds_ago=float(seconds), local_datetime=None)
    if accepted:
        assert lab.resolve_time(value, received, "Asia/Singapore") == received - dt.timedelta(seconds=seconds)
    else:
        with pytest.raises(ValueError):
            lab.resolve_time(value, received, "Asia/Singapore")


def test_midnight_future_clock_and_dst_are_rejected():
    received = dt.datetime(2026, 10, 23, 16, 5, tzinfo=dt.UTC)  # local 00:05
    with pytest.raises(ValueError, match="Only today"):
        lab.resolve_time(
            lab.ExtractedTime(kind="relative", seconds_ago=600.0, local_datetime=None),
            received,
            "Asia/Singapore",
        )
    for clock in ["2026-10-24T00:06:00", "2026-10-23T23:59:00"]:
        with pytest.raises(ValueError):
            lab.resolve_time(
                lab.ExtractedTime(kind="local", seconds_ago=None, local_datetime=clock),
                received,
                "Asia/Singapore",
            )
    for clock, instant in [
        ("2026-11-01T01:30:00", dt.datetime(2026, 11, 1, 12, tzinfo=dt.UTC)),
        ("2026-03-08T02:30:00", dt.datetime(2026, 3, 8, 12, tzinfo=dt.UTC)),
    ]:
        with pytest.raises(ValueError, match="ambiguous or does not exist"):
            lab.resolve_time(
                lab.ExtractedTime(kind="local", seconds_ago=None, local_datetime=clock),
                instant,
                "America/New_York",
            )



@pytest.mark.parametrize(
    "route,expected_calls", [("now", 2), ("extract", 3), ("future", 2), ("before_today", 2)]
)
def test_parallel_workflow_routes_and_costs(route, expected_calls, monkeypatch):
    async def scenario():
        entered = set()
        both = asyncio.Event()

        async def activity(s, message, now):
            entered.add("activity")
            if len(entered) == 2:
                both.set()
            await asyncio.wait_for(both.wait(), 0.5)
            return (
                lab.Decision(action="track", task_type="Meals"),
                {"model_calls": 1, "cost_usd": 0.01},
                {"choice": "track_1", "attempts": []},
            )

        async def timing(s, message, now):
            entered.add("timing")
            if len(entered) == 2:
                both.set()
            await asyncio.wait_for(both.wait(), 0.5)
            return route, {"model_calls": 1, "cost_usd": 0.02}, {"attempts": []}

        async def extract(s, message, now):
            assert entered == {"activity", "timing"}
            return (
                lab.ExtractedTime(kind="relative", seconds_ago=300.0, local_datetime=None),
                {"model_calls": 1, "cost_usd": 0.03},
                {"attempts": []},
            )

        monkeypatch.setattr(lab, "interpret_choice", activity)
        monkeypatch.setattr(lab, "interpret_timing", timing)
        monkeypatch.setattr(lab, "extract_time", extract)
        decision, usage, validation = await lab.interpret_workflow(state(), "request", NOW)
        assert usage["model_calls"] == expected_calls
        assert usage["cost_usd"] == pytest.approx(0.06 if expected_calls == 3 else 0.03)
        if route in ("future", "before_today"):
            assert decision.action is None and "effective_at" not in validation
        else:
            assert validation["effective_at"] == lab.stamp(
                NOW - dt.timedelta(seconds=300 if route == "extract" else 0)
            )

    asyncio.run(scenario())


