import { afterEach, expect, it, vi } from 'vitest'
import { ActivityRepository, type ActivitySnapshot, type ActivitySwitchUndo } from './activityRepository'

afterEach(() => { localStorage.clear(); vi.unstubAllGlobals(); vi.restoreAllMocks() })
const type = { id: 1, name: 'Work', created_at: '', updated_at: '' }
const a = { id: 1, task_type_id: 1, task_type: type, task_id: null, task: null, name: 'A', note: 'Keep this', planned_block_id: null, start_at: '2026-09-10T08:00:00Z', end_at: '2026-09-10T10:00:00Z', created_at: '', updated_at: '' }
const b = { ...a, id: 2, name: 'B', start_at: '2026-09-10T11:00:00Z', end_at: null }
const initial: ActivitySnapshot = { protocol: 'activity-online-v1', offline_ready: true, switch_history_ready: true, cursor: 1, server_at: '2026-09-10T14:00:00Z', reporting_timezone: 'UTC', current: b, records: [a, b], task_types: [type] }

it('durably restores full records, metadata and gaps offline, including after restart', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(initial))))
  const repository = new ActivityRepository(localStorage, work => work())
  let offer: ActivitySwitchUndo | undefined
  repository.subscribeSwitch(next => { offer = next })
  await repository.refresh()
  vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('Offline') }))
  expect(await repository.command('switch', 1, 'C', false, undefined, { targetId: 2, at: '2026-09-10T09:30:00Z' })).toBe(true)
  await repository.refresh()
  expect(repository.state.snapshot?.records.map(r => [r.name, r.start_at, r.end_at])).toEqual([
    ['A', a.start_at, '2026-09-10T09:30:00.000Z'], ['C', '2026-09-10T09:30:00Z', null],
  ])
  await repository.undoSwitch(offer!.operationId)
  await repository.refresh()
  expect(repository.state.snapshot?.records).toEqual(initial.records)
  const restarted = new ActivityRepository(localStorage, work => work())
  expect(restarted.state.snapshot?.records).toEqual(initial.records)
  expect(restarted.state.pending).toBe(true)
})

it('does not consume Undo or change recording when durable storage fails', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(initial))))
  const repository = new ActivityRepository(localStorage, work => work())
  let offer: ActivitySwitchUndo | undefined
  repository.subscribeSwitch(next => { offer = next })
  await repository.refresh()
  vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('Offline') }))
  await repository.command('switch', 1, 'C'); await repository.refresh()
  const save = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Disk full') })
  await expect(repository.undoSwitch(offer!.operationId)).rejects.toThrow('storage failed')
  expect(repository.state.snapshot?.current?.name).toBe('C')
  save.mockRestore()
  await repository.undoSwitch(offer!.operationId)
  expect(repository.state.snapshot?.current?.name).toBe('B')
})

it('invalidates an earlier Undo after another switch and keeps Stop bounds', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(initial))))
  const repository = new ActivityRepository(localStorage, work => work())
  const offers: ActivitySwitchUndo[] = []
  repository.subscribeSwitch(offer => offers.push(offer))
  await repository.refresh()
  vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('Offline') }))
  expect(await repository.command('stop', undefined, undefined, false, undefined, { targetId: 2, at: '2026-09-10T09:00:00Z' })).toBe(false)
  await repository.command('switch', 1, 'C'); await repository.refresh()
  await repository.command('switch', 1, 'D'); await repository.refresh()
  await expect(repository.undoSwitch(offers[0].operationId)).rejects.toThrow('no longer available')
  expect(repository.state.snapshot?.current?.name).toBe('D')
})
