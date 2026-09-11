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
    state = db.get(ActivityState, 1)
    if state is not None and state.cutover:
        if not request.url.path.startswith("/activity") and request.headers.get("X-Timebox-Protocol") != "activity-online-v1":
            raise HTTPException(426, "Timebox was upgraded. Update Android or reload the web app. Unsaved work must be reviewed, not replayed.", headers={"X-Timebox-Protocol": "activity-online-v1"})
        if request.method not in {"GET", "HEAD", "OPTIONS"}:
            if state.cutover.get("paused"):
                raise HTTPException(503, "Activity updates are paused for recovery. Keep local changes for retry.")
    if request.method in {"GET", "HEAD", "OPTIONS"}:
        return
    path = request.url.path
    legacy = path.startswith(("/actual-blocks", "/planned-blocks"))
    if path.startswith("/task-types/") and request.method == "DELETE":
        # These legacy options rewrite/delete Actuals as a side effect. Reject
        # before inspecting usage so a concurrent switch cannot race the check.
        legacy = "cascade_blocks" in request.query_params or "migrate_blocks_to" in request.query_params
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
    if settings.activity_tracking_dev or (state is not None and state.enabled):
        raise HTTPException(409, "Legacy Actual writes are disabled. Update Android or reload the web app; review unsaved work through Day corrections.")
