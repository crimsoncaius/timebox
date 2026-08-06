from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    database_url: str = "postgresql://timebox:timebox@localhost:5432/timebox"
    app_timezone: str = "America/New_York"
    cors_origins: str = "http://localhost:5174,http://127.0.0.1:5174"
    cors_origin_regex: str | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
