import datetime as dt
from pathlib import Path

import sqlalchemy as sa
from alembic.config import Config
from alembic.migration import MigrationContext
from alembic.operations import Operations

from alembic import command
from app.core.config import get_settings
from app.db.base import Base


def test_migration_preserves_plans_correspondence_and_actual_constraints(tmp_path, monkeypatch):
    url = f"sqlite:///{(tmp_path / 'plans.sqlite').as_posix()}"
    engine = sa.create_engine(url)
    Base.metadata.create_all(engine)
    with engine.begin() as db:
        db.execute(sa.text("DROP TABLE plan_now_operations"))
        with Operations(MigrationContext.configure(db)).batch_alter_table("time_blocks") as batch:
            batch.drop_constraint("ck_time_blocks_planned_grid_shape", type_="check")
            batch.create_check_constraint("ck_time_blocks_planned_grid_shape", "lane != 'planned' OR (day_id IS NOT NULL AND start_minute IS NOT NULL AND end_minute IS NOT NULL AND start_at IS NULL AND end_at IS NULL)")
        db.execute(Base.metadata.tables["task_types"].insert().values(id=1, name="Work"))
        db.execute(Base.metadata.tables["days"].insert().values(id=1, date=dt.date(2026, 10, 4)))
        db.execute(sa.text("INSERT INTO time_blocks (id, day_id, lane, task_type_id, start_minute, end_minute, note) VALUES (1, 1, 'planned', 1, 600, 660, 'Keep')"))
        db.execute(sa.text("INSERT INTO time_blocks (id, lane, task_type_id, start_at, end_at, planned_block_id) VALUES (2, 'actual', 1, '2026-10-04 10:00:00', '2026-10-04 11:00:00', 1)"))
        db.execute(sa.text("CREATE TABLE alembic_version (version_num VARCHAR(255) NOT NULL)"))
        db.execute(sa.text("INSERT INTO alembic_version VALUES ('038_time_goals')"))
    monkeypatch.setattr(get_settings(), "database_url", url)
    command.upgrade(Config(str(Path(__file__).resolve().parents[1] / 'alembic.ini')), "head")
    with engine.begin() as db:
        assert db.execute(sa.text("SELECT note FROM time_blocks WHERE id=1")).scalar() == "Keep"
        assert db.execute(sa.text("SELECT planned_block_id FROM time_blocks WHERE id=2")).scalar() == 1
        assert not db.execute(sa.text("PRAGMA foreign_key_check")).all()
        db.execute(sa.text("UPDATE time_blocks SET start_at='2026-10-04 10:00:59.732', end_at='2026-10-04 10:15:59.732' WHERE id=1"))
        triggers = db.execute(sa.text("SELECT name FROM sqlite_master WHERE type='trigger'")).scalars().all()
        assert "validate_time_block_actual_insert" in triggers
        assert "validate_time_block_actual_update" in triggers
    engine.dispose()
