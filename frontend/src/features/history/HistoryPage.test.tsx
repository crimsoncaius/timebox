import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DayListItem } from '../../lib/api'
import { HistoryPage } from './HistoryPage'

function response(data: unknown) {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('HistoryPage', () => {
  const originalFetch = globalThis.fetch
  let days: DayListItem[]

  beforeEach(() => {
    localStorage.clear()
    days = []
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (url.includes('/health')) {
        return response({ status: 'ok', today: '1980-02-27', timezone: 'Pacific/Kiritimati' })
      }
      if (url.includes('/days?limit=500')) return response(days)
      throw new Error(`Unexpected request: GET ${url}`)
    }) as typeof fetch
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('uses the application month when the Chronicle has no archived days', async () => {
    render(<MemoryRouter initialEntries={['/history']}><HistoryPage /></MemoryRouter>)

    expect(await screen.findByTestId('chronicle-month-heading')).toHaveTextContent('February 1980')
  })

  it('returns to the application month from an archived month', async () => {
    days = [{
      id: 1,
      date: '2026-06-12',
      start_hour: 8,
      end_hour: 18,
      show_full_day: false,
      updated_at: '2026-06-12T10:00:00Z',
    }]
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/history']}><HistoryPage /></MemoryRouter>)

    expect(await screen.findByTestId('chronicle-month-heading')).toHaveTextContent('June 2026')
    await user.click(screen.getByRole('button', { name: 'This month' }))
    expect(screen.getByTestId('chronicle-month-heading')).toHaveTextContent('February 1980')
  })
})
