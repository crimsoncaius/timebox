import { describe, expect, it } from 'vitest'
import type { TaskType } from './api'
import {
  buildTaskTypeSuggestions,
  canonicalizeTaskTypePathInput,
  createAncestorHint,
  filterTaskTypesByQuery,
  formatTaskTypePathParts,
  groupTaskTypesByRoot,
  pathDepth,
  rankTaskTypes,
  taskTypeRootSegment,
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

  it('drops empty segments and trailing slashes', () => {
    expect(canonicalizeTaskTypePathInput('coding//ai')).toBe('coding/ai')
    expect(canonicalizeTaskTypePathInput('coding/')).toBe('coding')
    expect(canonicalizeTaskTypePathInput('coding//ai/')).toBe('coding/ai')
  })

  it('collapses inner whitespace in segments', () => {
    expect(canonicalizeTaskTypePathInput('Deep   Work / Writing')).toBe('deep work/writing')
  })

  it('returns null when nothing survives', () => {
    expect(canonicalizeTaskTypePathInput('')).toBeNull()
    expect(canonicalizeTaskTypePathInput('   ')).toBeNull()
    expect(canonicalizeTaskTypePathInput('///')).toBeNull()
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

  it('offers create for a repaired new path', () => {
    expect(buildTaskTypeSuggestions(rows, 'coding//personal/').createPath).toBe('coding/personal')
  })

  it('filterTaskTypesByQuery returns all sorted rows for empty query', () => {
    expect(filterTaskTypesByQuery(rows, '').map((t) => t.name)).toEqual([
      'coding',
      'coding/ai',
      'exercise/cardio',
    ])
  })

  it('filterTaskTypesByQuery matches partial root like cod to coding branch', () => {
    expect(filterTaskTypesByQuery(rows, 'cod').map((t) => t.name)).toEqual(['coding', 'coding/ai'])
  })

  it('filterTaskTypesByQuery matches nested partial query coding/a', () => {
    expect(filterTaskTypesByQuery(rows, 'coding/a').map((t) => t.name)).toEqual(['coding', 'coding/ai'])
  })

  it('filterTaskTypesByQuery matches child segment without matching parent root', () => {
    expect(filterTaskTypesByQuery(rows, 'ai').map((t) => t.name)).toEqual(['coding/ai'])
  })

  it('filterTaskTypesByQuery matches query aligned to path suffix segments', () => {
    const deep: TaskType[] = [
      { id: 1, name: 'a/b/c', created_at: '', updated_at: '' },
      { id: 2, name: 'x/y', created_at: '', updated_at: '' },
    ]
    expect(filterTaskTypesByQuery(deep, 'b/c').map((t) => t.name)).toEqual(['a/b/c'])
  })

  it('matches stored names case-insensitively', () => {
    const mixed: TaskType[] = [{ id: 2, name: 'Reading', created_at: '', updated_at: '' }]
    expect(filterTaskTypesByQuery(mixed, 'reading').map((t) => t.name)).toEqual(['Reading'])
    expect(buildTaskTypeSuggestions(mixed, 'Reading').createPath).toBeNull()
  })

  it('filterTaskTypesByQuery matches a repaired double-slash query', () => {
    expect(filterTaskTypesByQuery(rows, 'coding//a').map((t) => t.name)).toEqual([
      'coding',
      'coding/ai',
    ])
  })

  it('taskTypeRootSegment and pathDepth', () => {
    expect(taskTypeRootSegment('coding/ai')).toBe('coding')
    expect(taskTypeRootSegment('work')).toBe('work')
    expect(pathDepth('a')).toBe(0)
    expect(pathDepth('a/b/c')).toBe(2)
  })

  it('groupTaskTypesByRoot sorts and groups by first segment', () => {
    const mixed: TaskType[] = [
      { id: 3, name: 'coding/ai', created_at: '', updated_at: '' },
      { id: 1, name: 'coding', created_at: '', updated_at: '' },
      { id: 2, name: 'dev/x', created_at: '', updated_at: '' },
    ]
    const groups = groupTaskTypesByRoot(mixed)
    expect(groups.map((g) => g.root)).toEqual(['coding', 'dev'])
    expect(groups[0]!.items.map((t) => t.name)).toEqual(['coding', 'coding/ai'])
    expect(groups[1]!.items.map((t) => t.name)).toEqual(['dev/x'])
  })
})

describe('rankTaskTypes', () => {
  const types: TaskType[] = [
    { id: 1, name: 'coding', usage_count: 64, created_at: '', updated_at: '' },
    { id: 2, name: 'coding/ai', usage_count: 41, created_at: '', updated_at: '' },
    { id: 3, name: 'coding/ai/agents', usage_count: 32, created_at: '', updated_at: '' },
    { id: 4, name: 'reading', usage_count: 19, created_at: '', updated_at: '' },
    { id: 9, name: 'unspecified', usage_count: 400, created_at: '', updated_at: '' },
  ]

  it('empty query pins current, then usage, with unspecified last', () => {
    expect(rankTaskTypes(types, '  ', 2).map((t) => t.name)).toEqual([
      'coding/ai',
      'coding',
      'coding/ai/agents',
      'reading',
      'unspecified',
    ])
  })

  it('does not repeat unspecified when it is already current', () => {
    expect(rankTaskTypes(types, '', 9).map((t) => t.name)[0]).toBe('unspecified')
    expect(rankTaskTypes(types, '', 9).filter((t) => t.name === 'unspecified')).toHaveLength(1)
  })

  it('typed query keeps nearby paths for a new slashed path', () => {
    expect(rankTaskTypes(types, 'coding/ai/tooling').map((t) => t.name)).toEqual([
      'coding',
      'coding/ai',
    ])
  })

  it('typed query sorts by path score then name, not usage or current', () => {
    expect(rankTaskTypes(types, 'coding/ai', 1).map((t) => t.name)).toEqual([
      'coding/ai',
      'coding/ai/agents',
      'coding',
    ])
  })
})

describe('createAncestorHint', () => {
  it('names the parent when every ancestor already exists', () => {
    expect(createAncestorHint(rows, 'coding/ai/tooling')).toEqual({
      lead: 'Adds under existing ',
      path: 'coding/ai',
    })
  })

  it('lists only the ancestors that will be created', () => {
    expect(createAncestorHint(rows, 'coding/tooling/scripts')).toEqual({
      lead: 'Also creates ',
      path: 'coding/tooling',
    })
  })

  it('a top level path has nothing to explain', () => {
    expect(createAncestorHint(rows, 'errands')).toBeNull()
  })
})
