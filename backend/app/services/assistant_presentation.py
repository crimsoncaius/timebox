"""Server-owned plan snapshots and the bounded model presentation grammar."""
from __future__ import annotations

import datetime as dt
import json
import re
from typing import Literal
from uuid import uuid4
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.services.assistant_limits import MAX_CARDS


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


class BlockRow(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    id: int
    lane: Literal["planned", "actual"]
    start_at: str
    end_at: str
    running: bool
    duration_minutes: int = Field(ge=0)
    minutes_in_date: int = Field(ge=0)
    name: str | None
    task_type: str
    task_id: int | None
    task_title: str | None
    planned_block_id: int | None
    supporting_note: str | None = Field(default=None, max_length=2000)
    task_description: str | None = Field(default=None, max_length=2000)


class BlockSnapshot(PlanSnapshot):
    schema_version: Literal[2] = 2
    planned_blocks: list[PlannedRow] = Field(default_factory=list, exclude=True)
    group_by: Literal["blocks"]
    lane: Literal["planned", "actual", "both"]
    task_type: str | None
    blocks: list[BlockRow]
    recurring_not_materialized: bool
    actual_unavailable_reason: str | None


class TaskTypeRow(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    task_type: str
    period: str
    planned_seconds: float | None = Field(default=None, ge=0)
    actual_seconds: float | None = Field(default=None, ge=0)
    difference_seconds: float | None = None


class TaskTypeSnapshot(PlanSnapshot):
    schema_version: Literal[3] = 3
    planned_blocks: list[PlannedRow] = Field(default_factory=list, exclude=True)
    group_by: Literal["task_type"]
    lane: Literal["planned", "actual", "both"]
    task_type: str | None
    start: str
    end: str
    detail: Literal["total", "day", "week"]
    weekdays: list[int] | None
    types: list[TaskTypeRow]
    recurring_not_materialized: bool


def validate_snapshot(value: dict) -> dict:
    if value.get("schema_version") == 4:
        from app.services.assistant_tasks import validate
        return validate(value)
    model = {1: PlanSnapshot, 2: BlockSnapshot, 3: TaskTypeSnapshot}.get(value.get("schema_version", 1))
    if model is None:
        raise ValueError("Unknown snapshot schema version")
    return model.model_validate(value).model_dump(exclude_none=False)


def snapshot(plan: dict) -> dict:
    return validate_snapshot({"read_at": dt.datetime.now(dt.UTC).isoformat(),
        "schema_version": 1, **plan, "snapshot_id": str(uuid4())})


def text_schedule(plan: dict) -> str:
    if plan.get("schema_version") == 3:
        lines = [f"Time by Task Type for {plan['start']}–{plan['end']} · As of {plan['read_at']} · {plan['reporting_timezone']}"]
        for row in plan["types"]:
            values = [f"{label}: {row[key]/60:g} min" for key, label in
                      (("planned_seconds", "Planned"), ("actual_seconds", "Actual"), ("difference_seconds", "Actual minus planned"))
                      if row.get(key) is not None]
            lines.append(f"{row['task_type']} · {row['period']} · " + " · ".join(values))
        if not plan["types"]:
            lines.append("No stored time for this selection.")
        if plan["recurring_not_materialized"]:
            lines.append("Future recurring work is not materialized; actual time stops at the read time.")
        return "\n".join(lines) + "\n\n"
    if plan.get("schema_version") == 2:
        lines = [f"Blocks for {plan['date']} · As of {plan['read_at']} · {plan['reporting_timezone']}"]
        for row in plan["blocks"]:
            title = row["name"] or row["task_title"] or row["task_type"]
            lines.append(f"{row['lane'].title()} · {row['start_at']}–{row['end_at']} · {title} · {row['task_type']} · {row['duration_minutes']} min ({row['minutes_in_date']} min in date)")
        if not plan["blocks"]:
            lines.append("No stored Blocks for this selection.")
        if plan["recurring_not_materialized"]:
            lines.append("Recurring work is not materialized for this future date.")
        if plan["actual_unavailable_reason"]:
            lines.append(plan["actual_unavailable_reason"])
        return "\n".join(lines) + "\n\n"
    def clock(minute):
        return f"{minute // 60:02}:{minute % 60:02}"
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


GLUED_NONE = re.compile(r'\s*\{\s*"presentation"\s*:\s*"none"\s*\}(?=\S)')
NONE_HEADER = re.compile(r'\s*\{\s*"presentation"\s*:\s*"none"\s*\}[ \t]*\r?\n?')


class PresentationParser:
    def __init__(self, snapshots: dict[str, dict]):
        self.snapshots = snapshots
        self.buffer = ""
        self.selected = False
        self.text_only = False
        self.allow_empty = False

    def expect_text_only(self):
        """After a Tracking Proposal the answer is plain text; a leading none-selector is tolerated and removed."""
        self.text_only = True

    def feed(self, text: str) -> list[tuple[str, dict]]:
        if self.selected:
            return [("text_delta", {"text": text})] if text else []
        self.buffer += text
        if self.text_only:
            # Wait while the text could still be the none-selector; otherwise strip it if present.
            if '{"presentation":"none"}'.startswith(re.sub(r"\s", "", self.buffer)) and "\n" not in self.buffer.lstrip():
                return []
            match = NONE_HEADER.match(self.buffer)
            rest = self.buffer[match.end():] if match else self.buffer
            self.selected = True
            self.buffer = ""
            return [("text_delta", {"text": rest})] if rest else []
        glued = GLUED_NONE.match(self.buffer)
        if glued:
            # A none-selector followed directly by text on the same line selects nothing either way.
            self.selected = True
            rest, self.buffer = self.buffer[glued.end():], ""
            return [("text_delta", {"text": rest})]
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
            events = self.cards([value["snapshot_id"]])
        elif isinstance(value, dict) and set(value) == {"presentation", "snapshot_ids"} and value["presentation"] == "snapshots":
            events = self.cards(value["snapshot_ids"])
        else:
            raise ValueError("Invalid presentation selection")
        self.selected = True
        self.buffer = ""
        if rest:
            events.append(("text_delta", {"text": rest}))
        return events

    def cards(self, keys):
        if (not isinstance(keys, list) or not 1 <= len(keys) <= MAX_CARDS
                or any(not isinstance(key, str) or key not in self.snapshots for key in keys)
                or len(set(keys)) != len(keys)):
            raise ValueError("Invalid or unknown snapshot selection")
        values = [validate_snapshot(self.snapshots[key]) for key in keys]
        if any(value.get("kind") == "choices" for value in values):
            raise ValueError("Choices are not Task Cards")
        return [("task_card" if value.get("kind") == "tasks" else "plan_card", value) for value in values]

    def finish(self, *, successful_terminal: bool = False) -> list[tuple[str, dict]]:
        if self.selected:
            return []
        if self.text_only and successful_terminal:
            # Only a bare none-selector (or nothing) remains: the card is the whole answer.
            self.selected = True
            match = NONE_HEADER.match(self.buffer)
            rest = self.buffer[match.end():] if match else self.buffer
            return [("text_delta", {"text": rest})] if rest.strip() else []
        # Only a confirmed normal model terminal may delimit a card-only selector.
        # EOF, cancellation, truncation, or a closing brace during streaming cannot.
        if successful_terminal:
            value = json.loads(self.buffer, object_pairs_hook=unique_object)
            if (self.allow_empty and value == {"presentation": "none"}) or (isinstance(value, dict) and value.get("presentation") in ("snapshot", "snapshots")):
                return self.feed("\n")
        raise ValueError("Incomplete presentation header")
