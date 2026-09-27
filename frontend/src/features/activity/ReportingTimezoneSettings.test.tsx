import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  fireEvent.change(screen.getByRole('combobox', { name: 'Reporting Time Zone' }), { target: { value: 'America/New_York' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save time zone' }))
  await waitFor(() => expect(repository.state.snapshot?.reporting_timezone).toBe('America/New_York'))
  await repository.refresh()
  expect(initializations).toBe(1)
  expect(repository.state.snapshot?.records).toEqual([])
})


it('preserves the draft and focus when a background refresh changes the shared zone', async () => {
  let snapshot = { protocol: 'activity-online-v1', cursor: 1, server_at: '2026-09-11T10:00:00Z', reporting_timezone: 'UTC', reporting_timezone_initialized: true, current: null, records: [] }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(snapshot))))
  const repository = new ActivityRepository(localStorage, work => work())
  render(<ReportingTimezoneSettings repository={repository} />)
  await waitFor(() => expect(screen.getByRole('combobox')).toHaveValue('UTC'))
  const input = screen.getByRole('combobox', { name: 'Reporting Time Zone' })
  input.focus()
  fireEvent.change(input, { target: { value: 'America/New_' } })
  snapshot = { ...snapshot, cursor: 2, reporting_timezone: 'Asia/Singapore' }
  await act(() => repository.refresh())
  expect(screen.getByRole('combobox', { name: 'Reporting Time Zone' })).toHaveValue('America/New_')
  expect(input).toHaveFocus()
})

it('accepts a background zone change while the form is untouched', async () => {
  let snapshot = { protocol: 'activity-online-v1', cursor: 1, server_at: '2026-09-11T10:00:00Z', reporting_timezone: 'UTC', reporting_timezone_initialized: true, current: null, records: [] }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(snapshot))))
  const repository = new ActivityRepository(localStorage, work => work())
  render(<ReportingTimezoneSettings repository={repository} />)
  await waitFor(() => expect(screen.getByRole('combobox')).toHaveValue('UTC'))
  snapshot = { ...snapshot, cursor: 2, reporting_timezone: 'Asia/Singapore' }
  await act(() => repository.refresh())
  expect(screen.getByRole('combobox')).toHaveValue('Asia/Singapore')
})


it('keeps a newer edit back to the original zone while a save is pending', async () => {
  let snapshot = { protocol: 'activity-online-v1', cursor: 1, server_at: '2026-09-11T10:00:00Z', reporting_timezone: 'UTC', reporting_timezone_initialized: true, current: null, records: [] }
  let finishSave: (() => void) | undefined
  vi.stubGlobal('fetch', vi.fn(async (_url, init) => {
    if (init?.method === 'PUT') {
      await new Promise<void>(resolve => { finishSave = resolve })
      snapshot = { ...snapshot, cursor: 2, reporting_timezone: 'Asia/Tokyo' }
    }
    return new Response(JSON.stringify(snapshot))
  }))
  const repository = new ActivityRepository(localStorage, work => work())
  render(<ReportingTimezoneSettings repository={repository} />)
  await waitFor(() => expect(screen.getByRole('combobox')).toHaveValue('UTC'))
  fireEvent.change(screen.getByRole('combobox'), { target: { value: 'Asia/Tokyo' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save time zone' }))
  await waitFor(() => expect(finishSave).toBeDefined())
  fireEvent.change(screen.getByRole('combobox'), { target: { value: 'UTC' } })
  await act(async () => { finishSave?.() })
  await waitFor(() => expect(repository.state.snapshot?.reporting_timezone).toBe('Asia/Tokyo'))
  expect(screen.getByRole('combobox')).toHaveValue('UTC')
  expect(screen.getByRole('button', { name: 'Save time zone' })).toBeEnabled()
})

it('explains an unknown zone after editing and keeps it from being saved', async () => {
  const snapshot = { protocol: 'activity-online-v1', cursor: 1, server_at: '2026-09-11T10:00:00Z', reporting_timezone: 'UTC', reporting_timezone_initialized: true, current: null, records: [] }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(snapshot))))
  const repository = new ActivityRepository(localStorage, work => work())
  render(<ReportingTimezoneSettings repository={repository} />)
  const input = await screen.findByRole('combobox', { name: 'Reporting Time Zone' })
  await waitFor(() => expect(input).toHaveValue('UTC'))
  fireEvent.change(input, { target: { value: 'Mars/Olympus' } })
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  fireEvent.blur(input)
  expect(screen.getByRole('alert')).toHaveTextContent('Choose a time zone from the list')
  expect(screen.getByRole('button', { name: 'Save time zone' })).toBeDisabled()
  fireEvent.change(input, { target: { value: 'Asia/Singapore' } })
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Save time zone' })).toBeEnabled()
})
