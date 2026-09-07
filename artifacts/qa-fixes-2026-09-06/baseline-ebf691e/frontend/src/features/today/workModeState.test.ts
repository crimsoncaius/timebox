import { beforeEach, describe, expect, it } from 'vitest'
import { readStoredWorkMode, writeStoredWorkMode, type StoredWorkMode } from './workModeState'

describe('Work Mode storage', () => {
  beforeEach(() => localStorage.clear())

  it('does not revive an explicitly exited session from a delayed write', () => {
    const session = storedWorkMode('2026-06-01T12:00:00Z')
    writeStoredWorkMode(session)
    writeStoredWorkMode(null, session.entryAt)

    writeStoredWorkMode({ ...session, lastObservedAt: '2026-06-01T12:10:00Z' })

    expect(readStoredWorkMode()).toBeNull()
  })

  it('allows a newly entered session after an earlier session exits', () => {
    const exited = storedWorkMode('2026-06-01T12:00:00Z')
    const next = storedWorkMode('2026-06-01T13:00:00Z')
    writeStoredWorkMode(exited)
    writeStoredWorkMode(null, exited.entryAt)

    writeStoredWorkMode(next)

    expect(readStoredWorkMode()).toEqual(next)
  })
})

function storedWorkMode(entryAt: string): StoredWorkMode {
  return {
    entryAt,
    lastConfirmedAt: entryAt,
    lastObservedAt: entryAt,
    confirmingPlannedBlockId: null,
    confirmationStartedAt: null,
    activeActualId: null,
    activePlannedBlockId: null,
    activePlannedEndAt: null,
  }
}
