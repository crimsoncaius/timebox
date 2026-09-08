import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('App routing', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'

      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/settings') && method === 'PATCH') {
        return Promise.resolve(
          jsonResponse({
            id: 1,
            start_hour: 9,
            end_hour: 20,
            show_full_day: false,
            created_at: '2026-01-01T00:00:00Z',
            updated_at: '2026-01-02T00:00:00Z',
          }),
        )
      }
      if (url.includes('/settings')) {
        return Promise.resolve(
          jsonResponse({
            id: 1,
            start_hour: 8,
            end_hour: 20,
            show_full_day: false,
            created_at: '2026-01-01T00:00:00Z',
            updated_at: '2026-01-01T00:00:00Z',
          }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(jsonResponse([]))
      }
      if (url.includes('/days/2026-04-13') && !url.includes('/blocks')) {
        return Promise.resolve(
          jsonResponse({
            id: 1,
            date: '2026-04-13',
            start_hour: 8,
            end_hour: 20,
            show_full_day: false,
            created_at: '2026-01-01T00:00:00Z',
            updated_at: '2026-01-01T00:00:00Z',
            time_blocks: [],
            actual_blocks: [],
            meta: {
              timezone: 'UTC',
              today: '2026-04-13',
              server_now_iso: '2026-04-13T12:00:00Z',
            },
          }),
        )
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('puts Settings in the header and removes search and notification placeholders', async () => {
    render(
      <MemoryRouter initialEntries={['/day/2026-04-13']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    await screen.findByRole('link', { name: 'Day' })

    const banner = screen.getByRole('banner')
    expect(within(banner).getByRole('link', { name: 'Settings' })).toBeInTheDocument()

    expect(screen.queryByPlaceholderText(/Search the archive/)).not.toBeInTheDocument()
    expect(screen.queryByTitle('Search is not wired yet')).not.toBeInTheDocument()
    expect(screen.queryByTitle('Not available yet')).not.toBeInTheDocument()
  })

  it('shows day window controls on Settings instead of Day', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/day/2026-04-13']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    await screen.findByRole('link', { name: 'Settings' })
    await user.click(screen.getByRole('link', { name: 'Settings' }))
    expect(await screen.findByText(/Start hour/)).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Day' }))
    expect(screen.queryByText('Day window')).not.toBeInTheDocument()
  })

  it('PATCHes settings on start hour blur', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/day/2026-04-13']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    await screen.findByRole('link', { name: 'Settings' })
    await user.click(screen.getByRole('link', { name: 'Settings' }))
    const startInput = await screen.findByLabelText(/Start hour/i)
    await user.clear(startInput)
    await user.type(startInput, '9')
    await user.tab()

    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/settings'),
      expect.objectContaining({
        method: 'PATCH',
        body: expect.stringContaining('"start_hour":9'),
      }),
    )
  })
})

describe('Ready to Plan route projection', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('shows a pending addition across routes without making it schedulable', async () => {
    let releaseSave!: () => void
    let saveGate = new Promise<void>((resolve) => { releaseSave = resolve })
    const task = {
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
    }
    const requests: Array<{ url: string; method: string; body?: string }> = []
    let serverReady = false

    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      requests.push({ url, method, body: typeof init?.body === 'string' ? init.body : undefined })
      if (url.includes('/health')) return jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' })
      if (url.includes('/actual-blocks/active')) return jsonResponse(null)
      if (url.endsWith('/projects')) return jsonResponse([])
      if (url.endsWith('/task-types')) return jsonResponse([task.task_type])
      if (url.includes('/tasks?state=active')) {
        return jsonResponse({ items: [{ ...task, ready_to_plan: serverReady }], timezone: 'UTC', server_now_iso: '2026-04-13T12:00:00Z' })
      }
      if (url.endsWith('/tasks/77') && method === 'PATCH') {
        const requested = JSON.parse(String(init?.body)) as { ready_to_plan: boolean }
        await saveGate
        serverReady = requested.ready_to_plan
        return jsonResponse({ ...task, ready_to_plan: requested.ready_to_plan })
      }
      if (url.includes('/days/2026-04-13') && !url.includes('/blocks')) {
        return jsonResponse({
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
        })
      }
      return new Response('not found', { status: 404 })
    }) as typeof fetch

    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/battle-plan']}>
        <AppRoutes />
      </MemoryRouter>,
    )

    await user.click(await screen.findByRole('button', { name: 'Add Prepare launch narrative to Ready to Plan' }))
    expect(await screen.findByRole('status', { name: 'Prepare launch narrative readiness' })).toHaveTextContent('Saving')
    expect(screen.getByRole('button', { name: 'Remove Prepare launch narrative from Ready to Plan' })).toBeEnabled()

    await user.click(screen.getByRole('link', { name: 'Day' }))

    const pendingChoices = await screen.findAllByRole('button', { name: 'Prepare launch narrative is saving and unavailable to plan' })
    expect(pendingChoices.length).toBeGreaterThan(0)
    pendingChoices.forEach((choice) => expect(choice).toBeDisabled())
    screen.getAllByRole('button', { name: 'Drag Prepare launch narrative to Planned timeline' })
      .forEach((handle) => expect(handle).toBeDisabled())
    expect(requests.some(({ url }) => url.includes('/days/2026-04-13/blocks'))).toBe(false)

    releaseSave()
    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: 'Prepare launch narrative' })[0]).toBeEnabled()
    })

    await user.click(screen.getAllByRole('button', { name: 'Prepare launch narrative' })[0])
    expect(screen.getAllByRole('button', { name: 'Prepare launch narrative' })[0]).toHaveAttribute('aria-pressed', 'true')
    await user.click(screen.getByRole('link', { name: 'Battle Plan' }))
    await user.click(await screen.findByRole('heading', { name: 'Prepare launch narrative' }))

    saveGate = new Promise<void>((resolve) => { releaseSave = resolve })
    await user.click(await screen.findByRole('button', { name: 'Ready to Plan' }))
    expect(await screen.findByRole('button', { name: 'Add to Ready to Plan' })).toHaveAttribute('aria-busy', 'true')

    await user.click(screen.getByRole('link', { name: 'Day' }))
    await screen.findAllByTestId('ready-to-plan-list')
    expect(screen.queryByRole('button', { name: 'Prepare launch narrative' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Prepare launch narrative is selected/)).not.toBeInTheDocument()
    expect(requests.some(({ url }) => url.includes('/days/2026-04-13/blocks'))).toBe(false)

    releaseSave()
    await waitFor(() => {
      expect(requests.filter(({ url, method }) => url.endsWith('/tasks/77') && method === 'PATCH')).toHaveLength(2)
    })
  })
})
