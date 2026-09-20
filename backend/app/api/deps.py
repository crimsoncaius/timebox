from __future__ import annotations

import datetime as dt
import secrets
from typing import Callable

from fastapi import Depends, Header, HTTPException, Path

from app.core.config import Settings, get_settings
from app.core.time import parse_iso_date, utc_now

API_KEY_HEADER = "X-API-Key"


def day_date(date: str = Path(description="Local calendar day, YYYY-MM-DD")) -> dt.date:
    """Parse a `{date}` path segment, rejecting anything that is not a calendar day."""

    try:
        return parse_iso_date(date)
    except ValueError as exc:
        raise HTTPException(
            status_code=422, detail="Invalid date, use YYYY-MM-DD"
        ) from exc


def utc_clock_seam() -> Callable[[], dt.datetime]:
    """Build one router's clock dependency.

    Each router keeps its *own* function object so tests can override the
    routers' clocks independently; sharing one would make a single override
    move every router's clock at once.
    """

    def capture_utc_now() -> dt.datetime:
        return utc_now()

    return capture_utc_now


def require_api_key(
    x_api_key: str | None = Header(default=None, alias=API_KEY_HEADER),
    settings: Settings = Depends(get_settings),
) -> None:
    """Reject requests without a matching API key, but only once one is configured.

    The Android client always sends the header; the web frontend and the local dev
    setup leave API_KEY unset, in which case this is a no-op.
    """
    expected = settings.api_key
    if not expected:
        return
    if x_api_key is None:
        raise HTTPException(
            status_code=401,
            detail="Missing API key",
            headers={"WWW-Authenticate": API_KEY_HEADER},
        )
    if not secrets.compare_digest(x_api_key, expected):
        raise HTTPException(status_code=403, detail="Invalid API key")
