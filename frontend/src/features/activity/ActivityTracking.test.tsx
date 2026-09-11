import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { ActivityTracking } from './ActivityTracking'
import { ActivityRepository } from './activityRepository'

afterEach(() => { vi.unstubAllGlobals(); localStorage.clear() })

it('starts without a dialog and retries a lost acknowledgement with the original command', async () => {
  const initial = { protocol: 'activity-online-v1', cursor: 0, server_at: '2026-09-11T10:00:00Z', current: null, records: [] }
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
  const view = render(<ActivityTracking repository={repository} taskTypes={[]} onChanged={() => {}} />)
  await waitFor(() => expect(screen.getByRole('button', { name: 'Start tracking' })).toBeEnabled())
  fireEvent.click(screen.getByRole('button', { name: 'Start tracking' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  await screen.findByText(/Connection lost/)
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
  const initial = { protocol: 'activity-online-v1', cursor: 1, server_at: current.start_at, current, records: [current] }
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
  fireEvent.change(screen.getByLabelText('Task Type'), { target: { value: '2' } })
  fireEvent.click(screen.getByRole('button', { name: 'Switch activity' }))
  await screen.findByText('reading')
  expect(commands[0]).toMatchObject({ kind: 'switch', task_type_id: 2, target_id: 1 })
  expect(commands[0]).not.toHaveProperty('name')
  fireEvent.click(screen.getByRole('button', { name: 'Stop' }))
  await screen.findByRole('button', { name: 'Start tracking' })
  expect(commands[1]).toMatchObject({ kind: 'stop', target_id: 2 })
})

it('does not send a command when durable storage fails', async () => {
  const fetch = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(JSON.stringify({ protocol: 'activity-online-v1', cursor: 0, server_at: '2026-09-11T10:00:00Z', current: null, records: [] })))
  vi.stubGlobal('fetch', fetch)
  const repository = new ActivityRepository(localStorage, (work) => work())
  await repository.refresh()
  const failure = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Storage full') })
  render(<ActivityTracking repository={repository} taskTypes={[]} onChanged={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: 'Start tracking' }))
  await screen.findByText(/Storage full/)
  expect(fetch.mock.calls.some((args) => args[1]?.method === 'POST')).toBe(false)
  expect(repository.state.snapshot?.current).toBeNull()
  failure.mockRestore()
})
