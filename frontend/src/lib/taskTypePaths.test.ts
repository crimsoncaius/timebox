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
