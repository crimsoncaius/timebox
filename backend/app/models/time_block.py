from __future__ import annotations

import datetime as dt
import enum

from sqlalchemy import DateTime, Enum, ForeignKey, Integer, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class BlockLane(str, enum.Enum):
    planned = "planned"
    actual = "actual"


class TimeBlock(Base):
    __tablename__ = "time_blocks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    day_id: Mapped[int] = mapped_column(ForeignKey("days.id", ondelete="CASCADE"), nullable=False, index=True)
    lane: Mapped[BlockLane] = mapped_column(
        Enum(BlockLane, name="block_lane", native_enum=False, length=16),
        nullable=False,
    )
    task_type_id: Mapped[int] = mapped_column(
        ForeignKey("task_types.id", ondelete="RESTRICT"),
        nullable=False,
        index=True,
    )
    task_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True, index=True
    )
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    # When set on an Actual block, points to the Planned block this row completes (as planned).
    planned_block_id: Mapped[int | None] = mapped_column(
        ForeignKey("time_blocks.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    start_minute: Mapped[int] = mapped_column(Integer, nullable=False)
    end_minute: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    day: Mapped["Day"] = relationship("Day", back_populates="time_blocks")
    task_type: Mapped["TaskType"] = relationship("TaskType", back_populates="time_blocks")
    task: Mapped["Task | None"] = relationship("Task", back_populates="time_blocks")
