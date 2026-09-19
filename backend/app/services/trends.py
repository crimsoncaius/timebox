"""Actual-time reporting, clipped once before aggregation into the type hierarchy."""
import datetime as dt
from collections import defaultdict
from zoneinfo import ZoneInfo

from sqlalchemy import or_, select
from sqlalchemy.orm import Session, joinedload

from app.models.time_block import BlockLane, TimeBlock
from app.schemas.trends import TrendNode, TrendsRead


def report(db: Session, start: dt.date, end: dt.date, timezone: str, now: dt.datetime) -> TrendsRead:
    zone = ZoneInfo(timezone)
    def midnight(date):
        return dt.datetime.combine(date, dt.time(), zone).astimezone(dt.timezone.utc)
    def utc(value):
        return value.replace(tzinfo=dt.timezone.utc) if value.tzinfo is None else value.astimezone(dt.timezone.utc)

    lower = midnight(start)
    upper = min(midnight(end + dt.timedelta(days=1)), now)
    direct = defaultdict(lambda: defaultdict(float))
    if upper > lower:
        records = db.scalars(select(TimeBlock).options(joinedload(TimeBlock.task_type)).where(
            TimeBlock.lane == BlockLane.actual, TimeBlock.start_at < upper,
            or_(TimeBlock.end_at.is_(None), TimeBlock.end_at > lower),
        )).all()
        for record in records:
            left = max(utc(record.start_at), lower)
            right = min(utc(record.end_at) if record.end_at else now, upper)
            while left < right:
                day = left.astimezone(zone).date()
                boundary = min(midnight(day + dt.timedelta(days=1)), right)
                direct[record.task_type.name][day] += (boundary - left).total_seconds()
                left = boundary

    nodes = {}
    for path, days in direct.items():
        segments = path.split('/')
        for depth in range(1, len(segments) + 1):
            prefix = '/'.join(segments[:depth])
            if prefix not in nodes:
                nodes[prefix] = TrendNode(path=prefix, name=segments[depth - 1], duration_seconds=0,
                                         direct_seconds=0, days={}, direct_days={})
            node = nodes[prefix]
            for day, seconds in days.items():
                node.days[day] = node.days.get(day, 0) + seconds
                node.duration_seconds += seconds
        nodes[path].direct_days = dict(days)
        nodes[path].direct_seconds = sum(days.values())
    roots = []
    for path, node in nodes.items():
        if '/' in path:
            nodes[path.rsplit('/', 1)[0]].children.append(node)
        else:
            roots.append(node)
    def sort(children):
        children.sort(key=lambda node: (-node.duration_seconds, node.path))
        for node in children:
            sort(node.children)
    sort(roots)
    return TrendsRead(start=start, end=end, today=now.astimezone(zone).date(), timezone=timezone,
                      captured_at=now, duration_seconds=sum(n.duration_seconds for n in roots), types=roots)
