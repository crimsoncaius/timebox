"""Single-worker run coordination; idle context can be reloaded from durable storage."""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass, field
from uuid import uuid4

from fastapi import HTTPException

from app.services import assistant_storage


@dataclass
class Conversation:
    messages: list = field(default_factory=list)
    touched: float = field(default_factory=time.monotonic)
    run_id: str | None = None
    task: asyncio.Task | None = None
    snapshots: dict = field(default_factory=dict)
    capabilities: list[str] = field(default_factory=list)
    exchange_count: int = 0


class Conversations:
    def __init__(self):
        self.items: dict[str, Conversation] = {}

    def create(self, capabilities=None) -> str:
        self.prune()
        key = str(uuid4())
        assistant_storage.create(key, capabilities or [])
        self.items[key] = Conversation(capabilities=capabilities or [])
        return key

    def prune(self):
        # Eviction bounds RAM only; it never expires a stored conversation.
        for key, item in list(self.items.items()):
            if item.run_id is None and time.monotonic() - item.touched >= 3600:
                del self.items[key]
        if len(self.items) >= 100:
            idle = [(key, item) for key, item in self.items.items() if item.run_id is None]
            if idle:
                del self.items[min(idle, key=lambda pair: pair[1].touched)[0]]
            else:
                raise HTTPException(429, "Too many active responses. Try again later.")

    def get(self, key) -> Conversation:
        item = self.items.get(key)
        if item is None:
            capabilities, messages, snapshots = assistant_storage.load(key)
            self.prune()
            item = Conversation(capabilities=capabilities, messages=messages,
                                snapshots=snapshots, exchange_count=len(messages) // 2)
            self.items[key] = item
        return item

    def reserve(self, key, run_id) -> Conversation:
        item = self.get(key)
        if item.run_id is not None:
            raise HTTPException(409, "A response is already running. Stop it or wait.")
        item.run_id = run_id
        item.touched = time.monotonic()
        return item

    def stop(self, key, run_id):
        item = self.get(key)
        if item.run_id == run_id and item.task:
            item.task.cancel()
        assistant_storage.stop(key, run_id)

    def acknowledge(self, key, run_id):
        item = self.get(key)
        assistant_storage.acknowledge(key, run_id)
        _, item.messages, item.snapshots = assistant_storage.load(key)
        item.exchange_count = len(item.messages) // 2
        item.touched = time.monotonic()

    def delete(self, key):
        # Compatibility endpoint: New conversation closes, never erases history.
        assistant_storage.close(key)
        item = self.items.pop(key, None)
        if item and item.task:
            item.task.cancel()


conversations = Conversations()
