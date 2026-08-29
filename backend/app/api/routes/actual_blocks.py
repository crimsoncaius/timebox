from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.schemas.time_block import ActualBlockRead
from app.services import actual_block_service

router = APIRouter(prefix="/actual-blocks", tags=["actual-blocks"])


@router.get("/active", response_model=ActualBlockRead | None)
def get_active_actual_block(db: Session = Depends(get_db)) -> ActualBlockRead | None:
    return actual_block_service.get_active_actual_block(db)
