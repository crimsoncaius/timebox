from tests.test_activity_api import tracking, command


def test_shared_question_confirmation_and_stale_candidates(tracking):
    snapshot = tracking.post('/activity/commands', json=command(tracking.get('/activity').json(), 'start')).json()
    assert snapshot['check_in']['threshold_minutes'] == 60
    assert snapshot['check_in']['question'] is None
import datetime as dt

def event(tracking, snapshot, action, sequence=1, device='detector', **fields):
    action_at = fields.pop('action_at', snapshot['server_at'])
    state = snapshot['check_in']
    payload = dict(action=action, generation=state['generation'], rearm=state['rearm'], **fields)
    response = tracking.post('/activity/commands', json=command(snapshot, 'check_in', sequence, device, action_at=action_at, check_in=payload))
    assert response.status_code == 200, response.text
    return response.json()


def running(tracking):
    snapshot = tracking.get('/activity').json()
    at = (dt.datetime.fromisoformat(snapshot['server_at'].replace('Z','+00:00')) - dt.timedelta(hours=2)).isoformat()
    return tracking.post('/activity/commands', json=command(snapshot, 'start', action_at=at, effective={'mode':'instant','at':at})).json()


def candidate(tracking, snapshot, **fields):
    return event(tracking, snapshot, 'candidate', capability='supported', permission='granted', observed='idle',
                 coverage_start=snapshot['current']['start_at'], coverage_end=snapshot['server_at'], **fields)


def test_candidates_merge_confirmation_rearms_and_old_events_cannot_return(tracking):
    first = running(tracking)
    pending = candidate(tracking, first)
    question = pending['check_in']['question']['id']
    duplicate = candidate(tracking, first, device='android')
    assert duplicate['check_in']['question']['id'] == question
    confirmed = event(tracking, pending, 'confirm', device='answer', question_id=question)
    assert confirmed['check_in']['question'] is None
    assert confirmed['current'] == first['current']
    assert candidate(tracking, first, sequence=2)['check_in']['question'] is None
    assert candidate(tracking, confirmed, sequence=3)['check_in']['question'] is None
    assert event(tracking, pending, 'confirm', device='answer', sequence=2, question_id=question)['check_in']['rearm'] == 1
    assert tracking.get('/activity').json()['check_in']['question'] is None


def test_remote_active_suppresses_unknown_never_qualifies_and_switch_clears(tracking):
    first = running(tracking)
    unknown = event(tracking, first, 'candidate')
    assert unknown['check_in']['question'] is None
    active = event(tracking, first, 'observe', device='android', observed='active', capability='supported', permission='granted', coverage_start=first['server_at'], coverage_end=first['server_at'])
    assert candidate(tracking, active, sequence=2)['check_in']['question'] is None
    assert candidate(tracking, active, sequence=3, enabled=False)['check_in']['question'] is None


def test_stop_and_delivery_preserve_question_identity_without_escalation(tracking):
    pending = candidate(tracking, running(tracking))
    question = pending['check_in']['question']['id']
    delivered = event(tracking, pending, 'delivery', device='detector', sequence=2, question_id=question)
    repeated = event(tracking, delivered, 'delivery', device='web', sequence=2, question_id=question)
    assert repeated['check_in']['question']['delivery']['device_id'] == 'detector'
    dismissed = event(tracking, repeated, 'notification_dismiss', device='android', sequence=2, question_id=question)
    assert dismissed['check_in']['question']['id'] == question
    stopped = tracking.post('/activity/commands', json=command(dismissed,'stop', device='stopper', effective={'mode':'instant','at':dismissed['server_at']})).json()
    assert stopped['check_in']['question'] is None
    assert event(tracking, pending, 'confirm', device='answer', question_id=question)['current'] is None


def test_offline_confirmation_preserves_newer_remote_activity(tracking):
    first = running(tracking)
    pending = candidate(tracking, first)
    now = dt.datetime.fromisoformat(first['server_at'].replace('Z', '+00:00'))
    confirmed_at = (now - dt.timedelta(minutes=90)).isoformat()
    active_at = (now - dt.timedelta(minutes=85)).isoformat()
    event(tracking, pending, 'observe', device='active', observed='active', capability='supported', permission='granted', coverage_start=active_at, coverage_end=active_at)
    confirmed = event(tracking, pending, 'confirm', device='offline-answer', question_id=pending['check_in']['question']['id'], action_at=confirmed_at)
    result = event(tracking, confirmed, 'candidate', device='returning', observed='idle', capability='supported', permission='granted', coverage_start=confirmed_at, coverage_end=(now-dt.timedelta(minutes=30)).isoformat())
    assert result['check_in']['question'] is None
    full_interval = candidate(tracking, confirmed, device='later')
    assert full_interval['check_in']['question']['id'] != pending['check_in']['question']['id']


def test_candidate_switch_race_is_serialized_in_postgres(tracking):
    from app.db.session import get_engine
    from concurrent.futures import ThreadPoolExecutor
    from threading import Barrier
    import pytest
    if get_engine().dialect.name != 'postgresql':
        pytest.skip('Requires isolated PostgreSQL')
    first = running(tracking)
    barrier = Barrier(2)
    def detect():
        barrier.wait()
        return candidate(tracking, first)
    def stop():
        barrier.wait()
        return tracking.post('/activity/commands', json=command(first, 'stop', device='stopper', effective={'mode':'instant','at':first['server_at']}))
    with ThreadPoolExecutor(2) as pool:
        jobs = [pool.submit(detect), pool.submit(stop)]
        for job in jobs:
            job.result()
    final = tracking.get('/activity').json()
    assert final['current'] is None
    assert final['check_in']['question'] is None

def test_concurrent_candidates_have_one_durable_question(tracking):
    from app.db.session import get_engine
    from concurrent.futures import ThreadPoolExecutor
    from threading import Barrier
    import pytest
    if get_engine().dialect.name != 'postgresql':
        pytest.skip('Requires isolated PostgreSQL')
    first = running(tracking)
    barrier = Barrier(2)
    def detect(device):
        barrier.wait()
        return candidate(tracking, first, device=device)['check_in']['question']['id']
    with ThreadPoolExecutor(2) as pool:
        jobs = [pool.submit(detect, device) for device in ['one', 'two']]
        ids = [job.result() for job in jobs]
    assert ids[0] == ids[1] == tracking.get('/activity').json()['check_in']['question']['id']


def test_remote_active_arriving_after_offline_confirmation_is_not_discarded(tracking):
    first = running(tracking)
    pending = candidate(tracking, first)
    now = dt.datetime.fromisoformat(first['server_at'].replace('Z', '+00:00'))
    confirmed_at = (now - dt.timedelta(minutes=90)).isoformat()
    active_at = (now - dt.timedelta(minutes=85)).isoformat()
    confirmed = event(tracking, pending, 'confirm', device='offline-answer', question_id=pending['check_in']['question']['id'], action_at=confirmed_at)
    event(tracking, pending, 'observe', device='active', observed='active', capability='supported', permission='granted', coverage_start=active_at, coverage_end=active_at)
    result = event(tracking, confirmed, 'candidate', device='returning', observed='idle', capability='supported', permission='granted', coverage_start=confirmed_at, coverage_end=(now-dt.timedelta(minutes=30)).isoformat())
    assert result['check_in']['question'] is None


def test_browser_active_lower_bound_suppresses_other_browser_full_idle_interval(tracking):
    first = running(tracking)
    now = dt.datetime.fromisoformat(first['server_at'].replace('Z', '+00:00'))
    # The browser's short native detector proves input within the last minute.
    # Preserve only its earliest possible instant; do not fabricate input now.
    active_at = (now - dt.timedelta(minutes=1)).isoformat()
    event(tracking, first, 'observe', device='active-browser', observed='active',
          capability='supported', permission='granted', coverage_start=active_at,
          coverage_end=active_at)
    result = event(tracking, first, 'candidate', device='idle-browser', observed='idle',
                   capability='supported', permission='granted', threshold_minutes=15,
                   coverage_start=(now - dt.timedelta(minutes=15)).isoformat(),
                   coverage_end=first['server_at'])
    assert result['check_in']['question'] is None
    assert result['current'] == first['current']
