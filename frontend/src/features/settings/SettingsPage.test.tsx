import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SettingsPage } from './SettingsPage'

function response(data: unknown) {
  return new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
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

  it('keeps independently accepted field changes when responses resolve out of order', async () => {
    const settings = {
      id: 1, start_hour: 8, end_hour: 20, show_full_day: false, week_start: 'monday' as const,
      created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z',
    }
    const weekResponse = deferred<Response>()
    const fullDayResponse = deferred<Response>()
    globalThis.fetch = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method !== 'PATCH') return Promise.resolve(response(settings))
      const body = JSON.parse(String(init.body)) as { week_start?: string; show_full_day?: boolean }
      if (body.week_start) return weekResponse.promise
      return fullDayResponse.promise
    }) as typeof fetch

    const user = userEvent.setup()
    render(<MemoryRouter><SettingsPage /></MemoryRouter>)
    const weekStart = await screen.findByLabelText('Week starts on')
    const showFullDay = screen.getByRole('checkbox', { name: 'Show full 24 hours' })

    await user.selectOptions(weekStart, 'sunday')
    await user.click(showFullDay)

    await act(async () => {
      fullDayResponse.resolve(response({ ...settings, show_full_day: true, updated_at: '2026-01-03T00:00:00Z' }))
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 0))
    })
    await act(async () => {
      weekResponse.resolve(response({ ...settings, week_start: 'sunday', updated_at: '2026-01-02T00:00:00Z' }))
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 0))
    })

    expect(weekStart).toHaveValue('sunday')
    expect(showFullDay).toBeChecked()
  })

  it('keeps the latest repeated field change when its response resolves first', async () => {
    const settings = {
      id: 1, start_hour: 8, end_hour: 20, show_full_day: false, week_start: 'monday' as const,
      created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z',
    }
    const firstResponse = deferred<Response>()
    const secondResponse = deferred<Response>()
    let patchCount = 0
    globalThis.fetch = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method !== 'PATCH') return Promise.resolve(response(settings))
      patchCount += 1
      return patchCount === 1 ? firstResponse.promise : secondResponse.promise
    }) as typeof fetch

    render(<MemoryRouter><SettingsPage /></MemoryRouter>)
    const startHour = await screen.findByLabelText('Start hour')

    fireEvent.change(startHour, { target: { value: '9' } })
    fireEvent.blur(startHour)
    fireEvent.change(startHour, { target: { value: '10' } })
    fireEvent.blur(startHour)
    expect(patchCount).toBe(2)

    await act(async () => {
      secondResponse.resolve(response({ ...settings, start_hour: 10, updated_at: '2026-01-03T00:00:00Z' }))
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 0))
    })
    expect(screen.getByLabelText('Start hour')).toHaveValue(10)
    await act(async () => {
      firstResponse.resolve(response({ ...settings, start_hour: 9, updated_at: '2026-01-02T00:00:00Z' }))
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 0))
    })

    expect(screen.getByLabelText('Start hour')).toHaveValue(10)
  })
})
