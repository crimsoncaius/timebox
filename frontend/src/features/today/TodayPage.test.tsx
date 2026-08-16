import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TodayPage } from './TodayPage'

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const dayPayload = {
  id: 1,
  date: '2026-06-01',
  start_hour: 8,
  end_hour: 20,
  show_full_day: false,
  created_at: '',
  updated_at: '',
  meta: { timezone: 'UTC', today: '2026-06-01', server_now_iso: '2026-06-01T12:00:00Z' },
  time_blocks: [
    {
      id: 10,
      lane: 'planned' as const,
      task_type_id: 1,
      task_type: { id: 1, name: 'alpha', created_at: '', updated_at: '' },
      note: null,
      start_minute: 480,
      end_minute: 510,
      created_at: '',
      updated_at: '',
    },
    {
      id: 11,
      lane: 'planned' as const,
      task_type_id: 2,
      task_type: { id: 2, name: 'beta', created_at: '', updated_at: '' },
      note: null,
      start_minute: 540,
      end_minute: 570,
      created_at: '',
      updated_at: '',
    },
  ],
}

const taskTypes = [
  { id: 1, name: 'alpha', created_at: '', updated_at: '' },
  { id: 2, name: 'beta', created_at: '', updated_at: '' },
]

describe('TodayPage inspector rail', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    globalThis.fetch = vi.fn((input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (url.includes('/days/2026-06-01') && !url.includes('/blocks')) {
        return Promise.resolve(jsonResponse(dayPayload))
      }
      if (url.includes('/task-types') && !url.match(/\/task-types\/\d/)) {
        return Promise.resolve(jsonResponse(taskTypes))
      }
      if (url.includes('/tasks?state=active')) {
        return Promise.resolve(jsonResponse({
          timezone: 'UTC',
          server_now_iso: '2026-06-01T12:00:00Z',
          items: [{
            id: 77,
            title: 'Write launch narrative',
            ready_to_plan: true,
            task_type_id: 1,
            task_type: taskTypes[0],
            subtasks: [],
          }],
        }))
      }
      if (url.includes('/health')) {
        return Promise.resolve(
          jsonResponse({ status: 'ok', today: '2026-06-01', timezone: 'UTC' }),
        )
      }
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('shows persistent empty rail then block details after selecting a block', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const rail = await screen.findByRole('complementary', { name: 'Block details' })
    expect(within(rail).queryByLabelText('Task type', { exact: true })).not.toBeInTheDocument()

    await user.click(screen.getAllByRole('button', { name: /Edit planned block/i })[0]!)

    await expect(screen.findByLabelText('Task type', { exact: true })).resolves.toHaveValue('alpha')
    expect(within(rail).getByLabelText('Task type', { exact: true })).toBeInTheDocument()
  })

  it('selects a Ready to Plan task before placing it on the timeline', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const taskButtons = await screen.findAllByRole('button', { name: /Write launch narrative/ })
    await user.click(taskButtons[0]!)

    expect(screen.getByText(/is selected\. Choose an open slot/)).toHaveTextContent('Write launch narrative')
    expect(taskButtons[0]).toHaveAttribute('aria-pressed', 'true')
  })

  it('asks before discarding unsaved note when selecting another block', async () => {
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)

    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await screen.findByRole('complementary', { name: 'Block details' })
    await user.click(screen.getAllByRole('button', { name: /Edit planned block/i })[0]!)
    await screen.findByLabelText('Task type', { exact: true })

    const note = screen.getByLabelText('Note')
    await user.clear(note)
    await user.type(note, 'draft note')

    await user.click(screen.getAllByRole('button', { name: /Edit planned block/i })[1]!)

    expect(confirmSpy).toHaveBeenCalled()
    expect(screen.getByLabelText('Task type', { exact: true })).toHaveValue('alpha')

    confirmSpy.mockRestore()
  })

  it('deselects the block when clicking outside the timeline', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const rail = await screen.findByRole('complementary', { name: 'Block details' })
    await user.click(screen.getAllByRole('button', { name: /Edit planned block/i })[0]!)
    await screen.findByLabelText('Task type', { exact: true })
    expect(within(rail).getByLabelText('Task type', { exact: true })).toBeInTheDocument()

    await user.click(screen.getByText(/^Monday,/))

    await waitFor(() => {
      expect(within(rail).queryByLabelText('Task type', { exact: true })).not.toBeInTheDocument()
    })
  })

  it('does not deselect on outside click when user cancels discard', async () => {
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)

    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await screen.findByRole('complementary', { name: 'Block details' })
    await user.click(screen.getAllByRole('button', { name: /Edit planned block/i })[0]!)
    await screen.findByLabelText('Task type', { exact: true })

    const note = screen.getByLabelText('Note')
    await user.clear(note)
    await user.type(note, 'draft note')

    await user.click(screen.getByText(/^Monday,/))

    expect(confirmSpy).toHaveBeenCalled()
    expect(screen.getByLabelText('Task type', { exact: true })).toHaveValue('alpha')

    confirmSpy.mockRestore()
  })
})
