"""Durable question state. Called only while holding the activity singleton lock."""
import datetime as dt
import uuid
from copy import deepcopy


def instant(value):
    if isinstance(value, str):
        value = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    return value.replace(tzinfo=dt.timezone.utc) if value.tzinfo is None else value


def synchronize(state, current):
    value = deepcopy(state.check_in or dict(enabled=True, threshold_minutes=60, rearm=0, question=None))
    identity = f'{current.id}:{instant(current.start_at).isoformat()}' if current else None
    if value.get('activity') != identity or 'generation' not in value:
        value.update(activity=identity, generation=str(uuid.uuid4()), rearm=0, question=None,
                     armed_at=instant(current.start_at).isoformat() if current else None, active_at=None)
    state.check_in = value
    return value


def apply(state, command):
    event = command.check_in
    if event is None:
        raise ValueError('Check-in event is required')
    value = deepcopy(state.check_in)
    now = dt.datetime.now(dt.timezone.utc)
    if command.action_at > now + dt.timedelta(seconds=5):
        raise ValueError('Check-in instant is in the future')
    outcome = 'applied'
    active_observation = event.action == 'observe' and event.observed == 'active'
    if event.generation != value['generation'] or (event.rearm != value['rearm'] and not active_observation) or not value['activity']:
        outcome = 'superseded'
    elif event.action in ('observe', 'candidate'):
        qualified = (event.capability in ('supported', 'approximate') and event.permission == 'granted'
                     and event.coverage_start is not None and event.coverage_end is not None
                     and event.coverage_start <= event.coverage_end <= now + dt.timedelta(seconds=5))
        if qualified and event.observed == 'active':
            prior = value.get('active_at')
            value['active_at'] = max(instant(prior), event.coverage_end).isoformat() if prior else event.coverage_end.isoformat()
        elif qualified and event.action == 'candidate' and event.observed in ('idle', 'locked') and value['enabled'] and event.enabled is not False:
            start = max(event.coverage_start, instant(value['armed_at']))
            if value.get('active_at'):
                start = max(start, instant(value['active_at']))
            if event.coverage_end - start >= dt.timedelta(minutes=event.threshold_minutes or value['threshold_minutes']) and value['question'] is None:
                value['question'] = dict(id=f"{value['generation']}:{value['rearm']}", created_at=now.isoformat(), candidate_device=command.device_id, candidate_operation_id=str(command.operation_id), delivery=None)
            else:
                outcome = 'superseded'
        else:
            outcome = 'superseded'
    elif not value['question'] or event.question_id != value['question']['id']:
        outcome = 'superseded'
    elif event.action == 'confirm':
        value.update(question=None, rearm=value['rearm'] + 1,
                     armed_at=max(instant(value['armed_at']), command.action_at).isoformat())
    elif event.action == 'delivery':
        if value['question']['delivery'] is None and value['question']['candidate_device'] == command.device_id:
            value['question']['delivery'] = dict(device_id=command.device_id, operation_id=str(command.operation_id), at=now.isoformat(), dismissed=False)
        else:
            outcome = 'superseded'
    elif event.action == 'notification_dismiss':
        if value['question']['delivery'] is not None:
            value['question']['delivery']['dismissed'] = True
    state.check_in = value
    return outcome
