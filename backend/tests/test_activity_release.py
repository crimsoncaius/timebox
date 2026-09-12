"""Cross-slice release checks through Activity, Day and Task APIs."""
import pytest

from tests.test_activity_planning import setup_plan
from tests.test_activity_reconciliation import tracking, correction, send


@pytest.mark.parametrize("reverse", [False, True])
def test_offline_correction_fragments_keep_plan_links_after_zone_change(tracking, reverse):
    kind, task, plan = setup_plan(tracking)
    initial = tracking.get('/activity').json()
    planned = correction(initial, 'add', 10, 13, 14, task_type_id=kind,
                         task_id=task['id'], planned_block_id=plan['id'], name='Writing')
    lunch = correction(initial, 'add', 11, 12, 15, device='android',
                       task_type_id=kind, name='Lunch')
    for operation in ([lunch, planned] if reverse else [planned, lunch]):
        send(tracking, operation)
    before = tracking.get('/activity').json()
    assert [r['name'] for r in before['records']] == ['Writing', 'Lunch', 'Writing']
    linked = [r['id'] for r in before['records'] if r['planned_block_id'] == plan['id']]
    assert len(linked) == 2
    day = tracking.get('/days/2026-09-10').json()
    assert set(day['planned_blocks'][0]['actual_block_ids']) == set(linked)
    assert day['planned_blocks'][0]['actual_duration_minutes'] == 120

    changed = tracking.put('/activity/reporting-timezone', json={'timezone': 'Pacific/Honolulu'})
    assert changed.status_code == 200, changed.text
    assert changed.json()['records'] == before['records']
    edited = send(tracking, correction(changed.json(), 'edit', 12, 13, 16,
                  target_id=linked[-1], sequence=2, task_type_id=kind, name='Revised'))
    for operation in [lunch, planned]:
        assert send(tracking, operation)['records'] == edited['records']
    day = tracking.get('/days/2026-09-10').json()
    assert day['meta']['timezone'] == 'Pacific/Honolulu'
    assert day['planned_blocks'][0]['actual_duration_minutes'] == 120
    assert set(day['planned_blocks'][0]['actual_block_ids']) == set(linked)
    assert tracking.get('/tasks').json()['items'][0]['status'] == task['status']
    assert tracking.post(f"/planned-blocks/{plan['id']}/record-actual-as-planned").status_code == 409
