from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from alembic.config import Config as AlembicConfig
from alembic.script import ScriptDirectory
from sqlalchemy import Engine, text
from sqlalchemy.exc import SQLAlchemyError


@lru_cache
def expected_alembic_head() -> str:
    backend_root = Path(__file__).resolve().parents[1]
    config = AlembicConfig(str(backend_root / "alembic.ini"))
    heads = ScriptDirectory.from_config(config).get_heads()
    if len(heads) != 1:
        raise RuntimeError(f"Expected one Alembic head, found {len(heads)}")
    return heads[0]


def readiness_details(engine: Engine) -> tuple[dict[str, object], int]:
    head = expected_alembic_head()
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
            try:
                current = connection.execute(
                    text("SELECT version_num FROM alembic_version")
                ).scalar_one_or_none()
            except SQLAlchemyError:
                current = None
    except SQLAlchemyError:
        return (
            {
                "status": "not_ready",
                "database": "error",
                "alembic": {"current": None, "head": head},
            },
            503,
        )

    ready = current == head
    return (
        {
            "status": "ready" if ready else "not_ready",
            "database": "ok",
            "alembic": {"current": current, "head": head},
        },
        200 if ready else 503,
    )
