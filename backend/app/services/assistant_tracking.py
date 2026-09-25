"""Tracking Proposals: the Assistant proposes, the client applies (ADR 0014).

Nothing here writes Activity Tracking data. The server only checks Task Type Paths
against existing Task Types and resolves stated times in the Reporting Time Zone.
"""

from __future__ import annotations

import datetime as dt
from typing import Literal
from uuid import uuid4
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field, create_model, field_validator, model_validator
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.db.session import get_engine
from app.models.task_type import TaskType
from app.services.activity_service import reporting_settings

EXPIRY = dt.timedelta(minutes=15)


class StatedTime(BaseModel):
    """Either minutes before the message, or a clock time; never a future instant."""

    model_config = ConfigDict(extra="forbid")
    minutes_ago: int | None = Field(default=None, ge=0, le=1440)
    hour: int | None = Field(default=None, ge=0, le=23)
    minute: int = Field(default=0, ge=0, le=59)
    meridiem: Literal["am", "pm"] | None = None

    @model_validator(mode="after")
    def one_form(self):
        if (self.minutes_ago is None) == (self.hour is None):
            raise ValueError("Give either minutes_ago or hour")
        if self.meridiem and not 1 <= (self.hour or 0) <= 12:
            raise ValueError("am/pm needs an hour from 1 to 12")
        return self


class ProposeTrackingArgs(BaseModel):
    """The model's request, kept flat for reliability. Shape problems are explained back by propose(), not raised."""

    model_config = ConfigDict(extra="ignore")
    action: Literal["track", "stop"]
    task_type_paths: list[str] = Field(default_factory=list, max_length=4,
                                       description="For track only: one existing Task Type Path, or 2-4 when ambiguous.")
    block_name: str | None = Field(default=None, max_length=500, description="For track only, when the user names something specific.")
    minutes_ago: int | None = Field(default=None, ge=0, le=1440, description="Relative time, e.g. 10 for '10 minutes ago'.")
    hour: int | None = Field(default=None, ge=0, le=23, description="Clock time hour, e.g. 3 for 'since 3'.")
    minute: int = Field(default=0, ge=0, le=59)
    meridiem: Literal["am", "pm"] | None = Field(default=None, description="Only when the user said am or pm.")

    @model_validator(mode="before")
    @classmethod
    def flatten_time(cls, value):
        # Models sometimes nest the time fields; never let them be dropped as "now".
        if isinstance(value, dict) and isinstance(value.get("time"), dict):
            value = {**value.pop("time"), **{k: v for k, v in value.items() if k != "time"}}
        return value

    def stated_time(self) -> StatedTime | None:
        if self.minutes_ago is None and self.hour is None:
            return None
        return StatedTime(minutes_ago=self.minutes_ago, hour=self.hour, minute=self.minute, meridiem=self.meridiem)


def arguments_schema(context: dict) -> type[ProposeTrackingArgs]:
    """Offer the existing Task Type Paths as the only allowed values, so the model cannot invent one."""
    paths = tuple(t["path"] for t in context["task_types"])
    if not paths:
        return ProposeTrackingArgs
    return create_model("ProposeTrackingArgs", __base__=ProposeTrackingArgs,
                        task_type_paths=(list[Literal[paths]], Field(default_factory=list, max_length=4)))


class ProposalTaskType(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    id: int
    path: str


class TrackingProposal(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    schema_version: int = Field(default=1, ge=1, le=1)
    proposal_id: str
    action: Literal["track", "stop"]
    task_types: list[ProposalTaskType] = Field(max_length=4)
    block_name: str | None
    at: str | None
    proposed_at: str
    expires_at: str
    reporting_timezone: str

    @field_validator("at", "proposed_at", "expires_at")
    @classmethod
    def utc(cls, value):
        if value is not None and dt.datetime.fromisoformat(value.replace("Z", "+00:00")).utcoffset() != dt.timedelta(0):
            raise ValueError("Instants must be UTC")
        return value


def tracking_context() -> dict:
    """Task Type Paths the model may choose from, and the Reporting Time Zone."""
    with Session(get_engine(), autoflush=False) as db:
        zone = reporting_settings(db, get_settings()).app_timezone
        rows = db.execute(select(TaskType.id, TaskType.name).where(TaskType.is_merged.is_(False)).order_by(TaskType.name)).all()
    return {"reporting_timezone": zone,
            "task_types": [{"id": id, "path": name} for id, name in rows if name.lower() != "unspecified"]}


def resolve_time(stated: StatedTime | None, sent_at: dt.datetime, zone: str) -> dt.datetime | None:
    """A stated time is fixed at sending; a clock time is its most recent past occurrence."""
    if stated is None:
        return None
    if stated.minutes_ago is not None:
        return sent_at - dt.timedelta(minutes=stated.minutes_ago)
    tz = ZoneInfo(zone)
    local_now = sent_at.astimezone(tz)
    hour = stated.hour
    if stated.meridiem == "am":
        hours = [hour % 12]
    elif stated.meridiem == "pm":
        hours = [hour % 12 + 12]
    elif 1 <= hour <= 12:
        hours = [hour % 12, hour % 12 + 12]
    else:
        hours = [hour]
    candidates = []
    for h in hours:
        for days in (0, 1):
            date = local_now.date() - dt.timedelta(days=days)
            local = dt.datetime.combine(date, dt.time(h, stated.minute), tzinfo=tz)
            if local <= local_now:
                candidates.append(local)
                break
    return max(candidates).astimezone(dt.UTC)


def stamp(value: dt.datetime) -> str:
    return value.astimezone(dt.UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def propose(args: ProposeTrackingArgs, sent_at: dt.datetime, context: dict) -> dict:
    """Return {"proposal": ...} or {"error": ...} for the model; never persists."""
    if args.action == "track" and not args.task_type_paths:
        return {"error": "Choose one to four task_type_paths from the Task Type Paths, or ask the user which to use."}
    if args.action == "stop":
        # Stop ends whatever is running; a named activity adds nothing.
        args = args.model_copy(update={"task_type_paths": [], "block_name": None})
    try:
        stated = args.stated_time()
    except ValueError:
        return {"error": "Give either minutes_ago or hour (with optional minute and meridiem), not both."}
    known = {t["path"].lower(): t for t in context["task_types"]}
    chosen, missing = [], []
    for path in args.task_type_paths:
        match = known.get(path.strip().lower())
        if match is None:
            missing.append(path)
        elif match not in chosen:
            chosen.append(match)
    if args.action == "track" and not chosen:
        return {"error": f"No existing Task Type matches {', '.join(missing)}. Ask the user which existing Task Type "
                         "to use; you cannot create Task Types."}
    at = resolve_time(stated, sent_at, context["reporting_timezone"])
    if at is not None and at > sent_at:
        return {"error": "That time is in the future. Tracking can only start or stop now or earlier."}
    proposal = TrackingProposal(
        proposal_id=str(uuid4()), action=args.action, task_types=[ProposalTaskType(**t) for t in chosen],
        block_name=(args.block_name or "").strip() or None, at=stamp(at) if at else None,
        proposed_at=stamp(sent_at), expires_at=stamp(sent_at + EXPIRY), reporting_timezone=context["reporting_timezone"],
    ).model_dump()
    result = {"proposal": proposal}
    if missing:
        result["ignored_paths"] = missing
    return result


def context_line(proposal: dict) -> str:
    """How a displayed proposal appears in later model context. It may never have been confirmed."""
    what = "Stop tracking" if proposal["action"] == "stop" else "Track " + " or ".join(t["path"] for t in proposal["task_types"])
    when = f" at {proposal['at']}" if proposal["at"] else " from the moment of confirmation"
    return f"\n[Displayed Tracking Proposal, not necessarily confirmed: {what}{when}]"
