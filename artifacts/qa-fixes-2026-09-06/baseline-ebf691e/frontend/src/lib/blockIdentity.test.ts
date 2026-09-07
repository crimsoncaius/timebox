import { describe, expect, it } from 'vitest'
import type { TimeBlock } from './api'
import { blockPrimaryIdentity, blockSecondaryIdentity } from './blockIdentity'

const base: TimeBlock = {
  id: 1,
  lane: 'planned',
  task_type_id: 2,
  task_type: { id: 2, name: 'Deep work', created_at: '', updated_at: '' },
  task_id: 7,
  task: { id: 7, title: 'Prepare launch', status: 'open', task_type_id: 2 },
  name: null,
  note: null,
  start_minute: 600,
  end_minute: 660,
  created_at: '',
  updated_at: '',
}

describe('Block identity', () => {
  it('puts a linked Block Name first and keeps the Battle Plan Task as context', () => {
    const named = { ...base, name: 'Outline session' }
    expect(blockPrimaryIdentity(named)).toBe('Outline session')
    expect(blockSecondaryIdentity(named)).toBe('Prepare launch')
  })

  it('uses the linked task first when unnamed and keeps meaningful Task Type context', () => {
    expect(blockPrimaryIdentity(base)).toBe('Prepare launch')
    expect(blockSecondaryIdentity(base)).toBe('Deep work')
  })

  it('preserves ordinary fallbacks after unlink without copying the former task title', () => {
    const namedUnlinked = { ...base, task_id: null, task: null, name: 'Outline session' }
    expect(blockPrimaryIdentity(namedUnlinked)).toBe('Outline session')
    expect(blockSecondaryIdentity(namedUnlinked)).toBe('Deep work')

    const unnamedUnlinked = { ...namedUnlinked, name: null }
    expect(blockPrimaryIdentity(unnamedUnlinked)).toBe('Deep work')
    expect(blockSecondaryIdentity(unnamedUnlinked)).toBeNull()
  })
})
