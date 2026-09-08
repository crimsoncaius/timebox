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
      if (url.endsWith('/recurring-templates/9') && method === 'PATCH') {
        const body = JSON.parse(String(init?.body)) as Partial<RecurringTemplate>
        const edited = { ...active[0], ...body } as RecurringTemplate
        active = [edited]
        return response(edited)
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

  it('shows persisted Recurring Pre-planning Schedule details', async () => {
    active = [{
      ...template,
      mode: 'scheduled',
      frequency: 'weekly',
      weekdays: [0, 2],
      quota_count: null,
      cadence: 'Every week on Mon, Wed',
      preplanning_schedule: { slots: [
        { id: 31, key: 'morning', position: 0, weekday: 0, start_minute: 480, end_minute: 540 },
        { id: 32, key: 'afternoon', position: 1, weekday: 2, start_minute: 900, end_minute: 1440 },
      ] },
    }]
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Gym' }))

    const detail = screen.getByRole('dialog', { name: 'Recurring template Gym' })
    const schedule = within(detail).getByRole('region', { name: 'Recurring Pre-planning Schedule' })
    expect(within(schedule).getByText('Mon · 08:00–09:00')).toBeInTheDocument()
    expect(within(schedule).getByText('Wed · 15:00–00:00')).toBeInTheDocument()
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
    await user.click(within(form).getByLabelText('Pre-plan each Task Occurrence'))
    await user.clear(within(form).getByLabelText('Pre-planning start'))
    await user.type(within(form).getByLabelText('Pre-planning start'), '08:30')
    await user.clear(within(form).getByLabelText('Pre-planning end'))
    await user.type(within(form).getByLabelText('Pre-planning end'), '09:15')
    await waitFor(() => expect(within(form).getByText(/Every day, starting/)).toBeInTheDocument())
    await user.click(within(form).getByRole('button', { name: 'Create recurrence' }))
    expect(await screen.findByText('Morning review')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/recurring-templates'),
      expect.objectContaining({ method: 'POST' }),
    )
    const request = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.find(
      ([input, init]) => String(input).endsWith('/recurring-templates') && init?.method === 'POST',
    )
    const requestBody = JSON.parse(String(request?.[1]?.body))
    expect(requestBody).not.toHaveProperty('project_id')
    expect(requestBody.preplanning_schedule).toEqual({
      slots: [{ start_minute: 510, end_minute: 555, weekday: null }],
    })
    expect(within(form).queryByLabelText('Location')).not.toBeInTheDocument()
    expect(window.confirm).not.toHaveBeenCalled()
  })

  it('creates a Planned Block ending at midnight as minute 1440', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)
    await screen.findByText('3 times per week')
    await user.click(screen.getByRole('button', { name: 'New recurring task' }))
    const form = screen.getByRole('dialog', { name: 'New recurring task' })
    await user.type(within(form).getByLabelText('Title'), 'Evening review')
    await user.click(within(form).getByLabelText('Pre-plan each Task Occurrence'))
    await user.clear(within(form).getByLabelText('Pre-planning start'))
    await user.type(within(form).getByLabelText('Pre-planning start'), '23:00')
    await user.clear(within(form).getByLabelText('Pre-planning end'))
    await user.type(within(form).getByLabelText('Pre-planning end'), '00:00')

    await user.click(within(form).getByRole('button', { name: 'Create recurrence' }))

    const request = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.find(
      ([input, init]) => String(input).endsWith('/recurring-templates') && init?.method === 'POST',
    )
    expect(JSON.parse(String(request?.[1]?.body)).preplanning_schedule).toEqual({
      slots: [{ start_minute: 1380, end_minute: 1440, weekday: null }],
    })
  })

  it('edits an existing Recurring Pre-planning Schedule with multiple slots', async () => {
    active = [{
      ...template,
      mode: 'scheduled',
      frequency: 'daily',
      weekdays: [],
      quota_count: null,
      cadence: 'Every day',
      preplanning_schedule: { slots: [
        { id: 31, key: 'morning', position: 0, weekday: null, start_minute: 480, end_minute: 540 },
        { id: 32, key: 'afternoon', position: 1, weekday: null, start_minute: 780, end_minute: 840 },
      ] },
    }]
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?view=recurring']}><RecurringPage /></MemoryRouter>)
    await screen.findByText('Every day')

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const form = screen.getByRole('dialog', { name: 'Edit Gym' })
    expect(within(form).getByLabelText('Pre-planning start')).toHaveValue('08:00')
    expect(within(form).getByLabelText('Pre-planning start 2')).toHaveValue('13:00')
    await user.clear(within(form).getByLabelText('Pre-planning start'))
    await user.type(within(form).getByLabelText('Pre-planning start'), '08:30')
    await user.click(within(form).getByRole('button', { name: 'Remove pre-planning slot 2' }))
    await user.click(within(form).getByRole('button', { name: 'Add Planned Block slot' }))
    await user.clear(within(form).getByLabelText('Pre-planning start 2'))
    await user.type(within(form).getByLabelText('Pre-planning start 2'), '15:00')
    await user.clear(within(form).getByLabelText('Pre-planning end 2'))
    await user.type(within(form).getByLabelText('Pre-planning end 2'), '16:00')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))

    const request = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.find(
      ([input, init]) => String(input).endsWith('/recurring-templates/9') && init?.method === 'PATCH',
    )
    const requestBody = JSON.parse(String(request?.[1]?.body))
    expect(requestBody).not.toHaveProperty('mode')
    expect(requestBody.preplanning_schedule).toEqual({
      slots: [
        { key: 'morning', start_minute: 510, end_minute: 540, weekday: null },
        { start_minute: 900, end_minute: 960, weekday: null },
      ],
    })
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
