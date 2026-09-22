import asyncio
import json
from uuid import uuid4

import pytest
from langchain_core.messages import AIMessage, AIMessageChunk, ToolMessage

from app.api.routes import assistant
from app.core.config import get_settings
from app.services.assistant_agent import translate_events
from app.services.assistant_presentation import PresentationParser, snapshot
from app.services.assistant_sessions import conversations
from tests.test_assistant import decode


def plan():
    return snapshot({"date": "2026-09-21", "reporting_timezone": "Asia/Singapore", "planned_blocks": []})


def test_prefix_all_chunk_boundaries_and_body_is_not_control():
    item = plan()
    wire = json.dumps({"presentation": "snapshot", "snapshot_id": item["snapshot_id"]}) + '\r\nHello 世界\n{"presentation":"none"}'
    for i in range(len(wire) + 1):
        parser = PresentationParser({item["snapshot_id"]: item})
        events = parser.feed(wire[:i]) + parser.feed(wire[i:])
        parser.finish()
        assert [k for k, _ in events].count("plan_card") == 1
        assert ''.join(d["text"] for k, d in events if k == "text_delta") == 'Hello 世界\n{"presentation":"none"}'


@pytest.mark.parametrize("wire", [
    'prose\n', '```json\n', '{"presentation":"none","presentation":"none"}\n',
    '{"presentation":"none","extra":1}\n', '{"presentation":"snapshot","snapshot_id":"other"}\n',
    '{"presentation":"snapshot","snapshot_id":[]}\n', 'x' * 513,
    '{"presentation":"none"}',
])
def test_bad_prefix_fails_without_visible_output(wire):
    parser = PresentationParser({})
    with pytest.raises(ValueError):
        parser.feed(wire)
        parser.finish()


def test_terminal_only_card_waits_for_confirmed_success():
    item = plan()
    wire = json.dumps({"presentation": "snapshot", "snapshot_id": item["snapshot_id"]})
    for split in range(len(wire) + 1):
        parser = PresentationParser({item["snapshot_id"]: item})
        assert parser.feed(wire[:split]) + parser.feed(wire[split:]) == []
        with pytest.raises(ValueError):
            parser.finish()
        assert parser.finish(successful_terminal=True) == [("plan_card", item)]
        assert parser.finish(successful_terminal=True) == []


@pytest.mark.parametrize('wire', [
    '{"presentation":"none"}',
    '{"presentation":"snapshot","snapshot_id":"unknown"}',
    '{"presentation":"snapshot","snapshot_id":"known"}Answer without newline',
    '{"presentation":"snapshot","snapshot_id":"known","extra":true}',
    '{"presentation":"snapshot","snapshot_id":"known","snapshot_id":"known"}',
    '{"presentation":"snapshot","snapshot_id":',
])
def test_terminal_exception_does_not_relax_other_validation(wire):
    item = plan()
    parser = PresentationParser({'known': item})
    parser.feed(wire)
    with pytest.raises(ValueError):
        parser.finish(successful_terminal=True)


@pytest.mark.parametrize('reason', ['stop', 'length', None])
def test_actual_provider_card_only_pattern_through_event_translation(reason):
    item = plan()
    wire = json.dumps({'presentation': 'snapshot', 'snapshot_id': item['snapshot_id']})
    async def source():
        for text in [wire[:20], wire[20:]]:
            yield {'event':'on_chat_model_stream', 'data':{'chunk':AIMessageChunk(content=text)}}
        yield {'event':'on_chat_model_end', 'data':{'output':AIMessage(content=wire, response_metadata={'finish_reason':reason})}}
    async def run():
        return [event async for event in translate_events(source(), {item['snapshot_id']:item})]
    if reason == 'stop':
        assert asyncio.run(run()) == [('plan_card', item)]
    else:
        with pytest.raises(RuntimeError, match='incomplete'):
            asyncio.run(run())


def test_terminal_only_card_completes_and_commits_through_route(client, monkeypatch):
    conversations.items.clear()
    monkeypatch.setattr(get_settings(), 'openrouter_api_key', 'fake')
    item = plan()
    wire = json.dumps({'presentation':'snapshot', 'snapshot_id':item['snapshot_id']})
    async def source():
        yield {'event':'on_tool_end', 'data':{'output':item}}
        yield {'event':'on_chat_model_stream', 'data':{'chunk':AIMessageChunk(content=wire)}}
        yield {'event':'on_chat_model_end', 'data':{'output':AIMessage(content=wire, response_metadata={'finish_reason':'stop'})}}
    async def fake(messages, snapshots):
        async for event in translate_events(source(), snapshots):
            yield event
    monkeypatch.setattr(assistant, 'agent_events', fake)
    key = client.post('/assistant/conversations', json={'capabilities':['plan_card_v1']}).json()['conversation_id']
    run = str(uuid4())
    events = decode(client.post(f'/assistant/conversations/{key}/messages', json={'message':'Show plan', 'run_id':run}))
    assert [k for k,d in events] == ['started','tool_completed','plan_card','completed']
    assert conversations.get(key).snapshots == {}
    client.post(f'/assistant/conversations/{key}/runs/{run}/ack')
    assert conversations.get(key).snapshots == {item['snapshot_id']:item}
    assert conversations.get(key).exchange_count == 1


def test_stream_without_terminal_cannot_complete_even_after_valid_card():
    item = plan()
    async def source():
        yield {"event": "on_tool_end", "data": {"output": item}}
        yield {"event":"on_chat_model_stream", "data":{"chunk":AIMessageChunk(content=json.dumps({"presentation":"snapshot","snapshot_id":item['snapshot_id']})+'\n')}}
    async def run():
        return [event async for event in translate_events(source())]
    with pytest.raises(RuntimeError, match='incomplete'):
        asyncio.run(run())


def test_tool_preamble_discarded_card_precedes_text():
    item = plan()
    async def source():
        yield {"event": "on_chat_model_stream", "data": {"chunk": AIMessageChunk(content="Do not leak")}}
        yield {"event": "on_chat_model_end", "data": {"output": AIMessage(content="Do not leak", tool_calls=[{"id":"1","name":"read_today_plan","args":{}}], response_metadata={"finish_reason":"tool_calls"})}}
        yield {"event": "on_tool_start", "data": {}}
        yield {"event": "on_tool_end", "data": {"output": ToolMessage(content=json.dumps(item), tool_call_id="1")}}
        header = json.dumps({"presentation":"snapshot", "snapshot_id":item["snapshot_id"]})+'\nAnswer'
        for c in header:
            yield {"event":"on_chat_model_stream", "data":{"chunk":AIMessageChunk(content=c)}}
        yield {"event":"on_chat_model_end", "data":{"output":AIMessage(content=header, response_metadata={"finish_reason":"stop"})}}
    async def run(): return [event async for event in translate_events(source())]
    events = asyncio.run(run())
    assert [k for k,d in events][:4] == ['tool_started','snapshot_read','tool_completed','plan_card']
    assert ''.join(d['text'] for k,d in events if k=='text_delta') == 'Answer'


@pytest.mark.parametrize("cards", [True, False])
def test_snapshot_ack_and_legacy_card_only(client, monkeypatch, cards):
    conversations.items.clear()
    monkeypatch.setattr(get_settings(), "openrouter_api_key", "fake")
    item = plan()
    async def fake(messages, snapshots):
        yield "snapshot_read", item
        yield "plan_card", item
    monkeypatch.setattr(assistant, "agent_events", fake)
    response = client.post('/assistant/conversations', json={"capabilities":["plan_card_v1"]} if cards else {})
    key = response.json()['conversation_id']
    run = str(uuid4())
    events = decode(client.post(f'/assistant/conversations/{key}/messages', json={"message":"Show plan", "run_id":run}))
    assert events[-1][0] == 'completed'
    assert ('plan_card' in [k for k,d in events]) == cards
    if not cards:
        assert 'No Planned Blocks' in ''.join(d['text'] for k,d in events if k=='text_delta')
    assert conversations.get(key).snapshots == {}
    for _ in range(2): client.post(f'/assistant/conversations/{key}/runs/{run}/ack')
    assert conversations.get(key).snapshots == {item['snapshot_id']:item}
    assert conversations.get(key).exchange_count == 1
    client.delete(f'/assistant/conversations/{key}')
    assert key not in conversations.items


def test_interrupted_card_never_commits_snapshot(client, monkeypatch):
    conversations.items.clear()
    monkeypatch.setattr(get_settings(), "openrouter_api_key", "fake")
    item = plan()
    async def fake(messages, snapshots):
        yield 'snapshot_read', item
        yield 'plan_card', item
        raise RuntimeError('unfinished')
    monkeypatch.setattr(assistant, 'agent_events', fake)
    key = client.post('/assistant/conversations', json={"capabilities":["plan_card_v1"]}).json()['conversation_id']
    run = str(uuid4())
    events = decode(client.post(f'/assistant/conversations/{key}/messages', json={"message":"Show plan", "run_id":run}))
    assert events[-1][0] == 'failed'
    client.post(f'/assistant/conversations/{key}/runs/{run}/ack')
    assert conversations.get(key).snapshots == {}
    assert conversations.get(key).exchange_count == 0
