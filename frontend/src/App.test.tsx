import { render, screen, within } from '@testing-library/react'
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
