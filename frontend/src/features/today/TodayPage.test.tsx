import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/days/2026-06-01') && !url.includes('/blocks')) {
        return Promise.resolve(jsonResponse(dayPayload))
      }
      if (url.includes('/days/2026-06-01/blocks') && method === 'POST') {
        if (url.includes('/blocks/10/complete-as-planned')) {
          return Promise.resolve(jsonResponse({
            ...dayPayload,
            time_blocks: [
              ...dayPayload.time_blocks,
              {
                ...dayPayload.time_blocks[0],
                id: 12,
                lane: 'actual',
              },
            ],
          }))
        }
        const body = JSON.parse(String(init?.body)) as {
          task_id: number
          task_type_id: number
          start_minute: number
          end_minute: number
        }
        return Promise.resolve(jsonResponse({
          ...dayPayload,
          time_blocks: [
            ...dayPayload.time_blocks,
            {
              id: 12,
              lane: 'planned',
              task_id: body.task_id,
              task_type_id: body.task_type_id,
              task_type: { id: body.task_type_id, name: 'unspecified', created_at: '', updated_at: '' },
              note: null,
              start_minute: body.start_minute,
              end_minute: body.end_minute,
              created_at: '',
              updated_at: '',
            },
          ],
        }))
      }
      if (url.includes('/task-types') && method === 'POST') {
        return Promise.resolve(jsonResponse({ id: 3, name: 'unspecified', created_at: '', updated_at: '' }))
      }
      if (url.includes('/task-types') && !url.match(/\/task-types\/\d/)) {
        return Promise.resolve(jsonResponse(taskTypes))
      }
      if (url.includes('/tasks?state=active')) {
        return Promise.resolve(jsonResponse({
          timezone: 'UTC',
          server_now_iso: '2026-06-01T12:00:00Z',
          items: [
            {
              id: 77,
              title: 'Write launch narrative',
              ready_to_plan: true,
              task_type_id: 1,
              task_type: taskTypes[0],
              subtasks: [],
            },
            {
              id: 78,
              title: 'Plan untyped report',
              ready_to_plan: true,
              task_type_id: null,
              task_type: null,
              subtasks: [],
            },
          ],
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

  it('completes a planned block by swiping it right', async () => {
    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const plannedBlock = (await screen.findAllByRole('button', { name: 'Edit planned block' }))[0]!
    fireEvent.pointerDown(plannedBlock, {
      button: 0,
      pointerId: 1,
      clientX: 10,
      clientY: 100,
    })
    fireEvent.pointerMove(window, {
      pointerId: 1,
      clientX: 90,
      clientY: 100,
    })
    fireEvent.pointerUp(window, {
      pointerId: 1,
      clientX: 90,
      clientY: 100,
    })

    await waitFor(() => {
      expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledWith(
        '/api/days/2026-06-01/blocks/10/complete-as-planned',
        expect.objectContaining({ method: 'POST' }),
      )
    })
    expect(await screen.findByRole('button', { name: 'Edit actual block' })).toBeInTheDocument()
  })

  it('plans an untyped Ready to Plan task with the unspecified fallback', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const taskTitles = await screen.findAllByText('Plan untyped report')
    const taskButton = taskTitles[0]!.closest('button')
    expect(taskButton).not.toBeNull()
    expect(screen.getAllByRole('button', { name: 'Drag Plan untyped report to Planned timeline' })).not.toHaveLength(0)
    await user.click(taskButton!)

    const plannedLane = screen.getByTestId('day-timeline').querySelector('[data-day-lane="planned"]')
    expect(plannedLane).not.toBeNull()
    fireEvent.click(plannedLane!, { clientY: 47 })

    await waitFor(() => {
      const calls = vi.mocked(globalThis.fetch).mock.calls
      const createType = calls.find(([input, init]) =>
        String(input).endsWith('/task-types') && init?.method === 'POST',
      )
      const createBlock = calls.find(([input, init]) =>
        String(input).includes('/days/2026-06-01/blocks') && init?.method === 'POST',
      )
      expect(createType).toBeDefined()
      expect(JSON.parse(String(createType?.[1]?.body))).toEqual({ name: 'unspecified' })
      expect(createBlock).toBeDefined()
      expect(JSON.parse(String(createBlock?.[1]?.body))).toMatchObject({
        lane: 'planned',
        task_id: 78,
        task_type_id: 3,
        start_minute: 510,
        end_minute: 540,
      })
    })
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
