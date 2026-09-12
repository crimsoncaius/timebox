"""Plan/Task recording through the public activity and Day APIs."""
import pytest
from tests.test_activity_reconciliation import tracking, make, send
from tests.test_actual_blocks_api import _planned_block


def setup_plan(client):
    kind = client.post('/task-types', json={'name': 'Writing'}).json()['id']
    task = client.post('/tasks', json={'title': 'Draft chapter', 'task_type_id': kind, 'ready_to_plan': True}).json()
    plan = _planned_block(client, kind, date='2026-09-10', start_minute=600, end_minute=720,
                          task_id=task['id'], name='Chapter one', note='Outline first')
    return kind, task, plan


def test_empty_start_interruption_resume_and_durable_correspondence(tracking):
    kind, task, plan = setup_plan(tracking)
    initial = tracking.get('/activity').json()
    first = send(tracking, make(initial, 'start', 10))
    assert {k: first['current'][k] for k in ('name', 'note', 'task_id', 'planned_block_id', 'task_type_id')} == dict(
        name='Chapter one', note='Outline first', task_id=task['id'], planned_block_id=plan['id'], task_type_id=kind)
    interrupted = send(tracking, make(first, 'switch', 10, 30, sequence=2, task_type_id=kind, name='Break'))
    assert interrupted['current']['planned_block_id'] is None
    assert interrupted['current']['task_id'] is None
    resumed_command = make(interrupted, 'switch', 11, sequence=3, planned_block_id=plan['id'])
    resumed = send(tracking, resumed_command)
    final = send(tracking, make(resumed, 'stop', 11, 30, sequence=4))
    assert send(tracking, resumed_command)['records'] == final['records']
    day = tracking.get('/days/2026-09-10').json()
    planned = day['planned_blocks'][0]
    assert len(planned['actual_block_ids']) == 2
    assert planned['actual_duration_minutes'] == 60
    assert day['time_blocks'][0]['actual_block_ids'] == planned['actual_block_ids']
    assert tracking.get("/tasks").json()["items"][0]["status"] == task['status']
    assert tracking.post(f"/planned-blocks/{plan['id']}/record-actual-as-planned").status_code == 409
    assert tracking.get('/activity').json()['records'] == final['records']


@pytest.mark.parametrize('change', ['rename', 'reclassify', 'delete'])
def test_plan_changes_preserve_actual_facts_and_unlink_survives_replay(tracking, change):
    kind, task, plan = setup_plan(tracking)
    first = send(tracking, make(tracking.get('/activity').json(), 'start', 10))
    stopped = send(tracking, make(first, 'stop', 10, 30, sequence=2))
    resumed = send(tracking, make(stopped, 'start', 11, sequence=3))
    before = resumed['records']
    url = f"/days/2026-09-10/blocks/{plan['id']}"
    other = tracking.post('/task-types', json={'name': 'Reading'}).json()['id']
    response = tracking.delete(url) if change == 'delete' else tracking.patch(url, json={'name': 'New plan'} if change == 'rename' else {'task_type_id': other})
    assert response.status_code in (200, 204), response.text
    after = send(tracking, make(tracking.get('/activity').json(), 'stop', 11, 30, sequence=4))
    assert [r['name'] for r in after['records']] == ['Chapter one', 'Chapter one']
    assert [r['id'] for r in after['records']] == [r['id'] for r in before]
    assert all(r['planned_block_id'] == (plan['id'] if change == 'rename' else None) for r in after['records'])
    assert all(r['task_id'] == task['id'] and r['task_type_id'] == kind for r in after['records'])


def test_delayed_selection_keeps_snapshot_and_does_not_adopt_later_plan(tracking):
    kind, task, plan = setup_plan(tracking)
    initial = tracking.get('/activity').json()
    operation = make(initial, 'start', 10, selection_snapshot=True, task_type_id=kind,
                     task_id=task['id'], planned_block_id=plan['id'], name='Chapter one', note='Original note')
    assert tracking.patch(f"/days/2026-09-10/blocks/{plan['id']}", json={'name': 'Renamed'}).status_code == 200
    saved = send(tracking, operation)
    assert saved['current']['name'] == 'Chapter one'
    assert saved['current']['note'] == 'Original note'
    assert send(tracking, operation)['current'] == saved['current']
    stopped = send(tracking, make(saved, 'stop', 10, 30, sequence=2))
    explicit_empty = send(tracking, make(stopped, 'start', 11, sequence=3, selection_snapshot=True))
    assert explicit_empty['current']['planned_block_id'] is None
    assert explicit_empty['current']['task_type']['name'] == 'unspecified'


def test_direct_task_and_session_leave_plans_readiness_and_completion_unchanged(tracking):
    today = tracking.get('/health').json()['today']
    kind = tracking.post('/task-types', json={'name': 'Exercise'}).json()['id']
    response = tracking.post('/recurring-templates', json={'title': 'Gym', 'mode': 'quota', 'frequency': 'weekly',
        'interval': 1, 'quota_count': 2, 'start_date': today, 'task_type_id': kind})
    assert response.status_code == 201, response.text
    parent = tracking.get('/tasks').json()['items'][0]
    session = parent['session_tasks'][0]
    initial = tracking.get('/activity').json()
    rejected = tracking.post('/activity/commands', json=make(initial, 'start', 10, task_id=parent['id']))
    assert rejected.status_code == 422 and 'Quota Trackers' in rejected.text
    saved = send(tracking, make(initial, 'start', 10, task_id=session['id']))
    assert saved['current']['task_id'] == session['id']
    assert saved['current']['name'] == session['title']
    assert saved['current']['planned_block_id'] is None
    stopped = send(tracking, make(saved, 'stop', 11, sequence=2))
    assert stopped['current'] is None
    after = tracking.get('/tasks').json()['items'][0]
    assert after['quota_completed'] == 0
    assert after['session_tasks'][0]['status'] == session['status']
    assert after['session_tasks'][0]['ready_to_plan'] == session['ready_to_plan']
    assert tracking.get('/days/2026-09-10').json()['planned_blocks'] == []
