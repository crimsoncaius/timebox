import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { RecurringTemplate } from '../../lib/api'
import { RecurringPage } from './RecurringPage'

function response(data: unknown, status = 200) {
  return status === 204
    ? new Response(null, { status })
    : new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

const template: RecurringTemplate = {
  id: 9,
  title: 'Gym',
  description: 'Strength sessions',
  project_id: null,
  project: null,
  task_type_id: null,
  task_type: null,
  mode: 'quota',
  status: 'active',
  frequency: 'weekly',
  interval: 1,
  weekdays: [],
  month_day: null,
  quota_count: 3,
  start_date: '2099-08-17',
  end_date: null,
  cycle_limit: null,
  urgency: null,
  importance: null,
  paused_at: null,
  ended_at: null,
  created_at: '2099-08-01T00:00:00Z',
  updated_at: '2099-08-01T00:00:00Z',
  checklist_items: [],
  upcoming: [{ key: 'quota:2099-08-17', start: '2099-08-17', end: '2099-08-23' }],
  current_tasks: [{ id: 17, title: 'Gym', deadline_date: '2099-08-23', overdue: false }],
  cadence: '3 times per week',
  next_occurrence: '2099-08-17',
}

describe('RecurringPage', () => {
  const originalFetch = globalThis.fetch
  const originalConfirm = window.confirm
  let active: RecurringTemplate[]
  let paused: RecurringTemplate[]
  let applicationToday: string

  beforeEach(() => {
    localStorage.clear()
    active = [template]
    paused = []
    applicationToday = '2099-08-16'
    window.confirm = vi.fn(() => true)
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) return response({ status: 'ok', today: applicationToday, timezone: 'UTC' })
      if (url.endsWith('/projects')) return response([])
      if (url.endsWith('/task-types')) return response([])
      if (url.includes('/recurring-templates?status=active')) return response(active)
      if (url.includes('/recurring-templates?status=paused')) return response(paused)
      if (url.includes('/recurring-templates?status=ended')) return response([])
      if (url.endsWith('/recurring-templates/preview')) {
        return response({
          upcoming: [{ key: 'scheduled:2099-08-17', start: '2099-08-17', end: '2099-08-17' }],
          past_cycles: 0,
          past_tasks: 0,
        })
      }
      if (url.endsWith('/recurring-templates') && method === 'POST') {
        const body = JSON.parse(String(init?.body)) as { title: string }
        const created = { ...template, id: 10, title: body.title, mode: 'scheduled' as const, cadence: 'Daily' }
        active = [...active, created]
        return response(created, 201)
      }
      if (url.endsWith('/recurring-templates/9/pause')) {
        const next = { ...template, status: 'paused' as const }
        active = []
        paused = [next]
        return response(next)
      }
      throw new Error(`Unexpected request: ${method} ${url}`)
    }) as typeof fetch
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    window.confirm = originalConfirm
    vi.restoreAllMocks()
  })

  it('shows cadence, detail task links, status filters, and lifecycle controls', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)

    expect(await screen.findByText('3 times per week')).toBeInTheDocument()
    const sidebar = screen.getByRole('complementary', { name: 'Battle Plan lists and projects' })
    expect(within(sidebar).getByRole('button', { name: 'All Tasks' })).toBeInTheDocument()
    expect(within(sidebar).getByRole('link', { name: 'Recurring' })).toHaveClass('bg-surface-container-high')
    await user.click(screen.getByRole('button', { name: 'Gym' }))
    const detail = screen.getByRole('dialog', { name: 'Recurring template Gym' })
    expect(within(detail).getByText('Next five')).toBeInTheDocument()
    expect(within(detail).getByRole('link', { name: /Gym/ })).toHaveAttribute('href', '/battle-plan?task=17')
    await user.click(within(detail).getByRole('button', { name: 'Close recurring details' }))

    await user.click(screen.getByRole('button', { name: 'Pause' }))
    await waitFor(() => expect(active).toHaveLength(0))
    await user.click(screen.getByRole('button', { name: 'Paused' }))
    expect(await screen.findByText('Gym')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Resume' })).toBeInTheDocument()
  })

  it('creates a scheduled template from the previewed form', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)
    await screen.findByText('3 times per week')
    await user.click(screen.getByRole('button', { name: 'New recurring task' }))
    const form = screen.getByRole('dialog', { name: 'New recurring task' })
    expect(within(form).getByRole('radio', { name: 'On a schedule' })).toHaveAttribute('aria-checked', 'true')
    await user.click(within(form).getByRole('radio', { name: 'Times per period' }))
    expect(within(form).getByRole('heading', { name: 'The quota' })).toBeInTheDocument()
    expect(within(form).getByText(/3 times per calendar week/)).toBeInTheDocument()
    await user.click(within(form).getByRole('radio', { name: 'On a schedule' }))
    await user.type(within(form).getByLabelText('Title'), 'Morning review')
    await waitFor(() => expect(within(form).getByText(/Every day, starting/)).toBeInTheDocument())
    await user.click(within(form).getByRole('button', { name: 'Create recurrence' }))
    expect(await screen.findByText('Morning review')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/recurring-templates'),
      expect.objectContaining({ method: 'POST' }),
    )
    expect(window.confirm).not.toHaveBeenCalled()
  })

  it('keeps the latest status results when an older request resolves last', async () => {
    const pausedResponse = deferred<Response>()
    const endedResponse = deferred<Response>()
    const baseFetch = globalThis.fetch
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (url.includes('/recurring-templates?status=paused')) return pausedResponse.promise
      if (url.includes('/recurring-templates?status=ended')) return endedResponse.promise
      return baseFetch(input, init)
    }) as typeof fetch

    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)
    await screen.findByText('3 times per week')

    await user.click(screen.getByRole('button', { name: 'Paused' }))
    await user.click(screen.getByRole('button', { name: 'Ended' }))

    endedResponse.resolve(response([{ ...template, id: 11, title: 'Finished review', status: 'ended' }]))
    expect(await screen.findByText('Finished review')).toBeInTheDocument()

    await act(async () => {
      pausedResponse.resolve(response([{ ...template, id: 12, title: 'Stale paused review', status: 'paused' }]))
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 0))
    })
    expect(screen.queryByText('Stale paused review')).not.toBeInTheDocument()
    expect(screen.getByText('Finished review')).toBeInTheDocument()
  })

  it('uses the application day for new recurrence defaults', async () => {
    applicationToday = '1980-02-27'
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)

    await screen.findByText('3 times per week')
    await user.click(screen.getByRole('button', { name: 'New recurring task' }))
    const form = screen.getByRole('dialog', { name: 'New recurring task' })

    expect(within(form).getByLabelText('Start date')).toHaveValue('1980-02-27')

    await user.click(within(form).getByRole('button', { name: 'Weekly' }))
    expect(within(form).getByRole('button', { name: 'Wed' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(within(form).getByRole('button', { name: 'Monthly' }))
    expect(within(form).getByLabelText('Day of month')).toHaveValue(27)
  })

  it('keeps a changed recurrence open when Escape dismissal is rejected', async () => {
    window.confirm = vi.fn(() => false)
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)

    await screen.findByText('3 times per week')
    await user.click(screen.getByRole('button', { name: 'New recurring task' }))
    const form = screen.getByRole('dialog', { name: 'New recurring task' })
    await user.type(within(form).getByLabelText('Title'), 'Unsaved recurrence')
    await user.keyboard('{Escape}')

    expect(screen.getByRole('dialog', { name: 'New recurring task' })).toBeInTheDocument()
    expect(window.confirm).toHaveBeenCalledWith('Discard your unsaved changes?')
    expect(window.confirm).toHaveBeenCalledTimes(1)
  })

  it('closes a pristine recurrence on Escape without prompting', async () => {
    window.confirm = vi.fn(() => false)
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)

    await screen.findByText('3 times per week')
    await user.click(screen.getByRole('button', { name: 'New recurring task' }))
    expect(screen.getByRole('dialog', { name: 'New recurring task' })).toBeInTheDocument()
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'New recurring task' })).not.toBeInTheDocument()
    expect(window.confirm).not.toHaveBeenCalled()
  })

  it('closes a changed recurrence from the close button after one confirmation', async () => {
    window.confirm = vi.fn(() => true)
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)

    await screen.findByText('3 times per week')
    await user.click(screen.getByRole('button', { name: 'New recurring task' }))
    const form = screen.getByRole('dialog', { name: 'New recurring task' })
    await user.type(within(form).getByLabelText('Title'), 'Unsaved recurrence')
    await user.click(within(form).getByRole('button', { name: 'Close recurring form' }))

    expect(screen.queryByRole('dialog', { name: 'New recurring task' })).not.toBeInTheDocument()
    expect(window.confirm).toHaveBeenCalledWith('Discard your unsaved changes?')
    expect(window.confirm).toHaveBeenCalledTimes(1)
  })
})
