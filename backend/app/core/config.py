from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", Path(__file__).resolve().parents[2] / ".env"),
        env_file_encoding="utf-8", extra="ignore",
    )

    database_url: str = "postgresql://timebox:timebox@localhost:5432/timebox"
    app_timezone: str = "America/New_York"
    cors_origins: str = "http://localhost:5174,http://127.0.0.1:5174"
    cors_origin_regex: str | None = None
    # When set, every /days, /settings and /task-types request must send a matching
    # X-API-Key header. Unset (the default) leaves the API open, as it was before.
    api_key: str | None = None
    # Opt in only on an isolated development database. Never a production cutover.
    activity_tracking_dev: bool = False
    openrouter_api_key: str | None = None
    jev_api_key: str | None = None
    typesafe_model: str = "jev-1.13.0"
    # Explicit opt-in telemetry; credentials stay on the backend.
    assistant_trace_endpoint: str | None = None
    assistant_trace_api_key: str | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
