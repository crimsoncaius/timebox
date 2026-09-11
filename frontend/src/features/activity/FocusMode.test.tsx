import { afterEach, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ActivityRepository } from './activityRepository'
import { ActivityTracking } from './ActivityTracking'
import { FocusController } from './focusController'
import { observeFocusWake } from './focusWake'

afterEach(() => { localStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
async function fixture(running = true) {
  const at = '2026-09-11T10:00:00Z'
  const unspecified = { id: 1, name: 'unspecified', created_at: at, updated_at: at }
  const current = running ? { id: 7, task_type_id: 1, task_type: unspecified, start_at: at, end_at: null, name: null, task_id: null, planned_block_id: null, task: null, note: null, created_at: at, updated_at: at } : null
  const snapshot = { protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: at, reporting_timezone: 'UTC', current, records: current ? [current] : [], task_types: [unspecified, { ...unspecified, id: 2, name: 'Reading' }] }
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => { if (init?.method) throw Error('Offline'); return new Response(JSON.stringify(snapshot)) }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  return { repository, snapshot }
}
it('retains current identity, persists local Focus without expiry, exits on remote stop and never restarts', async () => {
  const { repository, snapshot } = await fixture()
  const controller = new FocusController(localStorage)
  expect(await controller.enter(repository)).toBe(true)
  expect(repository.state.snapshot?.current?.id).toBe(7)
  const restored = new FocusController(localStorage)
  expect(restored.state.active).toBe(true)
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ ...snapshot, cursor: 2, current: null }))))
  await repository.refresh(); restored.reconcile(repository)
  expect(restored.state.active).toBe(false)
  expect(repository.state.snapshot?.current).toBeNull()
})
it('rejects planning and cancels an in-flight entry without deferring or discarding planning', async () => {
  const { repository } = await fixture(false)
  const controller = new FocusController(localStorage)
  controller.setPlanning(true)
  expect(await controller.enter(repository)).toBe(false)
  controller.setPlanning(false)
  let finish!: (value: boolean) => void
  vi.spyOn(repository, 'command').mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const entry = controller.enter(repository)
  controller.setPlanning(true); controller.setPlanning(false); finish(true)
  expect(await entry).toBe(false)
  expect(controller.state.active).toBe(false)
})
it('records an unknown description from original start offline and restores it, with no Stop in Focus', async () => {
  const { repository } = await fixture()
  render(<ActivityTracking repository={repository} taskTypes={[]} onChanged={() => {}} focus />)
  expect(screen.queryByRole('button', { name: 'Stop' })).toBeNull()
  expect(screen.getByText('What are you doing right now?')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Describe Task Type'), { target: { value: '2' } })
  fireEvent.click(screen.getByRole('button', { name: 'Apply from original start' }))
  await waitFor(() => expect(repository.state.snapshot?.current?.task_type.name).toBe('Reading'))
  const restored = new ActivityRepository(localStorage, work => work())
  expect(restored.state.snapshot?.records).toHaveLength(1)
  expect(restored.state.snapshot?.current).toMatchObject({ id: 7, start_at: '2026-09-11T10:00:00Z' })
  await act(async () => {})
})
it('releases a stale pending wake request and reacquires only for visible Focus', async () => {
  let visible = 'visible'
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visible as DocumentVisibilityState)
  const release = vi.fn(async () => {}), explain = vi.fn()
  let resolve!: (value: WakeLockSentinel) => void
  const request = vi.fn(() => new Promise<WakeLockSentinel>(r => { resolve = r }))
  Object.defineProperty(navigator, 'wakeLock', { configurable: true, value: { request } })
  const stop = observeFocusWake(true, explain)
  visible = 'hidden'; document.dispatchEvent(new Event('visibilitychange'))
  resolve({ release, addEventListener: vi.fn() } as unknown as WakeLockSentinel)
  await Promise.resolve(); expect(release).toHaveBeenCalledTimes(1)
  visible = 'visible'; document.dispatchEvent(new Event('visibilitychange')); expect(request).toHaveBeenCalledTimes(2)
  stop(); resolve({ release, addEventListener: vi.fn() } as unknown as WakeLockSentinel)
  await Promise.resolve(); expect(release).toHaveBeenCalledTimes(2)
})

it('requests no wake for ordinary tracking or off setting, and quietly tolerates refusal', async () => {
  const request = vi.fn(async () => { throw Error('Battery policy') }), explain = vi.fn()
  Object.defineProperty(navigator, 'wakeLock', { configurable: true, value: { request } })
  const off = observeFocusWake(false, explain)
  expect(request).not.toHaveBeenCalled(); off()
  const enabled = observeFocusWake(true, explain)
  await Promise.resolve()
  expect(explain).toHaveBeenCalledWith('The display may sleep. Focus and recording continue.')
  enabled()
})
