import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SettingsPage } from './SettingsPage'

function response(data: unknown) {
  return new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } })
}

describe('SettingsPage week start', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => { globalThis.fetch = originalFetch; vi.restoreAllMocks() })

  it('loads and updates the weekly recurrence boundary', async () => {
    const settings = {
      id: 1, start_hour: 8, end_hour: 20, show_full_day: false, week_start: 'monday',
      created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z',
    }
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (url.includes('/health')) return response({ status: 'ok', today: '2026-08-16', timezone: 'UTC' })
      if (init?.method === 'PATCH') {
        const body = JSON.parse(String(init.body)) as { week_start: string }
        return response({ ...settings, week_start: body.week_start, updated_at: '2026-01-02T00:00:00Z' })
      }
      return response(settings)
    }) as typeof fetch

    const user = userEvent.setup()
    render(<MemoryRouter><SettingsPage /></MemoryRouter>)
    const select = await screen.findByLabelText('Week starts on')
    expect(select).toHaveValue('monday')
    await user.selectOptions(select, 'sunday')
    await waitFor(() => expect(select).toHaveValue('sunday'))
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/settings'),
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ week_start: 'sunday' }) }),
    )
  })
})
