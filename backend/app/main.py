from __future__ import annotations

import os
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import inspect
from sqlalchemy.orm import Session

import app.models  # noqa: F401 — register models on Base before create_all
from app.api.activity_gate import guard_legacy_actual_writes
from app.api.deps import require_api_key
from app.api.routes import (
    activity,
    actual_blocks,
    assistant,
    battle_plan,
    days,
    recurring,
    settings,
    task_type_recommendations,
    task_types,
    trends,
)
from app.core.config import Settings, get_settings
from app.core.time import today_in_tz
from app.db.base import Base
from app.db.session import get_db, get_engine, repair_sqlite_actual_record_operation_references
from app.models.app_settings import AppSettings
from app.readiness import readiness_details
from app.services.activity_service import reporting_settings
from app.services.assistant_tracing import setup_tracing
from app.services.day_service import validate_timezone


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
    repair_sqlite_actual_record_operation_references(get_engine())
    tracing = setup_tracing()
    try:
        yield
    finally:
        if tracing:
            tracing.shutdown()


app = FastAPI(title="Timebox API", lifespan=lifespan)

_settings = get_settings()


def cors_middleware_options(settings: Settings) -> dict[str, object]:
    origins = [origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()]
    return {
        "allow_origins": origins,
        "allow_origin_regex": settings.cors_origin_regex or None,
        "allow_credentials": True,
        "allow_methods": ["*"],
        "allow_headers": ["*"],
    }


app.add_middleware(
    CORSMiddleware,
    **cors_middleware_options(_settings),
)

# /health stays open so container health checks keep working without a key.
_protected = [Depends(require_api_key), Depends(guard_legacy_actual_writes)]

app.include_router(days.router, dependencies=_protected)
app.include_router(settings.router, dependencies=_protected)
app.include_router(task_types.router, dependencies=_protected)
app.include_router(task_type_recommendations.router, dependencies=[Depends(require_api_key)])
app.include_router(battle_plan.router, dependencies=_protected)
app.include_router(recurring.router, dependencies=_protected)
app.include_router(actual_blocks.router, dependencies=_protected)
app.include_router(actual_blocks.planned_router, dependencies=_protected)
app.include_router(activity.router, dependencies=_protected)
# Assistant has no Timebox mutation endpoints. Do not hold the legacy write
# admission/database dependency open over an SSE response (Stop must run concurrently).
app.include_router(assistant.router, dependencies=[Depends(require_api_key)])
app.include_router(trends.router, dependencies=_protected)


@app.get("/health")
def health(db: Session = Depends(get_db), settings: Settings = Depends(get_settings)) -> dict[str, str]:
    settings = reporting_settings(db, settings)
    return {
        "status": "ok",
        "today": today_in_tz(settings.app_timezone).isoformat(),
        "timezone": settings.app_timezone,
    }


@app.get("/ready")
def ready() -> JSONResponse:
    body, status_code = readiness_details(get_engine())
    return JSONResponse(content=body, status_code=status_code)
