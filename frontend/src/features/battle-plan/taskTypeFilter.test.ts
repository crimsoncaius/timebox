import { describe, expect, it } from 'vitest'
import type { TaskType } from '../../lib/api'
import { coveringTaskType, taskTypeFilterCoverage, toggleTaskTypeFilter } from './taskTypeFilter'

const type = (id: number, name: string): TaskType => ({ id, name, created_at: '', updated_at: '', usage_count: 0 })
const coding = type(1, 'coding')
const ai = type(2, 'coding/ai')
const evals = type(3, 'coding/ai/evals')
const codingTools = type(4, 'codingtools')
const writing = type(5, 'writing')
const types = [coding, ai, evals, codingTools, writing]

describe('taskTypeFilter', () => {
  it('covers the whole branch of a chosen parent but not prefix siblings', () => {
    expect([...taskTypeFilterCoverage(['1'], types)].sort()).toEqual(['1', '2', '3'])
    expect([...taskTypeFilterCoverage(['2', 'unset'], types)].sort()).toEqual(['2', '3', 'unset'])
  })

  it('names the shallowest chosen ancestor as the covering type', () => {
    expect(coveringTaskType(evals, ['1', '2'], types)).toBe(coding)
    expect(coveringTaskType(coding, ['1'], types)).toBeNull()
    expect(coveringTaskType(codingTools, ['1'], types)).toBeNull()
  })

  it('absorbs chosen descendants when a parent is chosen and clears on a second toggle', () => {
    expect(toggleTaskTypeFilter(['2', '3', '5', 'unset'], '1', types)).toEqual(['5', 'unset', '1'])
    expect(toggleTaskTypeFilter(['1', '5'], '1', types)).toEqual(['5'])
    expect(toggleTaskTypeFilter(['1'], 'unset', types)).toEqual(['1', 'unset'])
  })
})
