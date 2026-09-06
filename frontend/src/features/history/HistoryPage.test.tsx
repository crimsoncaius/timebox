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

  it('shows a standalone Actual Block Name in Chronicle without unspecified noise', async () => {
    days = [{
      id: 2,
      date: '1980-02-12',
      start_hour: 8,
      end_hour: 18,
      show_full_day: false,
      block_count: 1,
      updated_at: '1980-02-12T10:00:00Z',
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
      id: 3,
      date: '1980-02-13',
      start_hour: 8,
      end_hour: 18,
      show_full_day: false,
      block_count: 1,
      updated_at: '1980-02-13T10:00:00Z',
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

    expect(await screen.findByText('Outline session')).toBeInTheDocument()
    expect(screen.queryByText('Prepare launch')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/1980-02-13, archived day, Outline session/)).toBeInTheDocument()
  })
})
