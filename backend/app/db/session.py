from __future__ import annotations

from collections.abc import Generator
from functools import lru_cache

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.config import get_settings


@lru_cache
def get_engine():
    settings = get_settings()
    url = settings.database_url
    if url.startswith("sqlite"):
        engine_options = {"connect_args": {"check_same_thread": False}}
        # In-memory SQLite databases need one shared connection so every test
        # session sees the same schema. File-backed databases must instead use
        # SQLite's regular pool: sharing one connection across FastAPI worker
        # threads can corrupt concurrent reminder and page-load requests.
        if ":memory:" in url or url.rstrip("/") == "sqlite:":
            engine_options["poolclass"] = StaticPool
        return create_engine(url, **engine_options)
    return create_engine(url, pool_pre_ping=True)


@lru_cache
def _session_factory():
    return sessionmaker(autocommit=False, autoflush=False, bind=get_engine())


def get_db() -> Generator[Session, None, None]:
    db = _session_factory()()
    try:
        yield db
    finally:
        db.close()
