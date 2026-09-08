import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import type { BattleTask } from './lib/api'

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function battleTask(overrides: Partial<BattleTask> = {}): BattleTask {
  return {
    id: 77,
    parent_id: null,
    project_id: null,
    project: null,
    task_type_id: 3,
    task_type: { id: 3, name: 'Deep work', created_at: '', updated_at: '' },
    title: 'Prepare launch narrative',
    description: '',
    ready_to_plan: false,
    status: 'open',
    version: 1,
    urgency: null,
    importance: null,
    deadline_date: null,
    deadline_at: null,
    reminder_at: null,
    reminder_delivered_at: null,
    position: 0,
    archived_at: null,
    deleted_at: null,
    created_at: '',
    updated_at: '',
    overdue: false,
    subtasks: [],
    ...overrides,
  }
}

type PatchRequest = {
  taskId: number
  ready: boolean
  resolve: (task: BattleTask) => void
  reject: (status?: number, detail?: string) => void
}

function controllableTransport(initialTasks: BattleTask[]) {
  let serverTasks = initialTasks
  let failedTaskReads = 0
  const patchRequests: PatchRequest[] = []

  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    const method = init?.method ?? 'GET'
    if (url.includes('/health')) return Promise.resolve(jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }))
    if (url.includes('/actual-blocks/active')) return Promise.resolve(jsonResponse(null))
    if (url.endsWith('/projects')) return Promise.resolve(jsonResponse([]))
    if (url.endsWith('/task-types')) return Promise.resolve(jsonResponse(initialTasks.flatMap((task) => task.task_type ? [task.task_type] : [])))
    if (url.includes('/tasks?state=active')) {
      if (failedTaskReads > 0) {
        failedTaskReads -= 1
        return Promise.reject(new Error('Reconciliation unavailable'))
      }
      return Promise.resolve(jsonResponse({ items: serverTasks, timezone: 'UTC', server_now_iso: '2026-04-13T12:00:00Z' }))
    }
    if (url.includes('/days/2026-04-13') && !url.includes('/blocks')) {
      return Promise.resolve(jsonResponse({
        id: 1,
        date: '2026-04-13',
        start_hour: 8,
        end_hour: 20,
        show_full_day: false,
        created_at: '',
        updated_at: '',
        time_blocks: [],
        actual_blocks: [],
        meta: { timezone: 'UTC', today: '2026-04-13', server_now_iso: '2026-04-13T12:00:00Z' },
      }))
    }
    const taskMatch = url.match(/\/tasks\/(\d+)$/)
    if (taskMatch && method === 'PATCH') {
      const taskId = Number(taskMatch[1])
      const ready = Boolean(JSON.parse(String(init?.body)).ready_to_plan)
      return new Promise((resolve) => {
        patchRequests.push({
          taskId,
          ready,
          resolve: (task) => {
            serverTasks = serverTasks.map((current) => current.id === taskId ? task : current)
            resolve(jsonResponse(task))
          },
          reject: (status = 500, detail = 'Save failed') => resolve(jsonResponse({ detail }, status)),
        })
      })
    }
    return Promise.resolve(new Response('not found', { status: 404 }))
  })

  return {
    fetch,
    patchRequests,
    setServerTasks: (tasks: BattleTask[]) => { serverTasks = tasks },
    failNextTaskRead: () => { failedTaskReads += 1 },
  }
}

describe('Ready to Plan persistence resilience', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('serializes a compensating readiness write behind the in-flight write', async () => {
    const transport = controllableTransport([battleTask()])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/battle-plan']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    await user.click(screen.getByRole('button', { name: 'Remove Prepare launch narrative from Ready to Plan' }))

    expect(transport.patchRequests).toHaveLength(1)
    expect(transport.patchRequests[0]).toMatchObject({ taskId: 77, ready: true })
    expect(screen.getByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' })).toHaveAttribute('aria-busy', 'true')

    transport.patchRequests[0].resolve(battleTask({ ready_to_plan: true, version: 2 }))
    await waitFor(() => expect(transport.patchRequests).toHaveLength(2))
    expect(transport.patchRequests[1]).toMatchObject({ taskId: 77, ready: false })
    expect(screen.getByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' })).toBeInTheDocument()

    transport.patchRequests[1].resolve(battleTask({ ready_to_plan: false, version: 3 }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' })).not.toHaveAttribute('aria-busy'))
  })

  it('uses task versions to keep stale responses and reloads from reverting newer readiness', async () => {
    const transport = controllableTransport([battleTask({ version: 5 })])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/battle-plan']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    transport.setServerTasks([battleTask({ ready_to_plan: true, version: 7, title: 'Prepare newer narrative' })])
    await user.click(screen.getByRole('link', { name: 'Day' }))
    expect((await screen.findAllByRole('button', { name: 'Prepare newer narrative is saving and unavailable to plan' }))[0]).toBeDisabled()
    transport.patchRequests[0].resolve(battleTask({ ready_to_plan: false, version: 6, title: 'Stale response title' }))
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Prepare newer narrative' })[0]).toBeEnabled())
    transport.setServerTasks([battleTask({ ready_to_plan: false, version: 5, title: 'Stale reload title' })])
    await user.click(screen.getByRole('link', { name: 'Battle Plan' }))

    expect(await screen.findByRole('button', { name: 'Remove Stale reload title from Ready to Plan' })).toBeInTheDocument()
    expect(transport.patchRequests).toHaveLength(1)
  })

  it('merges a successful readiness response without replacing unrelated task fields or flickering', async () => {
    const transport = controllableTransport([battleTask({ title: 'Current launch narrative', description: 'Keep this copy' })])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><AppRoutes /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Add Current launch narrative to Ready to Plan' }))
    expect(screen.getByRole('button', { name: 'Remove Current launch narrative from Ready to Plan' })).toHaveAttribute('aria-busy', 'true')

    transport.patchRequests[0].resolve(battleTask({
      title: 'Stale response title',
      description: 'Stale response copy',
      ready_to_plan: true,
      version: 2,
    }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Remove Current launch narrative from Ready to Plan' })).not.toHaveAttribute('aria-busy'))
    expect(screen.queryByText('Stale response title')).not.toBeInTheDocument()
  })

  it('reconciles a failed latest intent and keeps its task-scoped Retry after navigation', async () => {
    const transport = controllableTransport([battleTask()])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/battle-plan']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    transport.setServerTasks([battleTask({ version: 2 })])
    transport.patchRequests[0].reject(503, 'Database unavailable')

    const failure = await screen.findByRole('alert', { name: 'Prepare launch narrative readiness error' })
    expect(failure).toHaveTextContent('Ready to Plan was not saved')
    expect(screen.getByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' })).not.toHaveAttribute('aria-busy')

    await user.click(screen.getByRole('link', { name: 'Day' }))
    await screen.findByTestId('day-date')
    await user.click(screen.getByRole('link', { name: 'Battle Plan' }))

    await user.click(await screen.findByRole('button', { name: 'Retry Ready to Plan for Prepare launch narrative' }))
    await waitFor(() => expect(transport.patchRequests).toHaveLength(2))
    expect(transport.patchRequests[1]).toMatchObject({ taskId: 77, ready: true })
  })

  it('settles silently when reconciliation shows that the latest desired readiness was saved', async () => {
    const transport = controllableTransport([battleTask()])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><AppRoutes /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    transport.setServerTasks([battleTask({ ready_to_plan: true, version: 2 })])
    transport.patchRequests[0].reject(503)

    await waitFor(() => expect(screen.getByRole('button', { name: 'Remove Prepare launch narrative from Ready to Plan' })).not.toHaveAttribute('aria-busy'))
    expect(screen.queryByRole('alert', { name: 'Prepare launch narrative readiness error' })).not.toBeInTheDocument()
    expect(transport.patchRequests).toHaveLength(1)
  })

  it('settles an obsolete failure silently when the newer intent already matches saved state', async () => {
    const transport = controllableTransport([battleTask()])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><AppRoutes /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    await user.click(screen.getByRole('button', { name: 'Remove Prepare launch narrative from Ready to Plan' }))
    transport.setServerTasks([battleTask({ version: 2 })])
    transport.patchRequests[0].reject(503)

    await waitFor(() => expect(screen.getByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' })).not.toHaveAttribute('aria-busy'))
    expect(screen.queryByRole('alert', { name: 'Prepare launch narrative readiness error' })).not.toBeInTheDocument()
    expect(transport.patchRequests).toHaveLength(1)
  })

  it('falls back to confirmed readiness and retains Retry when reconciliation is unavailable', async () => {
    const transport = controllableTransport([battleTask()])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><AppRoutes /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    transport.failNextTaskRead()
    transport.patchRequests[0].reject(504, 'Timed out')

    const failure = await screen.findByRole('alert', { name: 'Prepare launch narrative readiness error' })
    expect(failure).toHaveTextContent('could not be confirmed')
    expect(screen.getByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' })).not.toHaveAttribute('aria-busy')

    await user.click(screen.getByRole('link', { name: 'Day' }))
    await screen.findByTestId('day-date')
    expect(screen.queryByRole('button', { name: 'Prepare launch narrative' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Battle Plan' }))
    await user.click(await screen.findByRole('button', { name: 'Retry Ready to Plan for Prepare launch narrative' }))
    await waitFor(() => expect(transport.patchRequests).toHaveLength(2))
    expect(transport.patchRequests[1]).toMatchObject({ taskId: 77, ready: true })
  })

  it('keeps failures and retries independent for different tasks', async () => {
    const second = battleTask({ id: 88, title: 'Prepare support briefing' })
    const transport = controllableTransport([battleTask(), second])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><AppRoutes /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    await user.click(screen.getByRole('button', { name: 'Add Prepare support briefing to Ready to Plan' }))
    expect(transport.patchRequests).toHaveLength(2)
    transport.setServerTasks([battleTask({ version: 2 }), { ...second, version: 2 }])
    transport.patchRequests[0].reject(503)
    transport.patchRequests[1].reject(503)

    await screen.findByRole('alert', { name: 'Prepare launch narrative readiness error' })
    await screen.findByRole('alert', { name: 'Prepare support briefing readiness error' })
    await user.click(screen.getByRole('button', { name: 'Retry Ready to Plan for Prepare launch narrative' }))
    await user.click(screen.getByRole('button', { name: 'Retry Ready to Plan for Prepare support briefing' }))
    await waitFor(() => expect(transport.patchRequests).toHaveLength(4))
    expect(transport.patchRequests.slice(2)).toEqual(expect.arrayContaining([
      expect.objectContaining({ taskId: 77, ready: true }),
      expect.objectContaining({ taskId: 88, ready: true }),
    ]))
  })

  it('clears an old failed Retry when a new explicit toggle supersedes it', async () => {
    const transport = controllableTransport([battleTask()])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><AppRoutes /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    transport.setServerTasks([battleTask({ version: 2 })])
    transport.patchRequests[0].reject(503)
    await screen.findByRole('alert', { name: 'Prepare launch narrative readiness error' })

    await user.click(screen.getByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    expect(screen.queryByRole('alert', { name: 'Prepare launch narrative readiness error' })).not.toBeInTheDocument()
    await waitFor(() => expect(transport.patchRequests).toHaveLength(2))
    expect(transport.patchRequests[1]).toMatchObject({ taskId: 77, ready: true })
  })

  it('keeps a failed removal recovery action with the still-schedulable task on Day', async () => {
    const transport = controllableTransport([battleTask({ ready_to_plan: true })])
    globalThis.fetch = transport.fetch as typeof fetch
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><AppRoutes /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Remove Prepare launch narrative from Ready to Plan' }))
    transport.setServerTasks([battleTask({ ready_to_plan: true, version: 2 })])
    transport.patchRequests[0].reject(409, 'Completed tasks cannot change readiness')
    await screen.findByRole('alert', { name: 'Prepare launch narrative readiness error' })

    await user.click(screen.getByRole('link', { name: 'Day' }))
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Prepare launch narrative' })[0]).toBeEnabled())
    expect(screen.getAllByRole('alert', { name: 'Prepare launch narrative readiness error' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: 'Retry Ready to Plan for Prepare launch narrative' }).length).toBeGreaterThan(0)
  })
})
