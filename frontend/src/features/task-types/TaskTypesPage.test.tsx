import { render, screen, within } from '@testing-library/react'
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
    expect(screen.getByRole('heading', { name: 'New task type' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Saved types' })).toBeInTheDocument()
    expect(screen.getByPlaceholderText('e.g. work')).toBeInTheDocument()
    expect(await screen.findByText('No task types yet. Add one above.')).toBeInTheDocument()
    expect(screen.getByText('Up to date')).toBeInTheDocument()
  })

  it('lists task types from GET /task-types', async () => {
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

    const rowInput = await screen.findByRole('textbox', { name: /Task type name 2/i })
    expect(rowInput).toHaveValue('Deep work')
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

    const composer = screen.getByRole('heading', { name: 'New task type' }).closest('section')
    expect(composer).toBeTruthy()
    const input = within(composer!).getByPlaceholderText('e.g. work')
    await user.type(input, 'work')
    await user.click(screen.getByRole('button', { name: 'Add' }))

    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/task-types'),
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('"name":"work"'),
      }),
    )

    expect(await screen.findByRole('textbox', { name: /Task type name 1/i })).toHaveValue('work')
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
    const input = await screen.findByRole('textbox', { name: /Task type name 1/i })
    await user.clear(input)
    await user.type(input, 'development')
    await user.tab()

    expect(await screen.findByRole('textbox', { name: /Task type name 2/i })).toHaveValue('development/ai')
  })
})
