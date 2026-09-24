import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ChronicleDay } from '../../lib/api'
import { HistoryPage } from './HistoryPage'

function response(data: unknown) {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('HistoryPage', () => {
  const originalFetch = globalThis.fetch
  let days: ChronicleDay[]

  beforeEach(() => {
    localStorage.clear()
    days = []
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (url.includes('/health')) {
        return response({ status: 'ok', today: '1980-02-27', timezone: 'Pacific/Kiritimati' })
      }
      if (url.includes('/trends?')) return response({ start: '1980-02-25', end: '1980-03-02', today: '1980-02-27', timezone: 'Pacific/Kiritimati', captured_at: '1980-02-27T00:00:00Z', duration_seconds: 0, types: [] })
      if (url.includes('/days/chronicle')) {
        const month = new URL(url, 'http://localhost').searchParams.get('month') ?? '1980-02'
        return response({ month, today: '1980-02-27', days: days.filter(day => day.date.startsWith(month)) })
      }
      throw new Error(`Unexpected request: GET ${url}`)
    }) as typeof fetch
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('switches between the Calendar and Trends views', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/history']}><HistoryPage /></MemoryRouter>)

    expect(await screen.findByTestId('chronicle-month-heading')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Calendar' })).toHaveAttribute('aria-selected', 'true')
    await user.click(screen.getByRole('tab', { name: 'Trends' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Trends' })).toBeInTheDocument()
    expect(await screen.findByText('No recorded time in this range.')).toBeInTheDocument()
    expect(screen.queryByTestId('chronicle-month-heading')).not.toBeInTheDocument()
  })

  it('opens Trends directly from its URL', async () => {
    render(<MemoryRouter initialEntries={['/history?view=trends']}><HistoryPage /></MemoryRouter>)

    expect(await screen.findByRole('tab', { name: 'Trends' })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('No recorded time in this range.')).toBeInTheDocument()
  })

  it('uses the reporting month when the Chronicle has no activity', async () => {
    render(<MemoryRouter initialEntries={['/history']}><HistoryPage /></MemoryRouter>)

    expect(await screen.findByTestId('chronicle-month-heading')).toHaveTextContent('February 1980')
  })

  it('returns to the reporting month from an older month and cannot browse ahead', async () => {
    days = [{
      date: '1980-01-12', planned_count: 1, actual_count: 0, has_completion: false, actual_blocks: [],
    }]
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/history']}><HistoryPage /></MemoryRouter>)

    expect(await screen.findByTestId('chronicle-month-heading')).toHaveTextContent('February 1980')
    expect(screen.getByRole('button', { name: 'Next month' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Previous month' }))
    expect(await screen.findByTestId('chronicle-month-heading')).toHaveTextContent('January 1980')
    await user.click(screen.getByRole('button', { name: 'This month' }))
    expect(screen.getByTestId('chronicle-month-heading')).toHaveTextContent('February 1980')
  })

  it('shows a standalone Actual Block Name in Chronicle without unspecified noise', async () => {
    days = [{
      date: '1980-02-12',
      planned_count: 0, actual_count: 1, has_completion: false,
      actual_blocks: [{
        date: '1980-02-12',
        start_minute: 600,
        end_minute: 660,
        duration_minutes: 60,
        actual_block: {
          id: 4,
          task_type_id: 3,
          task_type: { id: 3, name: 'unspecified', created_at: '', updated_at: '' },
          task_id: null,
          task: null,
          name: 'Evening walk',
          note: null,
          planned_block_id: null,
          start_at: '1980-02-12T10:00:00Z',
          end_at: '1980-02-12T11:00:00Z',
          created_at: '',
          updated_at: '',
        },
      }],
    }]
    render(<MemoryRouter initialEntries={['/history']}><HistoryPage /></MemoryRouter>)

    expect(await screen.findByText('Evening walk')).toBeInTheDocument()
    expect(screen.queryByText('unspecified')).not.toBeInTheDocument()
  })

  it('shows a task-backed Actual Block Name ahead of its linked task in Chronicle', async () => {
    days = [{
      date: '1980-02-13',
      planned_count: 0, actual_count: 1, has_completion: false,
      actual_blocks: [{
        date: '1980-02-13',
        start_minute: 600,
        end_minute: 660,
        duration_minutes: 60,
        actual_block: {
          id: 5,
          task_type_id: 4,
          task_type: { id: 4, name: 'Deep work', created_at: '', updated_at: '' },
          task_id: 9,
          task: { id: 9, title: 'Prepare launch', status: 'in_progress', task_type_id: 4 },
          name: 'Outline session',
          note: null,
          planned_block_id: null,
          start_at: '1980-02-13T10:00:00Z',
          end_at: '1980-02-13T11:00:00Z',
          created_at: '',
          updated_at: '',
        },
      }],
    }]
    render(<MemoryRouter initialEntries={['/history']}><HistoryPage /></MemoryRouter>)

    expect(await screen.findByText('Outline session · Deep work')).toBeInTheDocument()
    expect(screen.queryByText('Prepare launch')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/1980-02-13, 0 planned blocks, 1 actual block, Outline session/)).toBeInTheDocument()
  })
})
