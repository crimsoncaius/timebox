import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { ActivityTracking } from './ActivityTracking'
import { ActivityRepository } from './activityRepository'

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); localStorage.clear() })

function chooseTaskType(name: string) {
  fireEvent.focus(screen.getByLabelText('Task Type'))
  fireEvent.change(screen.getByLabelText('Task Type'), { target: { value: name } })
  fireEvent.click(screen.getByRole('option', { name: new RegExp(name, 'i') }))
}

it('keeps the recording and refresh subscription alive while controls are hidden, then restores Stop', async () => {
  const at = new Date().toISOString()
  const row = { id: 172, name: 'Design', task_type_id: 1, task_type: { id: 1, name: 'Design' }, start_at: at, end_at: null }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ protocol: 'activity-online-v1', cursor: 1, server_at: at, reporting_timezone: 'UTC', current: row, records: [row] }))))
  const repository = new ActivityRepository(localStorage, work => work())
  const command = vi.spyOn(repository, 'command')
  const view = render(<ActivityTracking repository={repository} taskTypes={[]} controlsVisible={false} onChanged={() => {}} />)
  await waitFor(() => expect(repository.state.snapshot?.current?.id).toBe(172))
  expect(screen.queryByRole('button', { name: 'Stop' })).not.toBeInTheDocument()
  const refresh = vi.spyOn(repository, 'refresh')
  fireEvent(window, new Event('focus'))
  await waitFor(() => expect(refresh).toHaveBeenCalled())
  view.rerender(<ActivityTracking repository={repository} taskTypes={[]} controlsVisible onChanged={() => {}} />)
  expect(screen.getByRole('button', { name: 'Stop' })).toBeEnabled()
  expect(repository.state.snapshot?.current?.id).toBe(172)
  expect(command).not.toHaveBeenCalled()
})

it('reconciles a superseded offline chain without clearing newer remote time, and shows brief feedback', async () => {
  const at = '2026-09-11T10:00:00Z'
  const row = { id: 1, name: 'Writing', task_type_id: 1, task_type: { id: 1, name: 'writing' }, start_at: at, end_at: null }
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: at, current: row, records: [row] }
  let connected = true, allowStop = false
  let canonical: Record<string, unknown> = initial
  const submitted: Record<string, unknown>[] = []
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (!connected) throw new Error('Offline')
    if (!init?.method) return new Response(JSON.stringify(canonical))
    const command = JSON.parse(init.body)
    submitted.push(command)
    if (command.kind === 'stop' && !allowStop) throw new Error('Connection lost')
    const reading = { ...row, id: 3, name: 'Reading', start_at: '2026-09-11T12:10:00Z' }
    canonical = { ...initial, cursor: command.kind === 'stop' ? 4 : 3, current: reading,
      records: [{ ...row, end_at: '2026-09-11T12:00:00Z' }, { ...row, id: 2, name: 'Lunch', start_at: '2026-09-11T12:00:00Z', end_at: reading.start_at }, reading],
      operation_outcomes: { [command.operation_id]: { device_id: command.device_id, outcome: 'superseded' } },
      coverage: [
        { start: at, end: '2026-09-11T12:00:00Z', record_id: 1, order: ['', '', 0, 'baseline'] },
        { start: '2026-09-11T12:00:00Z', end: reading.start_at, record_id: 2, order: ['2026-09-11T12:00:00Z', command.device_id, 1, 'lunch'] },
        { start: reading.start_at, end: null, record_id: 3, order: [reading.start_at, 'remote', 1, 'reading'] },
      ], acknowledgement: { operation_id: command.operation_id, outcome: 'superseded' } }
    return new Response(JSON.stringify(canonical))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  connected = false
  vi.spyOn(repository, 'now').mockReturnValue(Date.parse('2026-09-11T12:00:00Z'))
  await repository.command('switch', 1, 'Lunch')
  await repository.refresh()
  vi.spyOn(repository, 'now').mockReturnValue(Date.parse('2026-09-11T12:05:00Z'))
  await repository.command('stop')
  await repository.refresh()
  connected = true
  const restored = new ActivityRepository(localStorage, work => work())
  await restored.refresh()
  expect(restored.state.pending).toBe(true)
  expect(restored.state.snapshot?.current?.name).toBe('Reading')
  expect(restored.state.snapshot?.records.filter(r => r.name === 'Lunch')[0].end_at).toBe('2026-09-11T12:05:00.000Z')
  const view = render(<ActivityTracking repository={restored} taskTypes={[]} onChanged={() => {}} />)
  await screen.findByText('A newer change on another device updated this time.')
  expect(screen.getByRole('button', { name: 'Stop' })).toBeEnabled()
  allowStop = true
  await act(() => restored.refresh())
  expect(restored.state.pending).toBe(false)
  expect(restored.state.error).toBeNull()
  expect(restored.state.snapshot?.current?.name).toBe('Reading')
  await act(() => restored.dismissFeedback())
  expect(screen.queryByText('A newer change on another device updated this time.')).not.toBeInTheDocument()
  expect(submitted.filter(c => c.kind === 'stop').every(c => c.action_at === '2026-09-11T12:05:00.000Z')).toBe(true)
  view.unmount()
})

it('starts an explicitly chosen unspecified Task Type and retries a lost acknowledgement with the original command', async () => {
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: '2026-09-11T10:00:00Z', current: null, records: [] }
  let saved = initial
  const operations: unknown[] = []
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (init?.method === 'POST') {
      const command = JSON.parse(init.body)
      operations.push(command)
      if (operations.length === 1) throw new Error('Connection lost')
      return new Response(JSON.stringify({ ...initial, cursor: 1, current: {
        id: 1, name: null, task_type: { id: 1, name: 'unspecified' }, start_at: initial.server_at,
      }, acknowledgement: { operation_id: command.operation_id, outcome: 'applied' } }))
    }
    return new Response(JSON.stringify(saved))
  }))
  const repository = new ActivityRepository(localStorage, (work) => work())
  const view = render(<ActivityTracking repository={repository} taskTypes={[{ id: 1, name: 'unspecified', created_at: '', updated_at: '' }]} onChanged={() => {}} />)
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start tracking' })).toBeEnabled())
  fireEvent.click(screen.getByRole('button', { name: 'Start tracking' }))
  chooseTaskType('unspecified')
  fireEvent.click(screen.getByRole('button', { name: 'Start' }))
  await screen.findByText('Unsynced')
  expect(operations[0]).toMatchObject({ kind: 'start', task_type_id: 1 })
  expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()
  expect(screen.queryByText(/Connection lost/)).not.toBeInTheDocument()
  view.unmount()
  // A normal app restart keeps the operation ready for an explicit retry.
  const restored = new ActivityRepository(localStorage, (work) => work())
  render(<ActivityTracking repository={restored} taskTypes={[]} onChanged={() => {}} />)
  fireEvent.click(await screen.findByRole('button', { name: 'Retry' }))
  await screen.findByText('unspecified')
  expect(operations[1]).toEqual(operations[0])
  // An older read cannot turn recording off.
  saved = initial
  await act(() => restored.refresh())
  expect(screen.getByRole('button', { name: 'Stop' })).toBeInTheDocument()
})

it('requires Task Type for switching, allows no name, and stops without completing a Task', async () => {
  const current = { id: 1, name: null, task_type: { id: 1, name: 'unspecified' }, start_at: '2026-09-11T10:00:00Z' }
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: current.start_at, current, records: [current] }
  const commands: Record<string, unknown>[] = []
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (!init?.method) return new Response(JSON.stringify(initial))
    const command = JSON.parse(init.body)
    commands.push(command)
    return new Response(JSON.stringify({ ...initial, cursor: commands.length + 1,
      current: command.kind === 'stop' ? null : { ...current, id: 2, task_type: { id: 2, name: 'reading' } },
      acknowledgement: { operation_id: command.operation_id, outcome: 'applied' },
    }))
  }))
  render(<ActivityTracking repository={new ActivityRepository(localStorage, (work) => work())}
    taskTypes={[{ id: 2, name: 'reading', created_at: '', updated_at: '' }]} onChanged={() => {}} />)
  fireEvent.click(await screen.findByRole('button', { name: 'Switch' }))
  expect(screen.getByRole('button', { name: 'Switch activity' })).toBeDisabled()
  fireEvent.focus(screen.getByLabelText('Task Type'))
  fireEvent.change(screen.getByLabelText('Task Type'), { target: { value: 'reading' } })
  fireEvent.click(screen.getByRole('option', { name: /reading/i }))
  fireEvent.click(screen.getByRole('button', { name: 'Switch activity' }))
  await screen.findByText('reading')
  expect(commands[0]).toMatchObject({ kind: 'switch', task_type_id: 2, target_id: 1 })
  expect(commands[0]).not.toHaveProperty('name')
  fireEvent.click(screen.getByRole('button', { name: 'Stop' }))
  expect(screen.getByRole('region', { name: 'After this change' })).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Stop tracking' }))
  await screen.findByRole('button', { name: 'Start tracking' })
  expect(commands[1]).toMatchObject({ kind: 'stop', target_id: 2 })
})

it('does not send a command when durable storage fails', async () => {
  const fetch = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(async () => new Response(JSON.stringify({ protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: '2026-09-11T10:00:00Z', current: null, records: [] })))
  vi.stubGlobal('fetch', fetch)
  const repository = new ActivityRepository(localStorage, (work) => work())
  await repository.refresh()
  const failure = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Storage full') })
  render(<ActivityTracking repository={repository} taskTypes={[{ id: 2, name: 'reading', created_at: '', updated_at: '' }]} onChanged={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: 'Start tracking' }))
  chooseTaskType('reading')
  fireEvent.click(screen.getByRole('button', { name: 'Start' }))
  await screen.findByText('Unsynced')
  expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()
  expect(screen.queryByText(/Activity storage failed/)).not.toBeInTheDocument()
  expect(fetch.mock.calls.some((args) => args[1]?.method === 'POST')).toBe(false)
  expect(repository.state.snapshot?.current).toBeNull()
  failure.mockRestore()
})

it('a delayed older refresh cannot replace newer clock calibration', async () => {
  let release!: (response: Response) => void
  const delayed = new Promise<Response>((resolve) => { release = resolve })
  let reads = 0
  const snapshot = (cursor: number, time: string) => new Response(JSON.stringify({ protocol: 'activity-online-v1', offline_ready: true, cursor, server_at: time, current: null, records: [] }))
  vi.stubGlobal('fetch', vi.fn(() => ++reads === 1 ? delayed : Promise.resolve(snapshot(2, '2026-09-11T12:00:00Z'))))
  const repository = new ActivityRepository(localStorage, (work) => work())
  const oldRead = repository.refresh()
  await repository.refresh()
  release(snapshot(1, '2026-09-11T11:00:00Z'))
  await oldRead
  expect(repository.now()).toBeGreaterThanOrEqual(Date.parse('2026-09-11T12:00:00Z'))
  expect(repository.state.snapshot?.cursor).toBe(2)
})

it('records a sequence offline, restores pending history after restart, and replays immutable envelopes', async () => {
  const at = new Date().toISOString()
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: at, reporting_timezone: 'UTC', current: null, records: [], task_types: [{ id: 2, name: 'reading', created_at: at, updated_at: at }] }
  let connected = true
  const submitted: unknown[] = []
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (!connected) throw new Error('Offline')
    if (init?.method === 'POST') {
      const command = JSON.parse(init.body)
      submitted.push(command)
      return new Response(JSON.stringify({ ...initial, cursor: command.sequence, acknowledgement: { operation_id: command.operation_id, outcome: 'applied' } }))
    }
    return new Response(JSON.stringify({ ...initial, cursor: submitted.length }))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  connected = false
  await repository.refresh()
  await repository.command('start')
  await repository.command('switch', 2)
  await repository.command('stop')
  await waitFor(() => expect(repository.state.pending).toBe(true))
  const before = JSON.parse(localStorage.getItem(localStorage.key(0)!)!)
  const restored = new ActivityRepository(localStorage, work => work())
  const view = render(<ActivityTracking repository={restored} taskTypes={[]} onChanged={() => {}} />)
  await screen.findByText('Offline · Unsynced')
  expect(restored.state.snapshot?.records).toHaveLength(2)
  expect(restored.state.snapshot?.records[0].end_at).toBe(restored.state.snapshot?.records[1].start_at)
  expect(restored.state.snapshot?.current).toBeNull()
  expect(screen.getByRole('button', { name: 'Start tracking' })).toBeEnabled()
  view.unmount()
  connected = true
  await restored.refresh()
  expect(submitted).toEqual(before.outbox)
  expect(restored.state.pending).toBe(false)
  await restored.refresh()
  expect(submitted).toHaveLength(3)
})

it('requires initial bootstrap and restores confirmed history after storage loss without inventing activity', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('Offline') }))
  const repository = new ActivityRepository(localStorage, work => work())
  expect(await repository.command('start')).toBe(false)
  expect(repository.state.snapshot).toBeNull()
  expect(repository.state.error).toMatch(/Connect once/)
})

it('retains calibrated action order through wall-clock reversal, restart, and recalibration', async () => {
  let wall = Date.parse('2026-09-11T08:00:00Z'), monotonic = 0, connected = true
  vi.spyOn(Date, 'now').mockImplementation(() => wall)
  vi.spyOn(performance, 'now').mockImplementation(() => monotonic)
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: '2026-09-11T10:00:00Z', current: null, records: [] }
  const submitted: { action_at: string; calibration: { offset_ms: number } }[] = []
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (!connected) throw new Error('Offline')
    const command = init?.method ? JSON.parse(init.body) : null
    if (command) submitted.push(command)
    return new Response(JSON.stringify({ ...initial, cursor: submitted.length,
      server_at: submitted.length ? '2026-09-11T12:00:00Z' : initial.server_at,
      acknowledgement: command ? { operation_id: command.operation_id, outcome: 'applied' } : null }))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  connected = false
  wall -= 3600000; monotonic += 60000
  await repository.command('start'); await repository.refresh()
  const start = repository.state.snapshot!.current!.start_at
  const restored = new ActivityRepository(localStorage, work => work())
  await restored.command('stop'); await restored.refresh()
  const before = JSON.parse(localStorage.getItem(localStorage.key(0)!)!).outbox
  connected = true
  await restored.refresh()
  expect(submitted).toEqual(before)
  expect(submitted[0].action_at).toBe(start)
  expect(Date.parse(submitted[1].action_at)).toBeGreaterThan(Date.parse(start))
  expect(submitted.every(c => c.calibration.offset_ms === 7200000)).toBe(true)
  expect(JSON.parse(localStorage.getItem(localStorage.key(0)!)!).calibration.offset_ms).toBe(18000000)
})

it('does not turn microsecond-separated canonical and pending actions into a device tie', () => {
  const start = '2026-09-11T12:00:00.000001Z'
  const row = { id: 9, name: 'Reading', start_at: start, end_at: null }
  localStorage.setItem('timebox.activity.online.v1:/api', JSON.stringify({
    device: 'z', sequence: 1, lastAction: Date.parse(start), pending: null,
    snapshot: { protocol: 'activity-online-v1', cursor: 2, current: row, records: [row],
      coverage: [{ start, end: null, record_id: 9, order: [start, 'a', 1, 'remote'] }] },
    outbox: [{ operation_id: 'pending', device_id: 'z', sequence: 1, kind: 'stop',
      action_at: '2026-09-11T12:00:00.000Z', effective: { mode: 'instant', at: '2026-09-11T12:00:00.000Z' } }],
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  expect(repository.state.snapshot?.current?.name).toBe('Reading')
  expect(repository.state.snapshot?.current?.start_at).toBe(start)
})


it('snapshots a current plan offline, resumes explicitly, and expires suggestions without rewriting history', async () => {
  const at = '2026-09-11T10:00:00Z'
  const type = { id: 1, name: 'Writing', created_at: at, updated_at: at }
  const plan = { id: 4, task_type_id: 1, task_id: 7, name: 'Chapter', note: 'Outline', start_at: at, end_at: '2026-09-11T11:00:00Z' }
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: at, current: null, records: [], task_types: [type], plans: [plan] }
  let connected = true
  vi.stubGlobal('fetch', vi.fn(async () => { if (!connected) throw new Error('Offline'); return new Response(JSON.stringify(initial)) }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  connected = false
  vi.spyOn(repository, 'now').mockReturnValue(Date.parse(at))
  const view = render(<ActivityTracking repository={repository} taskTypes={[type]} onChanged={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: 'Start tracking' }))
  await waitFor(() => expect(repository.state.snapshot?.current?.planned_block_id).toBe(4))
  expect(repository.state.snapshot?.current).toMatchObject({ name: 'Chapter', task_id: 7, note: 'Outline' })
  expect(screen.queryByText('Switch to planned activity')).not.toBeInTheDocument()
  view.unmount()
  const restored = new ActivityRepository(localStorage, work => work())
  vi.spyOn(restored, 'now').mockReturnValue(Date.parse('2026-09-11T10:15:00Z'))
  expect(restored.state.snapshot?.current?.planned_block_id).toBe(4)
  await restored.command('switch', 1, 'Break')
  const resumedView = render(<ActivityTracking repository={restored} taskTypes={[type]} onChanged={() => {}} />)
  await screen.findByText('Switch to planned activity')
  expect(restored.state.snapshot?.current?.planned_block_id).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Switch to planned activity' }))
  await waitFor(() => expect(restored.state.snapshot?.current?.planned_block_id).toBe(4))
  expect(restored.state.snapshot?.records.filter(r => r.planned_block_id === 4)).toHaveLength(2)
  expect(screen.getByText(/2 linked Actual Blocks/)).toBeInTheDocument()
  await act(() => restored.command('switch', 1, 'Lunch'))
  expect(screen.getByText('Switch to planned activity')).toBeInTheDocument()
  vi.spyOn(restored, 'now').mockReturnValue(Date.parse(plan.end_at))
  await act(() => restored.refresh())
  expect(screen.queryByText('Switch to planned activity')).not.toBeInTheDocument()
  expect(restored.state.snapshot?.current?.name).toBe('Lunch')
  resumedView.unmount()
})

it('records a direct Session Task offline without a plan or completion mutation', async () => {
  const at = '2026-09-11T10:00:00Z'
  let connected = true
  const fetch = vi.fn(async () => { if (!connected) throw new Error('Offline'); return new Response(JSON.stringify({ protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: at, current: null, records: [] })) })
  vi.stubGlobal('fetch', fetch)
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  connected = false
  expect(await repository.trackTask({ id: 8, title: 'Session 1', task_type_id: 2, status: 'todo', recurrence_kind: 'quota_session' })).toBe(true)
  const restored = new ActivityRepository(localStorage, work => work())
  expect(restored.state.snapshot?.current).toMatchObject({ task_id: 8, name: 'Session 1', planned_block_id: null })
  expect(await restored.trackTask({ id: 9, title: 'Quota', task_type_id: 2, status: 'todo', recurrence_kind: 'quota_parent' })).toBe(false)
  expect(restored.state.snapshot?.current?.task_id).toBe(8)
})

it('keeps historical corrections offline, rejects occupied time, and leaves deletion gaps after restart', async () => {
  const writing = { id: 1, name: 'Writing', task_type_id: 1, task_type: { id: 1, name: 'work' }, start_at: '2026-09-10T10:00:00Z', end_at: '2026-09-10T12:00:00Z' }
  const current = { ...writing, id: 2, name: 'Reading', start_at: '2026-09-11T10:00:00Z', end_at: null }
  let online = true
  vi.stubGlobal('fetch', vi.fn(async () => {
    if (!online) throw new Error('Offline')
    return new Response(JSON.stringify({ protocol: 'activity-online-v1', offline_ready: true, cursor: 2, server_at: '2026-09-11T13:00:00Z', reporting_timezone: 'UTC', current, records: [writing, current], task_types: [writing.task_type] }))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh(); online = false
  expect(await repository.correct('add', null, { start_at: '2026-09-10T11:00:00Z', end_at: '2026-09-10T11:30:00Z', name: 'Lunch', task_type_id: 1 })).toBe(false)
  expect(repository.state.snapshot?.records[0]).toEqual(writing)
  expect(await repository.correct('edit', 1, { end_at: '2026-09-10T11:00:00Z' })).toBe(true)
  expect(await repository.correct('add', null, { start_at: '2026-09-10T11:00:00Z', end_at: '2026-09-10T11:30:00Z', name: 'Lunch', task_type_id: 1 })).toBe(true)
  const restored = new ActivityRepository(localStorage, work => work())
  expect(restored.state.snapshot?.records.find(r => r.name === 'Lunch')).toBeDefined()
  expect(await restored.correct('delete', 1)).toBe(true)
  expect(restored.state.snapshot?.records.map(r => r.name).sort()).toEqual(['Lunch', 'Reading'])
  expect(restored.state.snapshot?.current).toEqual(current)
})

it('keeps add then multiple moves and delete correct while only the first acknowledgement arrives', async () => {
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: '2026-09-11T13:00:00Z', reporting_timezone: 'UTC', current: null, records: [], task_types: [{ id: 1, name: 'work' }] }
  let online = false, acknowledgeOne = false
  let canonical: Record<string, unknown> = initial
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (!init?.method) return new Response(JSON.stringify(canonical))
    if (!online || !acknowledgeOne) throw new Error('Offline')
    acknowledgeOne = false
    const command = JSON.parse(init.body)
    const row = { id: 88, task_type_id: 1, task_type: initial.task_types[0], name: 'Writing', start_at: command.effective.at, end_at: command.effective.end, task_id: null, planned_block_id: null, note: null }
    canonical = { ...initial, cursor: 1, records: [row], provenance: { 88: command.operation_id }, acknowledgement: { operation_id: command.operation_id, outcome: 'applied' } }
    return new Response(JSON.stringify(canonical))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  await repository.correct('add', null, { name: 'Writing', task_type_id: 1, start_at: '2026-09-10T10:00:00Z', end_at: '2026-09-10T12:00:00Z' })
  const localId = repository.state.snapshot!.records[0].id
  await repository.correct('edit', localId, { start_at: '2026-09-10T08:00:00Z', end_at: '2026-09-10T09:00:00Z' })
  await repository.correct('edit', localId, { start_at: '2026-09-10T06:00:00Z', end_at: '2026-09-10T07:00:00Z' })
  online = true; acknowledgeOne = true; await repository.refresh()
  expect(repository.state.snapshot!.records).toHaveLength(1)
  expect(repository.state.snapshot!.records[0].start_at).toBe('2026-09-10T06:00:00Z')
  const restored = new ActivityRepository(localStorage, work => work())
  expect(await restored.correct('delete', restored.state.snapshot!.records[0].id)).toBe(true)
  expect(restored.state.snapshot!.records).toEqual([])
})

it('confirms inline while offline, survives restart and never offers Stop in Focus', async () => {
  const at = '2026-09-11T10:00:00Z'
  const row = { id: 1, name: 'Writing', task_type_id: 1, task_type: { id: 1, name: 'writing' }, start_at: at, end_at: null }
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: at, current: row, records: [row], check_in: { enabled: true, threshold_minutes: 60, generation: 'a', rearm: 0, armed_at: at, question: { id: 'a:0', created_at: at, delivery: null } } }
  let online = true
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (!online || init?.method) throw new Error('Offline')
    return new Response(JSON.stringify(initial))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  online = false
  const view = render(<ActivityTracking repository={repository} taskTypes={[]} onChanged={() => {}} focus />)
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Stop' })).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Yes, still doing this' }))
  await waitFor(() => expect(screen.queryByRole('region', { name: 'Inactivity check-in' })).not.toBeInTheDocument())
  view.unmount()
  const restored = new ActivityRepository(localStorage, work => work())
  expect(restored.state.snapshot?.check_in?.question).toBeNull()
  expect(restored.state.snapshot?.current).toEqual(row)
  expect(restored.state.pending).toBe(true)
})

it('retains a qualified offline candidate and its original question identity across restart', async () => {
  const at = '2026-09-11T10:00:00Z', end = '2026-09-11T14:00:00Z'
  const row = { id: 1, name: 'Writing', task_type_id: 1, task_type: { id: 1, name: 'writing' }, start_at: at, end_at: null }
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: end, current: row, records: [row], check_in: { enabled: true, threshold_minutes: 60, generation: 'a', rearm: 0, armed_at: at, question: null } }
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => { if (init?.method) throw new Error('Offline'); return new Response(JSON.stringify(initial)) }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  await repository.checkIn({ action: 'observe', generation: 'a', rearm: 0, capability: 'supported', permission: 'granted', observed: 'active', coverage_start: '2026-09-11T11:30:00Z', coverage_end: '2026-09-11T11:30:00Z' })
  await repository.checkIn({ action: 'candidate', generation: 'a', rearm: 0, capability: 'supported', permission: 'granted', observed: 'idle', coverage_start: at, coverage_end: '2026-09-11T12:00:00Z' })
  expect(repository.state.snapshot?.check_in?.question).toBeNull()
  await repository.checkIn({ action: 'candidate', generation: 'a', rearm: 0, capability: 'supported', permission: 'granted', observed: 'idle', coverage_start: at, coverage_end: end })
  const restored = new ActivityRepository(localStorage, work => work())
  expect(restored.state.snapshot?.check_in?.question?.id).toBe('a:0')
  await restored.checkIn({ action: 'confirm', question_id: 'a:0' })
  expect(new ActivityRepository(localStorage, work => work()).state.snapshot?.check_in?.question).toBeNull()
  expect(restored.state.snapshot?.current).toEqual(row)
})

it.each([false, true])('shows three-hour activity without total-minute conversion (focus=%s)', async (focus) => {
  const at = '2026-09-11T10:00:00Z'
  const now = '2026-09-11T13:00:07Z'
  const current = { id: 1, name: 'Writing', task_type_id: 1, task_type: { id: 1, name: 'writing' }, start_at: at, end_at: null }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: now, current, records: [current],
  }))))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  vi.spyOn(repository, 'now').mockReturnValue(Date.parse(now))
  const view = render(<ActivityTracking focus={focus} repository={repository} taskTypes={[]} onChanged={() => {}} />)
  expect(screen.getByLabelText('Elapsed time')).toHaveTextContent('3 hours')
  await act(async () => {})
  view.unmount()
})

it('asks for a Task Type before starting without a covering Planned Block, and records nothing until confirmed', async () => {
  const at = '2026-09-11T10:00:00Z'
  const reading = { id: 2, name: 'reading', created_at: at, updated_at: at }
  const initial = { protocol: 'activity-online-v1', offline_ready: true, cursor: 0, server_at: at, reporting_timezone: 'UTC', current: null, records: [], task_types: [reading],
    plans: [{ id: 4, task_type_id: 2, task_id: null, name: 'Later', note: null, start_at: '2026-09-11T10:05:00Z', end_at: '2026-09-11T11:00:00Z' }] }
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (init?.method) throw new Error('Offline')
    return new Response(JSON.stringify(initial))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  const clock = vi.spyOn(repository, 'now').mockReturnValue(Date.parse(at))
  render(<ActivityTracking repository={repository} taskTypes={[reading]} onChanged={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: 'Start tracking' }))
  const form = screen.getByRole('form', { name: 'Start tracking' })
  expect(screen.queryByText('When did this change happen?')).not.toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'After this change' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Start' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Start tracking' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(form).not.toBeInTheDocument()
  expect(repository.state.snapshot?.current).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Start tracking' }))
  chooseTaskType('reading')
  fireEvent.change(screen.getByLabelText('Block Name (optional)'), { target: { value: 'Paper' } })
  clock.mockReturnValue(Date.parse('2026-09-11T10:02:00Z'))
  fireEvent.click(screen.getByRole('button', { name: 'Start' }))
  await waitFor(() => expect(repository.state.snapshot?.current).toMatchObject({ name: 'Paper', task_type_id: 2, planned_block_id: null }))
  expect(repository.state.snapshot?.current?.start_at).toBe('2026-09-11T10:02:00.000Z')
  expect(screen.queryByRole('form', { name: 'Start tracking' })).not.toBeInTheDocument()
})


it.each(['outbox', 'pending'])('retains obsolete %s changes for recovery across restart', async field => {
  const at = '2026-09-11T10:00:00Z'
  const row = { id: 7, task_type_id: 1, task_type: { id: 1, name: 'unspecified' }, start_at: at, end_at: null }
  const snapshot = { protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: at, current: row, records: [row] }
  const obsolete = { kind: 'describe', operation_id: 'obsolete', name: 'Keep my text' }
  const dependent = { kind: 'stop', operation_id: 'dependent', predecessor_id: 'obsolete' }
  const key = `timebox.activity.online.v1:${import.meta.env.VITE_API_BASE_URL ?? '/api'}`
  localStorage.setItem(key, JSON.stringify({ device: 'original', sequence: 3, lastAction: 0, snapshot,
    rejected: { kind: 'edit', operation_id: 'previous' },
    ...(field === 'pending' ? { pending: obsolete } : { outbox: [obsolete, dependent] }) }))
  const repository = new ActivityRepository(localStorage, work => work())
  expect(repository.state.snapshot?.current).toEqual(row)
  expect(repository.state.pending).toBe(false)
  expect(repository.recoveryData()).toContain('Keep my text')
  expect(repository.recoveryData()).toContain('previous')
  if (field === 'outbox') expect(repository.recoveryData()).toContain('dependent')
  const restored = new ActivityRepository(localStorage, work => work())
  expect(restored.recoveryData()).toBe(repository.recoveryData())
  expect(restored.state.error).toBeNull()
  const fetch = vi.fn(async () => new Response(JSON.stringify(snapshot)))
  vi.stubGlobal('fetch', fetch)
  await restored.refresh()
  expect(fetch).toHaveBeenCalledTimes(1)
  expect(JSON.parse(localStorage.getItem(key)!).device).toBe('original')
})
