import { describe, expect, it } from 'vitest'
import type { TaskType } from './api'
import {
  buildTaskTypeSuggestions,
  canonicalizeTaskTypePathInput,
  filterTaskTypesByQuery,
  formatTaskTypePathParts,
  groupTaskTypesByRoot,
  pathDepth,
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

  it('filterTaskTypesByQuery falls back to all rows for invalid path input', () => {
    expect(filterTaskTypesByQuery(rows, 'coding//a').map((t) => t.name)).toEqual([
      'coding',
      'coding/ai',
      'exercise/cardio',
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
