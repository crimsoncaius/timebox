from __future__ import annotations

import datetime as dt
from zoneinfo import ZoneInfo


def get_zone(tz_name: str) -> ZoneInfo:
    return ZoneInfo(tz_name)


def utc_now() -> dt.datetime:
    return dt.datetime.now(dt.UTC)


def as_utc(value: dt.datetime) -> dt.datetime:
    """Normalize to an aware UTC instant; naive values are read as UTC.

    Columns come back naive on SQLite and aware on PostgreSQL, so every
    comparison against a captured instant goes through here.
    """
    if value.tzinfo is None:
        return value.replace(tzinfo=dt.UTC)
    return value.astimezone(dt.UTC)


def now_in_tz(tz_name: str) -> dt.datetime:
    return dt.datetime.now(get_zone(tz_name))


def today_in_tz(tz_name: str) -> dt.date:
    return now_in_tz(tz_name).date()


def parse_iso_date(s: str) -> dt.date:
    return dt.date.fromisoformat(s)


def isoformat_z(dt_value: dt.datetime) -> str:
    if dt_value.tzinfo is None:
        dt_value = dt_value.replace(tzinfo=dt.UTC)
    return dt_value.isoformat()
