import importlib.util
from pathlib import Path
import tempfile

import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from alembic.migration import MigrationContext
from alembic.operations import Operations

from app.core.config import get_settings


BACKEND_ROOT = Path(__file__).resolve().parents[1]


def test_project_contract_and_task_metadata_remain_independent(client):
    project = client.post('/projects', json={'name': 'Launch'}).json()
    expected = {'id', 'name', 'position', 'created_at', 'updated_at'}
    assert set(project) == expected
    task = client.post('/tasks', json={
        'title': 'Submit', 'project_id': project['id'],
        'description': 'Keep these instructions', 'deadline_date': '2026-10-01',
    }).json()
    renamed = client.patch(f"/projects/{project['id']}", json={'name': 'Release'})
    assert renamed.status_code == 200
    assert set(renamed.json()) == expected
    assert set(client.get('/projects').json()[0]) == expected
    saved = next(row for row in client.get('/tasks').json()['items'] if row['id'] == task['id'])
    assert set(saved['project']) == expected
    assert saved['project']['name'] == 'Release'
    assert saved['description'] == 'Keep these instructions'
    assert saved['deadline_date'] == '2026-10-01'


def test_fresh_schema_can_be_stamped_at_head(monkeypatch):
    with tempfile.TemporaryDirectory(prefix=".fresh-migration-", dir=BACKEND_ROOT / "tests") as path:
        database_path = Path(path) / "fresh.sqlite3"
        import app.models  # noqa: F401
        from app.db.base import Base

        engine = sa.create_engine(f"sqlite:///{database_path.as_posix()}")
        Base.metadata.create_all(engine)
        monkeypatch.setattr(
            get_settings(), "database_url", f"sqlite:///{database_path.as_posix()}"
        )
        config = Config(str(BACKEND_ROOT / "alembic.ini"))

        command.stamp(config, "head")
        command.check(config)

        with engine.connect() as connection:
            columns = {
                column["name"] for column in sa.inspect(connection).get_columns("projects")
            }
        engine.dispose()

        assert {"description", "deadline_date", "deadline_at"}.isdisjoint(columns)


def test_migration_discards_only_project_metadata():
    path = Path(__file__).parents[1] / 'alembic/versions/021_remove_project_metadata.py'
    spec = importlib.util.spec_from_file_location('project_metadata_migration', path)
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    engine = sa.create_engine('sqlite:///:memory:')
    with engine.begin() as connection:
        connection.exec_driver_sql('PRAGMA foreign_keys=ON')
        connection.exec_driver_sql('CREATE TABLE projects (id INTEGER PRIMARY KEY, name TEXT UNIQUE, position INTEGER, description TEXT NOT NULL, deadline_date DATE, deadline_at DATETIME)')
        connection.exec_driver_sql('CREATE TABLE tasks (id INTEGER PRIMARY KEY, project_id INTEGER REFERENCES projects(id), description TEXT, deadline_date DATE, deadline_at DATETIME)')
        connection.exec_driver_sql("INSERT INTO projects VALUES (1, 'Launch', 3, 'Old notes', '2026-10-01', NULL)")
        connection.exec_driver_sql("INSERT INTO tasks VALUES (2, 1, 'Task notes', '2026-10-02', NULL)")
        before = connection.exec_driver_sql('SELECT * FROM tasks').all()
        with Operations.context(MigrationContext.configure(connection)):
            migration.upgrade()
        assert {c['name'] for c in sa.inspect(connection).get_columns('projects')} == {'id', 'name', 'position'}
        assert connection.exec_driver_sql('SELECT * FROM projects').all() == [(1, 'Launch', 3)]
        assert connection.exec_driver_sql('SELECT * FROM tasks').all() == before
        with Operations.context(MigrationContext.configure(connection)):
            migration.downgrade()
        assert connection.exec_driver_sql('SELECT description, deadline_date, deadline_at FROM projects').one() == ('', None, None)
        assert connection.exec_driver_sql('SELECT * FROM tasks').all() == before


def test_migration_detaches_recurring_work_before_removing_project_association():
    path = Path(__file__).parents[1] / 'alembic/versions/022_separate_recurring_work_from_projects.py'
    spec = importlib.util.spec_from_file_location('separate_recurring_work_migration', path)
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    engine = sa.create_engine('sqlite:///:memory:')
    with engine.begin() as connection:
        connection.exec_driver_sql('CREATE TABLE projects (id INTEGER PRIMARY KEY)')
        connection.exec_driver_sql('CREATE TABLE recurring_templates (id INTEGER PRIMARY KEY, project_id INTEGER REFERENCES projects(id))')
        connection.exec_driver_sql('CREATE INDEX ix_recurring_templates_project_id ON recurring_templates (project_id)')
        connection.exec_driver_sql('CREATE TABLE tasks (id INTEGER PRIMARY KEY, parent_id INTEGER, project_id INTEGER REFERENCES projects(id), recurring_template_id INTEGER, recurrence_kind TEXT)')
        connection.exec_driver_sql('INSERT INTO projects VALUES (1)')
        connection.exec_driver_sql('INSERT INTO recurring_templates VALUES (2, 1)')
        connection.exec_driver_sql("INSERT INTO tasks VALUES (3, NULL, 1, NULL, 'scheduled')")
        connection.exec_driver_sql("INSERT INTO tasks VALUES (4, 3, 1, NULL, 'checklist')")
        with Operations.context(MigrationContext.configure(connection)):
            migration.upgrade()
        assert 'project_id' not in {column['name'] for column in sa.inspect(connection).get_columns('recurring_templates')}
        assert connection.exec_driver_sql('SELECT project_id FROM tasks WHERE id = 3').scalar_one() is None
        assert connection.exec_driver_sql('SELECT project_id FROM tasks WHERE id = 4').scalar_one() is None
