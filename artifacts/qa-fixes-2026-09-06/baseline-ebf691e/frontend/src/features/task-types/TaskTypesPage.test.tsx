import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Layout } from '../../components/Layout'
import { TaskTypesPage } from './TaskTypesPage'

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('TaskTypesPage', () => {
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
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(jsonResponse([]))
      }
      if (url.includes('/task-types') && method === 'POST') {
        const body = init?.body ? (JSON.parse(init.body as string) as { name: string }) : { name: '' }
        return Promise.resolve(
          jsonResponse({
            id: 1,
            name: body.name,
            created_at: '2026-01-01T00:00:00Z',
            updated_at: '2026-01-01T00:00:00Z',
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

  function renderPage() {
    return render(
      <MemoryRouter>
        <Layout>
          <TaskTypesPage />
        </Layout>
      </MemoryRouter>,
    )
  }

  it('renders editorial headings, composer, and empty saved types', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Task types', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Task type' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Saved types' })).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Search or add (e.g. work)')).toBeInTheDocument()
    expect(await screen.findByText('No task types yet. Add one above.')).toBeInTheDocument()
    expect(screen.getByText('Up to date')).toBeInTheDocument()
  })

  it('lists task types from GET /task-types', async () => {
    const user = userEvent.setup()
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(
          jsonResponse([
            {
              id: 2,
              name: 'Deep work',
              created_at: '2026-01-01T00:00:00Z',
              updated_at: '2026-01-02T00:00:00Z',
            },
          ]),
        )
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch

    renderPage()

    expect(await screen.findByLabelText('Task type Deep work')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Edit Deep work' }))
    expect(await screen.findByRole('textbox', { name: /Task type name 2/i })).toHaveValue('Deep work')
  })

  it('POSTs new task type on Add', async () => {
    const user = userEvent.setup()
    const savedRows: Array<{
      id: number
      name: string
      created_at: string
      updated_at: string
    }> = []
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(jsonResponse(savedRows))
      }
      if (url.includes('/task-types') && method === 'POST') {
        const body = init?.body ? (JSON.parse(init.body as string) as { name: string }) : { name: '' }
        const row = {
          id: 1,
          name: body.name,
          created_at: '2026-01-01T00:00:00Z',
          updated_at: '2026-01-01T00:00:00Z',
        }
        savedRows.length = 0
        savedRows.push(row)
        return Promise.resolve(jsonResponse(row))
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch

    renderPage()

    await screen.findByRole('heading', { name: 'Task types' })

    const composer = screen.getByRole('heading', { name: 'Task type' }).closest('section')
    expect(composer).toBeTruthy()
    const input = within(composer!).getByPlaceholderText('Search or add (e.g. work)')
    await user.type(input, 'work')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/task-types'),
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('"name":"work"'),
      }),
    )

    expect(await screen.findByLabelText('Task type work')).toBeInTheDocument()
    expect(input).toHaveValue('')
    await user.click(screen.getByRole('button', { name: 'Edit work' }))
    expect(await screen.findByRole('textbox', { name: /Task type name 1/i })).toHaveValue('work')
  })

  it('filters saved types as the user types in the composer', async () => {
    const user = userEvent.setup()
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(
          jsonResponse([
            { id: 1, name: 'coding', created_at: '', updated_at: '' },
            { id: 2, name: 'coding/ai', created_at: '', updated_at: '' },
            { id: 3, name: 'gym', created_at: '', updated_at: '' },
          ]),
        )
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch

    renderPage()

    expect(await screen.findByLabelText('Task type coding')).toBeInTheDocument()
    expect(screen.getByLabelText('Task type gym')).toBeInTheDocument()

    const composer = screen.getByRole('heading', { name: 'Task type' }).closest('section')
    expect(composer).toBeTruthy()
    const input = within(composer!).getByPlaceholderText('Search or add (e.g. work)')
    await user.type(input, 'gym')

    expect(screen.getByLabelText('Task type gym')).toBeInTheDocument()
    expect(screen.queryByLabelText('Task type coding')).not.toBeInTheDocument()
  })

  it('shows no matching task types when the filter matches nothing', async () => {
    const user = userEvent.setup()
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(
          jsonResponse([{ id: 1, name: 'work', created_at: '', updated_at: '' }]),
        )
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch

    renderPage()

    await screen.findByLabelText('Task type work')

    const composer = screen.getByRole('heading', { name: 'Task type' }).closest('section')
    expect(composer).toBeTruthy()
    const input = within(composer!).getByPlaceholderText('Search or add (e.g. work)')
    await user.type(input, 'nomatchxyz')

    expect(screen.getByText('No matching task types.')).toBeInTheDocument()
  })

  it('clearing the composer shows all saved types again', async () => {
    const user = userEvent.setup()
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(
          jsonResponse([
            { id: 1, name: 'coding', created_at: '', updated_at: '' },
            { id: 3, name: 'gym', created_at: '', updated_at: '' },
          ]),
        )
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch

    renderPage()

    expect(await screen.findByLabelText('Task type coding')).toBeInTheDocument()

    const composer = screen.getByRole('heading', { name: 'Task type' }).closest('section')
    expect(composer).toBeTruthy()
    const input = within(composer!).getByPlaceholderText('Search or add (e.g. work)')
    await user.type(input, 'gym')
    expect(screen.queryByLabelText('Task type coding')).not.toBeInTheDocument()

    await user.clear(input)
    expect(screen.getByLabelText('Task type coding')).toBeInTheDocument()
    expect(screen.getByLabelText('Task type gym')).toBeInTheDocument()
  })

  it('refetches task types after rename updates descendant paths', async () => {
    const user = userEvent.setup()
    let rows = [
      { id: 1, name: 'coding', created_at: '', updated_at: 'a' },
      { id: 2, name: 'coding/ai', created_at: '', updated_at: 'b' },
    ]
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(jsonResponse(rows))
      }
      if (url.includes('/task-types') && method === 'PATCH') {
        rows = [
          { id: 1, name: 'development', created_at: '', updated_at: 'c' },
          { id: 2, name: 'development/ai', created_at: '', updated_at: 'd' },
        ]
        return Promise.resolve(jsonResponse(rows[0]))
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch

    renderPage()
    await screen.findByRole('button', { name: 'Edit coding' })
    await user.click(screen.getByRole('button', { name: 'Edit coding' }))
    const input = await screen.findByRole('textbox', { name: /Task type name 1/i })
    await user.clear(input)
    await user.type(input, 'development')
    await user.tab()

    expect(await screen.findByLabelText('Task type development/ai')).toBeInTheDocument()
  })

  it('preserves an unused task type when permanent deletion is cancelled', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(
          jsonResponse([
            {
              id: 2,
              name: 'Deep work',
              usage_count: 0,
              task_usage_count: 0,
              created_at: '',
              updated_at: '',
            },
          ]),
        )
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch

    renderPage()
    expect(await screen.findByLabelText('Task type Deep work')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Delete Deep work' }))

    expect(window.confirm).toHaveBeenCalledWith(
      'Permanently delete “Deep work”? This cannot be undone.',
    )
    expect(globalThis.fetch).not.toHaveBeenCalledWith(
      expect.stringMatching(/\/task-types\/2$/),
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(screen.getByLabelText('Task type Deep work')).toBeInTheDocument()
  })

  it('permanently deletes an unused task type after confirmation', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let rows = [
      {
        id: 2,
        name: 'Deep work',
        usage_count: 0,
        task_usage_count: 0,
        created_at: '',
        updated_at: '',
      },
    ]
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-04-13', timezone: 'UTC' }),
        )
      }
      if (url.includes('/task-types') && method === 'GET') {
        return Promise.resolve(jsonResponse(rows))
      }
      if (url.match(/\/task-types\/2$/) && method === 'DELETE') {
        rows = []
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch

    renderPage()
    expect(await screen.findByLabelText('Task type Deep work')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Delete Deep work' }))

    expect(window.confirm).toHaveBeenCalledWith(
      'Permanently delete “Deep work”? This cannot be undone.',
    )
    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringMatching(/\/task-types\/2$/),
        expect.objectContaining({ method: 'DELETE' }),
      )
      expect(screen.queryByLabelText('Task type Deep work')).not.toBeInTheDocument()
    })
  })
})
