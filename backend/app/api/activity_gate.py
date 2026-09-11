"""Keep the development timeline inaccessible to legacy Actual mutation routes."""
from fastapi import Depends, HTTPException, Request
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.models.activity import ActivityState
from app.models.time_block import BlockLane, TimeBlock


async def guard_legacy_actual_writes(
    request: Request, db: Session = Depends(get_db), settings: Settings = Depends(get_settings),
):
    if request.method in {"GET", "HEAD", "OPTIONS"}:
        return
    path = request.url.path
    legacy = path.startswith(("/actual-blocks", "/planned-blocks"))
    if path.startswith("/days/") and "/blocks" in path:
        block_id = request.path_params.get("block_id")
        if block_id is not None:
            block = db.get(TimeBlock, int(block_id))
            legacy = block is not None and block.lane == BlockLane.actual
        if request.method in {"POST", "PATCH"}:
            body = await request.json()
            legacy = legacy or body.get("lane") == "actual"
    if not legacy:
        return
    state = db.get(ActivityState, 1)
    if settings.activity_tracking_dev or (state is not None and state.enabled):
        raise HTTPException(409, "Legacy Actual writes are disabled for this activity development database")
