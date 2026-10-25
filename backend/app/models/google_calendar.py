"""Source identity outlives placement, exclusion, and historical block deletion."""
from sqlalchemy import JSON, ForeignKey, Integer, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class GoogleCalendarConnection(Base):
    __tablename__ = "google_calendar_connection"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    credentials: Mapped[str | None] = mapped_column(Text)
    data: Mapped[dict] = mapped_column(JSON, default=dict)


class GoogleCalendarOccurrence(Base):
    __tablename__ = "google_calendar_occurrences"
    id: Mapped[str] = mapped_column(Text, primary_key=True)
    account: Mapped[str] = mapped_column(Text, index=True)
    block_id: Mapped[int | None] = mapped_column(ForeignKey("time_blocks.id", ondelete="SET NULL"), unique=True)
    task_type_id: Mapped[int | None] = mapped_column(ForeignKey("task_types.id", ondelete="SET NULL"))
    task_id: Mapped[int | None] = mapped_column(ForeignKey("tasks.id", ondelete="SET NULL"))
    note: Mapped[str | None] = mapped_column(Text)
    data: Mapped[dict] = mapped_column(JSON, default=dict)
