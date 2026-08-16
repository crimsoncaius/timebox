import { render, screen, waitFor, within } from '@testing-library/react'
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

  beforeEach(() => {
    localStorage.clear()
    active = [template]
    paused = []
    window.confirm = vi.fn(() => true)
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) return response({ status: 'ok', today: '2099-08-16', timezone: 'UTC' })
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
    await user.click(screen.getByRole('button', { name: 'On a schedule' }))
    const form = screen.getByRole('dialog', { name: 'New recurring task' })
    await user.type(within(form).getByText('Title').nextElementSibling as HTMLInputElement, 'Morning review')
    await waitFor(() => expect(within(form).getByText(/Next:/)).toBeInTheDocument())
    await user.click(within(form).getByRole('button', { name: 'Create recurrence' }))
    expect(await screen.findByText('Morning review')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/recurring-templates'),
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
