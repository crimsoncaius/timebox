"""Run the normal API with disposable issue 297 review data on port 12075.

No prototype endpoints, timestamp overrides, or production imports. Reuse keeps
review edits; remove only this scratch database to seed a new scenario.
"""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "artifacts" / "plan-now-297-production.sqlite"
DATA.parent.mkdir(exist_ok=True)
os.environ["DATABASE_URL"] = "sqlite:///" + DATA.as_posix()
os.environ["APP_TIMEZONE"] = "Asia/Singapore"
os.environ["ACTIVITY_TRACKING_DEV"] = "true"
os.environ["API_KEY"] = ""


def seed():
    import datetime as dt

    from sqlalchemy import select
    from sqlalchemy.orm import Session

    from app.db.base import Base
    from app.db.session import get_engine
    from app.models.activity import ActivityState
    from app.models.task_type import TaskType
    from app.models.time_block import BlockLane, TimeBlock
    from app.services.planned_intervals import set_interval

    Base.metadata.create_all(get_engine())
    with Session(get_engine()) as db:
        if db.scalar(select(TaskType.id).limit(1)):
            return
        now = dt.datetime.now(dt.UTC)
        writing, email, meeting = [TaskType(name=name) for name in ("Writing", "Email", "Meeting")]
        db.add_all([writing, email, meeting])
        db.add(ActivityState(id=1, enabled=True, cursor=0, reporting_timezone="Asia/Singapore"))
        db.flush()
        for kind, name, note, left, right in [(writing, "Draft the proposal", "Keep this Writing note", 20, 50), (meeting, "Team catch-up", None, 60, 90)]:
            plan = TimeBlock(lane=BlockLane.planned, task_type_id=kind.id, name=name, note=note)
            set_interval(db, plan, now + dt.timedelta(minutes=left), now + dt.timedelta(minutes=right), "Asia/Singapore")
        db.commit()


if __name__ == "__main__":
    import uvicorn

    from app.main import app

    seed()
    uvicorn.run(app, host="127.0.0.1", port=12075)
