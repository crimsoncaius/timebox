from __future__ import annotations

import secrets

from fastapi import Depends, Header, HTTPException

from app.core.config import Settings, get_settings

API_KEY_HEADER = "X-API-Key"


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
