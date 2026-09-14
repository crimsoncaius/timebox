"""Seed only the isolated issue-173 review API."""
import datetime as dt
import uuid
from zoneinfo import ZoneInfo
import httpx

api = httpx.Client(base_url="http://127.0.0.1:12018", timeout=20)
def post(path, data):
    response = api.post(path, json=data)
    response.raise_for_status()
    return response.json()

kind = post('/task-types', {'name': 'Writing'})['id']
today = dt.datetime.now(ZoneInfo('Asia/Singapore')).date()
date = (today - dt.timedelta(days=1)).isoformat()
day = post(f'/days/{date}/blocks', dict(lane='planned', task_type_id=kind,
    start_minute=600, end_minute=660, name='Write chapter', note='Follow the outline'))
snapshot = api.get('/activity').json()
post('/activity/commands', dict(operation_id=str(uuid.uuid4()), device_id='issue173-seed', sequence=1,
    action_at=snapshot['server_at'], calibration={'server_at': snapshot['server_at'], 'offset_ms': 0},
    base_cursor=snapshot['cursor'], kind='add', target_id=None, task_type_id=kind,
    name='Earlier draft', note='Preserve this note outside replaced time',
    effective={'mode': 'range', 'at': f'{date}T09:45:00+08:00', 'end': f'{date}T10:15:00+08:00'}))
print(f"http://127.0.0.1:12019/day/{date}?block={day['planned_blocks'][0]['id']}")
