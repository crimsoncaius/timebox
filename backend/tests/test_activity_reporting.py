from test_activity_api import tracking


def test_reporting_initializes_once_and_changes_only_explicitly(tracking):
    first = tracking.post('/activity/reporting-timezone/initialize', json={'timezone': 'Asia/Singapore'})
    assert first.status_code == 200, first.text
    assert first.json()['reporting_timezone'] == 'Asia/Singapore'
    assert tracking.post('/activity/reporting-timezone/initialize', json={'timezone': 'America/New_York'}).json()['reporting_timezone'] == 'Asia/Singapore'
    assert tracking.get('/days/2026-09-11').json()['meta']['timezone'] == 'Asia/Singapore'
    assert tracking.put('/activity/reporting-timezone', json={'timezone': 'America/New_York'}).json()['reporting_timezone'] == 'America/New_York'
    assert tracking.get('/days/2026-09-11').json()['meta']['timezone'] == 'America/New_York'
    assert tracking.get('/health').json()['timezone'] == 'America/New_York'
    assert tracking.put('/activity/reporting-timezone', json={'timezone': 'Unknown/Zone'}).status_code == 422


def test_cross_midnight_day_shares_use_elapsed_dst_duration(tracking):
    from test_activity_api import command
    initial = tracking.post('/activity/reporting-timezone/initialize', json={'timezone': 'America/New_York'}).json()
    kind = tracking.post('/task-types', json={'name': 'Reading'}).json()
    added = tracking.post('/activity/commands', json=command(initial, 'add', task_type_id=kind['id'], effective={'mode': 'range', 'at': '2025-11-02T04:00:00Z', 'end': '2025-11-03T06:00:00Z'}))
    assert added.status_code == 200, added.text
    row = added.json()['records'][0]
    day = tracking.get('/days/2025-11-02')
    assert day.status_code == 200, day.text
    assert day.json()['actual_blocks'][0]['duration_minutes'] == 1500
    assert day.json()['actual_blocks'][0]['day_length_minutes'] == 1500
    next_day = tracking.get('/days/2025-11-03').json()
    assert next_day['actual_blocks'][0]['actual_block']['id'] == row['id']
    assert next_day['actual_blocks'][0]['duration_minutes'] == 60
    updated = tracking.put('/activity/reporting-timezone', json={'timezone': 'UTC'}).json()
    assert updated['records'] == added.json()['records']
    assert tracking.get('/days/2025-11-02').json()['actual_blocks'][0]['duration_minutes'] == 1200


def test_concurrent_initialization_uses_one_device_zone(tracking):
    import pytest
    from concurrent.futures import ThreadPoolExecutor
    from threading import Barrier
    from app.db.session import get_engine
    if get_engine().dialect.name != 'postgresql':
        pytest.skip('Requires isolated PostgreSQL')
    barrier = Barrier(2)
    def initialize(zone):
        barrier.wait()
        response = tracking.post('/activity/reporting-timezone/initialize', json={'timezone': zone})
        assert response.status_code == 200, response.text
        return response.json()['reporting_timezone']
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(initialize, ['Asia/Singapore', 'America/New_York']))
    assert results[0] == results[1]
    assert tracking.get('/activity').json()['reporting_timezone'] == results[0]


def test_running_spring_day_keeps_identity_and_23_hour_share(tracking):
    from test_activity_api import command
    initial = tracking.post('/activity/reporting-timezone/initialize', json={'timezone': 'America/New_York'}).json()
    at = '2025-03-09T05:00:00Z'
    saved = tracking.post('/activity/commands', json=command(initial, 'start', action_at=at, effective={'mode': 'instant', 'at': at})).json()
    projection = tracking.get('/days/2025-03-09').json()['actual_blocks'][0]
    assert projection['duration_minutes'] == 1380
    assert projection['actual_block']['id'] == saved['current']['id']
    assert projection['actual_block']['end_at'] is None
    assert tracking.get('/activity').json()['current'] == saved['current']
