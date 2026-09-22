"""Server-owned plan snapshots and the bounded model presentation grammar."""
from __future__ import annotations

import datetime as dt
import json
from uuid import uuid4
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class PlannedRow(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    start_minute: int = Field(ge=0, lt=1440)
    end_minute: int = Field(gt=0, le=1440)
    name: str | None
    task_type: str
    task_id: int | None
    task_title: str | None

    @model_validator(mode="after")
    def ordered(self):
        if self.end_minute <= self.start_minute:
            raise ValueError("Invalid block times")
        return self


class PlanSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    schema_version: int = Field(default=1, ge=1, le=1)
    snapshot_id: str
    date: str
    reporting_timezone: str
    read_at: str
    planned_blocks: list[PlannedRow]

    @field_validator("date")
    @classmethod
    def date_valid(cls, value):
        dt.date.fromisoformat(value)
        return value

    @field_validator("reporting_timezone")
    @classmethod
    def zone_valid(cls, value):
        ZoneInfo(value)
        return value

    @field_validator("read_at")
    @classmethod
    def instant_valid(cls, value):
        if dt.datetime.fromisoformat(value.replace("Z", "+00:00")).utcoffset() != dt.timedelta(0):
            raise ValueError("Read time must be UTC")
        return value


def snapshot(plan: dict) -> dict:
    return PlanSnapshot.model_validate({**plan, "snapshot_id": str(uuid4()),
        "read_at": dt.datetime.now(dt.timezone.utc).isoformat(), "schema_version": 1}).model_dump()


def text_schedule(plan: dict) -> str:
    clock = lambda minute: f"{minute // 60:02}:{minute % 60:02}"
    read = dt.datetime.fromisoformat(plan["read_at"].replace("Z", "+00:00")).astimezone(ZoneInfo(plan["reporting_timezone"]))
    lines = [f"Plan for {plan['date']} · Read at {read:%H:%M} · {plan['reporting_timezone']}"]
    for row in plan["planned_blocks"]:
        title = row["name"] or row["task_title"] or row["task_type"]
        lines.append(f"{clock(row['start_minute'])}–{clock(row['end_minute'])} · {title} · {row['task_type']}")
    if not plan["planned_blocks"]:
        lines.append("No Planned Blocks for this date.")
    return "\n".join(lines) + "\n\n"


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate presentation field")
        result[key] = value
    return result


class PresentationParser:
    def __init__(self, snapshots: dict[str, dict]):
        self.snapshots = snapshots
        self.buffer = ""
        self.selected = False

    def feed(self, text: str) -> list[tuple[str, dict]]:
        if self.selected:
            return [("text_delta", {"text": text})] if text else []
        self.buffer += text
        line, separator, rest = self.buffer.partition("\n")
        header = line.removesuffix("\r")
        if len(header.encode("utf-8")) > 512:
            raise ValueError("Presentation header too long")
        if not separator:
            return []
        if not header.startswith("{"):
            raise ValueError("Missing presentation header")
        value = json.loads(header, object_pairs_hook=unique_object)
        events = []
        if value == {"presentation": "none"}:
            pass
        elif isinstance(value, dict) and set(value) == {"presentation", "snapshot_id"} and value["presentation"] == "snapshot":
            key = value["snapshot_id"]
            if not isinstance(key, str) or key not in self.snapshots:
                raise ValueError("Unknown plan snapshot")
            events.append(("plan_card", PlanSnapshot.model_validate(self.snapshots[key]).model_dump()))
        else:
            raise ValueError("Invalid presentation selection")
        self.selected = True
        self.buffer = ""
        if rest:
            events.append(("text_delta", {"text": rest}))
        return events

    def finish(self, *, successful_terminal: bool = False) -> list[tuple[str, dict]]:
        if self.selected:
            return []
        # Only a confirmed normal model terminal may delimit a card-only selector.
        # EOF, cancellation, truncation, or a closing brace during streaming cannot.
        if successful_terminal:
            value = json.loads(self.buffer, object_pairs_hook=unique_object)
            if isinstance(value, dict) and value.get("presentation") == "snapshot":
                return self.feed("\n")
        raise ValueError("Incomplete presentation header")
