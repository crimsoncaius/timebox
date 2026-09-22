"""Ephemeral single-worker sessions. All methods run on the API event loop."""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass, field
from uuid import uuid4

from fastapi import HTTPException


@dataclass
class Conversation:
    messages: list = field(default_factory=list)
    touched: float = field(default_factory=time.monotonic)
    run_id: str | None = None
    task: asyncio.Task | None = None
    pending: tuple | None = None
    snapshots: dict = field(default_factory=dict)
    capabilities: list[str] = field(default_factory=list)
    exchange_count: int = 0


class Conversations:
    def __init__(self):
        self.items: dict[str, Conversation] = {}

    def create(self, capabilities=None) -> str:
        for key, item in list(self.items.items()):
            if item.run_id is None and time.monotonic() - item.touched >= 3600:
                del self.items[key]
        if len(self.items) >= 100:
            raise HTTPException(429, "Too many conversations. Try again later.")
        key = str(uuid4())
        self.items[key] = Conversation(capabilities=capabilities or [])
        return key

    def get(self, key) -> Conversation:
        item = self.items.get(key)
        if item is None or (item.run_id is None and time.monotonic() - item.touched >= 3600):
            self.items.pop(key, None)
            raise HTTPException(410, "Conversation expired. Start a new conversation.")
        return item

    def reserve(self, key, run_id) -> Conversation:
        item = self.get(key)
        if item.run_id is not None:
            raise HTTPException(409, "A response is already running. Stop it or wait.")
        if item.exchange_count >= 20:
            raise HTTPException(409, "20 exchanges reached. Start a new conversation.")
        item.pending = None
        item.run_id = run_id
        item.touched = time.monotonic()
        return item

    def stop(self, key, run_id):
        item = self.get(key)
        if item.run_id == run_id and item.task:
            item.task.cancel()
        if item.pending and item.pending[0] == run_id:
            item.pending = None

    def acknowledge(self, key, run_id):
        item = self.get(key)
        if item.pending and item.pending[0] == run_id:
            item.messages.extend(item.pending[1])
            item.snapshots.update(item.pending[2])
            item.exchange_count += 1
            item.pending = None
            item.touched = time.monotonic()

    def delete(self, key):
        item = self.items.pop(key, None)
        if item and item.task:
            item.task.cancel()


conversations = Conversations()
