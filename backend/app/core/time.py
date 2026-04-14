from __future__ import annotations

import datetime as dt
from zoneinfo import ZoneInfo


def get_zone(tz_name: str) -> ZoneInfo:
    return ZoneInfo(tz_name)


def now_in_tz(tz_name: str) -> dt.datetime:
    return dt.datetime.now(get_zone(tz_name))


def today_in_tz(tz_name: str) -> dt.date:
    return now_in_tz(tz_name).date()


def parse_iso_date(s: str) -> dt.date:
    return dt.date.fromisoformat(s)


def isoformat_z(dt_value: dt.datetime) -> str:
    if dt_value.tzinfo is None:
        dt_value = dt_value.replace(tzinfo=dt.timezone.utc)
    return dt_value.isoformat()
