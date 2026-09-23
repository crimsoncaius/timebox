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

describe('SettingsPage', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => { globalThis.fetch = originalFetch; vi.restoreAllMocks() })

  it('has no configurable week boundary', async () => {
    const settings = {
      id: 1, start_hour: 8, end_hour: 20, show_full_day: false,
      created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z',
    }
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (url.includes('/health')) return response({ status: 'ok', today: '2026-08-16', timezone: 'UTC' })
      void init
      return response(settings)
    }) as typeof fetch

    render(<MemoryRouter><SettingsPage /></MemoryRouter>)
    await screen.findByLabelText('Start hour')
    expect(screen.queryByLabelText('Week starts on')).not.toBeInTheDocument()
  })

  it('keeps independently accepted field changes when responses resolve out of order', async () => {
    const settings = {
      id: 1, start_hour: 8, end_hour: 20, show_full_day: false,
      created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z',
    }
    const startHourResponse = deferred<Response>()
    const fullDayResponse = deferred<Response>()
    globalThis.fetch = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method !== 'PATCH') return Promise.resolve(response(settings))
      const body = JSON.parse(String(init.body)) as { start_hour?: number; show_full_day?: boolean }
      if (body.start_hour !== undefined) return startHourResponse.promise
      return fullDayResponse.promise
    }) as typeof fetch

    const user = userEvent.setup()
    render(<MemoryRouter><SettingsPage /></MemoryRouter>)
    const startHour = await screen.findByLabelText('Start hour')
    const showFullDay = screen.getByRole('checkbox', { name: 'Show full 24 hours' })

    fireEvent.change(startHour, { target: { value: '9' } })
    fireEvent.blur(startHour)
    await user.click(showFullDay)

    await act(async () => {
      fullDayResponse.resolve(response({ ...settings, show_full_day: true, updated_at: '2026-01-03T00:00:00Z' }))
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 0))
    })
    await act(async () => {
      startHourResponse.resolve(response({ ...settings, start_hour: 9, updated_at: '2026-01-02T00:00:00Z' }))
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 0))
    })

    expect(startHour).toHaveValue(9)
    expect(showFullDay).toBeChecked()
  })

  it('keeps the latest repeated field change when its response resolves first', async () => {
    const settings = {
      id: 1, start_hour: 8, end_hour: 20, show_full_day: false,
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


describe('SettingsPage day window validation', () => {
  afterEach(() => { vi.unstubAllGlobals() })

  it.each([
    ['Start hour', '', '0', 'start_hour'],
    ['End hour', '', '24', 'end_hour'],
    ['Start hour', '-1', '9', 'start_hour'],
    ['Start hour', '24', '9', 'start_hour'],
    ['Start hour', '8.5', '9', 'start_hour'],
    ['Start hour', '20', '9', 'start_hour'],
    ['End hour', '0', '21', 'end_hour'],
    ['End hour', '25', '21', 'end_hour'],
    ['End hour', '20.5', '21', 'end_hour'],
    ['End hour', '8', '21', 'end_hour'],
  ])('rejects %s value "%s" and allows correction to %s', async (label, invalid, valid, field) => {
    const settings = {
      id: 1, start_hour: 8, end_hour: 20, show_full_day: false,
      created_at: '2026-01-01T00:00:00Z', updated_at: '2026-01-01T00:00:00Z',
    }
    const patches: unknown[] = []
    vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'PATCH') {
        const patch = JSON.parse(String(init.body))
        patches.push(patch)
        return response({ ...settings, ...patch, updated_at: '2026-01-02T00:00:00Z' })
      }
      return response(settings)
    }))
    render(<MemoryRouter><SettingsPage /></MemoryRouter>)
    const input = await screen.findByLabelText(label)
    fireEvent.change(input, { target: { value: invalid } })
    fireEvent.blur(input)
    expect(patches).toEqual([])
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription(/hour/i)
    expect(input).toHaveValue(invalid === '' ? null : Number(invalid))

    fireEvent.change(input, { target: { value: valid } })
    fireEvent.blur(input)
    await waitFor(() => expect(patches).toEqual([{ [field]: Number(valid) }]))
    await waitFor(() => expect(screen.getByLabelText(label)).toHaveAttribute('aria-invalid', 'false'))
    expect(screen.getByLabelText(label)).toHaveValue(Number(valid))
  })
})
