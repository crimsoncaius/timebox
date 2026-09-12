from __future__ import annotations

import datetime as dt
import enum

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    DDL,
    Enum,
    ForeignKey,
    Index,
    Integer,
    Text,
    event,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class BlockLane(str, enum.Enum):
    planned = "planned"
    actual = "actual"


class TimeBlock(Base):
    __tablename__ = "time_blocks"
    __table_args__ = (
        Index("uq_time_blocks_legacy_correspondence", "planned_block_id", unique=True,
              postgresql_where=text("activity_source IS NULL"), sqlite_where=text("activity_source IS NULL")),
        CheckConstraint(
            "planned_block_id IS NULL OR lane = 'actual'",
            name="ck_time_blocks_correspondence_from_actual",
        ),
        CheckConstraint(
            "end_at IS NULL OR start_at IS NOT NULL",
            name="ck_time_blocks_actual_end_requires_start",
        ),
        CheckConstraint(
            "lane != 'planned' OR (day_id IS NOT NULL AND start_minute IS NOT NULL "
            "AND end_minute IS NOT NULL AND start_at IS NULL AND end_at IS NULL)",
            name="ck_time_blocks_planned_grid_shape",
        ),
        CheckConstraint(
            "lane != 'actual' OR start_at IS NULL OR "
            "(day_id IS NULL AND start_minute IS NULL AND end_minute IS NULL)",
            name="ck_time_blocks_definitive_actual_shape",
        ),
        CheckConstraint(
            "lane != 'actual' OR start_at IS NULL OR end_at IS NULL OR end_at > start_at",
            name="ck_time_blocks_actual_positive_interval",
        ),
        Index(
            "uq_time_blocks_one_active_actual",
            "lane",
            unique=True,
            postgresql_where=text("lane = 'actual' AND start_at IS NOT NULL AND end_at IS NULL"),
            sqlite_where=text("lane = 'actual' AND start_at IS NOT NULL AND end_at IS NULL"),
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    day_id: Mapped[int | None] = mapped_column(
        ForeignKey("days.id", ondelete="CASCADE"), nullable=True, index=True
    )
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
    name: Mapped[str | None] = mapped_column(Text, nullable=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    activity_source: Mapped[str | None] = mapped_column(Text, nullable=True)
    # When set on an Actual block, points to the Planned block this row completes (as planned).
    planned_block_id: Mapped[int | None] = mapped_column(
        ForeignKey("time_blocks.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    start_minute: Mapped[int | None] = mapped_column(Integer, nullable=True)
    end_minute: Mapped[int | None] = mapped_column(Integer, nullable=True)
    # Definitive Actual Blocks are authoritative instant intervals. `end_at` is
    # nullable only for the single active Actual Block. Legacy Actual rows retain
    # their grid fields until the coordinated destructive migration.
    start_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    end_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    day: Mapped["Day | None"] = relationship("Day", back_populates="time_blocks")
    task_type: Mapped["TaskType"] = relationship("TaskType", back_populates="time_blocks")
    task: Mapped["Task | None"] = relationship("Task", back_populates="time_blocks")
    planned_block: Mapped["TimeBlock | None"] = relationship(
        "TimeBlock",
        remote_side=[id],
        foreign_keys=[planned_block_id],
        back_populates="completion_actuals",
    )
    completion_actuals: Mapped[list["TimeBlock"]] = relationship(
        "TimeBlock",
        foreign_keys=[planned_block_id],
        back_populates="planned_block",
        order_by="TimeBlock.start_at, TimeBlock.id",
    )

    @property
    def actual_block_id(self) -> int | None:
        """The explicit corresponding Actual Block, never an inferred match."""

        return self.completion_actuals[0].id if self.completion_actuals else None

    @property
    def actual_duration_minutes(self) -> float:
        now = dt.datetime.now(dt.timezone.utc)
        def utc(value):
            return value.replace(tzinfo=dt.timezone.utc) if value.tzinfo is None else value
        return sum(max(0, (utc(row.end_at) if row.end_at else now).timestamp() - utc(row.start_at).timestamp()) / 60
                   if row.start_at else row.end_minute - row.start_minute for row in self.completion_actuals)

    @property
    def actual_block_ids(self) -> list[int]:
        return [row.id for row in self.completion_actuals]


class ActualBlockRecordOperation(Base):
    """One-shot Undo capability for Record actual as planned."""

    __tablename__ = "actual_block_record_operations"

    token: Mapped[str] = mapped_column(Text, primary_key=True)
    actual_block_id: Mapped[int | None] = mapped_column(
        ForeignKey("time_blocks.id", ondelete="SET NULL"),
        nullable=True,
        unique=True,
        index=True,
    )
    planned_block_id: Mapped[int | None] = mapped_column(
        ForeignKey("time_blocks.id", ondelete="SET NULL"), nullable=True, index=True
    )
    invalidated_at: Mapped[dt.datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    undone_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


# SQLite is the backend test dialect and has no exclusion constraints. These
# triggers mirror the production PostgreSQL overlap/correspondence constraints so
# model tests exercise database enforcement rather than service-only validation.
_sqlite_validate_actual_insert = DDL(
    """
    CREATE TRIGGER validate_time_block_actual_insert
    BEFORE INSERT ON time_blocks
    WHEN NEW.lane = 'actual'
      AND (NEW.start_at IS NOT NULL OR NEW.planned_block_id IS NOT NULL)
    BEGIN
      SELECT CASE WHEN NEW.planned_block_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM time_blocks planned
        WHERE planned.id = NEW.planned_block_id AND planned.lane = 'planned'
      ) THEN RAISE(ABORT, 'Actual correspondence must target a Planned Block') END;
      SELECT CASE WHEN EXISTS (
        SELECT 1 FROM time_blocks actual
        WHERE actual.lane = 'actual' AND actual.start_at IS NOT NULL
          AND NEW.start_at < COALESCE(actual.end_at, '9999-12-31 23:59:59')
          AND actual.start_at < COALESCE(NEW.end_at, '9999-12-31 23:59:59')
      ) THEN RAISE(ABORT, 'Actual Blocks cannot overlap') END;
    END
    """
).execute_if(dialect="sqlite")

_sqlite_validate_actual_update = DDL(
    """
    CREATE TRIGGER validate_time_block_actual_update
    BEFORE UPDATE OF lane, planned_block_id, start_at, end_at ON time_blocks
    WHEN NEW.lane = 'actual'
      AND (NEW.start_at IS NOT NULL OR NEW.planned_block_id IS NOT NULL)
    BEGIN
      SELECT CASE WHEN NEW.planned_block_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM time_blocks planned
        WHERE planned.id = NEW.planned_block_id AND planned.lane = 'planned'
      ) THEN RAISE(ABORT, 'Actual correspondence must target a Planned Block') END;
      SELECT CASE WHEN EXISTS (
        SELECT 1 FROM time_blocks actual
        WHERE actual.id != NEW.id AND actual.lane = 'actual' AND actual.start_at IS NOT NULL
          AND NEW.start_at < COALESCE(actual.end_at, '9999-12-31 23:59:59')
          AND actual.start_at < COALESCE(NEW.end_at, '9999-12-31 23:59:59')
      ) THEN RAISE(ABORT, 'Actual Blocks cannot overlap') END;
    END
    """
).execute_if(dialect="sqlite")

event.listen(TimeBlock.__table__, "after_create", _sqlite_validate_actual_insert)
event.listen(TimeBlock.__table__, "after_create", _sqlite_validate_actual_update)
