"""Observable API sequences, including real PostgreSQL transaction races."""
import datetime as dt
import itertools
import uuid
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier

import pytest

from app.core.config import Settings, get_settings
from app.main import app
from tests.test_activity_api import command


@pytest.fixture
def tracking(client):
    app.dependency_overrides[get_settings] = lambda: Settings(activity_tracking_dev=True)
    yield client
    app.dependency_overrides.pop(get_settings, None)


def at(hour, minute=0):
    return f"2026-09-10T{hour:02}:{minute:02}:00+00:00"


def make(snapshot, kind, hour, minute=0, device="web", sequence=1, **payload):
    action = payload.pop("action_at", at(hour, minute))
    return command(snapshot, kind, device=device, sequence=sequence, action_at=action,
                   effective=payload.pop("effective", {"mode": "instant", "at": at(hour, minute)}), **payload)


def send(client, operation):
    response = client.post('/activity/commands', json=operation)
    assert response.status_code == 200, response.text
    return response.json()


def ranges(snapshot):
    return [(r['name'], r['start_at'], r['end_at']) for r in snapshot['records']]


@pytest.mark.parametrize('delivery', list(itertools.permutations(range(3))))
def test_lunch_reading_stop_converge_for_all_deliveries(tracking, delivery):
    initial = tracking.get('/activity').json()
    reading = tracking.post('/task-types', json={'name': 'reading'}).json()['id']
    start = make(initial, 'start', 10)
    base = send(tracking, start)
    lunch = make(base, 'switch', 12, sequence=2, name='Lunch', task_type_id=reading)
    reading_op = make(base, 'switch', 12, 10, device='android', name='Reading', task_type_id=reading)
    stop = make(base, 'stop', 13, sequence=3, predecessor_id=lunch['operation_id'], target_id=None)
    operations = [lunch, reading_op, stop]
    for i in delivery:
        send(tracking, operations[i])
    final = tracking.get('/activity').json()
    assert ranges(final) == [(None, '2026-09-10T10:00:00Z', '2026-09-10T12:00:00Z'),
                             ('Lunch', '2026-09-10T12:00:00Z', '2026-09-10T12:10:00Z'),
                             ('Reading', '2026-09-10T12:10:00Z', '2026-09-10T13:00:00Z')]
    assert final['current'] is None
    assert final['operation_outcomes'][lunch['operation_id']]['outcome'] == 'superseded'
    for operation in operations:
        retried = send(tracking, operation)
        assert retried['records'] == final['records']
        assert retried['cursor'] == final['cursor']
    # Reads used by Day agree with the synchronization API.
    day = tracking.get('/days/2026-09-10').json()
    assert len(day['actual_blocks']) == 3


@pytest.mark.parametrize('reverse', [False, True])
def test_equal_action_ties_use_device_sequence_and_id(tracking, reverse):
    initial = tracking.get('/activity').json()
    operations = [make(initial, 'start', 10, device='a', name='A'), make(initial, 'start', 10, device='z', name='Z')]
    for op in reversed(operations) if reverse else operations:
        send(tracking, op)
    final = tracking.get('/activity').json()
    assert len(final['records']) == 1
    assert final['current']['name'] == 'Z'


def correction(snapshot, kind, start, end, action, **payload):
    return make(snapshot, kind, start, action_at=at(action),
                effective={'mode': 'range', 'at': at(start), 'end': at(end)}, **payload)


@pytest.mark.parametrize('reverse', [False, True])
def test_partial_overlap_preserves_unrelated_time_metadata_and_fragment_identity(tracking, reverse):
    initial = tracking.get('/activity').json()
    writing = correction(initial, 'add', 10, 14, 15, name='Writing', note='keep this note')
    lunch = correction(initial, 'add', 11, 12, 16, device='android', name='Lunch')
    for op in [lunch, writing] if reverse else [writing, lunch]:
        send(tracking, op)
    final = tracking.get('/activity').json()
    assert [r['name'] for r in final['records']] == ['Writing', 'Lunch', 'Writing']
    assert [r['note'] for r in final['records']] == ['keep this note', None, 'keep this note']
    assert len({r['id'] for r in final['records']}) == 3
    writing_ids = [r['id'] for r in final['records'] if r['name'] == 'Writing']
    assert all(final['provenance'][str(i)] == writing['operation_id'] for i in writing_ids)
    unchanged = final['records'][0]
    unrelated = correction(final, 'add', 8, 9, 17, device='third', name='Earlier')
    later = send(tracking, unrelated)
    assert next(r for r in later['records'] if r['id'] == unchanged['id']) == unchanged


def test_known_overlap_rejected_then_delete_leaves_gap_and_delayed_edit_cannot_revive(tracking):
    initial = tracking.get('/activity').json()
    writing = correction(initial, 'add', 10, 12, 13, name='Writing')
    base = send(tracking, writing)
    invalid = correction(base, 'add', 11, 12, 14, sequence=2, target_id=None, name='Lunch')
    rejected = tracking.post('/activity/commands', json=invalid)
    assert rejected.status_code == 422
    assert 'overlaps known' in rejected.text
    assert tracking.get('/activity').json()['records'] == base['records']
    target = base['records'][0]['id']
    deletion = correction(base, 'delete', 10, 12, 16, device='android', target_id=target)
    stopped = send(tracking, deletion)
    assert stopped['records'] == []
    assert target in stopped['tombstones']
    edit = correction(base, 'edit', 10, 12, 15, sequence=2, target_id=target, name='Older correction')
    final = send(tracking, edit)
    assert final['records'] == []
    assert final['acknowledgement']['outcome'] == 'superseded'
    assert send(tracking, writing)['records'] == []
    # Explicitly later intent can fill the gap; receipt replay cannot.
    added = send(tracking, correction(final, 'add', 11, 12, 17, sequence=3, name='Lunch'))
    assert ranges(added) == [('Lunch', '2026-09-10T11:00:00Z', '2026-09-10T12:00:00Z')]


def test_later_retrospective_edit_orders_by_action_not_interval_start_or_calibration(tracking):
    initial = tracking.get('/activity').json()
    older = correction(initial, 'add', 10, 12, 13, name='Old', calibration={'server_at': at(8), 'offset_ms': -3600000})
    newer = correction(initial, 'add', 9, 11, 14, device='android', name='Retrospective', calibration={'server_at': at(14), 'offset_ms': 7200000})
    send(tracking, newer)
    final = send(tracking, older)
    assert [r['name'] for r in final['records']] == ['Retrospective', 'Old']
    assert final['records'][1]['start_at'] == '2026-09-10T11:00:00Z'
    assert tracking.post('/activity/commands', json={**older, 'calibration': newer['calibration']}).status_code == 422


def test_historical_edit_cannot_stop_current_and_running_overlap_is_known(tracking):
    base = send(tracking, make(tracking.get('/activity').json(), 'start', 10))
    bad = correction(base, 'edit', 10, 11, 15, sequence=2, target_id=base['current']['id'])
    assert tracking.post('/activity/commands', json=bad).status_code == 422
    bad = correction(base, 'add', 11, 12, 15, sequence=2, target_id=None)
    assert tracking.post('/activity/commands', json=bad).status_code == 422
    assert tracking.get('/activity').json()['current'] == base['current']


def test_real_postgres_competing_range_writers_and_duplicate_receipts(tracking):
    from app.db.session import get_engine
    if get_engine().dialect.name != 'postgresql':
        pytest.skip('Requires actual PostgreSQL transactions')
    initial = tracking.get('/activity').json()
    operations = [correction(initial, 'add', 10, 14, 15, device='web', name='Writing'),
                  correction(initial, 'add', 11, 12, 16, device='android', name='Lunch')]
    barrier = Barrier(2)
    def concurrent(op):
        barrier.wait(timeout=10)
        return send(tracking, op)
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(concurrent, operations))
    final = tracking.get('/activity').json()
    assert [r['name'] for r in final['records']] == ['Writing', 'Lunch', 'Writing']
    with ThreadPoolExecutor(max_workers=2) as pool:
        duplicates = list(pool.map(concurrent, [operations[0], operations[0]]))
    assert all(r['records'] == final['records'] and r['cursor'] == 2 for r in duplicates)


def test_complete_canonical_records_provenance_and_tombstones_are_permutation_invariant(tracking):
    from sqlalchemy import delete
    from sqlalchemy.orm import Session
    from app.db.session import get_engine
    from app.models.activity import ActivityOperation, ActivityState
    from app.models.time_block import TimeBlock
    initial = tracking.get('/activity').json()
    operations = [correction(initial, 'add', 10, 14, 15, device='a', name='Writing'),
                  correction(initial, 'add', 11, 12, 16, device='b', name='Lunch'),
                  correction(initial, 'add', 10, 11, 17, device='c', name='Reading')]
    expected = None
    # Reset only this test's activity rows, retaining the same Task Type metadata.
    tracking.post('/task-types', json={'name': 'unspecified'})
    for permutation in itertools.permutations(operations):
        with Session(get_engine()) as db:
            db.execute(delete(TimeBlock))
            db.execute(delete(ActivityOperation))
            db.execute(delete(ActivityState))
            db.commit()
        for operation in permutation:
            send(tracking, operation)
        snapshot = tracking.get('/activity').json()
        canonical = {k: snapshot[k] for k in ['records', 'current', 'provenance', 'tombstones', 'operation_outcomes', 'coverage']}
        if expected is None:
            expected = canonical
        else:
            assert canonical == expected


def test_split_preserves_task_plan_note_and_unaffected_record_without_completing_task(tracking):
    task_type = tracking.post('/task-types', json={'name': 'writing'}).json()['id']
    task = tracking.post('/tasks', json={'title': 'Writing task', 'task_type_id': task_type}).json()
    day = tracking.post('/days/2026-09-10/blocks', json={'lane': 'planned', 'task_type_id': task_type,
                         'task_id': task['id'], 'start_minute': 600, 'end_minute': 840}).json()
    plan = day['planned_blocks'][0]['id']
    initial = tracking.get('/activity').json()
    writing = correction(initial, 'add', 10, 14, 15, planned_block_id=plan, note='Original note')
    lunch = correction(initial, 'add', 11, 12, 16, device='android', name='Lunch')
    send(tracking, writing)
    final = send(tracking, lunch)
    fragments = [r for r in final['records'] if r['planned_block_id'] == plan]
    assert len(fragments) == 2
    assert all(r['task_id'] == task['id'] and r['note'] == 'Original note' for r in fragments)
    assert all(r['task']['status'] == task['status'] for r in fragments)
    assert len(tracking.get('/days/2026-09-10').json()['actual_blocks']) == 3


@pytest.mark.parametrize('reverse', [False, True])
def test_competing_disjoint_moves_preserve_both_unaffected_destinations(tracking, reverse):
    base = send(tracking, correction(tracking.get('/activity').json(), 'add', 10, 11, 12))
    target = base['records'][0]['id']
    first = correction(base, 'edit', 8, 9, 14, target_id=target, sequence=2, name='Earlier')
    second = correction(base, 'edit', 12, 13, 15, target_id=target, device='android', name='Later')
    for op in [second, first] if reverse else [first, second]:
        final = send(tracking, op)
    assert [r['name'] for r in final['records']] == ['Earlier', 'Later']
    assert len({r['id'] for r in final['records']}) == 2
    assert len(set(final['provenance'].values())) == 1
    assert target in final['tombstones']

def test_late_stop_uses_action_order_but_only_changes_current_interval(tracking):
    base = send(tracking, make(tracking.get('/activity').json(), 'start', 10, name='Writing'))
    late = make(base, 'stop', 12, action_at=at(13), sequence=2)
    result = send(tracking, late)
    assert ranges(result) == [('Writing', '2026-09-10T10:00:00Z', '2026-09-10T12:00:00Z')]
    assert result['current'] is None
    for hour in (9, 14):
        invalid = make(base, 'stop', hour, action_at=at(13), device='other')
        assert tracking.post('/activity/commands', json=invalid).status_code == 422
    assert tracking.get('/activity').json()['records'] == result['records']

def test_offline_corrections_follow_own_edits_and_keep_current_unchanged(tracking):
    initial = tracking.get('/activity').json()
    running = send(tracking, make(initial, 'start', 18, name='Reading', device='running'))
    add = correction(running, 'add', 10, 12, 19, name='Writing')
    base = send(tracking, add)
    row = next(r for r in base['records'] if r['name'] == 'Writing')
    first = correction(base, 'edit', 8, 9, 20, sequence=2, target_id=row['id'])
    moved = send(tracking, first)
    second = correction(base, 'edit', 6, 7, 21, sequence=3, target_id=row['id'], target_source=add['operation_id'], target_start_at=at(8))
    moved_again = send(tracking, second)
    assert [(r['start_at'], r['end_at']) for r in moved_again['records'] if r['name'] == 'Writing'] == [('2026-09-10T06:00:00Z', '2026-09-10T07:00:00Z')]
    deletion = correction(base, 'delete', 6, 7, 22, sequence=4, target_id=row['id'], target_source=add['operation_id'], target_start_at=at(6))
    final = send(tracking, deletion)
    assert final['records'] == running['records']
    assert final['current'] == running['current']


def test_predecessor_late_transition_cannot_rewrite_before_current_start(tracking):
    initial = tracking.get('/activity').json()
    start = make(initial, 'start', 10)
    started = send(tracking, start)
    for kind in ('switch', 'stop'):
        invalid = make(initial, kind, 9, action_at=at(13), sequence=2, predecessor_id=start['operation_id'], target_id=None)
        assert tracking.post('/activity/commands', json=invalid).status_code == 422
    assert tracking.get('/activity').json()['records'] == started['records']
