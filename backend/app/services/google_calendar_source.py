"""Read-only Google Calendar transport. No event content or tokens enter logs."""
import datetime as dt
import hashlib
import json
import secrets
from urllib.parse import quote, urlencode

import httpx
from cryptography.fernet import Fernet, InvalidToken

from app.services.planned_intervals import utc

SCOPES = ["https://www.googleapis.com/auth/calendar.calendarlist.readonly",
          "https://www.googleapis.com/auth/calendar.events.readonly"]
API = "https://www.googleapis.com/calendar/v3"


class GoogleUnavailable(ValueError):
    pass


def configured(settings):
    return all((settings.api_key, settings.google_client_id, settings.google_client_secret,
                settings.google_redirect_uri, settings.google_token_key))


def seal(settings, value):
    return Fernet(settings.google_token_key.encode()).encrypt(json.dumps(value).encode()).decode()


def unseal(settings, value):
    try:
        return json.loads(Fernet(settings.google_token_key.encode()).decrypt(value.encode()))
    except (InvalidToken, AttributeError, ValueError) as exc:
        raise GoogleUnavailable("Google credentials cannot be read. Restore the encryption key or reconnect Google Calendar.") from exc


def authorize(settings, now):
    nonce, verifier = secrets.token_urlsafe(32), secrets.token_urlsafe(64)
    import base64
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    url = "https://accounts.google.com/o/oauth2/v2/auth?" + urlencode(dict(
        client_id=settings.google_client_id, redirect_uri=settings.google_redirect_uri,
        response_type="code", scope=" ".join(SCOPES), access_type="offline", prompt="consent",
        state=nonce, code_challenge=challenge, code_challenge_method="S256"))
    return url, dict(nonce=hashlib.sha256(nonce.encode()).hexdigest(), verifier=verifier,
                     expires=(now + dt.timedelta(minutes=10)).isoformat())


def token(settings, **fields):
    try:
        response = httpx.post("https://oauth2.googleapis.com/token", data=dict(
            client_id=settings.google_client_id, client_secret=settings.google_client_secret, **fields), timeout=20)
        if response.status_code != 200:
            raise GoogleUnavailable("Google access could not be renewed. Reconnect Google Calendar.")
        return response.json()
    except httpx.HTTPError as exc:
        raise GoogleUnavailable("Google could not be reached. Retry when the connection recovers.") from exc


class GoogleReader:
    def __init__(self, access_token):
        self.client = httpx.Client(headers={"Authorization": f"Bearer {access_token}"}, timeout=20)

    def close(self):
        self.client.close()

    def get(self, path, params=None, missing=False):
        try:
            response = self.client.get(API + path, params=params)
        except httpx.HTTPError as exc:
            raise GoogleUnavailable("Google could not be reached. Existing plans are unchanged; retry.") from exc
        if missing and response.status_code == 410:
            return {"status": "cancelled"}
        # 404 can mean revoked access. Never infer cancellation from it.
        if response.status_code != 200:
            raise GoogleUnavailable("Calendar access failed. Existing plans are unchanged; retry or reconnect.")
        return response.json()

    def pages(self, path, params=None):
        result, page = [], None
        while True:
            body = self.get(path, {**(params or {}), **({"pageToken": page} if page else {})})
            result.extend(body.get("items", []))
            page = body.get("nextPageToken")
            if not page:
                return result

    def calendars(self):
        return [dict(id=c["id"], name=c.get("summaryOverride") or c.get("summary") or c["id"],
                     primary=bool(c.get("primary"))) for c in self.pages("/users/me/calendarList")
                if not c.get("deleted")]

    def events(self, calendar_id, now, horizon, known):
        path = "/calendars/" + quote(calendar_id, safe="") + "/events"
        result = self.pages(path, dict(singleEvents="true", showDeleted="true", maxResults=2500,
                                      timeMin=now.isoformat(), timeMax=horizon.isoformat()))
        found = {e["id"] for e in result}
        for event_id in sorted(known - found):
            event = self.get(path + "/" + quote(event_id, safe=""), missing=True)
            event["id"] = event_id
            result.append(event)
        return result


def instant(value):
    raw = (value or {}).get("dateTime")
    if not raw:
        return None
    parsed = dt.datetime.fromisoformat(raw.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        from zoneinfo import ZoneInfo
        parsed = parsed.replace(tzinfo=ZoneInfo(value["timeZone"]))
    return utc(parsed).isoformat()


def normalize(event, calendar):
    """Only retain scheduling/identity fields, not private event descriptions or guests."""
    start, end = instant(event.get("start")), instant(event.get("end"))
    declined = any(a.get("self") and a.get("responseStatus") == "declined" for a in event.get("attendees", []))
    original = instant(event.get("originalStartTime")) or (event.get("originalStartTime") or {}).get("date", "")
    uid = event.get("iCalUID")
    identity = f"{uid}|{original}" if uid else f"{calendar}|{event['id']}"
    url = event.get("htmlLink", "")
    if not url.startswith("https://calendar.google.com/") and not url.startswith("https://www.google.com/calendar/"):
        url = "https://calendar.google.com/"
    return dict(identity=identity, event_id=event["id"], calendar_id=calendar,
                name=event.get("summary") or "Busy", start=start, end=end, url=url,
                eligible=bool(start and end and end > start and event.get("status") != "cancelled"
                              and event.get("transparency") != "transparent" and not declined),
                etag=event.get("etag", ""))
