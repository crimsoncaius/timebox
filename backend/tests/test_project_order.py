import pytest


def test_saved_order_survives_rename_and_new_projects_append(client):
    projects = [client.post('/projects', json={'name': name}).json() for name in ['Zulu', 'Alpha', 'Beta']]
    ids = [p['id'] for p in projects]
    task = client.post('/tasks', json={'title': 'Keep this task', 'project_id': ids[0]}).json()
    order = [ids[2], ids[0], ids[1]]
    response = client.post('/projects/reorder', json={'project_ids': order})
    assert response.status_code == 200
    assert [p['id'] for p in response.json()] == order
    client.patch(f'/projects/{ids[0]}', json={'name': 'AAA renamed'})
    new = client.post('/projects', json={'name': 'A new project'}).json()
    assert [p['id'] for p in client.get('/projects').json()] == order + [new['id']]
    saved_task = next(row for row in client.get('/tasks').json()['items'] if row['id'] == task['id'])
    assert saved_task['project_id'] == ids[0]
    assert saved_task['title'] == 'Keep this task'
    assert [p['position'] for p in client.get('/projects').json()] == [0, 1, 2, 3]


@pytest.mark.parametrize('invalid', ['duplicate', 'missing', 'unknown'])
def test_invalid_reorder_is_atomic(client, invalid):
    ids = [client.post('/projects', json={'name': name}).json()['id'] for name in ['A', 'B']]
    requested = {'duplicate': [ids[1], ids[1]], 'missing': [ids[1]], 'unknown': [ids[1], 9999]}[invalid]
    assert client.post('/projects/reorder', json={'project_ids': requested}).status_code == 422
    assert [p['id'] for p in client.get('/projects').json()] == ids


def test_project_order_migration_preserves_alphabetical_order():
    import importlib.util
    from pathlib import Path
    import sqlalchemy as sa
    from alembic.migration import MigrationContext
    from alembic.operations import Operations

    path = Path(__file__).parents[1] / 'alembic/versions/019_project_order.py'
    spec = importlib.util.spec_from_file_location('project_order_migration', path)
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    engine = sa.create_engine('sqlite:///:memory:')
    with engine.begin() as connection:
        connection.exec_driver_sql('CREATE TABLE projects (id INTEGER PRIMARY KEY, name TEXT)')
        connection.exec_driver_sql("INSERT INTO projects VALUES (1, 'Zulu'), (2, 'beta'), (3, 'Alpha')")
        with Operations.context(MigrationContext.configure(connection)):
            migration.upgrade()
        assert connection.exec_driver_sql('SELECT id FROM projects ORDER BY position').scalars().all() == [3, 2, 1]
