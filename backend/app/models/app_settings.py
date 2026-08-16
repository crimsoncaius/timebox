from __future__ import annotations

import datetime as dt

from sqlalchemy import Boolean, DateTime, Integer, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class AppSettings(Base):
    """Singleton row for global day-window defaults (single-user v1)."""

    __tablename__ = "app_settings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    start_hour: Mapped[int] = mapped_column(Integer, nullable=False, default=8)
    end_hour: Mapped[int] = mapped_column(Integer, nullable=False, default=20)
    show_full_day: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    week_start: Mapped[str] = mapped_column(Text, nullable=False, default="monday", server_default="monday")
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )
