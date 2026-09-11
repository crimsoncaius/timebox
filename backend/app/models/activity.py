"""Additive online command journal; the Actual Block remains the time record."""
import datetime as dt

from sqlalchemy import Boolean, DateTime, Integer, JSON, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class ActivityState(Base):
    __tablename__ = "activity_state"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    cursor: Mapped[int] = mapped_column(Integer, default=0)
    reconciliation: Mapped[dict | None] = mapped_column(JSON, nullable=True)


class ActivityOperation(Base):
    __tablename__ = "activity_operations"
    __table_args__ = (UniqueConstraint("device_id", "sequence"),)
    operation_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    device_id: Mapped[str] = mapped_column(String(100))
    sequence: Mapped[int] = mapped_column(Integer)
    envelope: Mapped[dict] = mapped_column(JSON)
    cursor: Mapped[int] = mapped_column(Integer, unique=True)
    outcome: Mapped[str] = mapped_column(String(30))
    effective_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True))
    intent: Mapped[dict | None] = mapped_column(JSON, nullable=True)
