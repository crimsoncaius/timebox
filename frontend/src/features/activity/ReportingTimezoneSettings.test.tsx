import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { ActivityRepository } from './activityRepository'
import { ReportingTimezoneSettings } from './ReportingTimezoneSettings'

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); localStorage.clear() })

it('initializes a device zone once and saves the shared reporting zone explicitly', async () => {
  let snapshot = { protocol: 'activity-online-v1', cursor: 0, server_at: '2026-09-11T10:00:00Z', reporting_timezone: 'UTC', reporting_timezone_initialized: false, current: null, records: [] }
  let initializations = 0
  vi.stubGlobal('fetch', vi.fn(async (url, init) => {
    if (String(url).endsWith('/initialize')) initializations++
    if (init?.method) snapshot = { ...snapshot, cursor: snapshot.cursor + 1, reporting_timezone: JSON.parse(init.body).timezone, reporting_timezone_initialized: true }
    return new Response(JSON.stringify(snapshot))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  render(<ReportingTimezoneSettings repository={repository} />)
  await waitFor(() => expect(repository.state.snapshot?.reporting_timezone_initialized).toBe(true))
  fireEvent.change(screen.getByRole('textbox', { name: 'Reporting Time Zone' }), { target: { value: 'America/New_York' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save time zone' }))
  await waitFor(() => expect(repository.state.snapshot?.reporting_timezone).toBe('America/New_York'))
  await repository.refresh()
  expect(initializations).toBe(1)
  expect(repository.state.snapshot?.records).toEqual([])
})
