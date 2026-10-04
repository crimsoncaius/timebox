"""Saved Task reads. Domain queries select columns only and never synchronize or flush.

Query evidence is captured separately from the read transaction. Cursors reference
fixed saved identities, not a query to rerun against changing membership.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import time
from contextlib import contextmanager
from typing import Annotated, Literal
from uuid import UUID, uuid4
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import and_, case, event, func, or_, select, text
from sqlalchemy.orm import Session, aliased

from app.core.config import get_settings
from app.core.time import as_utc, utc_now
from app.db.session import get_engine
from app.models.assistant import AssistantQuery, AssistantQueryCursor
from app.models.battle_plan import Project, RecurrenceOccurrence, RecurringTemplate, Task, TaskStatus
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.services.activity_service import reporting_settings
from app.services.assistant_limits import (
    MAX_TASK_MANIFEST,
    MAX_TASK_PAGE,
    MAX_TASK_READ_BYTES,
    TASK_CURSOR_SECONDS,
    TASK_READ_TIMEOUT,
)

TaskKind = Literal["ordinary", "occurrence", "quota_tracker", "session"]
SavedId = Annotated[int, Field(strict=True, gt=0)]


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True).encode("utf-8")


def projection_hash(row):
    def canonical(value):
        if isinstance(value, dict):
            return {k: canonical(v) for k, v in value.items() if k not in {"description", "projection_hash", "field_coverage", "matched_subtask_ids"}
                    and not k.endswith("_cursor")}
        return [canonical(v) for v in value] if isinstance(value, list) else value
    return hashlib.sha256(encoded(canonical(row))).hexdigest()


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class DateRange(Strict):
    start: str
    end: str

    @model_validator(mode="after")
    def ordered(self):
        if dt.date.fromisoformat(self.start) >= dt.date.fromisoformat(self.end):
            raise ValueError("Ranges use inclusive start and exclusive end dates")
        return self


class TaskFilters(Strict):
    project_id: SavedId | Literal["unassigned"] | None = None
    task_type_id: SavedId | Literal["unset"] | None = None
    task_type_subtree: bool = False
    completion: Literal["incomplete", "completed", "skipped", "any"] = "incomplete"
    blocked: bool | None = None
    ready_to_plan: bool | None = None
    urgency: Literal["low", "medium", "high", "unset"] | None = None
    importance: Literal["low", "medium", "high", "unset"] | None = None
    deadline: DateRange | None = None
    reminder: DateRange | None = None
    completed_at: DateRange | None = None


class ReadTasksArgs(Strict):
    mode: Literal["search", "get", "children", "outcomes"]
    query: str | None = Field(default=None, max_length=500)
    search_in: Literal["titles", "descriptions"] = "titles"
    scope: Literal["current", "history", "saved_future"] = "current"
    kinds: list[TaskKind] | None = Field(default=None, min_length=1, max_length=4)
    filters: TaskFilters = Field(default_factory=TaskFilters)
    cursor: str | None = None
    task_ids: list[SavedId] | None = Field(default=None, min_length=1, max_length=10)
    include_description: bool = False
    parent_id: SavedId | None = None
    kind: Literal["subtasks", "sessions"] | None = None
    operation_ids: list[str] | None = Field(default=None, min_length=1, max_length=20)

    @model_validator(mode="after")
    def selection(self):
        allowed = {"search": {"query", "search_in", "scope", "kinds", "filters", "cursor"},
                   "get": {"task_ids", "include_description", "cursor"},
                   "children": {"parent_id", "kind", "cursor"}, "outcomes": {"operation_ids"}}
        if self.model_fields_set - allowed[self.mode] - {"mode"}:
            raise ValueError("Fields do not apply to the selected mode")
        if self.mode == "get" and not self.task_ids:
            raise ValueError("get requires 1–10 saved Task IDs")
        if self.mode == "children" and (self.parent_id is None or self.kind is None):
            raise ValueError("children requires parent_id and subtasks or sessions")
        if self.mode == "outcomes" and not self.operation_ids:
            raise ValueError("outcomes requires explicit operation IDs")
        if self.filters.completed_at and self.scope != "history":
            raise ValueError("completed_at requires explicit history")
        if self.filters.completion != "incomplete" and self.scope != "history":
            raise ValueError("Completed/skipped/any requires explicit history")
        if self.filters.task_type_subtree and not isinstance(self.filters.task_type_id, int):
            raise ValueError("Task Type subtree requires an existing Task Type ID")
        if self.kinds and len(set(self.kinds)) != len(self.kinds):
            raise ValueError("Kinds must be distinct")
        for value in [self.cursor, *(self.operation_ids or [])]:
            if value is not None:
                UUID(value)
        return self


class ReadTaskChoicesArgs(Strict):
    kind: Literal["projects", "task_types"]
    query: str | None = Field(default=None, max_length=500)
    cursor: str | None = None


class NamedIdentity(Strict):
    id: SavedId
    name: str


class TypeIdentity(Strict):
    id: SavedId
    path: str


class Description(Strict):
    text: str = Field(max_length=2000)
    truncated: bool


class Child(Strict):
    id: SavedId
    title: str | None = None
    checked: bool | None = None
    availability: Literal["available", "unavailable"] = "available"


class Deadline(Strict):
    kind: Literal["date", "instant"]
    date: str | None = None
    instant: str | None = None

    @model_validator(mode="after")
    def shape(self):
        if (self.kind == "date" and (not self.date or self.instant)) or (self.kind == "instant" and (not self.instant or self.date)):
            raise ValueError("Deadline must contain its selected date or instant")
        return self


class Period(Strict):
    start: str
    end: str


class Quota(Strict):
    required: int
    completed: int
    saved_session_count: int


class TaskProjection(Strict):
    id: SavedId
    kind: TaskKind
    availability: Literal["available", "unavailable", "unverified"]
    title: str | None = None
    lifecycle: Literal["incomplete", "completed", "skipped"] | None = None
    relevance: Literal["current", "history", "saved_future"] | None = None
    project: NamedIdentity | None = None
    task_type: TypeIdentity | None = None
    ready_to_plan: bool | None = None
    blocked: bool | None = None
    blocking_reason: str | None = None
    urgency: Literal["low", "medium", "high"] | None = None
    importance: Literal["low", "medium", "high"] | None = None
    deadline: Deadline | None = None
    reminder_at: str | None = None
    completed_at: str | None = None
    completion_precision: Literal["date", "instant", "unknown"] | None = None
    completion_local_date: str | None = None
    completion_timezone: str | None = None
    parent_id: SavedId | None = None
    series_id: SavedId | None = None
    period: Period | None = None
    quota: Quota | None = None
    subtasks: list[Child] | None = None
    subtasks_count: int | None = None
    subtasks_next_cursor: str | None = None
    sessions: list[TaskProjection] | None = None
    sessions_next_cursor: str | None = None
    matched_subtask_ids: list[SavedId] | None = None
    planned_dates: list[str] | None = None
    planned_dates_complete: bool | None = None
    description: Description | None = None
    projection_hash: str | None = None
    field_coverage: dict[str, Literal["fetched", "excluded", "truncated", "unverified"]] | None = None

    @model_validator(mode="after")
    def unavailable(self):
        if self.availability != "available" and self.model_fields_set - {"id", "kind", "availability"}:
            raise ValueError("Unavailable identities must not contain private fields")
        return self


class Source(Strict):
    tool: Literal["read_tasks", "read_task_choices"]
    mode: str
    normalized_arguments: dict


class Choice(Strict):
    id: SavedId
    name: str | None = None
    path: str | None = None
    availability: Literal["available", "unavailable", "unverified"]


class TaskSnapshotV4(Strict):
    schema_version: Literal[4] = 4
    kind: Literal["tasks", "choices"]
    snapshot_id: str
    read_at: str
    reporting_timezone: str
    source: Source
    query_id: str | None = None
    query_at: str | None = None
    matching_count: int | None = None
    count_relation: Literal["exact", "at_least", "unknown"]
    next_cursor: str | None = None
    completeness: Literal["complete", "partial"]
    limitations: list[Literal["saved_only", "query_limit", "payload_limit", "unverified_readiness", "unavailable"]]
    rows: list[TaskProjection] | list[Choice] = Field(max_length=MAX_TASK_PAGE)

    @model_validator(mode="after")
    def typed_rows(self):
        UUID(self.snapshot_id)
        ZoneInfo(self.reporting_timezone)
        if as_utc(dt.datetime.fromisoformat(self.read_at)).isoformat() != self.read_at:
            raise ValueError("read_at must be a canonical UTC instant")
        if any(not isinstance(row, TaskProjection if self.kind == "tasks" else Choice) for row in self.rows):
            raise ValueError("Rows must match snapshot kind")
        return self


def validate(value):
    if len(encoded(value)) > MAX_TASK_READ_BYTES:
        raise ValueError("Saved-task snapshot exceeds its byte limit")
    return TaskSnapshotV4.model_validate(value).model_dump(exclude_unset=True)


@contextmanager
def read_session():
    """Bound database work too: cancelling a thread alone cannot cancel its SQL."""
    with Session(get_engine(), autoflush=False) as db:
        connection = db.connection()
        deadline = time.monotonic() + TASK_READ_TIMEOUT
        raw = None
        if connection.dialect.name == "sqlite":
            raw = connection.connection.driver_connection
            raw.set_progress_handler(lambda: int(time.monotonic() >= deadline), 1000)
        elif connection.dialect.name == "postgresql":
            db.execute(text("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY"))
            db.execute(select(func.set_config("statement_timeout", "5000", True)))
        def remaining_budget(conn, cursor, statement, parameters, context, executemany):
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("Saved-data read deadline exceeded")
            if connection.dialect.name == "postgresql":
                cursor.execute(f"SET LOCAL statement_timeout = '{max(1, int(remaining * 1000))}ms'")
        event.listen(connection, "before_cursor_execute", remaining_budget)
        try:
            yield db
        finally:
            event.remove(connection, "before_cursor_execute", remaining_budget)
            if raw is not None:
                raw.set_progress_handler(None, 0)


def _base(today, description=False):
    parent = aliased(Task)
    occurrence = RecurrenceOccurrence
    period_start = func.coalesce(occurrence.cycle_start, Task.quota_period_start, parent.quota_period_start)
    period_end = func.coalesce(occurrence.cycle_end, Task.quota_period_end, parent.quota_period_end)
    kind = case((Task.recurrence_kind == "quota_session", "session"),
                (Task.recurrence_kind == "quota_parent", "quota_tracker"),
                (Task.recurrence_kind.is_not(None), "occurrence"), else_="ordinary")
    carry = and_(kind == "occurrence", or_(RecurringTemplate.keep_unfinished_overdue.is_(True),
        select(TimeBlock.id).join(Day).where(TimeBlock.task_id == Task.id,
            TimeBlock.lane == BlockLane.planned, Day.date >= today).exists()))
    expired = and_(period_end < today, ~carry)
    session = aliased(Task)
    completed_sessions = select(func.count()).where(session.parent_id == Task.id,
        session.recurrence_kind == "quota_session", session.deleted_at.is_(None), session.archived_at.is_(None),
        session.status == TaskStatus.completed).correlate(Task).scalar_subquery()
    completed = case((kind == "quota_tracker", and_(Task.expected_sessions > 0, completed_sessions >= Task.expected_sessions)),
                     else_=Task.status == TaskStatus.completed)
    lifecycle = case((completed, "completed"),
                     (or_(occurrence.skipped.is_(True), expired), "skipped"), else_="incomplete")
    relevance = case((period_start > today, "saved_future"),
                     (or_(lifecycle != "incomplete", expired), "history"), else_="current")
    fields = [Task.id, Task.title, Task.parent_id, Task.project_id, Task.task_type_id,
              Task.recurring_template_id, Task.ready_to_plan, Task.is_blocked, Task.blocking_reason,
              Task.urgency, Task.importance, Task.deadline_date, Task.deadline_at, Task.reminder_at,
              Task.completed_at, Task.position, Task.expected_sessions,
              Project.name.label("project_name"), TaskType.name.label("type_path"),
              period_start.label("period_start"), period_end.label("period_end"),
              kind.label("kind"), lifecycle.label("lifecycle"), relevance.label("relevance")]
    if description:
        # Bound transfer as well as the model excerpt. Do not SELECT full descriptions.
        fields += [func.substr(Task.description, 1, 2000).label("description"),
                   func.length(Task.description).label("description_length")]
    return (select(*fields).outerjoin(parent, Task.parent_id == parent.id)
            .outerjoin(occurrence, occurrence.task_id == Task.id)
            .outerjoin(RecurringTemplate, Task.recurring_template_id == RecurringTemplate.id)
            .outerjoin(Project, Task.project_id == Project.id).outerjoin(TaskType, Task.task_type_id == TaskType.id)
            .where(Task.deleted_at.is_(None), Task.archived_at.is_(None),
                   or_(Task.parent_id.is_(None), and_(parent.deleted_at.is_(None), parent.archived_at.is_(None))),
                   or_(Task.parent_id.is_(None), Task.recurrence_kind == "quota_session")))


def _pattern(value):
    return "%" + value.lower().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"


def _children(db, parent_id, kind):
    return select(Task.id, Task.title, Task.checked).where(Task.parent_id == parent_id,
        Task.deleted_at.is_(None), Task.archived_at.is_(None),
        Task.recurrence_kind == "quota_session" if kind == "sessions" else or_(Task.recurrence_kind.is_(None), Task.recurrence_kind != "quota_session")
    ).order_by(Task.position, Task.id)


def _manifest(arguments, identities, zone, now, tool="read_tasks"):
    return {"query_id": str(uuid4()), "arguments": arguments, "tool": tool,
            "query_at": now.isoformat(), "reporting_timezone": zone,
            "identities": identities[:MAX_TASK_MANIFEST], "matching_count": len(identities),
            "count_relation": "at_least" if len(identities) > MAX_TASK_MANIFEST else "exact",
            "cursors": {str(uuid4()): i for i in range(MAX_TASK_PAGE, min(len(identities), MAX_TASK_MANIFEST), MAX_TASK_PAGE)}}


def _cursor(manifest, offset):
    return next((key for key, value in manifest["cursors"].items() if value == offset), None)


def _project(db, record, zone, now, manifests, include_description=False):
    r = dict(record)
    result = {"id": r["id"], "kind": r["kind"], "availability": "available", "title": r["title"],
              "lifecycle": r["lifecycle"], "relevance": r["relevance"],
              "project": {"id": r["project_id"], "name": r["project_name"]} if r["project_id"] else None,
              "task_type": {"id": r["task_type_id"], "path": r["type_path"]} if r["task_type_id"] else None,
              "field_coverage": {"description": "excluded", "supporting_notes": "excluded"}}
    if r["kind"] != "quota_tracker":
        result.update(ready_to_plan=r["ready_to_plan"], blocked=r["is_blocked"], blocking_reason=r["blocking_reason"],
                      urgency=r["urgency"], importance=r["importance"],
                      deadline={"kind": "date", "date": r["deadline_date"].isoformat()} if r["deadline_date"] else
                      {"kind": "instant", "instant": as_utc(r["deadline_at"]).isoformat()} if r["deadline_at"] else None,
                      reminder_at=as_utc(r["reminder_at"]).isoformat() if r["reminder_at"] else None,
                      completed_at=as_utc(r["completed_at"]).isoformat() if r["completed_at"] else None,
                      completion_precision="unknown" if r["completed_at"] else None)
        dates = list(db.scalars(select(Day.date).join(TimeBlock).where(TimeBlock.task_id == r["id"],
                     TimeBlock.lane == BlockLane.planned).distinct().order_by(Day.date).limit(MAX_TASK_MANIFEST + 1)))
        result.update(planned_dates=[d.isoformat() for d in dates[:MAX_TASK_MANIFEST]],
                      planned_dates_complete=len(dates) <= MAX_TASK_MANIFEST)
        result["field_coverage"]["planned_dates"] = "fetched" if result["planned_dates_complete"] else "truncated"
    if r["parent_id"]:
        result["parent_id"] = r["parent_id"]
    if r["recurring_template_id"]:
        result["series_id"] = r["recurring_template_id"]
    if r["period_start"]:
        result["period"] = {"start": r["period_start"].isoformat(), "end": r["period_end"].isoformat()}
    child_kind = "sessions" if r["kind"] == "quota_tracker" else "subtasks"
    child_query = _children(db, r["id"], child_kind)
    children = list(db.execute(child_query.limit(MAX_TASK_MANIFEST + 1)).mappings())
    count = db.scalar(select(func.count()).select_from(child_query.subquery()))
    if child_kind == "sessions":
        completed = db.scalar(select(func.count()).select_from(child_query.where(Task.status == TaskStatus.completed).subquery()))
        result["quota"] = {"required": r["expected_sessions"] or 0, "completed": completed, "saved_session_count": count}
        result["sessions"] = project_ids(db, [{"id": c["id"], "kind": "session"} for c in children[:MAX_TASK_PAGE]], zone, now, manifests)
    else:
        result.update(subtasks=[dict(c) for c in children[:MAX_TASK_PAGE]], subtasks_count=count)
    result["field_coverage"][child_kind] = "truncated" if count > MAX_TASK_PAGE else "fetched"
    if children:
        manifest = _manifest({"mode": "children", "parent_id": r["id"], "kind": child_kind},
                             [{"id": c["id"], "kind": "session" if child_kind == "sessions" else r["kind"]} for c in children], zone, now)
        if child_kind == "sessions":
            result["sessions_next_cursor"] = _cursor(manifest, MAX_TASK_PAGE)
        elif count > MAX_TASK_PAGE:
            result["subtasks_next_cursor"] = _cursor(manifest, MAX_TASK_PAGE)
        if count > MAX_TASK_PAGE:
            manifests.append(manifest)
    if include_description:
        result["description"] = {"text": r["description"], "truncated": r["description_length"] > 2000}
        result["field_coverage"]["description"] = "truncated" if r["description_length"] > 2000 else "fetched"
    result["field_coverage"]["projection"] = "fetched"
    result["projection_hash"] = projection_hash(result)
    return result


def project_ids(db, identities, zone, now, manifests, include_description=False):
    records = {r["id"]: r for r in db.execute(_base(now.astimezone(ZoneInfo(zone)).date(), include_description)
               .where(Task.id.in_([i["id"] for i in identities]))).mappings()}
    return [_project(db, records[i["id"]], zone, now, manifests, include_description) if i["id"] in records
            else {**i, "availability": "unavailable"} for i in identities]


def _search(db, args, zone, now, only_ids=None):
    base = _base(now.astimezone(ZoneInfo(zone)).date(), args.search_in == "descriptions").subquery()
    c = base.c
    query = select(base).where(c.kind.in_(args.kinds or ["ordinary", "occurrence", "quota_tracker"]))
    if only_ids is not None:
        query = query.where(c.id.in_(only_ids))
    if args.scope != "history":
        query = query.where(c.relevance == args.scope)
    if args.filters.completion != "any":
        query = query.where(c.lifecycle == args.filters.completion)
    if args.scope == "current":
        older = base.alias()
        query = query.where(or_(c.kind != "occurrence", c.recurring_template_id.is_(None), ~select(older.c.id).where(
            older.c.recurring_template_id == c.recurring_template_id, older.c.relevance == "current",
            or_(older.c.period_start < c.period_start, and_(older.c.period_start == c.period_start, older.c.id < c.id))).exists()))
    if args.query:
        column = Task.description if args.search_in == "descriptions" else Task.title
        matched = select(Task.id).where(func.lower(column).like(_pattern(args.query), escape="\\"))
        if args.search_in == "titles":
            child = aliased(Task)
            query = query.where(or_(c.id.in_(matched), select(child.id).where(child.parent_id == c.id,
                child.deleted_at.is_(None), child.archived_at.is_(None),
                or_(child.recurrence_kind.is_(None), child.recurrence_kind != "quota_session"),
                func.lower(child.title).like(_pattern(args.query), escape="\\")).exists()))
        else:
            query = query.where(c.id.in_(matched))
    f = args.filters
    if f.project_id is not None:
        if isinstance(f.project_id, int) and db.scalar(select(Project.id).where(Project.id == f.project_id)) is None:
            raise ValueError("Choose an existing Project")
        query = query.where(c.project_id.is_(None) if f.project_id == "unassigned" else c.project_id == f.project_id)
    if f.task_type_id == "unset":
        query = query.where(c.task_type_id.is_(None))
    elif f.task_type_id:
        path = db.scalar(select(TaskType.name).where(TaskType.id == f.task_type_id, TaskType.is_merged.is_(False)))
        if path is None:
            raise ValueError("Choose an existing Task Type")
        query = query.where(or_(c.task_type_id == f.task_type_id, c.type_path.startswith(path + "/", autoescape=True))
                            if f.task_type_subtree else c.task_type_id == f.task_type_id)
    lifecycle_filters = f.model_fields_set - {"project_id", "task_type_id", "task_type_subtree", "completion"}
    if lifecycle_filters and args.kinds and "quota_tracker" in args.kinds:
        raise ValueError("Quota Trackers have no independent lifecycle fields; select Tasks or Sessions")
    if lifecycle_filters:
        query = query.where(c.kind != "quota_tracker")
    for name, column in [("blocked", c.is_blocked), ("ready_to_plan", c.ready_to_plan), ("urgency", c.urgency), ("importance", c.importance)]:
        value = getattr(f, name)
        if value is not None:
            query = query.where(column.is_(None) if value == "unset" else column == value)
    for name, column in [("deadline", c.deadline_at), ("reminder", c.reminder_at), ("completed_at", c.completed_at)]:
        value = getattr(f, name)
        if value:
            start, end = (dt.datetime.combine(dt.date.fromisoformat(d), dt.time(), ZoneInfo(zone)).astimezone(dt.UTC) for d in [value.start, value.end])
            condition = and_(column >= start, column < end)
            if name == "deadline":
                condition = or_(condition, and_(c.deadline_date >= dt.date.fromisoformat(value.start), c.deadline_date < dt.date.fromisoformat(value.end)))
            query = query.where(condition)
    return [{"id": r.id, "kind": r.kind} for r in db.execute(query.order_by(c.position, c.id).limit(MAX_TASK_MANIFEST + 1))]


def _load_cursor(db, conversation_id, args, tool, now):
    cursor = db.get(AssistantQueryCursor, args.cursor)
    if cursor:
        query = db.get(AssistantQuery, cursor.query_id)
        if query and query.conversation_id == conversation_id:
            evidence = query.evidence
            normalized = args.model_dump(mode="json", exclude_defaults=True, exclude_none=True)
            normalized.pop("cursor", None)
            original = evidence["arguments"]
            if evidence["tool"] != tool or normalized != original:
                raise ValueError("Cursor belongs to a different query; reuse its unchanged arguments")
            if (now - dt.datetime.fromisoformat(evidence["query_at"])).total_seconds() >= TASK_CURSOR_SECONDS:
                raise ValueError("Cursor expired; run a fresh search")
            return evidence, evidence["cursors"][args.cursor]
    raise ValueError("Unknown cursor for this conversation")


def _bound(snapshot):
    # Keep positional identities when a row cannot fit. Never truncate a value
    # and claim a complete projection, including multi-byte text.
    for row in snapshot["rows"]:
        if snapshot["kind"] == "tasks" and row["availability"] == "available":
            row["projection_hash"] = projection_hash(row)
    if len(encoded(snapshot)) > MAX_TASK_READ_BYTES:
        snapshot["completeness"] = "partial"
        snapshot["limitations"].append("payload_limit")
        for index in sorted(range(len(snapshot["rows"])), key=lambda i: len(encoded(snapshot["rows"][i])), reverse=True):
            row = snapshot["rows"][index]
            snapshot["rows"][index] = {k: row[k] for k in ("id", "kind") if k in row} | {"availability": "unverified"}
            if len(encoded(snapshot)) <= MAX_TASK_READ_BYTES:
                break
    if len(encoded(snapshot)) > MAX_TASK_READ_BYTES:
        raise ValueError("Read metadata exceeds the byte limit; narrow the request")
    return validate(snapshot)


def read_tasks(args, conversation_id, *, choices=False, now=None):
    now = now or utc_now()
    tool = "read_task_choices" if choices else "read_tasks"
    manifests = []
    if not choices and args.mode == "get" and args.cursor:
        # A detail continuation pages one of that parent's saved collections.
        with read_session() as db:
            cursor = db.get(AssistantQueryCursor, args.cursor)
            query = db.get(AssistantQuery, cursor.query_id) if cursor else None
            source = query.evidence["arguments"] if query and query.conversation_id == conversation_id else {}
            if source.get("mode") != "children" or args.task_ids != [source.get("parent_id")]:
                raise ValueError("Detail cursor must belong to this single Task's nested collection")
            if args.include_description:
                raise ValueError("Read the description separately from a nested collection continuation")
        nested = ReadTasksArgs.model_validate({**source, "cursor": args.cursor})
        result = read_tasks(nested, conversation_id, now=now)
        result["source"] = {"tool": "read_tasks", "mode": "get", "normalized_arguments": args.model_dump(mode="json", exclude_defaults=True, exclude_none=True)}
        return _bound(result)
    if not choices and args.mode == "outcomes":
        return {"error": "Task operation outcomes are not available until task confirmation is enabled."}
    with read_session() as db:
        zone = reporting_settings(db, get_settings()).app_timezone
        normalized = args.model_dump(mode="json", exclude_defaults=True, exclude_none=True)
        normalized.pop("cursor", None)
        offset = 0
        manifest = None
        if args.cursor:
            manifest, offset = _load_cursor(db, conversation_id, args, tool, now)
            zone = manifest["reporting_timezone"]
        elif choices:
            model = Project if args.kind == "projects" else TaskType
            query = select(model.id).order_by(func.lower(model.name), model.id)
            if args.kind == "task_types":
                query = query.where(TaskType.is_merged.is_(False))
            if args.query:
                query = query.where(func.lower(model.name).like(_pattern(args.query), escape="\\"))
            ids = [{"id": i} for i in db.scalars(query.limit(MAX_TASK_MANIFEST + 1))]
            manifest = _manifest(normalized, ids, zone, now, tool)
        elif args.mode == "search":
            manifest = _manifest(normalized, _search(db, args, zone, now), zone, now)
        elif args.mode == "children":
            parent = db.execute(_base(now.astimezone(ZoneInfo(zone)).date()).where(Task.id == args.parent_id)).mappings().first()
            if parent is None:
                raise ValueError("Parent is unavailable")
            if (parent["kind"] == "quota_tracker") != (args.kind == "sessions"):
                raise ValueError("Quota Sessions and Subtasks are different collections")
            ids = [{"id": r.id, "kind": "session" if args.kind == "sessions" else parent["kind"]}
                   for r in db.execute(_children(db, args.parent_id, args.kind).limit(MAX_TASK_MANIFEST + 1))]
            manifest = _manifest(normalized, ids, zone, now)
        if manifest:
            if not args.cursor:
                manifests.append(manifest)
            identities = manifest["identities"][offset:offset + MAX_TASK_PAGE]
        else:
            identities = [{"id": i, "kind": "ordinary"} for i in dict.fromkeys(args.task_ids)]
        if choices:
            model = Project if args.kind == "projects" else TaskType
            query = select(model.id, model.name).where(model.id.in_([i["id"] for i in identities]))
            if args.kind == "task_types":
                query = query.where(TaskType.is_merged.is_(False))
            if args.query:
                query = query.where(func.lower(model.name).like(_pattern(args.query), escape="\\"))
            names = dict(db.execute(query).all())
            rows = [{"id": i["id"], "availability": "available", "name" if choices and args.kind == "projects" else "path": names[i["id"]]}
                    if i["id"] in names else {"id": i["id"], "availability": "unavailable"} for i in identities]
        elif args.mode == "children" and args.kind == "subtasks":
            # Children are represented in their parent projection, never as a fifth Task kind.
            rows = project_ids(db, [{"id": args.parent_id, "kind": "ordinary"}], zone, now, manifests)
            if rows[0]["availability"] == "available":
                values = {r.id: dict(r._mapping) for r in db.execute(_children(db, args.parent_id, "subtasks").where(Task.id.in_([i["id"] for i in identities])))}
                rows[0]["subtasks"] = [values.get(i["id"], {"id": i["id"], "availability": "unavailable"}) for i in identities]
                rows[0]["field_coverage"]["subtasks"] = "truncated" if len(values) != len(identities) or offset or len(identities) < manifest["matching_count"] else "fetched"
                rows[0]["subtasks_next_cursor"] = _cursor(manifest, offset + MAX_TASK_PAGE)
        else:
            include_description = args.include_description if args.mode == "get" else args.mode == "search" and args.search_in == "descriptions"
            rows = project_ids(db, identities, zone, now, manifests, include_description)
            if args.mode == "search" and args.cursor:
                eligible = {i["id"] for i in _search(db, args, zone, now, [i["id"] for i in identities])}
                rows = [row if row["id"] in eligible else {"id": row["id"], "kind": row["kind"], "availability": "unavailable"} for row in rows]
            if args.mode == "search" and args.query and args.search_in == "titles":
                for row in rows:
                    if row["availability"] == "available":
                        row["matched_subtask_ids"] = list(db.scalars(_children(db, row["id"], "subtasks").with_only_columns(Task.id)
                            .where(func.lower(Task.title).like(_pattern(args.query), escape="\\")).limit(MAX_TASK_MANIFEST)))
        next_cursor = _cursor(manifest, offset + MAX_TASK_PAGE) if manifest else None
        limitations = [] if choices else ["saved_only"]
        if any(r.get("kind") in ("occurrence", "quota_tracker", "session") for r in rows):
            limitations.append("unverified_readiness")
        if any(r["availability"] != "available" for r in rows):
            limitations.append("unavailable")
        if manifest and manifest["count_relation"] == "at_least":
            limitations.append("query_limit")
        nested_partial = any("truncated" in row.get("field_coverage", {}).values() or row.get("sessions_next_cursor") for row in rows)
        if any(row.get("subtasks_count", 0) > MAX_TASK_MANIFEST or row.get("quota", {}).get("saved_session_count", 0) > MAX_TASK_MANIFEST for row in rows):
            if "query_limit" not in limitations:
                limitations.append("query_limit")
        result = {"schema_version": 4, "kind": "choices" if choices else "tasks", "snapshot_id": str(uuid4()),
                  "read_at": now.isoformat(), "reporting_timezone": zone,
                  "source": {"tool": tool, "mode": args.kind if choices else args.mode, "normalized_arguments": args.model_dump(mode="json", exclude_defaults=True, exclude_none=True)},
                  "count_relation": manifest["count_relation"] if manifest else "exact",
                  "matching_count": manifest["matching_count"] if manifest else len(rows),
                  "next_cursor": next_cursor, "completeness": "partial" if next_cursor or nested_partial or "query_limit" in limitations or "unavailable" in limitations else "complete",
                  "limitations": limitations, "rows": rows}
        if manifest:
            result.update(query_id=manifest["query_id"], query_at=manifest["query_at"])
        result = _bound(result)
    # These are Assistant evidence writes only, after the pure domain read ends.
    with Session(get_engine()) as capture:
        for manifest in manifests:
            capture.add(AssistantQuery(id=manifest["query_id"], conversation_id=conversation_id, evidence=manifest))
        capture.flush()
        for manifest in manifests:
            capture.add_all([AssistantQueryCursor(id=key, query_id=manifest["query_id"], offset=offset)
                             for key, offset in manifest["cursors"].items()])
        capture.commit()
    return result


def task_card(value):
    """Independent public allowlist: no descriptions, hashes, or source arguments."""
    value = validate(value)
    if value["kind"] != "tasks":
        raise ValueError("Choice reads are not Task Cards")
    row_fields = {"id", "kind", "availability", "title", "lifecycle", "relevance", "project", "task_type",
                  "ready_to_plan", "blocked", "blocking_reason", "urgency", "importance", "deadline", "reminder_at",
                  "completed_at", "completion_precision", "completion_local_date", "completion_timezone", "parent_id",
                  "series_id", "period", "quota", "subtasks", "subtasks_count", "subtasks_next_cursor",
                  "sessions", "sessions_next_cursor", "matched_subtask_ids", "planned_dates", "planned_dates_complete"}
    def safe(row):
        return {key: [safe(s) for s in item] if key == "sessions" else item
                for key, item in row.items() if key in row_fields}
    card_fields = {"schema_version", "kind", "snapshot_id", "read_at", "reporting_timezone", "query_id", "query_at",
                   "matching_count", "count_relation", "next_cursor", "completeness", "limitations", "rows"}
    return {key: [safe(row) for row in item] if key == "rows" else item
            for key, item in value.items() if key in card_fields}
