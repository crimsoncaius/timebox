import { expect, it } from 'vitest'
import { compactStatusLabel, statusMarkKind, statusShowsRetry, type StatusFlags } from './activityStatus'

const flags = (value: Partial<StatusFlags>): StatusFlags => ({
  offline: false, pending: false, busy: false, hasSnapshot: true, error: null, ...value,
})

it('replaces confirmation with Saving… and keeps Offline while busy', () => {
  expect(compactStatusLabel(flags({ busy: true }))).toBe('Saving…')
  expect(compactStatusLabel(flags({ offline: true, busy: true }))).toBe('Offline · Saving…')
})

it('hides healthy Synced and keeps attention as a chip', () => {
  expect(statusMarkKind(flags({}))).toBeNull()
  expect(statusMarkKind(flags({ pending: true }))).toBe('chip')
  expect(statusMarkKind(flags({ busy: true }))).toBe('chip')
})

it('treats a storage or connection failure as Unsynced chrome with Retry', () => {
  const failed = flags({ pending: true, error: 'Activity storage failed: Storage full' })
  expect(compactStatusLabel(failed)).toBe('Unsynced')
  expect(statusShowsRetry(failed)).toBe(true)
  expect(compactStatusLabel(flags({ error: 'Connection lost' }))).toBe('Unsynced')
  expect(statusShowsRetry(flags({ error: 'Connection lost' }))).toBe(true)
  expect(statusShowsRetry(flags({ offline: true }))).toBe(false)
})
