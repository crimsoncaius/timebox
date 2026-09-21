from __future__ import annotations

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.db.base import Base
from app.db.session import get_engine
from app.main import app


def snapshot():
    with Session(get_engine()) as db:
        return {table.name: list(db.execute(select(table)).all()) for table in Base.metadata.sorted_tables}


@pytest.fixture(params=[False, True], ids=['legacy-permitted', 'activity-gated'])
def deletion_client(client, request):
    app.dependency_overrides[get_settings] = lambda: Settings(activity_tracking_dev=request.param)
    yield client, request.param
    app.dependency_overrides.pop(get_settings, None)


def references(client):
    tid = client.post('/task-types', json={'name': 'work'}).json()['id']
    assert client.post('/tasks', json={'title': 'Keep classification', 'task_type_id': tid}).status_code == 201
    response = client.post('/recurring-templates', json={
        'title': 'Keep series classification', 'task_type_id': tid,
        'mode': 'scheduled', 'frequency': 'daily', 'start_date': '2099-01-01',
    })
    assert response.status_code == 201, response.text
    return tid


@pytest.mark.parametrize('failure', ['descendants', 'cascade-descendants', 'missing-target', 'same-target', 'blocks-in-use', 'tasks-in-use'])
def test_failed_delete_preserves_every_row(deletion_client, failure):
    client, gated = deletion_client
    tid = references(client)
    query = {'clear_task_references': 'true'}
    expected = 409
    if 'descendants' in failure:
        assert client.post('/task-types', json={'name': 'work/coding'}).status_code == 200
    if failure == 'cascade-descendants':
        query['cascade_blocks'] = 'true'
    if failure in {'missing-target', 'same-target'}:
        query['migrate_blocks_to'] = 99999 if failure == 'missing-target' else tid
        expected = 409 if gated else 422
    if failure == 'blocks-in-use':
        assert client.post('/days/2026-06-01/blocks', json={
            'lane': 'planned', 'task_type_id': tid, 'start_minute': 0, 'end_minute': 30,
        }).status_code == 200
    if failure == 'tasks-in-use':
        query = {}
    before = snapshot()
    response = client.delete(f'/task-types/{tid}', params=query)
    assert response.status_code == expected, response.text
    assert snapshot() == before


@pytest.mark.parametrize('mode', ['clear', 'cascade', 'migrate'])
def test_successful_delete_updates_all_references(deletion_client, mode):
    client, gated = deletion_client
    tid = references(client)
    target = client.post('/task-types', json={'name': 'other'}).json()['id']
    query = {'clear_task_references': 'true'}
    if mode != 'clear':
        assert client.post('/days/2026-06-01/blocks', json={
            'lane': 'planned', 'task_type_id': tid, 'start_minute': 0, 'end_minute': 30,
        }).status_code == 200
        query.update({'cascade_blocks': 'true'} if mode == 'cascade' else {'migrate_blocks_to': target})
    before = snapshot()
    response = client.delete(f'/task-types/{tid}', params=query)
    if gated and mode != 'clear':
        assert response.status_code == 409
        assert snapshot() == before
        return
    assert response.status_code == 204, response.text
    after = snapshot()
    assert all(row.task_type_id is None for row in after['tasks'])
    assert all(row.task_type_id is None for row in after['recurring_templates'])
    assert all(row.id != tid for row in after['task_types'])
    assert all(row.task_type_id == target for row in after['time_blocks'])
    assert len(after['time_blocks']) == (1 if mode == 'migrate' else 0)

def test_unexpected_delete_failure_rolls_back_reference_changes(client, monkeypatch):
    from app.models.task_type import TaskType

    tid = references(client)
    before = snapshot()
    original_delete = Session.delete

    def fail_delete(db, row):
        if isinstance(row, TaskType):
            raise RuntimeError('simulated storage failure')
        return original_delete(db, row)

    monkeypatch.setattr(Session, 'delete', fail_delete)
    with pytest.raises(RuntimeError, match='simulated storage failure'):
        client.delete(f'/task-types/{tid}', params={'clear_task_references': 'true'})
    assert snapshot() == before
