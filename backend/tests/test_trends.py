import datetime as dt

import pytest
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api.routes.trends import capture_now
from app.core.config import Settings, get_settings
from app.db.session import get_engine
from app.main import app
from app.models.day import Day
from app.models.task_type import TaskType
from app.models.time_block import BlockLane, TimeBlock


def instant(value):
    return dt.datetime.fromisoformat(value.replace('Z', '+00:00'))


@pytest.fixture
def reporting(client):
    app.dependency_overrides[capture_now] = lambda: instant('2026-09-19T05:00:00Z')
    app.dependency_overrides[get_settings] = lambda: Settings(app_timezone='Asia/Singapore')
    yield client
    app.dependency_overrides.pop(capture_now, None)
    app.dependency_overrides.pop(get_settings, None)


def record(path, start, end):
    with Session(get_engine()) as db:
        kind = db.scalar(select(TaskType).where(TaskType.name == path))
        if kind is None:
            kind = TaskType(name=path)
            db.add(kind)
            db.flush()
        row = TimeBlock(lane=BlockLane.actual, task_type_id=kind.id, start_at=instant(start), end_at=instant(end) if end else None)
        db.add(row)
        db.commit()


def test_hierarchy_uses_block_types_and_never_double_counts(reporting):
    record('work/coding/python', '2026-09-18T01:00:00Z', '2026-09-18T03:00:00Z')
    record('work/coding', '2026-09-18T03:00:00Z', '2026-09-18T03:30:00Z')
    record('work', '2026-09-18T04:00:00Z', '2026-09-18T05:00:00Z')
    record('workshop', '2026-09-18T05:00:00Z', '2026-09-18T05:15:00Z')
    result = reporting.get('/trends').json()
    assert result['duration_seconds'] == 13500
    work, workshop = result['types']
    assert work['duration_seconds'] == 12600
    assert work['direct_seconds'] == 3600
    coding = work['children'][0]
    assert coding['duration_seconds'] == 9000
    assert coding['direct_seconds'] == 1800
    assert coding['children'][0]['duration_seconds'] == 7200
    assert workshop['duration_seconds'] == 900
    assert work['days'] == {'2026-09-18': 12600}


def test_inclusive_range_clips_midnight_and_running_time(reporting):
    record('reading', '2026-09-17T15:30:00Z', '2026-09-17T16:30:00Z')
    record('unspecified', '2026-09-18T16:00:00Z', None)
    result = reporting.get('/trends?period=custom&start=2026-09-18&end=2026-09-19').json()
    assert result['duration_seconds'] == 13 * 3600 + 1800
    assert result['types'][0]['days'] == {'2026-09-19': 13 * 3600}
    assert result['types'][1]['days'] == {'2026-09-18': 1800}
    assert reporting.get('/trends?period=day&anchor=2026-09-20').status_code == 422
    with Session(get_engine()) as db:
        assert db.scalar(select(func.count()).select_from(Day)) == 0  # reporting creates no Days


def test_dst_elapsed_duration_and_timezone_change(reporting):
    record('sleep', '2025-11-02T04:00:00Z', '2025-11-03T06:00:00Z')
    app.dependency_overrides[get_settings] = lambda: Settings(app_timezone='America/New_York')
    result = reporting.get('/trends?period=day&anchor=2025-11-02').json()
    assert result['duration_seconds'] == 25 * 3600
    app.dependency_overrides[get_settings] = lambda: Settings(app_timezone='UTC')
    assert reporting.get('/trends?period=day&anchor=2025-11-02').json()['duration_seconds'] == 20 * 3600


def test_presets_empty_and_validation(reporting):
    week = reporting.get('/trends').json()
    assert (week['start'], week['end']) == ('2026-09-14', '2026-09-20')
    assert week['types'] == []
    assert reporting.get('/trends?period=week&anchor=2026-09-20').status_code == 200
    month = reporting.get('/trends?period=month&anchor=2026-09-30').json()
    assert (month['start'], month['end'], month['today']) == ('2026-09-01', '2026-09-30', '2026-09-19')
    leap = reporting.get('/trends?period=month&anchor=2024-02-29').json()
    assert (leap['start'], leap['end']) == ('2024-02-01', '2024-02-29')
    for query in [
        'period=custom', 'period=custom&start=2026-09-20&end=2026-09-19',
        'period=custom&start=2026-09-18&end=2026-09-20',
        'period=day&anchor=2026-09-20', 'period=week&anchor=2026-09-21',
        'period=month&anchor=2026-10-01', 'period=nope', 'anchor=invalid',
        'period=day&anchor=9999-12-31',
    ]:
        assert reporting.get(f'/trends?{query}').status_code == 422


def test_future_boundary_uses_reporting_timezone(reporting):
    app.dependency_overrides[capture_now] = lambda: instant('2026-09-19T20:00:00Z')
    assert reporting.get('/trends?period=day&anchor=2026-09-20').json()['today'] == '2026-09-20'
    assert reporting.get('/trends?period=custom&start=2026-09-20&end=2026-09-20').status_code == 200
    assert reporting.get('/trends?period=day&anchor=2026-09-21').status_code == 422


def test_subminute_time_is_aggregated_before_display_rounding(reporting):
    record('reading', '2026-09-18T01:00:00Z', '2026-09-18T01:00:40Z')
    record('reading', '2026-09-18T02:00:00Z', '2026-09-18T02:00:40Z')
    assert reporting.get('/trends').json()['duration_seconds'] == 80
