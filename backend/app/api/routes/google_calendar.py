import asyncio
import datetime as dt
import hashlib
import secrets
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import AwareDatetime, BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session

from app.api.activity_gate import guard_legacy_actual_writes
from app.api.deps import require_api_key
from app.core.config import Settings, get_settings
from app.core.time import utc_now
from app.db.activity_admission import admission
from app.db.session import get_db, get_engine
from app.services import google_calendar as calendar
from app.services import google_calendar_source as source

router = APIRouter(prefix="/google-calendar", tags=["google-calendar"])
protected = [Depends(require_api_key), Depends(guard_legacy_actual_writes)]


class Edit(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: int
    action: Literal["move", "remove"]
    start: AwareDatetime | None = None
    end: AwareDatetime | None = None


class Command(BaseModel):
    model_config = ConfigDict(extra="forbid")
    kind: Literal["select", "disconnect", "forget", "exclude", "include", "resolve", "metadata"]
    id: str | None = None
    selected: list[str] = Field(default_factory=list, max_length=100)
    edits: list[Edit] = Field(default_factory=list, max_length=100)
    exclude_ids: list[str] = Field(default_factory=list, max_length=100)
    task_type_id: int | None = None
    task_id: int | None = None
    note: str | None = Field(None, max_length=50000)


class Action(BaseModel):
    command: Command
    fingerprint: str | None = None


def sync(db, settings, now, additional=()):
    from app.models.activity import ActivityState
    activity = db.get(ActivityState, 1)
    if activity and activity.cutover and activity.cutover.get("paused"):
        raise HTTPException(503, "Calendar updates are paused while activity recovery is in progress.")
    try:
        calendar.synchronize(db, settings, now, additional=additional)
        db.commit()
    except (source.GoogleUnavailable, ValueError) as exc:
        db.rollback()
        conn = calendar.connection(db)
        conn.data = {**conn.data, "error": str(exc)}
        db.commit()
        raise HTTPException(503, str(exc)) from exc


@router.get("", dependencies=protected)
def status(date: dt.date | None = None, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    now = utc_now()
    conn = calendar.connection(db)
    last = conn.data.get("last_success")
    if conn.credentials and source.configured(settings) and (not last or now - dt.datetime.fromisoformat(last) >= dt.timedelta(minutes=5)):
        try:
            sync(db, settings, now)
        except HTTPException:
            pass  # Status must remain readable when Google is unavailable.
    calendar.freeze(db, now)
    # Expire pending notices even during an outage without changing valid plans.
    for row in calendar.occurrences(db):
        if row.data.get("status") == "pending" and dt.datetime.fromisoformat(row.data["start"]) <= now:
            row.data = {**row.data, "status": "expired"}
            calendar.log(row, "Conflict expired", now)
    result = calendar.snapshot(db, now, date)
    result["configured"] = source.configured(settings)
    db.commit()
    return result


@router.post("/refresh", dependencies=protected)
def refresh(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    sync(db, settings, utc_now())
    return calendar.snapshot(db, utc_now())


@router.post("/connect", dependencies=protected)
def connect(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    if not source.configured(settings):
        raise HTTPException(503, "Configure authenticated Timebox access and Google OAuth on the backend first.")
    url, oauth = source.authorize(settings, utc_now())
    conn = calendar.connection(db)
    conn.data = {**conn.data, "oauth": source.seal(settings, oauth)}
    db.commit()
    return {"url": url}


@router.get("/callback", response_class=HTMLResponse)
def callback(state: str, code: str | None = None, error: str | None = None,
             db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    conn = calendar.connection(db)
    encrypted = conn.data.get("oauth")
    if not encrypted or not source.configured(settings):
        raise HTTPException(400, "Connection request expired. Start again in Timebox Settings.")
    oauth = source.unseal(settings, encrypted)
    if not secrets.compare_digest(oauth["nonce"], hashlib.sha256(state.encode()).hexdigest()) or dt.datetime.fromisoformat(oauth["expires"]) <= utc_now():
        raise HTTPException(400, "Invalid or expired connection request.")
    conn.data = {**conn.data, "oauth": None}
    db.commit()  # Consume once, including failed/declined callbacks.
    if error or not code:
        return "<h1>Google connection cancelled</h1><p>Return to Timebox Settings to try again.</p>"
    credentials = source.token(settings, grant_type="authorization_code", code=code,
                               redirect_uri=settings.google_redirect_uri, code_verifier=oauth["verifier"])
    if not credentials.get("refresh_token") or not set(source.SCOPES) <= set(credentials.get("scope", "").split()):
        raise HTTPException(400, "Read-only calendar access was not granted. Reconnect and allow both requested permissions.")
    google = source.GoogleReader(credentials["access_token"])
    try:
        calendars = google.calendars()
    finally:
        google.close()
    primary = next((c["id"] for c in calendars if c["primary"]), None)
    if not primary:
        raise HTTPException(400, "Google did not return a primary calendar.")
    if conn.credentials and conn.data.get("account") != primary:
        raise HTTPException(409, "Disconnect the existing Google account before switching accounts.")
    conn.credentials = source.seal(settings, {"refresh_token": credentials["refresh_token"]})
    conn.data = {**conn.data, "account": primary, "calendars": calendars, "error": None}
    db.commit()
    return "<h1>Google Calendar connected</h1><p>Return to Timebox Settings to select calendars. Google events will not be edited.</p>"


@router.post("/action", dependencies=protected)
def action(body: Action, db: Session = Depends(get_db), settings: Settings = Depends(get_settings)):
    command = body.command.model_dump(mode="json")
    now = utc_now()
    if command["kind"] in {"select", "resolve", "include"}:
        sync(db, settings, now, command.get("selected", []))
        now = utc_now()
    mark = calendar.fingerprint(db, command)
    if body.fingerprint and not secrets.compare_digest(body.fingerprint, mark):
        raise HTTPException(409, "The calendar or plan changed. Review a new preview before confirming.")
    before = calendar.activity_selection.plans(db, calendar.plans.zone_name(db))
    try:
        with db.begin_nested() as transaction:
            calendar.apply_command(db, command, now)
            after = calendar.activity_selection.plans(db, calendar.plans.zone_name(db))
            result = calendar.snapshot(db, now)
            if not body.fingerprint:
                transaction.rollback()
        if body.fingerprint:
            db.commit()
        return dict(fingerprint=mark, applied=bool(body.fingerprint), before=before, after=after, state=result)
    except (ValueError, TypeError) as exc:
        db.rollback()
        raise HTTPException(409, str(exc)) from exc


def background_refresh():
    settings = get_settings()
    if not source.configured(settings):
        return
    # ponytail: one poller under existing exclusive admission; move Google reads
    # outside admission with revision checks if request latency becomes material.
    with admission(get_engine(), exclusive=True) as connection, Session(bind=connection, autoflush=False) as db:
        try:
            sync(db, settings, utc_now())
        except HTTPException:
            pass


async def poll():
    while True:
        try:
            await asyncio.to_thread(background_refresh)
        except Exception:
            # A database outage must not kill subsequent polls or leak credentials.
            import logging
            logging.getLogger(__name__).warning("Google calendar refresh unavailable; will retry")
        await asyncio.sleep(300)
