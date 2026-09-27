"""Independent duration goals and their period-effective target history."""
from __future__ import annotations

import datetime as dt

from sqlalchemy import CheckConstraint, Date, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class TimeGoal(Base):
    __tablename__ = "time_goals"
    __table_args__ = (
        CheckConstraint("interval >= 1 AND interval <= 100", name="ck_time_goal_interval"),
        CheckConstraint("unit IN ('day', 'week', 'month')", name="ck_time_goal_unit"),
        CheckConstraint("end_date IS NULL OR end_date >= start_date", name="ck_time_goal_dates"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    task_type_id: Mapped[int] = mapped_column(ForeignKey("task_types.id", ondelete="RESTRICT"), index=True)
    unit: Mapped[str] = mapped_column(String(5))
    interval: Mapped[int] = mapped_column(Integer)
    start_date: Mapped[dt.date] = mapped_column(Date)
    end_date: Mapped[dt.date | None] = mapped_column(Date)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(),
    )
    task_type: Mapped[TaskType] = relationship("TaskType")
    targets: Mapped[list[TimeGoalTarget]] = relationship(
        cascade="all, delete-orphan", order_by="TimeGoalTarget.effective_date", lazy="selectin",
    )


class TimeGoalTarget(Base):
    __tablename__ = "time_goal_targets"
    __table_args__ = (CheckConstraint("minutes > 0", name="ck_time_goal_target_positive"),)

    goal_id: Mapped[int] = mapped_column(ForeignKey("time_goals.id", ondelete="CASCADE"), primary_key=True)
    effective_date: Mapped[dt.date] = mapped_column(Date, primary_key=True)
    minutes: Mapped[int] = mapped_column(Integer)
