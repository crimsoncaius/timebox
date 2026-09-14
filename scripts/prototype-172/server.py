from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from datetime import datetime, timedelta, timezone
import json
from urllib.parse import urlparse

now = lambda: datetime.now(timezone.utc)
start = now() - timedelta(minutes=42)
types = [dict(id=1, name='Design', usage_count=3), dict(id=2, name='unspecified', usage_count=0)]
current = dict(id=201, task_type_id=1, task_type=types[0], name='Day view exploration', start_at=start.isoformat(), end_at=None, created_at=start.isoformat(), updated_at=start.isoformat())
cursor = 1
settings = dict(start_hour=7, end_hour=23, show_full_day=False)

def snapshot(ack=None):
    return dict(protocol='activity-online-v1', cursor=cursor, server_at=now().isoformat(), reporting_timezone='Asia/Singapore', reporting_timezone_initialized=True, current=current, records=[current] if current else [], task_types=types, plans=[], acknowledgement=ack)

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def send(self, data):
        body = json.dumps(data).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(body)
    def do_GET(self):
        p = urlparse(self.path).path
        if p == '/activity': return self.send(snapshot())
        if p == '/settings': return self.send(settings)
        if p == '/task-types': return self.send(types)
        if p.startswith('/days/'):
            date = p.split('/')[2]
            local = now().astimezone(timezone(timedelta(hours=8)))
            minute = local.hour * 60 + local.minute
            blocks = [dict(id=i+1, lane='planned', task_type_id=1, task_type=types[0], name=name, start_minute=s, end_minute=e) for i, (name,s,e) in enumerate([('Review priorities',480,525), ('Day view exploration',540,660), ('Lunch',720,780), ('Build and review',minute-60,minute+60)])]
            actual = [dict(actual_block=current,date=date,start_minute=max(0,minute-42),end_minute=minute,duration_minutes=42)] if current else []
            return self.send(dict(id=1,date=date,**settings,time_blocks=blocks,actual_blocks=actual,planned_minutes=345,actual_minutes=42,meta=dict(timezone='Asia/Singapore',today=local.date().isoformat(),server_now_iso=local.isoformat())))
        if p == '/actual-blocks/active': return self.send(current)
        return self.send([])
    def do_POST(self):
        global current, cursor
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or '{}')
        if self.path == '/activity/commands':
            if body.get('kind') == 'stop': current = None
            cursor += 1
            return self.send(snapshot(dict(operation_id=body['operation_id'],outcome='applied')))
        return self.send(snapshot())

ThreadingHTTPServer(('127.0.0.1',12015),Handler).serve_forever()
