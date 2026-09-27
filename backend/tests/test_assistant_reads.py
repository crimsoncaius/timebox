import datetime as dt
import json

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db.session import get_engine
from app.models.battle_plan import Task
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock
from app.services import assistant_plan as reads
from app.services.assistant_presentation import snapshot, text_schedule, validate_snapshot


def test_blocks_snapshot_read_only_overlap_future_and_text(monkeypatch):
    now = dt.datetime(2026, 9, 27, 1, 30, tzinfo=dt.UTC)
    monkeypatch.setattr(reads, 'utc_now', lambda: now)
    with Session(get_engine()) as db:
        parent, child, sibling = TaskType(name='work'), TaskType(name='work/code'), TaskType(name='workout')
        today, future = Day(date=now.date()), Day(date=now.date()+dt.timedelta(days=1))
        task = Task(title='Ship', description='D'*2100)
        db.add_all([parent, child, sibling, today, future, task]); db.flush()
        plan = TimeBlock(day_id=today.id, lane=BlockLane.planned, start_minute=60, end_minute=120, task_type_id=child.id, task_id=task.id, note='N'*2100)
        db.add(plan); db.flush()
        db.add_all([TimeBlock(lane=BlockLane.actual, start_at=now-dt.timedelta(hours=3), end_at=None, task_type_id=child.id, task_id=task.id, planned_block_id=plan.id, note='private'),
                    TimeBlock(day_id=future.id, lane=BlockLane.planned, start_minute=60, end_minute=120, task_type_id=child.id)])
        db.commit()
    result = snapshot(reads.read_activity(reads.ReadActivityArgs(lane='both', task_type=' WORK ')))
    assert result['schema_version'] == 2
    assert len(result['blocks']) == 2
    actual = next(row for row in result['blocks'] if row['lane']=='actual')
    assert (actual['duration_minutes'], actual['minutes_in_date'], actual['running']) == (180,90,True)
    assert actual['planned_block_id'] is not None
    assert 'private' not in json.dumps(result)
    assert validate_snapshot(result) == result
    text = snapshot(reads.read_activity(reads.ReadActivityArgs(include_text=True)))
    assert len(text['blocks'][0]['supporting_note']) == 2000
    assert len(text['blocks'][0]['task_description']) == 2000
    assert 'NNNN' not in text_schedule(text)
    for lane in ('planned','actual','both'):
        future = reads.read_activity(reads.ReadActivityArgs(when='2026-09-28',lane=lane))
        assert len(future['blocks']) == (0 if lane=='actual' else 1)
        assert future['recurring_not_materialized']
    assert 'error' in reads.read_activity(reads.ReadActivityArgs(task_type='unknown'))
    assert 'error' in reads.read_activity(reads.ReadActivityArgs(when='this_week'))
    assert reads.read_activity(reads.ReadActivityArgs(when='2026-09-29'))['blocks'] == []
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(Day)) == 2


def test_v2_selected_snapshot_uses_safe_text_on_legacy_clients(client, monkeypatch):
    from app.api.routes import assistant
    from app.core.config import get_settings
    from app.services.assistant_sessions import conversations
    from tests.test_assistant import decode, send
    monkeypatch.setattr(get_settings(), 'openrouter_api_key', 'test-key-not-real')
    item = snapshot(reads.read_activity(reads.ReadActivityArgs(include_text=True)))
    async def fake(*args, **kwargs):
        yield 'snapshot_read', item
        yield 'plan_card', item
    monkeypatch.setattr(assistant, 'agent_events', fake)
    key = client.post('/assistant/conversations', json={'capabilities':['plan_card_v1']}).json()['conversation_id']
    events = decode(send(client, key))
    assert events[-1][0] == 'completed'
    assert 'plan_card' not in [kind for kind, _ in events]
    assert any(kind == 'text_delta' and 'Blocks for' in value['text'] for kind, value in events)
    conversations.items.clear()


def test_date_boundaries_use_reporting_zone_and_dst(monkeypatch):
    now = dt.datetime(2026, 11, 2, 7, tzinfo=dt.UTC)
    monkeypatch.setattr(reads, 'utc_now', lambda: now)
    monkeypatch.setattr(reads, 'reporting_settings', lambda db, s: s.model_copy(update={'app_timezone':'America/New_York'}))
    with Session(get_engine()) as db:
        kind = TaskType(name='work'); db.add(kind); db.flush()
        db.add(TimeBlock(lane=BlockLane.actual, task_type_id=kind.id,
                        start_at=dt.datetime(2026,11,1,4,tzinfo=dt.UTC),
                        end_at=dt.datetime(2026,11,2,5,tzinfo=dt.UTC)))
        db.commit()
    result = reads.read_activity(reads.ReadActivityArgs(when='yesterday', lane='actual'))
    assert result['date'] == '2026-11-01'
    assert result['blocks'][0]['minutes_in_date'] == 1500
    assert result['blocks'][0]['duration_minutes'] == 1500


def test_read_tool_exposes_only_new_tool_and_rejects_ranges():
    import asyncio

    from app.services.assistant_agent import build_agent, read_activity_tool
    seen = []
    class Model:
        def bind_tools(self, tools):
            seen.extend(t.name for t in tools)
            return self
    build_agent(Model())
    assert seen == ['read_activity']
    result = asyncio.run(read_activity_tool.ainvoke({'lane':'both'}))
    assert result['schema_version'] == 2
    assert result['snapshot_id']
    invalid = asyncio.run(read_activity_tool.ainvoke({'when': {'start':'2026-09-01', 'end':'2026-09-27'}}))
    assert 'Invalid read_activity arguments' in invalid
