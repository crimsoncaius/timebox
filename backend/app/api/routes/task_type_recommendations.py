from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.db.session import get_db
from app.services.task_type_recommendation import Recommendation, recommend
from app.services.task_type_service import list_task_types

router = APIRouter(prefix="/task-types", tags=["task-types"])


class RecommendationRequest(BaseModel):
    name: str = Field(max_length=2000)


@router.post("/recommendation", response_model=Recommendation)
def recommend_task_type(body: RecommendationRequest, db: Session = Depends(get_db),
                        settings: Settings = Depends(get_settings)) -> Recommendation:
    candidates = [(row.id, row.name) for row in list_task_types(db)]
    db.rollback()  # Release the read transaction before waiting on the provider.
    return recommend(body.name, candidates, settings)
