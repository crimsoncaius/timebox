import pytest

from tests.test_activity_reconciliation import at, make, ranges, send, tracking  # noqa: F401


def history(client):
    base = send(client, make(client.get('/activity').json(), 'start', 8, name='A'))
    base = send(client, make(base, 'stop', 10, sequence=2))
    return send(client, make(base, 'start', 11, sequence=3, name='B'))


def switch(client, base):
    type_id = client.post('/task-types', json={'name': 'design'}).json()['id']
    operation = make(base, 'switch', 9, 30, sequence=4, action_at=at(13), task_type_id=type_id, name='C')
    return operation, send(client, operation)


def undo(snapshot, operation):
    return make(snapshot, 'undo_switch', 14, sequence=5, undo_operation_id=operation['operation_id'])


def test_switch_replaces_history_and_gaps_undo_restores_exact_records(tracking):
    before = history(tracking)
    operation, after = switch(tracking, before)
    assert ranges(after) == [('A', '2026-09-10T08:00:00Z', '2026-09-10T09:30:00Z'), ('C', '2026-09-10T09:30:00Z', None)]
    request = undo(after, operation)
    restored = send(tracking, request)
    assert restored['records'] == before['records']
    assert restored['current'] == before['current']
    assert send(tracking, request)['records'] == before['records']


def test_offline_chain_undo_uses_predecessor_and_same_base(tracking):
    before = history(tracking)
    operation, after = switch(tracking, before)
    request = undo(before, operation)
    request.update(predecessor_id=operation['operation_id'], target_id=None)
    assert send(tracking, request)['records'] == before['records']


@pytest.mark.parametrize('late', [False, True])
def test_conflicting_remote_edit_never_overwritten_by_undo(tracking, late):
    before = history(tracking)
    operation, after = switch(tracking, before)
    edit = make(after, 'edit', 9, 30, device='other', action_at=at(13, 30),
                effective={'mode': 'range', 'at': at(9, 30)}, name='Remote C')
    request = undo(after, operation)
    if late:
        send(tracking, request)
        final = send(tracking, edit)
        assert final['operation_outcomes'][request['operation_id']]['outcome'] == 'superseded'
    else:
        final = send(tracking, edit)
        assert tracking.post('/activity/commands', json=request).status_code == 422
    assert tracking.get('/activity').json()['current']['name'] == 'Remote C'


def test_unrelated_earlier_edit_does_not_invalidate_undo(tracking):
    before = history(tracking)
    operation, after = switch(tracking, before)
    added = send(tracking, make(after, 'add', 6, device='other', action_at=at(13, 30),
        effective={'mode': 'range', 'at': at(6), 'end': at(7)}, target_id=None, name='Earlier'))
    restored = send(tracking, undo(added, operation))
    assert restored['records'][1:] == before['records']
    assert restored['records'][0]['name'] == 'Earlier'


def test_stop_still_rejects_earlier_history_and_stale_switch_target(tracking):
    before = history(tracking)
    assert tracking.post('/activity/commands', json=make(before, 'stop', 9, sequence=4, action_at=at(13))).status_code == 422
    type_id = before['current']['task_type_id']
    bad = make(before, 'switch', 9, sequence=4, action_at=at(13), task_type_id=type_id, target_id=123)
    assert tracking.post('/activity/commands', json=bad).status_code == 422


def test_overnight_switch_restores_notes_and_prior_running_start(tracking):
    before = history(tracking)
    before = send(tracking, make(before, 'edit', 11, sequence=4, action_at=at(12),
        effective={'mode': 'range', 'at': at(11)}, note='Draft notes', name='B'))
    request = make(before, 'switch', 13, sequence=5,
        effective={'mode': 'instant', 'at': '2026-09-09T23:30:00Z'},
        task_type_id=before['current']['task_type_id'], name='Overnight')
    after = send(tracking, request)
    assert ranges(after) == [('Overnight', '2026-09-09T23:30:00Z', None)]
    restored = send(tracking, make(after, 'undo_switch', 15, sequence=6,
        undo_operation_id=request['operation_id']))
    assert restored['records'] == before['records']
    assert restored['current']['start_at'] == before['current']['start_at']
    assert restored['current']['note'] == 'Draft notes'


def test_later_remote_change_reports_undo_as_superseded(tracking):
    before = history(tracking)
    operation, after = switch(tracking, before)
    request = undo(after, operation)
    restored = send(tracking, request)
    final = send(tracking, make(restored, 'stop', 15, device='other'))
    assert final['current'] is None
    assert final['operation_outcomes'][request['operation_id']]['outcome'] == 'superseded'


def test_undo_without_a_switch_cannot_enter_legacy_start_path(tracking):
    request = make(tracking.get('/activity').json(), 'undo_switch', 14,
                   effective={'mode': 'server_now'})
    assert tracking.post('/activity/commands', json=request).status_code == 422
    assert tracking.get('/activity').json()['records'] == []


def earlier_start(client, base, hour, minute=0, sequence=4):
    type_id = client.post('/task-types', json={'name': 'reading'}).json()['id']
    return make(base, 'start', hour, minute, sequence=sequence, action_at=at(13), task_type_id=type_id, name='R', target_id=None)


def stopped_history(client):
    base = send(client, make(client.get('/activity').json(), 'start', 8, name='A'))
    base = send(client, make(base, 'stop', 10, sequence=2))
    base = send(client, make(base, 'start', 11, sequence=3, name='B'))
    return send(client, make(base, 'stop', 12, sequence=4))


def test_snapshot_advertises_start_history(tracking):
    assert tracking.get('/activity').json()['start_history_ready'] is True


def test_earlier_start_replaces_history_and_gaps_undo_restores_exact_records(tracking):
    before = stopped_history(tracking)
    request = earlier_start(tracking, before, 9, 30, sequence=5)
    after = send(tracking, request)
    assert ranges(after) == [('A', '2026-09-10T08:00:00Z', '2026-09-10T09:30:00Z'), ('R', '2026-09-10T09:30:00Z', None)]
    assert after['current']['start_at'] == '2026-09-10T09:30:00Z'
    restored = send(tracking, make(after, 'undo_switch', 14, sequence=6, undo_operation_id=request['operation_id']))
    assert restored['records'] == before['records']
    assert restored['current'] is None


def test_earlier_start_into_gap_only_fills_unrecorded_time(tracking):
    before = stopped_history(tracking)
    after = send(tracking, earlier_start(tracking, before, 12, 30, sequence=5))
    assert ranges(after)[-1] == ('R', '2026-09-10T12:30:00Z', None)
    assert ranges(after)[:-1] == ranges(before)


def test_offline_earlier_start_undo_chains_on_predecessor(tracking):
    before = stopped_history(tracking)
    request = earlier_start(tracking, before, 11, 30, sequence=5)
    send(tracking, request)
    undo_request = make(before, 'undo_switch', 14, sequence=6, undo_operation_id=request['operation_id'],
                        predecessor_id=request['operation_id'], target_id=None)
    assert send(tracking, undo_request)['records'] == before['records']


def test_start_still_rejects_future_instant(tracking):
    before = stopped_history(tracking)
    future = earlier_start(tracking, before, 13, 30, sequence=5)
    assert tracking.post('/activity/commands', json=future).status_code == 422
