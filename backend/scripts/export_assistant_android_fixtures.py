"""Export deterministic current-server payloads for Android contract/native tests.

Always uses a fresh in-memory database; never reads local service configuration.
Run from backend: .venv/Scripts/python scripts/export_assistant_android_fixtures.py
"""
import os
os.environ["DATABASE_URL"] = "sqlite:///:memory:"
os.environ["APP_TIMEZONE"] = "UTC"
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import json
import datetime as dt
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import TimeBlock
from uuid import uuid4
from sqlalchemy.orm import Session
import app.models
from app.db.base import Base
from app.db.session import get_engine
from app.models.battle_plan import Task
from app.services import assistant_storage
from app.services import assistant_task_operations as ops
from app.services.assistant_task_intents import ProposeTaskChangesArgs
from app.services.assistant_tasks import ReadTasksArgs, read_tasks, task_card
from app.core.time import utc_now

Base.metadata.create_all(get_engine())
with Session(get_engine()) as db:
    tasks = [Task(title=title, description="Before literal <script>alert('data')</script>\n" + "Private original line. " * 80) for title in ["Review the complete client brief and all its detailed appendices before Wednesday", "Report", "Report", "Fourth saved Task"]]
    db.add_all(tasks); db.flush()
    db.add(Task(parent_id=tasks[0].id, title="Check appendix", checked=False))
    ids = [task.id for task in tasks]
    db.commit()
conversation, run = str(uuid4()), str(uuid4())
assistant_storage.create(conversation, [])
assistant_storage.begin(conversation, run, "Explicit description edit", "deterministic-fixture")
card = task_card(read_tasks(ReadTasksArgs(mode="search"), conversation))
proposal = ops.propose(ProposeTaskChangesArgs.model_validate({"operations": [
    {"op": "patch_task", "target": {"id": ids[0]}, "set": {"description": "After literal **not markdown**\n" + "Revised private line. " * 80, "ready_to_plan": True}},
    {"op": "add_subtask", "parent": {"id": ids[0]}, "ref": "second", "title": "Review second appendix"}
]}), conversation, run, utc_now(), "UTC")
assistant_storage.capture(run, "Review changes.", {}, None, "completed")
full = ops.review(proposal["proposal_id"])
_, result = ops.execute(proposal["proposal_id"], proposal["operation_id"], str(uuid4()))
fixture = {"card": card, "proposal": proposal, "full": full, "result": result}
def preview(operations):
    conv, run_id = str(uuid4()), str(uuid4())
    assistant_storage.create(conv, [])
    assistant_storage.begin(conv, run_id, "Explicit task change", "deterministic-fixture")
    p = ops.propose(ProposeTaskChangesArgs.model_validate({"operations": operations}), conv, run_id, utc_now(), "UTC")
    assistant_storage.capture(run_id, "Review changes.", {}, None, "completed")
    return p

with Session(get_engine()) as db:
    kind = TaskType(name="Work")
    day = Day(date=utc_now().date() + dt.timedelta(days=1))
    task = Task(title="Finish report", ready_to_plan=True, is_blocked=True, blocking_reason="Review")
    db.add_all([kind, day, task]); db.flush()
    tid = task.id
    db.add(TimeBlock(day_id=day.id, lane="planned", task_type_id=kind.id, task_id=tid, name="Final edit", start_minute=600, end_minute=660, note="PRIVATE SUPPORTING NOTE"))
    db.add(TimeBlock(lane="actual", task_type_id=kind.id, task_id=tid, start_at=utc_now()-dt.timedelta(hours=1)))
    db.commit()
complete = preview([{"op": "complete_now", "target": {"id": tid}}])
_, complete_result = ops.execute(complete["proposal_id"], complete["operation_id"], str(uuid4()))
undo_id = complete_result["receipt"]["undo"]["undo_operation_id"]
_, undo_result = ops.undo(complete["operation_id"], undo_id, str(uuid4()))
original_after_undo = ops.statuses([{"operation_id": complete["operation_id"]}])["operations"][0]
dated = preview([{"op": "complete_at", "target": {"id": tid}, "when": {"kind": "day_offset", "days": -1}}])
_, dated_result = ops.execute(dated["proposal_id"], dated["operation_id"], str(uuid4()))
reopen = preview([{"op": "reopen_task", "target": {"id": tid}}])
fixture.update(complete=complete, complete_result=complete_result, undo_result=undo_result, original_after_undo=original_after_undo, dated=dated, dated_result=dated_result, reopen=reopen)
output = Path(__file__).resolve().parents[2] / "android/app/src/test/resources/assistant-tasks.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(fixture, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Exported server fixtures to", output)
