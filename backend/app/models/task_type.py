from __future__ import annotations

import datetime as dt

from sqlalchemy import DateTime, Index, Integer, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class TaskType(Base):
    """Reusable task category (e.g. work, coding, gym)."""

    __tablename__ = "task_types"
    __table_args__ = (Index("uq_task_types_name", "name", unique=True),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(Text, nullable=False)
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
        "TimeBlock", back_populates="task_type"
    )
    tasks: Mapped[list["Task"]] = relationship("Task", back_populates="task_type")
    recurring_templates: Mapped[list["RecurringTemplate"]] = relationship(
        "RecurringTemplate", back_populates="task_type"
    )
