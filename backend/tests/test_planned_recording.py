import datetime as dt

import pytest
from sqlalchemy.orm import Session

from app.api.routes import actual_blocks
from app.db.session import get_engine
from app.main import app
from app.models.activity import ActivityState
from app.services import activity_service


@pytest.fixture
def recording(client):
    now = [dt.datetime(2026, 8, 30, 10, 30, tzinfo=dt.timezone.utc)]
    app.dependency_overrides[actual_blocks.capture_utc_now] = lambda: now[0]
    task_type = client.post('/task-types', json={'name': 'Writing'}).json()['id']
    day = client.post('/days/2026-08-30/blocks', json=dict(lane='planned', task_type_id=task_type,
        start_minute=600, end_minute=660, name='Chapter', note='Plan note')).json()
    plan = day['planned_blocks'][0]['id']
    url = f'/planned-blocks/{plan}/record-actual-as-planned'
    def invoke(preview=None):
        response = client.post(url, json={'until': preview['end_at'], 'fingerprint': preview['fingerprint']} if preview else {})
        assert response.status_code == 201, response.text
        return response.json()
    def undo(result):
        return client.post(f'/planned-blocks/{plan}/undo-record-actual-as-planned', json={'undo_token': result['undo_token']})
    def actual(start, end, name='Other', note='Keep me'):
        response = client.post('/actual-blocks', json=dict(task_type_id=task_type, start_at=f'2026-08-30T{start}:00Z',
            end_at=f'2026-08-30T{end}:00Z', name=name, note=note))
        assert response.status_code == 201, response.text
        return response.json()
    yield now, invoke, undo, actual, url
    app.dependency_overrides.pop(actual_blocks.capture_utc_now)


def test_partial_repeat_exact_match_and_undo(client, recording):
    now, record, undo, _, _ = recording
    first = record()
    assert first['actual_block']['end_at'].startswith('2026-08-30T10:30:00')
    assert record()['status'] == 'already_recorded'
    now[0] = now[0].replace(hour=11)
    preview = record()
    assert preview['status'] == 'confirmation_required'
    second = record(preview)
    assert second['actual_block']['end_at'].startswith('2026-08-30T11:00:00')
    assert undo(second).status_code == 204
    restored = client.get(f"/actual-blocks/{first['actual_block']['id']}").json()
    assert restored['end_at'] == first['actual_block']['end_at']


def test_matching_times_with_edited_note_is_already_recorded(client, recording):
    _, record, _, _, _ = recording
    first = record()
    actual_id = first['actual_block']['id']
    edited = client.patch(f'/actual-blocks/{actual_id}', json={'note': 'Written after recording'})
    assert edited.status_code == 200
    again = record()
    assert again['status'] == 'already_recorded'
    assert again['actual_block']['id'] == actual_id
    assert client.get(f'/actual-blocks/{actual_id}').json()['note'] == 'Written after recording'


def test_spanning_record_split_and_atomic_restore(client, recording):
    now, record, undo, actual, _ = recording
    now[0] = now[0].replace(hour=12)
    original = actual('09:45', '11:15')
    preview = record()
    assert preview['conflicts'][0]['note'] == 'Keep me'
    result = record(preview)
    day = client.get('/days/2026-08-30').json()
    records = [p['actual_block'] for p in day['actual_blocks']]
    assert len(records) == 3
    assert sum(r['note'] == 'Keep me' for r in records) == 2
    assert undo(result).status_code == 204
    records = client.get('/days/2026-08-30').json()['actual_blocks']
    assert len(records) == 1
    assert records[0]['actual_block'] == original


def test_stale_preview_requires_confirmation_and_freezes_end(client, recording):
    now, record, _, actual, _ = recording
    conflict = actual('10:00', '10:15')
    preview = record()
    client.patch(f"/actual-blocks/{conflict['id']}", json={'note': 'New note'})
    now[0] = now[0].replace(minute=45)
    refreshed = record(preview)
    assert refreshed['stale'] and refreshed['status'] == 'confirmation_required'
    assert refreshed['end_at'] == preview['end_at']
    result = record(refreshed)
    assert dt.datetime.fromisoformat(result['actual_block']['end_at']) == dt.datetime.fromisoformat(preview['end_at'])


def test_undo_rejects_edit_to_surviving_portion(client, recording):
    _, record, undo, actual, _ = recording
    original = actual('09:00', '11:15')
    result = record(record())
    client.patch(f"/actual-blocks/{original['id']}", json={'note': 'Later intent'})
    assert undo(result).status_code == 422


def test_future_and_zero_duration_rejected(client, recording):
    now, _, _, _, url = recording
    now[0] = now[0].replace(hour=9)
    assert client.post(url).status_code == 422
    now[0] = now[0].replace(hour=10, minute=0)
    assert client.post(url).status_code == 422


def test_running_activity_and_journal_replay_survive_undo(client, recording):
    now, record, undo, _, _ = recording
    response = client.post('/actual-blocks/start', json={'start_at': '2026-08-30T09:45:00Z', 'name': 'Current'})
    original = response.json()
    assert response.status_code == 201
    with Session(get_engine()) as db:
        db.add(ActivityState(id=1, enabled=True, cursor=0))
        db.commit()
    result = record(record())
    current = client.get('/actual-blocks/active').json()
    assert current['name'] == 'Current'
    assert current['start_at'].startswith('2026-08-30T10:30:00')
    now[0] = now[0].replace(minute=50)
    assert undo(result).status_code == 204
    with Session(get_engine()) as db:
        snapshot = activity_service.read(db, 'UTC')
        assert snapshot.current.id == original['id']
        assert snapshot.current.start_at.isoformat().startswith('2026-08-30T09:45:00')
        from app.models.activity import ActivityOperation
        from app.services import activity_reconciliation as rec
        from sqlalchemy import select
        state = db.get(ActivityState, 1)
        rec.materialize(db, state, list(db.scalars(select(ActivityOperation))))
        db.commit()
    assert client.get('/actual-blocks/active').json()['id'] == original['id']


def test_multiple_conflicts_and_unrelated_history_restore_together(client, recording):
    now, record, undo, actual, _ = recording
    now[0] = now[0].replace(hour=12)
    untouched = actual('08:00', '09:00')
    first = actual('09:45', '10:15', name='First')
    second = actual('10:30', '11:15', name='Second')
    preview = record()
    assert len(preview['conflicts']) == 2
    result = record(preview)
    assert client.get(f"/actual-blocks/{untouched['id']}").json() == untouched
    assert undo(result).status_code == 204
    assert client.get(f"/actual-blocks/{first['id']}").json() == first
    assert client.get(f"/actual-blocks/{second['id']}").json() == second


def test_disjoint_current_activity_is_unchanged(client, recording):
    now, record, undo, _, _ = recording
    now[0] = now[0].replace(hour=12)
    current = client.post('/actual-blocks/start', json={'start_at': '2026-08-30T11:30:00Z', 'name': 'Current'}).json()
    result = record()
    assert result['status'] == 'recorded'
    assert client.get('/actual-blocks/active').json() == current
    assert undo(result).status_code == 204
    assert client.get('/actual-blocks/active').json() == current
