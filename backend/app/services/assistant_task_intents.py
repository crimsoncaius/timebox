"""Strict model-authored intent. Saved identities and resolved dates are server owned."""
from __future__ import annotations

import datetime as dt
from typing import Annotated, Literal
from zoneinfo import ZoneInfo

from pydantic import Field, field_validator, model_validator

from app.core.time import as_utc
from app.services.assistant_limits import (
    MAX_DESCRIPTION_BYTES,
    MAX_DESCRIPTION_CHARACTERS,
    MAX_TASK_OPERATIONS,
)
from app.services.assistant_tasks import SavedId, Strict


class AbsoluteDate(Strict):
    kind: Literal["absolute"]
    date: str


class DayOffset(Strict):
    kind: Literal["day_offset"]
    days: int = Field(ge=-366, le=366)


class Weekday(Strict):
    kind: Literal["weekday"]
    weekday: int = Field(ge=1, le=7)
    week_offset: int = Field(ge=-53, le=53)


DateIntent = Annotated[AbsoluteDate | DayOffset | Weekday, Field(discriminator="kind")]


class LocalTime(Strict):
    date: DateIntent
    local_time: str
    utc_offset: str | None = None


class RelativeMinutes(Strict):
    kind: Literal["relative_minutes"]
    minutes: int = Field(ge=-525600, le=525600)


TimeIntent = LocalTime | RelativeMinutes
TemporalIntent = AbsoluteDate | DayOffset | Weekday | LocalTime | RelativeMinutes


class IdTarget(Strict):
    id: SavedId


class RefTarget(Strict):
    ref: str = Field(min_length=1, max_length=80)


Target = IdTarget | RefTarget


class Patch(Strict):
    title: str | None = Field(default=None, min_length=1, max_length=500)
    description: str | None = Field(default=None, max_length=MAX_DESCRIPTION_CHARACTERS)
    project_id: SavedId | None = None
    task_type_id: SavedId | None = None
    urgency: Literal["low", "medium", "high"] | None = None
    importance: Literal["low", "medium", "high"] | None = None
    deadline: TemporalIntent | None = None
    reminder_at: TimeIntent | None = None
    blocked: bool | None = None
    blocking_reason: str | None = Field(default=None, max_length=1000)
    ready_to_plan: bool | None = None

    @model_validator(mode="after")
    def valid_fields(self):
        for name in ("title", "blocked", "ready_to_plan"):
            if name in self.model_fields_set and getattr(self, name) is None:
                raise ValueError(name + " cannot be null")
        if self.title is not None and not self.title.strip():
            raise ValueError("Title must not be blank")
        if self.description is not None and len(self.description.encode("utf-8")) > MAX_DESCRIPTION_BYTES:
            raise ValueError("Description exceeds the byte limit")
        if self.blocked is False and self.blocking_reason and self.blocking_reason.strip():
            raise ValueError("A nonempty reason contradicts unblocking")
        return self


class CreateTask(Patch):
    op: Literal["create_task"]
    ref: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=500)


class PatchTask(Strict):
    op: Literal["patch_task"]
    target: Target
    set: Patch

    @model_validator(mode="after")
    def nonempty(self):
        if not self.set.model_fields_set:
            raise ValueError("Patch must contain at least one field")
        return self


class AddSubtask(Strict):
    op: Literal["add_subtask"]
    parent: Target
    ref: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=500)

    @field_validator("title")
    @classmethod
    def title_valid(cls, value):
        if not value.strip():
            raise ValueError("Title must not be blank")
        return value.strip()


class RenameSubtask(Strict):
    op: Literal["rename_subtask"]
    subtask_id: SavedId
    parent_id: SavedId
    title: str = Field(min_length=1, max_length=500)

    _title = field_validator("title")(AddSubtask.title_valid.__func__)


class CheckSubtask(Strict):
    op: Literal["set_subtask_checked"]
    target: Target
    parent: Target
    checked: bool


class CompleteNow(Strict):
    op: Literal["complete_now"]
    target: Target


class CompleteAt(Strict):
    op: Literal["complete_at"]
    target: Target
    when: TemporalIntent


class Reopen(Strict):
    op: Literal["reopen_task"]
    target: Target


Operation = Annotated[CreateTask | PatchTask | AddSubtask | RenameSubtask | CheckSubtask | CompleteNow | CompleteAt | Reopen, Field(discriminator="op")]


class ProposeTaskChangesArgs(Strict):
    operations: list[Operation] = Field(min_length=1, max_length=MAX_TASK_OPERATIONS)


def resolve_date(intent, anchor, zone):
    today = anchor.astimezone(ZoneInfo(zone)).date()
    if intent["kind"] == "absolute":
        return dt.date.fromisoformat(intent["date"])
    if intent["kind"] == "day_offset":
        return today + dt.timedelta(days=intent["days"])
    return today - dt.timedelta(days=today.weekday()) + dt.timedelta(weeks=intent["week_offset"], days=intent["weekday"] - 1)


def resolve_time(value, anchor, zone):
    if value.get("kind") == "relative_minutes":
        return as_utc(anchor) + dt.timedelta(minutes=value["minutes"])
    date = resolve_date(value["date"], anchor, zone)
    clock = dt.time.fromisoformat(value["local_time"])
    if clock.tzinfo is not None:
        raise ValueError("Use utc_offset to disambiguate a local time")
    local = dt.datetime.combine(date, clock)
    tz = ZoneInfo(zone)
    valid = {candidate.astimezone(dt.UTC) for fold in (0, 1)
             if (candidate := local.replace(tzinfo=tz, fold=fold)).astimezone(dt.UTC).astimezone(tz).replace(tzinfo=None) == local}
    if value.get("utc_offset") is not None:
        offset = dt.datetime.fromisoformat("2000-01-01T00:00:00" + value["utc_offset"]).utcoffset()
        valid = {v for v in valid if v.astimezone(tz).utcoffset() == offset}
    if len(valid) != 1:
        raise ValueError("Local time is nonexistent or ambiguous; provide a valid explicit UTC offset")
    return valid.pop()


def resolve_temporal(value, anchor, zone):
    if value.get("kind") in {"absolute", "day_offset", "weekday"}:
        return {"kind": "date", "date": resolve_date(value, anchor, zone).isoformat()}
    return {"kind": "instant", "instant": resolve_time(value, anchor, zone).isoformat()}


def canonical_operations(args, anchor, zone):
    result = []
    for operation in args.operations:
        op = operation.model_dump(exclude_unset=True)
        fields = op["set"] if op["op"] == "patch_task" else op if op["op"] == "create_task" else {}
        if "title" in fields:
            fields["title"] = fields["title"].strip()
        if "description" in fields:
            fields["description"] = fields["description"] or ""
        if "blocking_reason" in fields:
            fields["blocking_reason"] = (fields["blocking_reason"] or "").strip() or None
            if fields["blocking_reason"]:
                fields["blocked"] = True
        if fields.get("blocked") is False:
            fields["blocking_reason"] = None
        for field in ("deadline", "reminder_at"):
            if fields.get(field) is not None:
                fields[field] = resolve_temporal(fields[field], anchor, zone)
        if op["op"] == "complete_at":
            resolved = resolve_temporal(op.pop("when"), anchor, zone)
            if resolved["kind"] == "date":
                local_date = dt.date.fromisoformat(resolved["date"])
                if local_date >= anchor.astimezone(ZoneInfo(zone)).date():
                    raise ValueError("Date-only completion must be a prior date; earlier today needs a time")
                instant = resolve_time({"date": {"kind": "absolute", "date": resolved["date"]}, "local_time": "12:00:00"}, anchor, zone)
                op["completion"] = {"precision": "date", "local_date": resolved["date"], "instant": instant.isoformat(), "reporting_timezone": zone}
            else:
                instant = dt.datetime.fromisoformat(resolved["instant"])
                op["completion"] = {"precision": "instant", "instant": instant.isoformat(), "reporting_timezone": zone}
            if instant > anchor:
                raise ValueError("Completion cannot be in the future")
        result.append(op)
    return result
