# Hierarchical Task Type Paths Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add canonical slash-delimited task type paths with unlimited depth, inline creation from the block editor, descendant-aware rename, and conservative delete rules while keeping the existing flat `task_types` table.

**Architecture:** Keep `TaskType.name` as the single stored value, but reinterpret it as a materialized path. Backend path rules live in a focused helper plus `task_type_service`, while the frontend gets a path utility module and a dedicated searchable combobox component so path parsing, matching, and display are not duplicated across `Today` and `Task Types`.

**Tech Stack:** FastAPI, SQLAlchemy, Alembic, Pytest, React, TypeScript, Testing Library, Vitest, Playwright

---

## File Structure

**Create:**
- `backend/app/services/task_type_paths.py` - canonicalization and path-prefix helpers for backend path semantics
- `backend/tests/test_task_type_paths.py` - unit coverage for canonicalization and prefix helpers
- `backend/alembic/versions/006_task_type_materialized_paths.py` - data migration that canonicalizes existing saved task types
- `frontend/src/lib/taskTypePaths.ts` - shared path parsing, canonicalization, and suggestion ranking helpers
- `frontend/src/lib/taskTypePaths.test.ts` - unit coverage for frontend path helpers
- `frontend/src/components/TaskTypePathCombobox.tsx` - searchable combobox with existing-path selection and inline create action
- `frontend/src/components/TaskTypePathCombobox.test.tsx` - focused component tests for suggestion, create, and keyboard behavior

**Modify:**
- `backend/app/services/task_type_service.py` - create ancestors, cascade rename, and block delete when descendants exist
- `backend/app/api/routes/task_types.py` - map new service errors to stable HTTP responses
- `backend/tests/test_task_types_api.py` - API-level create, rename, delete, and descendant behavior
- `backend/tests/test_days_api.py` - keep block/task-type API expectations aligned with canonical lowercase names
- `frontend/src/components/TimeBlockModal.tsx` - replace the `<select>` with the path combobox and wire inline creation
- `frontend/src/components/TimeBlockModal.test.tsx` - modal save payload and inline-create flow coverage
- `frontend/src/features/today/TodayPage.tsx` - own inline create callback, refresh task types after create, and keep error/save state coherent
- `frontend/src/features/task-types/TaskTypesPage.tsx` - refetch after create/rename because ancestors and descendants can change
- `frontend/src/features/task-types/TaskTypesPage.test.tsx` - canonical display and refetch behavior coverage
- `frontend/e2e/timebox.spec.ts` - hierarchical path flow and lowercase-safe helpers
- `frontend/src/lib/api.ts` - only if a typed helper return shape needs tightening during refactor
- `README.md` - update product docs and spec references for path-based task types

## Task 1: Canonical Path Rules And Data Migration

**Files:**
- Create: `backend/app/services/task_type_paths.py`
- Create: `backend/tests/test_task_type_paths.py`
- Create: `backend/alembic/versions/006_task_type_materialized_paths.py`
- Modify: `backend/tests/test_days_api.py`

- [ ] **Step 1: Write failing backend helper tests for canonicalization and descendant detection**

```python
from app.services.task_type_paths import canonicalize_task_type_path, is_descendant_path, path_prefixes


def test_canonicalize_task_type_path_trims_whitespace_and_lowercases():
    assert canonicalize_task_type_path(" Coding / AI / Agents ") == "coding/ai/agents"


def test_canonicalize_task_type_path_rejects_empty_segments():
    try:
        canonicalize_task_type_path("coding//ai")
    except ValueError as exc:
        assert str(exc) == "Task type path is invalid"
    else:
        raise AssertionError("Expected ValueError")


def test_path_prefixes_returns_each_materialized_ancestor():
    assert path_prefixes("coding/ai/agents") == [
        "coding",
        "coding/ai",
        "coding/ai/agents",
    ]


def test_is_descendant_path_uses_slash_boundaries():
    assert is_descendant_path("coding", "coding/ai")
    assert is_descendant_path("coding/ai", "coding/ai/agents")
    assert not is_descendant_path("coding", "codingstuff")
```

- [ ] **Step 2: Run the focused helper tests and verify they fail because the module does not exist yet**

Run: `cd backend && uv run pytest tests/test_task_type_paths.py -v`

Expected: FAIL with `ModuleNotFoundError: No module named 'app.services.task_type_paths'`

- [ ] **Step 3: Add the backend path helper module**

```python
from __future__ import annotations


def canonicalize_task_type_path(name: str) -> str:
    raw = name.strip()
    if not raw:
        raise ValueError("Task type path is required")

    raw_segments = raw.split("/")
    if any(segment.strip() == "" for segment in raw_segments):
        raise ValueError("Task type path is invalid")

    return "/".join(segment.strip().lower() for segment in raw_segments)


def path_prefixes(path: str) -> list[str]:
    segments = path.split("/")
    return ["/".join(segments[:idx]) for idx in range(1, len(segments) + 1)]


def is_descendant_path(ancestor_path: str, candidate_path: str) -> bool:
    return candidate_path.startswith(f"{ancestor_path}/")
```

- [ ] **Step 4: Re-run helper tests and verify they pass**

Run: `cd backend && uv run pytest tests/test_task_type_paths.py -v`

Expected: PASS for all 4 helper tests

- [ ] **Step 5: Add a migration that canonicalizes existing saved task type names**

```python
from alembic import op
import sqlalchemy as sa


revision = "006_task_type_materialized_paths"
down_revision = "005_planned_block_link"
branch_labels = None
depends_on = None


def _canonicalize(name: str) -> str:
    raw = name.strip()
    if not raw:
        raise RuntimeError("Task type path is required during migration")

    raw_segments = raw.split("/")
    if any(segment.strip() == "" for segment in raw_segments):
        raise RuntimeError(f"Invalid task type path during migration: {name!r}")

    return "/".join(segment.strip().lower() for segment in raw_segments)


def upgrade() -> None:
    conn = op.get_bind()
    rows = conn.execute(sa.text("select id, name from task_types order by id")).mappings().all()

    seen: dict[str, int] = {}
    for row in rows:
        canonical = _canonicalize(row["name"])
        existing_id = seen.get(canonical)
        if existing_id is not None and existing_id != row["id"]:
            raise RuntimeError(
                f"Canonical task type collision during migration: {row['name']!r} -> {canonical!r}"
            )

        seen[canonical] = row["id"]
        conn.execute(
            sa.text("update task_types set name = :name where id = :id"),
            {"id": row["id"], "name": canonical},
        )


def downgrade() -> None:
    # Irreversible data normalization; leave values as canonical paths.
    pass
```

- [ ] **Step 6: Adjust existing day API expectations to canonical lowercase names**

```python
def test_create_and_patch_block(client):
    tid = _tid(client, "Deep work")
    client.get("/days/2026-04-13")
    r = client.post(
        "/days/2026-04-13/blocks",
        json={
            "lane": "planned",
            "task_type_id": tid,
            "note": "focus",
            "start_minute": 540,
            "end_minute": 600,
        },
    )
    assert r.status_code == 200
    block = r.json()["time_blocks"][0]
    assert block["task_type"]["name"] == "deep work"
```

- [ ] **Step 7: Run the targeted backend tests plus migration-sensitive day API checks**

Run: `cd backend && uv run pytest tests/test_task_type_paths.py tests/test_days_api.py::test_create_and_patch_block -v`

Expected: PASS, with the day API returning canonical lowercase task type names

- [ ] **Step 8: Commit the backend path helpers and migration**

```bash
git add backend/app/services/task_type_paths.py backend/tests/test_task_type_paths.py backend/alembic/versions/006_task_type_materialized_paths.py backend/tests/test_days_api.py
git commit -m "feat: canonicalize task type paths"
```

## Task 2: Backend Create, Cascade Rename, And Conservative Delete

**Files:**
- Modify: `backend/app/services/task_type_service.py`
- Modify: `backend/app/api/routes/task_types.py`
- Modify: `backend/tests/test_task_types_api.py`

- [ ] **Step 1: Write failing API tests for ancestor creation, cascade rename, and descendant-safe delete**

```python
def test_create_task_type_canonicalizes_and_creates_missing_ancestors(client):
    r = client.post("/task-types", json={"name": " Coding / AI / Agents "})
    assert r.status_code == 200
    assert r.json()["name"] == "coding/ai/agents"

    names = [row["name"] for row in client.get("/task-types").json()]
    assert names == ["coding", "coding/ai", "coding/ai/agents"]


def test_patch_task_type_renames_descendant_branch(client):
    client.post("/task-types", json={"name": "coding/ai"})
    rows = client.get("/task-types").json()
    root_id = next(row["id"] for row in rows if row["name"] == "coding")

    r = client.patch(f"/task-types/{root_id}", json={"name": "development"})
    assert r.status_code == 200
    assert r.json()["name"] == "development"

    names = [row["name"] for row in client.get("/task-types").json()]
    assert "development/ai" in names
    assert "coding/ai" not in names


def test_delete_task_type_with_descendants_returns_conflict(client):
    client.post("/task-types", json={"name": "exercise/cardio"})
    rows = client.get("/task-types").json()
    parent_id = next(row["id"] for row in rows if row["name"] == "exercise")
    r = client.delete(f"/task-types/{parent_id}")
    assert r.status_code == 409
    assert "subpaths" in r.json()["detail"].lower()


def test_patch_task_type_creates_missing_target_ancestors(client):
    leaf_id = client.post("/task-types", json={"name": "coding/ai"}).json()["id"]
    r = client.patch(f"/task-types/{leaf_id}", json={"name": "development/ml"})
    assert r.status_code == 200
    names = [row["name"] for row in client.get("/task-types").json()]
    assert "development" in names
    assert "development/ml" in names
```

- [ ] **Step 2: Run the task type API tests and verify they fail under the current flat-name behavior**

Run: `cd backend && uv run pytest tests/test_task_types_api.py -v`

Expected: FAIL on the new ancestor creation, cascade rename, and descendant delete tests

- [ ] **Step 3: Update the task type service to use canonical paths and auto-create ancestors**

```python
from sqlalchemy import func, or_, select

from app.services.task_type_paths import canonicalize_task_type_path, is_descendant_path, path_prefixes


def _name_taken(db: Session, name: str, exclude_id: int | None = None) -> bool:
    stmt = select(TaskType.id).where(TaskType.name == name)
    if exclude_id is not None:
        stmt = stmt.where(TaskType.id != exclude_id)
    return db.execute(stmt).scalar_one_or_none() is not None


def create_task_type(db: Session, body: TaskTypeCreate) -> TaskType:
    path = canonicalize_task_type_path(body.name)
    prefixes = path_prefixes(path)

    existing = {
        row.name: row
        for row in db.execute(select(TaskType).where(TaskType.name.in_(prefixes))).scalars().all()
    }

    created = existing.get(path)
    if created is not None:
        raise ValueError("A task type with this path already exists")

    for prefix in prefixes:
        if prefix in existing:
            continue
        row = TaskType(name=prefix)
        db.add(row)
        db.flush()
        existing[prefix] = row

    db.commit()
    created = existing[path]
    db.refresh(created)
    return created
```

- [ ] **Step 4: Implement branch-aware rename and descendant-safe delete in the service**

```python
def patch_task_type(db: Session, task_type_id: int, body: TaskTypePatch) -> TaskType:
    row = get_task_type(db, task_type_id)
    if row is None:
        raise ValueError("Task type not found")
    if body.name is None:
        return row

    old_path = row.name
    new_path = canonicalize_task_type_path(body.name)
    if new_path == old_path:
        return row

    branch_rows = list(
        db.execute(
            select(TaskType)
            .where(or_(TaskType.name == old_path, TaskType.name.like(f"{old_path}/%")))
            .order_by(func.length(TaskType.name), TaskType.id)
        ).scalars()
    )

    for prefix in path_prefixes(new_path)[:-1]:
        existing_prefix = db.execute(
            select(TaskType.id).where(TaskType.name == prefix).limit(1)
        ).scalar_one_or_none()
        if existing_prefix is None:
            db.add(TaskType(name=prefix))
            db.flush()

    replacements = {
        branch_row.id: new_path if branch_row.name == old_path else branch_row.name.replace(old_path, new_path, 1)
        for branch_row in branch_rows
    }

    collision_names = set(replacements.values())
    existing_conflict = db.execute(
        select(TaskType.id, TaskType.name)
        .where(TaskType.name.in_(collision_names))
        .where(~TaskType.id.in_(list(replacements.keys())))
    ).first()
    if existing_conflict is not None:
        raise ValueError("A task type with this path already exists")

    for branch_row in branch_rows:
        branch_row.name = replacements[branch_row.id]
        branch_row.updated_at = _utc_now()
        db.add(branch_row)

    db.commit()
    db.refresh(row)
    return row


def delete_task_type(db: Session, task_type_id: int) -> None:
    row = get_task_type(db, task_type_id)
    if row is None:
        raise ValueError("Task type not found")

    has_descendants = db.execute(
        select(TaskType.id).where(TaskType.name.like(f"{row.name}/%")).limit(1)
    ).scalar_one_or_none()
    if has_descendants is not None:
        raise ValueError("TASK_TYPE_HAS_DESCENDANTS")

    in_use = db.execute(
        select(TimeBlock.id).where(TimeBlock.task_type_id == task_type_id).limit(1)
    ).scalar_one_or_none()
    if in_use is not None:
        raise ValueError("TASK_TYPE_IN_USE")

    db.delete(row)
    db.commit()
```

- [ ] **Step 5: Map the new descendant conflict in the route layer**

```python
@router.delete("/{task_type_id}", status_code=204)
def delete_task_type(task_type_id: int, db: Session = Depends(get_db)) -> None:
    try:
        task_type_service.delete_task_type(db, task_type_id)
    except ValueError as e:
        msg = str(e)
        if msg == "TASK_TYPE_IN_USE":
            raise HTTPException(status_code=409, detail="Task type is still used by existing blocks") from e
        if msg == "TASK_TYPE_HAS_DESCENDANTS":
            raise HTTPException(status_code=409, detail="Task type still has saved subpaths") from e
        if msg == "Task type not found":
            raise HTTPException(status_code=404, detail=msg) from e
        raise HTTPException(status_code=422, detail=msg) from e
```

- [ ] **Step 6: Run the focused backend task type tests and verify they pass**

Run: `cd backend && uv run pytest tests/test_task_types_api.py -v`

Expected: PASS for legacy coverage plus new path create, cascade rename, and descendant delete cases

- [ ] **Step 7: Run the full backend suite to catch collateral regressions**

Run: `cd backend && uv run pytest`

Expected: PASS for the full backend test suite

- [ ] **Step 8: Commit the backend service and route changes**

```bash
git add backend/app/services/task_type_service.py backend/app/api/routes/task_types.py backend/tests/test_task_types_api.py
git commit -m "feat: add hierarchical task type path behavior"
```

## Task 3: Frontend Path Utilities And Searchable Combobox

**Files:**
- Create: `frontend/src/lib/taskTypePaths.ts`
- Create: `frontend/src/lib/taskTypePaths.test.ts`
- Create: `frontend/src/components/TaskTypePathCombobox.tsx`
- Create: `frontend/src/components/TaskTypePathCombobox.test.tsx`

- [ ] **Step 1: Write failing unit tests for frontend path normalization and suggestion ranking**

```ts
import { describe, expect, it } from 'vitest'
import type { TaskType } from './api'
import {
  buildTaskTypeSuggestions,
  canonicalizeTaskTypePathInput,
  formatTaskTypePathParts,
} from './taskTypePaths'

const rows: TaskType[] = [
  { id: 1, name: 'coding', created_at: '', updated_at: '' },
  { id: 2, name: 'coding/ai', created_at: '', updated_at: '' },
  { id: 3, name: 'exercise/cardio', created_at: '', updated_at: '' },
]

describe('taskTypePaths', () => {
  it('canonicalizes slash-delimited input', () => {
    expect(canonicalizeTaskTypePathInput(' Coding / AI ')).toBe('coding/ai')
  })

  it('returns null for empty segments', () => {
    expect(canonicalizeTaskTypePathInput('coding//ai')).toBeNull()
  })

  it('splits display into ancestor and leaf parts', () => {
    expect(formatTaskTypePathParts('coding/ai/agents')).toEqual({
      ancestorsLabel: 'coding / ai',
      leafLabel: 'agents',
      fullLabel: 'coding/ai/agents',
    })
  })

  it('offers a create row only when the canonical path is missing', () => {
    const suggestions = buildTaskTypeSuggestions(rows, 'coding/personal')
    expect(suggestions.createPath).toBe('coding/personal')
    expect(suggestions.rows.map((row) => row.name)).toContain('coding')
  })
})
```

- [ ] **Step 2: Run the helper tests and verify they fail because the new utility module does not exist**

Run: `cd frontend && npm test -- src/lib/taskTypePaths.test.ts`

Expected: FAIL with an import/module resolution error

- [ ] **Step 3: Implement the frontend path utilities**

```ts
import type { TaskType } from './api'

export function canonicalizeTaskTypePathInput(input: string): string | null {
  const raw = input.trim()
  if (!raw) return null

  const rawSegments = raw.split('/')
  if (rawSegments.some((segment) => segment.trim().length === 0)) return null

  return rawSegments.map((segment) => segment.trim().toLowerCase()).join('/')
}

export function formatTaskTypePathParts(path: string) {
  const segments = path.split('/')
  const leafLabel = segments[segments.length - 1] ?? path
  const ancestorsLabel = segments.slice(0, -1).join(' / ')
  return { fullLabel: path, leafLabel, ancestorsLabel }
}

export function buildTaskTypeSuggestions(taskTypes: TaskType[], query: string) {
  const canonicalQuery = canonicalizeTaskTypePathInput(query)
  const normalized = [...taskTypes].sort((a, b) => a.name.localeCompare(b.name))
  const rows = normalized.filter((row) => {
    if (!canonicalQuery) return true
    return row.name.includes(canonicalQuery) || row.name.startsWith(canonicalQuery.split('/')[0] ?? '')
  })
  const exact = canonicalQuery ? normalized.some((row) => row.name === canonicalQuery) : true
  return { rows, createPath: canonicalQuery && !exact ? canonicalQuery : null }
}
```

- [ ] **Step 4: Add failing combobox tests for existing selection and inline create CTA**

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TaskTypePathCombobox } from './TaskTypePathCombobox'

const taskTypes = [
  { id: 1, name: 'coding', created_at: '', updated_at: '' },
  { id: 2, name: 'coding/ai', created_at: '', updated_at: '' },
]

describe('TaskTypePathCombobox', () => {
  it('shows matching suggestions as the user types', async () => {
    const user = userEvent.setup()
    render(
      <TaskTypePathCombobox
        label="Task type"
        taskTypes={taskTypes}
        valueTaskTypeId={2}
        onSelectTaskTypeId={vi.fn()}
        onCreateTaskTypePath={vi.fn()}
      />,
    )

    await user.clear(screen.getByLabelText('Task type'))
    await user.type(screen.getByLabelText('Task type'), 'coding')
    expect(screen.getByRole('option', { name: /coding\/ai/i })).toBeInTheDocument()
  })

  it('creates a missing canonical path and selects the returned row', async () => {
    const user = userEvent.setup()
    const onSelectTaskTypeId = vi.fn()
    const onCreateTaskTypePath = vi.fn().mockResolvedValue({
      id: 5,
      name: 'coding/personal',
      created_at: '',
      updated_at: '',
    })

    render(
      <TaskTypePathCombobox
        label="Task type"
        taskTypes={taskTypes}
        valueTaskTypeId={2}
        onSelectTaskTypeId={onSelectTaskTypeId}
        onCreateTaskTypePath={onCreateTaskTypePath}
      />,
    )

    await user.clear(screen.getByLabelText('Task type'))
    await user.type(screen.getByLabelText('Task type'), 'Coding / Personal')
    await user.click(screen.getByRole('option', { name: /create "coding\/personal"/i }))

    expect(onCreateTaskTypePath).toHaveBeenCalledWith('coding/personal')
    expect(onSelectTaskTypeId).toHaveBeenCalledWith(5)
  })
})
```

- [ ] **Step 5: Implement the combobox component with input, suggestion list, and create action**

```tsx
import { useEffect, useMemo, useState } from 'react'
import type { TaskType } from '../lib/api'
import { buildTaskTypeSuggestions, formatTaskTypePathParts } from '../lib/taskTypePaths'

export function TaskTypePathCombobox({
  label,
  taskTypes,
  valueTaskTypeId,
  onSelectTaskTypeId,
  onCreateTaskTypePath,
}: {
  label: string
  taskTypes: TaskType[]
  valueTaskTypeId: number
  onSelectTaskTypeId: (taskTypeId: number) => void
  onCreateTaskTypePath: (path: string) => Promise<TaskType>
}) {
  const selected = taskTypes.find((row) => row.id === valueTaskTypeId) ?? null
  const [query, setQuery] = useState(selected?.name ?? '')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setQuery(selected?.name ?? '')
  }, [selected?.id, selected?.name])

  const suggestions = useMemo(() => buildTaskTypeSuggestions(taskTypes, query), [taskTypes, query])

  return (
    <div className="relative">
      <label htmlFor="block-task-type" className="mb-0.5 block font-body text-xs text-on-surface-variant">
        {label}
      </label>
      <input
        id="block-task-type"
        role="combobox"
        aria-expanded={open}
        aria-controls="block-task-type-listbox"
        className="w-full rounded-xl border border-outline-variant/15 bg-surface px-3 py-2.5 font-body text-sm text-on-surface outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/20"
        value={query}
        onFocus={() => setOpen(true)}
        onChange={(event) => {
          setQuery(event.target.value)
          setOpen(true)
        }}
      />

      {open && (
        <ul
          id="block-task-type-listbox"
          role="listbox"
          className="absolute z-20 mt-2 max-h-64 w-full overflow-auto rounded-xl bg-surface-container-lowest shadow-[0_0_40px_rgba(45,52,53,0.08)]"
        >
          {suggestions.rows.map((row) => {
            const parts = formatTaskTypePathParts(row.name)
            return (
              <li key={row.id}>
                <button
                  type="button"
                  role="option"
                  className="flex w-full items-center justify-between px-3 py-2 text-left hover:bg-surface-container-high"
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => {
                    onSelectTaskTypeId(row.id)
                    setQuery(row.name)
                    setOpen(false)
                  }}
                >
                  <span>
                    {parts.ancestorsLabel ? (
                      <span className="text-on-surface-variant">{parts.ancestorsLabel} / </span>
                    ) : null}
                    <span className="text-on-surface">{parts.leafLabel}</span>
                  </span>
                </button>
              </li>
            )
          })}

          {suggestions.createPath ? (
            <li>
              <button
                type="button"
                role="option"
                disabled={busy}
                className="w-full px-3 py-2 text-left text-primary hover:bg-surface-container-high disabled:opacity-50"
                onMouseDown={(event) => event.preventDefault()}
                onClick={async () => {
                  setBusy(true)
                  try {
                    const created = await onCreateTaskTypePath(suggestions.createPath!)
                    onSelectTaskTypeId(created.id)
                    setQuery(created.name)
                    setOpen(false)
                  } finally {
                    setBusy(false)
                  }
                }}
              >
                {busy ? 'Creating…' : `Create "${suggestions.createPath}"`}
              </button>
            </li>
          ) : null}
        </ul>
      )}
    </div>
  )
}
```

- [ ] **Step 6: Run the targeted frontend utility and combobox tests**

Run: `cd frontend && npm test -- src/lib/taskTypePaths.test.ts src/components/TaskTypePathCombobox.test.tsx`

Expected: PASS for canonicalization, suggestion ranking, existing selection, and inline create coverage

- [ ] **Step 7: Commit the frontend path utility layer**

```bash
git add frontend/src/lib/taskTypePaths.ts frontend/src/lib/taskTypePaths.test.ts frontend/src/components/TaskTypePathCombobox.tsx frontend/src/components/TaskTypePathCombobox.test.tsx
git commit -m "feat: add task type path search utilities"
```

## Task 4: Wire Inline Create Into Today And Keep Task Types Page Coherent

**Files:**
- Modify: `frontend/src/components/TimeBlockModal.tsx`
- Modify: `frontend/src/components/TimeBlockModal.test.tsx`
- Modify: `frontend/src/features/today/TodayPage.tsx`
- Modify: `frontend/src/features/task-types/TaskTypesPage.tsx`
- Modify: `frontend/src/features/task-types/TaskTypesPage.test.tsx`

- [ ] **Step 1: Write failing tests for modal inline-create flow and task types page refetch-after-rename**

```tsx
it('creates a missing task type path from the modal and saves only task_type_id', async () => {
  const user = userEvent.setup()
  const onCreateTaskType = vi.fn().mockResolvedValue({
    id: 7,
    name: 'coding/personal',
    created_at: '',
    updated_at: '',
  })
  const onSave = vi.fn().mockResolvedValue(undefined)

  render(
    <TimeBlockModal
      open
      block={makeBlock()}
      day={emptyDay}
      taskTypes={taskTypes}
      onClose={vi.fn()}
      onSave={onSave}
      onDelete={vi.fn()}
      onCreateTaskTypePath={onCreateTaskType}
    />,
  )

  await user.clear(screen.getByLabelText('Task type'))
  await user.type(screen.getByLabelText('Task type'), 'coding/personal')
  await user.click(screen.getByRole('option', { name: /create "coding\/personal"/i }))
  await user.click(screen.getByRole('button', { name: 'Save' }))

  expect(onCreateTaskType).toHaveBeenCalledWith('coding/personal')
  expect(onSave).toHaveBeenCalledWith({ task_type_id: 7 })
})
```

```tsx
it('reloads the full task type list after renaming a parent path', async () => {
  const user = userEvent.setup()
  globalThis.fetch = vi
    .fn()
    .mockResolvedValueOnce(jsonResponse([{ id: 1, name: 'coding', created_at: '', updated_at: '' }]))
    .mockResolvedValueOnce(jsonResponse({ id: 1, name: 'development', created_at: '', updated_at: '' }))
    .mockResolvedValueOnce(
      jsonResponse([
        { id: 1, name: 'development', created_at: '', updated_at: '' },
        { id: 2, name: 'development/ai', created_at: '', updated_at: '' },
      ]),
    )

  renderPage()
  const input = await screen.findByRole('textbox', { name: /Task type name 1/i })
  await user.clear(input)
  await user.type(input, 'development')
  await user.tab()

  expect(await screen.findByRole('textbox', { name: /Task type name 2/i })).toHaveValue('development/ai')
})
```

- [ ] **Step 2: Run the modal and task types page tests to verify they fail before wiring changes**

Run: `cd frontend && npm test -- src/components/TimeBlockModal.test.tsx src/features/task-types/TaskTypesPage.test.tsx`

Expected: FAIL because `TimeBlockModal` does not accept `onCreateTaskTypePath` yet and `TaskTypesPage` only patches local state

- [ ] **Step 3: Replace the modal select with the new combobox and add an inline-create prop**

```tsx
import { TaskTypePathCombobox } from './TaskTypePathCombobox'

export function TimeBlockModal({
  open,
  block,
  day,
  taskTypes,
  onClose,
  onSave,
  onDelete,
  onCompleteAsPlanned,
  onCreateTaskTypePath,
}: {
  open: boolean
  block: TimeBlock | null
  day: DayRead
  taskTypes: TaskType[]
  onClose: () => void
  onSave: (patch: { task_type_id?: number; note?: string | null }) => Promise<void>
  onDelete: () => Promise<void>
  onCompleteAsPlanned?: () => Promise<void>
  onCreateTaskTypePath: (path: string) => Promise<TaskType>
}) {
  // existing local state...

  return (
    <aside /* existing wrapper */>
      <form onSubmit={handleSubmit} className="flex max-h-[min(85vh,56rem)] flex-col gap-4 overflow-y-auto rounded-2xl bg-surface-container-lowest/90 px-4 pb-6 pt-6 shadow-[0_0_40px_rgba(45,52,53,0.04)] backdrop-blur-[20px] dark:bg-stone-950/85 dark:shadow-[0_0_40px_rgba(0,0,0,0.25)] lg:max-h-[calc(100vh-8rem)] lg:rounded-none lg:bg-transparent lg:px-0 lg:pb-6 lg:pt-6 lg:shadow-none lg:backdrop-blur-none">
        <TaskTypePathCombobox
          label="Task type"
          taskTypes={taskTypes}
          valueTaskTypeId={taskTypeId}
          onSelectTaskTypeId={setTaskTypeId}
          onCreateTaskTypePath={onCreateTaskTypePath}
        />
        {/* existing note and actions */}
      </form>
    </aside>
  )
}
```

- [ ] **Step 4: Add TodayPage inline-create wiring that refetches task types after create**

```tsx
const createTaskTypePath = useCallback(
  async (name: string) => {
    setSaveState('saving')
    setError(null)
    try {
      const created = await api.createTaskType({ name })
      const nextTaskTypes = await api.listTaskTypes()
      setTaskTypes(nextTaskTypes)
      setSaveState('saved')
      return created
    } catch (e) {
      setSaveState('error')
      const msg = e instanceof Error ? e.message : 'Failed to create task type'
      setError(msg)
      throw e
    }
  },
  [],
)

<TimeBlockModal
  open={selectedBlock != null}
  block={selectedBlock}
  day={day}
  taskTypes={taskTypes}
  onClose={() => setSelectedBlockId(null)}
  onSave={(patch) => {
    if (!selectedBlock) return Promise.resolve()
    return patchBlock(selectedBlock.id, patch)
  }}
  onDelete={() => {
    if (!selectedBlock) return Promise.resolve()
    return deleteBlock(selectedBlock.id)
  }}
  onCompleteAsPlanned={selectedBlock?.lane === 'planned' ? () => completeBlockAsPlanned(selectedBlock.id) : undefined}
  onCreateTaskTypePath={createTaskTypePath}
/>
```

- [ ] **Step 5: Refetch the full list after Task Types page create and rename, and update copy to mention slash paths**

```tsx
const addType = async () => {
  const name = newName.trim()
  if (!name) return
  setSaveState('saving')
  setError(null)
  try {
    await api.createTaskType({ name })
    await load()
    setNewName('')
    setSaveState('saved')
  } catch (e) {
    setSaveState('error')
    setError(e instanceof Error ? e.message : 'Failed to create task type')
  }
}

const rename = async (id: number, name: string) => {
  setSaveState('saving')
  setError(null)
  try {
    await api.patchTaskType(id, { name })
    await load()
    setSaveState('saved')
  } catch (e) {
    setSaveState('error')
    setError(e instanceof Error ? e.message : 'Failed to update task type')
  }
}

<p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
  Saved task type paths for time blocks (e.g. coding, coding/ai, exercise/cardio).
</p>
```

- [ ] **Step 6: Run the targeted frontend component/page tests**

Run: `cd frontend && npm test -- src/components/TimeBlockModal.test.tsx src/features/task-types/TaskTypesPage.test.tsx`

Expected: PASS for inline-create modal flow and refetch-after-rename coverage

- [ ] **Step 7: Run the broader frontend unit suite to catch routing or page regressions**

Run: `cd frontend && npm test`

Expected: PASS for the full frontend unit/component test suite

- [ ] **Step 8: Commit the Today and Task Types integration**

```bash
git add frontend/src/components/TimeBlockModal.tsx frontend/src/components/TimeBlockModal.test.tsx frontend/src/features/today/TodayPage.tsx frontend/src/features/task-types/TaskTypesPage.tsx frontend/src/features/task-types/TaskTypesPage.test.tsx
git commit -m "feat: support inline hierarchical task type creation"
```

## Task 5: End-To-End Verification And Docs

**Files:**
- Modify: `frontend/e2e/timebox.spec.ts`
- Modify: `README.md`

- [ ] **Step 1: Add a failing E2E test for hierarchical path create and parent rename**

```ts
test('creates a hierarchical task type from the block editor and renames its parent branch', async ({ page, request }) => {
  const date = '2026-06-04'
  const base = 'http://127.0.0.1:8000'

  await page.goto(`/day/${date}`)
  await expect(page.getByTestId('day-date')).toHaveText(date, { timeout: 30_000 })

  const baseType = await ensureTaskType(request, base, 'coding')
  const createDay = await request.post(`${base}/days/${date}/blocks`, {
    data: { lane: 'planned', task_type_id: baseType, start_minute: 480, end_minute: 510 },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(createDay.ok()).toBeTruthy()

  const blockId = (await createDay.json()).time_blocks[0].id as number
  await page.reload()
  await page.locator(`[data-block-id="${blockId}"]`).click()
  await page.getByLabel('Task type').fill('coding/ai')
  await page.getByRole('option', { name: /create "coding\/ai"/i }).click()
  await page.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('Saved')).toBeVisible({ timeout: 15_000 })

  const rows = (await (await request.get(`${base}/task-types`)).json()) as Array<{ id: number; name: string }>
  expect(rows.map((row) => row.name)).toContain('coding')
  expect(rows.map((row) => row.name)).toContain('coding/ai')

  await page.getByRole('link', { name: 'Task Types' }).click()
  const codingRow = rows.find((row) => row.name === 'coding')
  expect(codingRow).toBeTruthy()
  const codingInput = page.getByRole('textbox', { name: new RegExp(`Task type name ${codingRow!.id}`, 'i') })
  await codingInput.fill('development')
  await codingInput.blur()
  await expect(page.getByText('Saved')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByRole('textbox', { name: /Task type name/i }).nth(1)).toHaveValue('development/ai')

  await page.getByRole('link', { name: 'Today' }).click()
  await page.locator(`[data-block-id="${blockId}"]`).click()
  await expect(page.getByLabel('Task type')).toHaveValue('development/ai')
})
```

- [ ] **Step 2: Update the E2E helper so path lookups use canonical lowercase names**

```ts
function canonicalTaskTypeName(name: string): string {
  return name
    .trim()
    .split('/')
    .map((segment) => segment.trim().toLowerCase())
    .join('/')
}

async function ensureTaskType(request: APIRequestContext, base: string, name: string): Promise<number> {
  const canonical = canonicalTaskTypeName(name)
  const list = await request.get(`${base}/task-types`)
  expect(list.ok()).toBeTruthy()
  const rows = (await list.json()) as Array<{ id: number; name: string }>
  const found = rows.find((row) => row.name === canonical)
  if (found) return found.id

  const created = await request.post(`${base}/task-types`, {
    data: { name: canonical },
    headers: { 'Content-Type': 'application/json' },
  })
  expect(created.ok()).toBeTruthy()
  return (await created.json()).id as number
}
```

- [ ] **Step 3: Update README product docs and spec references**

```md
- **Task types:** Manage reusable task type paths under **Task types** (`GET`/`POST`/`PATCH`/`DELETE /task-types`). Each task type stores a canonical lowercase path such as `coding`, `coding/ai`, or `exercise/cardio`, and block editing can create a missing path inline; see `docs/superpowers/specs/2026-04-15-hierarchical-task-type-paths-design.md`.
```

- [ ] **Step 4: Run the targeted E2E spec**

Run: `cd frontend && npm run e2e -- -g "creates a hierarchical task type from the block editor and renames its parent branch"`

Expected: PASS in Chromium, including inline create, ancestor availability, and branch rename persistence

- [ ] **Step 5: Run the full project verification pass**

Run: `cd backend && uv run pytest && cd ..\\frontend && npm test && npm run e2e`

Expected: PASS for backend tests, frontend unit/component tests, and Playwright end-to-end coverage

- [ ] **Step 6: Commit the E2E and docs updates**

```bash
git add frontend/e2e/timebox.spec.ts README.md
git commit -m "test: cover hierarchical task type flows"
```

## Self-Review

- Spec coverage: backend path semantics, migration, inline create, descendant rename, conservative delete, unit tests, and E2E verification are all covered by Tasks 1-5.
- Placeholder scan: no `TODO`/`TBD` placeholders remain; each task lists exact files, commands, and concrete code snippets.
- Type consistency: the plan consistently uses `TaskType.name` as the canonical path, `onCreateTaskTypePath` for modal inline create, and `task_type_id` as the persisted block reference.
