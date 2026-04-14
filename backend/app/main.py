from __future__ import annotations

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import days
from app.core.config import get_settings
from app.core.time import today_in_tz
from app.db.base import Base
from app.db.session import get_engine
from app.services.day_service import validate_timezone


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    validate_timezone(settings.app_timezone)
    if os.getenv("AUTO_CREATE_TABLES") == "1":
        Base.metadata.create_all(bind=get_engine())
    yield


app = FastAPI(title="Timebox API", lifespan=lifespan)

_settings = get_settings()
_origins = [o.strip() for o in _settings.cors_origins.split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_origins if _origins else ["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(days.router)


@app.get("/health")
def health() -> dict[str, str]:
    settings = get_settings()
    return {
        "status": "ok",
        "today": today_in_tz(settings.app_timezone).isoformat(),
        "timezone": settings.app_timezone,
    }
