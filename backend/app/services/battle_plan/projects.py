from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.battle_plan import Project
from app.schemas.battle_plan import ProjectCreate, ProjectPatch
from app.services.battle_plan._shared import _clean_name


def list_projects(db: Session) -> list[Project]:
    return list(db.execute(select(Project).order_by(Project.position, Project.id)).scalars())


def create_project(db: Session, body: ProjectCreate) -> Project:
    name = _clean_name(body.name)
    exists = db.execute(select(Project.id).where(func.lower(Project.name) == name.lower())).scalar_one_or_none()
    if exists is not None:
        raise ValueError("A project with this name already exists")
    row = Project(
        name=name,
        position=(db.scalar(select(func.max(Project.position))) or 0) + 1,
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
    db.commit()
    db.refresh(row)
    return row


def delete_project(db: Session, project_id: int) -> None:
    row = db.get(Project, project_id)
    if row is None:
        raise ValueError("Project not found")
    db.delete(row)
    db.commit()


def reorder_projects(db: Session, project_ids: list[int]) -> list[Project]:
    rows = list_projects(db)
    by_id = {row.id: row for row in rows}
    if len(project_ids) != len(set(project_ids)) or set(project_ids) != set(by_id):
        raise ValueError("Project list changed. Refresh and try again with every project exactly once.")
    for position, project_id in enumerate(project_ids):
        by_id[project_id].position = position
    db.commit()
    return list_projects(db)
