from __future__ import annotations

import datetime as dt

from sqlalchemy import Boolean, Date, DateTime, Integer, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class Day(Base):
    __tablename__ = "days"
    __table_args__ = (UniqueConstraint("date", name="uq_days_date"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    date: Mapped[dt.date] = mapped_column(Date, nullable=False, index=True)
    start_hour: Mapped[int] = mapped_column(Integer, nullable=False, default=8)
    end_hour: Mapped[int] = mapped_column(Integer, nullable=False, default=20)
    show_full_day: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    time_blocks: Mapped[list["TimeBlock"]] = relationship(
        "TimeBlock",
        back_populates="day",
        cascade="all, delete-orphan",
        order_by="TimeBlock.start_minute",
    )
