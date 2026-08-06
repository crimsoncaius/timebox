from __future__ import annotations

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import days, settings, task_types
from app.core.config import Settings, get_settings
from app.core.time import today_in_tz
from sqlalchemy import inspect

from app.db.base import Base
from app.db.session import get_engine
from app.models.app_settings import AppSettings
from app.services.day_service import validate_timezone

import app.models  # noqa: F401 — register models on Base before create_all


def _ensure_app_settings_table() -> None:
    """Older AUTO_CREATE_TABLES SQLite files may predate app_settings; add the table if missing."""
    engine = get_engine()
    insp = inspect(engine)
    if "app_settings" not in insp.get_table_names():
        AppSettings.__table__.create(bind=engine)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    validate_timezone(settings.app_timezone)
    if os.getenv("AUTO_CREATE_TABLES") == "1":
        Base.metadata.create_all(bind=get_engine())
        _ensure_app_settings_table()
    yield


app = FastAPI(title="Timebox API", lifespan=lifespan)

_settings = get_settings()
_origins = [o.strip() for o in _settings.cors_origins.split(",") if o.strip()]


def cors_middleware_options(settings: Settings) -> dict[str, object]:
    origins = [origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()]
    return {
        "allow_origins": origins if origins else ["*"],
        "allow_origin_regex": settings.cors_origin_regex or None,
        "allow_credentials": True,
        "allow_methods": ["*"],
        "allow_headers": ["*"],
    }


app.add_middleware(
    CORSMiddleware,
    **cors_middleware_options(_settings),
)

app.include_router(days.router)
app.include_router(settings.router)
app.include_router(task_types.router)


@app.get("/health")
def health() -> dict[str, str]:
    settings = get_settings()
    return {
        "status": "ok",
        "today": today_in_tz(settings.app_timezone).isoformat(),
        "timezone": settings.app_timezone,
    }
