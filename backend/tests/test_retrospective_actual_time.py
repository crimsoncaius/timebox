import datetime as dt

import pytest

from app.api.routes import actual_blocks
from app.core.config import get_settings
from app.main import app


@pytest.fixture
def fixed_clock():
    calls = []
    def capture():
        calls.append(True)
        return dt.datetime(2026, 9, 1, 12, tzinfo=dt.UTC)
    app.dependency_overrides[actual_blocks.capture_utc_now] = capture
    yield calls
    app.dependency_overrides.pop(actual_blocks.capture_utc_now, None)


@pytest.mark.parametrize('start,end', [
    ('2026-09-01T12:00:00.000001Z', '2026-09-01T13:00:00Z'),
    ('2026-09-01T11:00:00Z', '2026-09-01T12:00:00.000001Z'),
])
def test_future_create_is_atomic(client, fixed_clock, start, end):
    response = client.post('/actual-blocks', json={'start_at': start, 'end_at': end})
    assert response.status_code == 422, response.text
    assert 'future' in response.json()['detail']
    assert len(fixed_clock) == 1
    assert client.get('/days/2026-09-01/preview').json()['actual_blocks'] == []
    assert client.get('/task-types').json() == []


@pytest.mark.parametrize('patch', [
    {'start_at': '2026-09-01T12:00:00.000001Z', 'end_at': None},
    {'end_at': '2026-09-01T12:00:00.000001Z'},
])
def test_future_patch_preserves_all_fields(client, fixed_clock, patch):
    created = client.post('/actual-blocks', json={
        'start_at': '2026-09-01T11:00:00Z', 'end_at': '2026-09-01T11:30:00Z', 'note': 'original',
    })
    assert created.status_code == 201, created.text
    original = created.json()
    fixed_clock.clear()
    response = client.patch(f"/actual-blocks/{original['id']}", json={**patch, 'note': 'must not persist'})
    assert response.status_code == 422, response.text
    assert 'future' in response.json()['detail']
    assert len(fixed_clock) == 1
    assert client.get(f"/actual-blocks/{original['id']}").json() == original


def test_boundary_and_offset_values_supported(client, fixed_clock):
    response = client.post('/actual-blocks', json={
        'start_at': '2026-09-01T19:00:00+08:00', 'end_at': '2026-09-01T20:00:00+08:00',
    })
    assert response.status_code == 201, response.text
    response = client.patch(f"/actual-blocks/{response.json()['id']}", json={
        'start_at': '2026-09-01T12:00:00Z', 'end_at': None,
    })
    assert response.status_code == 200, response.text
    assert len(fixed_clock) == 2


@pytest.mark.parametrize('method,path', [('post', '/actual-blocks'), ('patch', '/actual-blocks/1')])
def test_tracking_gate_still_wins(client, fixed_clock, method, path):
    app.dependency_overrides[get_settings] = lambda: get_settings().model_copy(update={'activity_tracking_dev': True})
    try:
        response = getattr(client, method)(path, json={
            'start_at': '2030-01-01T11:00:00Z', 'end_at': '2030-01-01T12:00:00Z',
        })
        assert response.status_code == 409, response.text
        assert 'Legacy Actual writes are disabled' in response.json()['detail']
        assert fixed_clock == []
    finally:
        app.dependency_overrides.pop(get_settings, None)
