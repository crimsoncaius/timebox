from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.battle_plan import Project
from app.schemas.battle_plan import ProjectCreate, ProjectPatch
from app.services.battle_plan._shared import _clean_name, _validate_deadline


def list_projects(db: Session) -> list[Project]:
    return list(db.execute(select(Project).order_by(func.lower(Project.name), Project.id)).scalars())


def create_project(db: Session, body: ProjectCreate) -> Project:
    name = _clean_name(body.name)
    _validate_deadline(body.deadline_date, body.deadline_at)
    exists = db.execute(select(Project.id).where(func.lower(Project.name) == name.lower())).scalar_one_or_none()
    if exists is not None:
        raise ValueError("A project with this name already exists")
    row = Project(
        name=name,
        description=body.description,
        deadline_date=body.deadline_date,
        deadline_at=body.deadline_at,
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def patch_project(db: Session, project_id: int, body: ProjectPatch) -> Project:
    row = db.get(Project, project_id)
    if row is None:
        raise ValueError("Project not found")
    fields = body.model_fields_set
    if "name" in fields and body.name is not None:
        name = _clean_name(body.name)
        exists = db.execute(
            select(Project.id).where(func.lower(Project.name) == name.lower(), Project.id != project_id)
        ).scalar_one_or_none()
        if exists is not None:
            raise ValueError("A project with this name already exists")
        row.name = name
    if "description" in fields:
        row.description = body.description or ""
    if "deadline_date" in fields or "deadline_at" in fields:
        date_value = body.deadline_date if "deadline_date" in fields else row.deadline_date
        at_value = body.deadline_at if "deadline_at" in fields else row.deadline_at
        if "deadline_date" in fields and body.deadline_date is not None:
            at_value = None
        if "deadline_at" in fields and body.deadline_at is not None:
            date_value = None
        _validate_deadline(date_value, at_value)
        row.deadline_date = date_value
        row.deadline_at = at_value
    db.commit()
    db.refresh(row)
    return row


def delete_project(db: Session, project_id: int) -> None:
    row = db.get(Project, project_id)
    if row is None:
        raise ValueError("Project not found")
    from app.services import recurrence_service

    recurrence_service.move_project_templates_to_admin(db, project_id)
    db.delete(row)
    db.commit()
