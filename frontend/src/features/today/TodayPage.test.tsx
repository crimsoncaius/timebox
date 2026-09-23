import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TodayPage } from './TodayPage'
import { ReadinessCoordinator } from '../readiness/readinessCoordinator'
import { ReadinessProvider } from '../readiness/ReadinessProvider'

// The page is tested against a stubbed Activity repository: the journal, its
// outbox and its conflict handling have their own tests in features/activity.
// `activityFake.snapshot` stays null by default, so `day` is the plain API day;
// a test that needs projected Actuals opts in by setting it.
const activityFake = {
  snapshot: null as unknown,
  pending: false,
  error: null as string | null,
  correct: vi.fn(async () => true),
  refresh: vi.fn(async () => {}),
  listeners: new Set<() => void>(),
  // useSyncExternalStore requires a referentially stable snapshot between changes.
  cached: { snapshot: null as unknown, pending: false, error: null as string | null },
  read() {
    const { snapshot, pending, error } = this
    if (this.cached.snapshot !== snapshot || this.cached.pending !== pending || this.cached.error !== error) {
      this.cached = { snapshot, pending, error }
    }
    return this.cached
  },
  emit() { this.listeners.forEach((listener) => listener()) },
  reset() {
    this.snapshot = null
    this.pending = false
    this.error = null
    this.cached = { snapshot: null, pending: false, error: null }
    this.correct = vi.fn(async () => true)
    this.refresh = vi.fn(async () => {})
    this.listeners.clear()
  },
}

const activityRepositoryStub = {
  subscribe: (listener: () => void) => {
    activityFake.listeners.add(listener)
    return () => activityFake.listeners.delete(listener)
  },
  getSnapshot: () => activityFake.read(),
  now: () => Date.parse('2026-06-01T12:00:00Z'),
  refresh: (...args: unknown[]) => activityFake.refresh(...args as []),
  correct: (...args: unknown[]) => activityFake.correct(...args as []),
  get state() { return { pending: activityFake.pending, error: activityFake.error } },
}

vi.mock('../activity/activityRepository', async (original) => ({
  ...await original<object>(),
  getActivityRepository: () => activityRepositoryStub,
}))

// The tracking controls have their own suite; the page only needs them to mount.
vi.mock('../activity/ActivityTracking', () => ({ ActivityTracking: () => null }))

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
      task_id: 77,
      task: { id: 77, title: 'Write launch narrative', status: 'open', task_type_id: 1 },
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
  actual_blocks: [],
}

const taskTypes = [
  { id: 1, name: 'alpha', created_at: '', updated_at: '' },
  { id: 2, name: 'beta', created_at: '', updated_at: '' },
]

describe('TodayPage inspector rail', () => {
  const originalFetch = globalThis.fetch
  let rejectNextTaskUndo = false
  let standaloneActual: Record<string, unknown> | null = null
  let readySaveGate: Promise<void> | null = null
  let recordingCalls = 0

  beforeEach(() => {
    localStorage.clear()
    activityFake.reset()
    rejectNextTaskUndo = false
    standaloneActual = null
    readySaveGate = null
    recordingCalls = 0
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/planned-blocks/10/record-actual-as-planned') && method === 'POST') {
        if (recordingCalls++ > 0) return Promise.resolve(jsonResponse({ status: 'already_recorded', actual_block: { id: 12 }, undo_token: null }, 201))
        return Promise.resolve(jsonResponse({ status: 'recorded', actual_block: { id: 12 }, undo_token: 'record-undo' }, 201))
      }
      if (url.includes('/actual-blocks/start') && method === 'POST') {
        return Promise.resolve(jsonResponse({
          id: 40, task_type_id: 1, task_type: taskTypes[0], task_id: 77,
          task: { id: 77, title: 'Write launch narrative', status: 'open', task_type_id: 1 },
          note: null, planned_block_id: 10, start_at: '2026-06-01T12:00:00Z', end_at: null,
          created_at: '', updated_at: '',
        }, 201))
      }
      if (url.includes('/actual-blocks/40/finish') && method === 'POST') return Promise.resolve(jsonResponse({}))
      if (url.includes('/tasks/77/complete') && method === 'POST') return Promise.resolve(jsonResponse({ task: {}, undo_token: 'task-undo', removed_planned_block_ids: [99, 100] }))
      if (url.includes('/tasks/77/undo-completion') && method === 'POST') {
        if (rejectNextTaskUndo) {
          rejectNextTaskUndo = false
          return Promise.resolve(jsonResponse({ detail: 'Task completion changed on another surface' }, 409))
        }
        return Promise.resolve(jsonResponse({}))
      }
      if (url.includes('/tasks/77') && method === 'PATCH') {
        const body = JSON.parse(String(init?.body)) as { ready_to_plan: boolean }
        const respond = () => jsonResponse({ id: 77, title: 'Write launch narrative', ready_to_plan: body.ready_to_plan })
        return readySaveGate ? readySaveGate.then(respond) : Promise.resolve(respond())
      }
      if (url.endsWith('/actual-blocks') && method === 'POST') {
        const body = JSON.parse(String(init?.body)) as {
          name: string | null
          note: string | null
          start_at: string
          end_at: string
        }
        standaloneActual = {
          id: 41,
          task_type_id: 3,
          task_type: { id: 3, name: 'unspecified', created_at: '', updated_at: '' },
          task_id: null,
          task: null,
          name: body.name,
          note: body.note,
          planned_block_id: null,
          start_at: body.start_at,
          end_at: body.end_at,
          created_at: '',
          updated_at: '',
        }
        return Promise.resolve(jsonResponse(standaloneActual, 201))
      }
      if (url.includes('/days/2026-06-01') && !url.includes('/blocks')) {
        return Promise.resolve(jsonResponse({
          ...dayPayload,
          actual_blocks: standaloneActual == null
            ? []
            : [{ actual_block: standaloneActual, start_minute: 510, end_minute: 540 }],
        }))
      }
      if (url.includes('/days/2026-06-01/blocks') && method === 'POST') {
        const body = JSON.parse(String(init?.body)) as {
          task_id: number
          task_type_id?: number
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
              task_type_id: body.task_type_id ?? (body.task_id === 79 ? 1 : 3),
              task_type: body.task_id === 79
                ? taskTypes[0]
                : { id: 3, name: 'unspecified', created_at: '', updated_at: '' },
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
              session_tasks: [{
                id: 79,
                title: 'Gym · Session 1',
                ready_to_plan: true,
                task_type_id: 1,
                task_type: taskTypes[0],
                status: 'open',
                subtasks: [],
              }],
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
    localStorage.clear()
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
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

  it('keeps a focused Note draft when the earlier Block Name PATCH returns a full day', async () => {
    const fallbackFetch = globalThis.fetch
    let savedBlock = { ...dayPayload.time_blocks[0]!, name: 'Original name', note: 'Previously saved note' }
    const snapshot = () => ({ ...dayPayload, time_blocks: [savedBlock, dayPayload.time_blocks[1]!] })
    let releaseName!: () => void
    const nameResponse = new Promise<void>((resolve) => { releaseName = resolve })
    const patches: Record<string, unknown>[] = []
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/days/2026-06-01') return jsonResponse(snapshot())
      if (url === '/api/days/2026-06-01/blocks/10' && init?.method === 'PATCH') {
        const patch = JSON.parse(String(init.body))
        patches.push(patch)
        savedBlock = { ...savedBlock, ...patch }
        const response = jsonResponse(snapshot())
        if ('name' in patch) await nameResponse
        return response
      }
      return fallbackFetch(input, init)
    })
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    fireEvent.click((await screen.findAllByRole('button', { name: 'Edit planned block' }))[0]!)
    const rail = screen.getByRole('complementary', { name: 'Block details' })
    const name = within(rail).getByLabelText('Name')
    const note = within(rail).getByLabelText('Note')
    fireEvent.change(name, { target: { value: 'Renamed block' } })
    fireEvent.blur(name)
    fireEvent.focus(note)
    fireEvent.change(note, { target: { value: 'New note that should not disappear' } })
    expect(patches).toEqual([{ name: 'Renamed block' }])
    await act(async () => { releaseName() })
    expect(note).toHaveValue('New note that should not disappear')
    fireEvent.blur(note)
    await waitFor(() => expect(savedBlock.note).toBe('New note that should not disappear'))
    // The hidden responsive editor must not write its previously saved Note back.
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 500)) })
    expect(patches).toEqual([{ name: 'Renamed block' }, { note: 'New note that should not disappear' }])
  })

  it('preserves Note edits through responsive layout changes without a hidden editor saving over them', async () => {
    const fallbackFetch = globalThis.fetch
    let savedBlock = { ...dayPayload.time_blocks[0]!, note: 'Original note' }
    const snapshot = () => ({ ...dayPayload, time_blocks: [savedBlock, dayPayload.time_blocks[1]!] })
    const patches: { note?: string }[] = []
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input) === '/api/days/2026-06-01') return jsonResponse(snapshot())
      if (String(input) === '/api/days/2026-06-01/blocks/10' && init?.method === 'PATCH') {
        const patch = JSON.parse(String(init.body))
        patches.push(patch)
        savedBlock = { ...savedBlock, ...patch }
        return jsonResponse(snapshot())
      }
      return fallbackFetch(input, init)
    })
    let resize: (event: { matches: boolean }) => void = () => {}
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: true,
      addEventListener: (_type: string, listener: typeof resize) => { resize = listener },
      removeEventListener: vi.fn(),
    }))
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    fireEvent.click((await screen.findAllByRole('button', { name: 'Edit planned block' }))[0]!)
    const desktop = screen.getByRole('complementary', { name: 'Block details' }).querySelector('textarea')!
    fireEvent.change(desktop, { target: { value: 'Desktop note before resize' } })
    fireEvent.blur(desktop)
    await waitFor(() => expect(savedBlock.note).toBe('Desktop note before resize'))
    act(() => { resize({ matches: false }) })
    const mobile = screen.getByRole('dialog').querySelector('textarea')!
    fireEvent.change(mobile, { target: { value: 'Latest mobile note' } })
    fireEvent.blur(mobile)
    await waitFor(() => expect(savedBlock.note).toBe('Latest mobile note'))
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 1100)) })
    expect(patches).toEqual([{ note: 'Desktop note before resize' }, { note: 'Latest mobile note' }])
    expect(savedBlock.note).toBe('Latest mobile note')

    // A resize must carry an unsaved draft too, rather than remount from older server data.
    fireEvent.change(mobile, { target: { value: 'Unsaved note across resize' } })
    act(() => { resize({ matches: true }) })
    const nextDesktop = screen.getByRole('complementary', { name: 'Block details' }).querySelector('textarea')!
    expect(nextDesktop).toHaveValue('Unsaved note across resize')
    fireEvent.blur(nextDesktop)
    await waitFor(() => expect(savedBlock.note).toBe('Unsaved note across resize'))
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

    expect(screen.getByText(/is selected\. Choose a time/)).toHaveTextContent('Write launch narrative')
    expect(taskButtons[0]).toHaveAttribute('aria-pressed', 'true')
    expect(taskButtons[0]).toHaveAttribute('aria-pressed', 'true')
    await user.click(taskButtons[0]!)
  })

  it('explains future Actual placement without creating a block and still allows Planned drafts', async () => {
    const fallbackFetch = globalThis.fetch
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
      String(input) === '/api/days/2026-06-01'
        ? Promise.resolve(jsonResponse({ ...dayPayload, meta: { ...dayPayload.meta, today: '2026-05-31' } }))
        : fallbackFetch(input, init))
    const view = render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    await screen.findByTestId('day-timeline')
    fireEvent.click(view.container.querySelector('[data-day-lane="actual"]')!, { clientY: 47 })
    expect(screen.getByRole('alert')).toHaveTextContent('Actual time cannot be recorded in the future')
    expect(screen.queryByTestId('draft-block')).not.toBeInTheDocument()
    expect(activityFake.correct).not.toHaveBeenCalled()
    expect(vi.mocked(fetch).mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(0)
    fireEvent.click(view.container.querySelector('[data-day-lane="planned"]')!, { clientY: 47 })
    expect(screen.getByTestId('draft-block')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each(['2026-06-01', '2026-06-02'])('retains no-space feedback for occupied Actual time when today is %s', async (today) => {
    const fallbackFetch = globalThis.fetch
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
      String(input) === '/api/days/2026-06-01'
        ? Promise.resolve(jsonResponse({ ...dayPayload, meta: { ...dayPayload.meta, today }, actual_blocks: [{
          start_minute: 480, end_minute: 1200,
          actual_block: { id: 41, task_type_id: 1, task_type: taskTypes[0], task_id: null, task: null,
            name: 'Recorded activity', note: null, planned_block_id: null,
            start_at: '2026-06-01T08:00:00Z', end_at: '2026-06-01T20:00:00Z', created_at: '', updated_at: '' },
        }] }))
        : fallbackFetch(input, init))
    const view = render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    await screen.findByTestId('day-timeline')
    fireEvent.click(view.container.querySelector('[data-day-lane="actual"]')!, { clientY: 47 })
    expect(screen.getByRole('alert')).toHaveTextContent('No available space in this day')
    expect(screen.queryByTestId('draft-block')).not.toBeInTheDocument()
  })

  it('places a manual Actual draft beside occupied time and fits future clicks into elapsed time', async () => {
    standaloneActual = {
      id: 41, task_type_id: 1, task_type: taskTypes[0], task_id: null, task: null,
      name: 'Recorded activity', note: null, planned_block_id: null,
      start_at: '2026-06-01T08:30:00Z', end_at: '2026-06-01T09:00:00Z', created_at: '', updated_at: '',
    }
    const view = render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    await screen.findByText('Recorded activity')
    const lane = view.container.querySelector('[data-day-lane="actual"]')!
    fireEvent.click(lane, { clientY: 461 }) // 13:00, more than 30 minutes from an elapsed slot
    expect(screen.getByTestId('draft-block').style.top).toBe('322px')
    fireEvent.click(lane, { clientY: 47 }) // 08:30, nearest full slot ties and goes later to 09:00
    expect(screen.getByTestId('draft-block').style.top).toBe('92px')
  })

  it('places a selected task over an occupied card at the closest available time', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    const taskButtons = await screen.findAllByRole('button', { name: /Write launch narrative/ })
    await user.click(taskButtons[0]!)
    // Overlay covers the saved card at 08:00; its ordinary editor must not open.
    fireEvent.click(screen.getByTestId('planned-placement-target'), { clientY: 1 })
    await waitFor(() => {
      const request = vi.mocked(globalThis.fetch).mock.calls.find(([input, init]) =>
        String(input).includes('/days/2026-06-01/blocks') && init?.method === 'POST')
      expect(request).toBeDefined()
      expect(JSON.parse(String(request?.[1]?.body))).toMatchObject({ start_minute: 510, end_minute: 540, task_id: 77 })
    })
  })

  it('places a nested quota Session Task as an ordinary Planned Task', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)

    const sessionTitle = (await screen.findAllByText('Gym · Session 1'))[0]!
    await user.click(sessionTitle.closest('button')!)
    const plannedLane = screen.getByTestId('day-timeline').querySelector('[data-day-lane="planned"]')
    expect(plannedLane).not.toBeNull()
    fireEvent.click(plannedLane!, { clientY: 47 })

    await waitFor(() => {
      const createBlock = vi.mocked(globalThis.fetch).mock.calls.find(([input, init]) =>
        String(input).includes('/days/2026-06-01/blocks') && init?.method === 'POST' &&
        JSON.parse(String(init.body)).task_id === 79,
      )
      expect(createBlock).toBeDefined()
      expect(JSON.parse(String(createBlock?.[1]?.body))).toMatchObject({
        lane: 'planned', task_id: 79,
      })
      expect(JSON.parse(String(createBlock?.[1]?.body))).not.toHaveProperty('task_type_id')
    })
  })

  it('records an Actual from a Planned Block with exact Undo', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await user.click((await screen.findAllByRole('button', { name: 'Edit planned block' }))[0]!)
    await user.click((await screen.findAllByRole('button', { name: 'Record Actual as planned' }))[0]!)

    await waitFor(() => {
      expect(vi.mocked(globalThis.fetch)).toHaveBeenCalledWith(
        '/api/planned-blocks/10/record-actual-as-planned',
        expect.objectContaining({ method: 'POST' }),
      )
    })
    expect(await screen.findByRole('button', { name: 'Undo' })).toBeInTheDocument()
    await user.click(screen.getAllByRole('button', { name: 'Record Actual as planned' })[0]!)
    expect(screen.getByRole('button', { name: 'Undo' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(await screen.findByText('Already recorded')).toBeInTheDocument()
  })

  it('keeps a newer recording Undo when an older Undo request completes', async () => {
    const user = userEvent.setup()
    const originalFetch = globalThis.fetch
    let finishUndo!: (response: Response) => void
    const undoPending = new Promise<Response>((resolve) => { finishUndo = resolve })
    let records = 0
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.includes('/planned-blocks/10/undo-record-actual')) return undoPending
      if (url.includes('/planned-blocks/10/record-actual-as-planned')) {
        records++
        return Promise.resolve(jsonResponse({ status: 'recorded', actual_block: { id: 12 }, undo_token: `token-${records}` }, 201))
      }
      return originalFetch(input, init)
    })
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes>
      <Route path="/day/:date" element={<TodayPage />} />
    </Routes></MemoryRouter>)
    await user.click((await screen.findAllByRole('button', { name: 'Edit planned block' }))[0]!)
    await user.click(screen.getAllByRole('button', { name: 'Record Actual as planned' })[0]!)
    await user.click(await screen.findByRole('button', { name: 'Undo' }))
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/planned-blocks/10/undo-record-actual'),
      expect.objectContaining({ body: JSON.stringify({ undo_token: 'token-1' }) }),
    ))
    await user.click((await screen.findAllByRole('button', { name: 'Edit planned block' }))[0]!)
    await user.click((await screen.findAllByRole('button', { name: 'Record Actual as planned' }))[0]!)
    await waitFor(() => expect(records).toBe(2))
    finishUndo(new Response(null, { status: 204 }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Undo' })).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Undo' }))
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/planned-blocks/10/undo-record-actual'),
      expect.objectContaining({ body: JSON.stringify({ undo_token: 'token-2' }) }),
    ))
  })

  it('delegates untyped Ready to Plan fallback resolution to the backend', async () => {
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
      expect(createType).toBeUndefined()
      expect(createBlock).toBeDefined()
      expect(JSON.parse(String(createBlock?.[1]?.body))).toMatchObject({
        lane: 'planned',
        task_id: 78,
        start_minute: 510,
        end_minute: 540,
      })
      expect(JSON.parse(String(createBlock?.[1]?.body))).not.toHaveProperty('task_type_id')
    })
  })

  it('keeps the Planned Block inspector focused on planning', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    await user.click((await screen.findAllByRole('button', { name: 'Edit planned block' }))[0]!)
    expect(screen.getAllByRole('button', { name: 'Record Actual as planned' })[0]).toBeVisible()
  })

  it.each([
    { state: 'selected task and its unsubmitted draft', clearSelectionFirst: false },
    { state: 'task-linked draft after its selection is cleared', clearSelectionFirst: true },
  ])('discards a $state when readiness removal becomes pending', async ({ clearSelectionFirst }) => {
    let releaseSave!: () => void
    readySaveGate = new Promise<void>((resolve) => { releaseSave = resolve })
    const coordinator = new ReadinessCoordinator()
    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <ReadinessProvider coordinator={coordinator}>
          <Routes>
            <Route path="/day/:date" element={<TodayPage />} />
          </Routes>
        </ReadinessProvider>
      </MemoryRouter>,
    )

    await screen.findAllByRole('button', { name: 'Write launch narrative' })
    const plannedLane = screen.getByTestId('day-timeline').querySelector('[data-day-lane="planned"]')
    expect(plannedLane).not.toBeNull()
    fireEvent.click(plannedLane!, { clientY: 47 })
    expect(await screen.findByRole('heading', { name: 'New block' })).toBeInTheDocument()

    const readyQueue = screen.getByRole('region', { name: 'Ready to Plan tasks' })
    const readyChoice = within(readyQueue).getByRole('button', { name: 'Write launch narrative' })
    fireEvent.click(readyChoice)
    expect(screen.getByRole('button', { name: 'Write launch narrative' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('heading', { name: 'New block' })).toBeInTheDocument()
    expect(screen.getByText('Selected from Ready to Plan')).toBeInTheDocument()
    expect(screen.getByText(/is selected\. Choose a time/)).toHaveTextContent('Write launch narrative')
    if (clearSelectionFirst) {
      fireEvent.click(screen.getByRole('button', { name: 'Write launch narrative' }))
      expect(screen.getByRole('button', { name: 'Write launch narrative' })).toHaveAttribute('aria-pressed', 'false')
      expect(screen.queryByText(/is selected\. Choose a time/)).not.toBeInTheDocument()
      expect(screen.getByText('Selected from Ready to Plan')).toBeInTheDocument()
    }

    const task = {
      id: 77,
      title: 'Write launch narrative',
      ready_to_plan: true,
      task_type_id: 1,
      task_type: taskTypes[0],
      status: 'open',
      subtasks: [],
    }
    let removal!: Promise<void>
    await act(async () => {
      removal = coordinator.setReadyToPlan(task, false)
      await Promise.resolve()
    })

    await waitFor(() => {
      expect(screen.queryByText(/is selected\. Choose a time/)).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'New block' })).not.toBeInTheDocument()
      expect(screen.queryByText('Selected from Ready to Plan')).not.toBeInTheDocument()
    })

    await act(async () => {
      releaseSave()
      await removal
    })
  })

  it('clears the draft after creating a standalone Actual so no stale discard prompt follows', async () => {
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    render(
      <MemoryRouter initialEntries={['/day/2026-06-01']}>
        <Routes>
          <Route path="/day/:date" element={<TodayPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const rail = await screen.findByRole('complementary', { name: 'Block details' })
    const actualLane = screen.getByTestId('day-timeline').querySelector('[data-day-lane="actual"]')
    expect(actualLane).not.toBeNull()
    fireEvent.click(actualLane!, { clientY: 47 })

    await user.type(within(rail).getByLabelText('Block Name (optional)'), 'Evening walk')
    await user.click(within(rail).getByRole('button', { name: 'Create block' }))

    await waitFor(() => expect(activityFake.correct).toHaveBeenCalledWith(
      'add', null, expect.objectContaining({ name: 'Evening walk' }),
    ))

    // The draft is gone and nothing is dirty, so selecting a block does not prompt.
    await user.click((await screen.findAllByRole('button', { name: 'Edit planned block' }))[0]!)
    expect(confirmSpy).not.toHaveBeenCalled()
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

    await user.click(screen.getByRole('heading', { name: 'Timebox', level: 2 }))

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

    await user.click(screen.getByRole('heading', { name: 'Timebox', level: 2 }))

    expect(confirmSpy).toHaveBeenCalled()
    expect(screen.getByLabelText('Task type', { exact: true })).toHaveValue('alpha')

    confirmSpy.mockRestore()
  })

  it('re-scrolls to the Now Line when Today is requested while already viewing Today', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-06-01T12:00:00Z'))
    const user = userEvent.setup()
    const scrollBy = vi.spyOn(window, 'scrollBy').mockImplementation(() => undefined)
    try {
      render(
        <MemoryRouter initialEntries={['/day/2026-06-01']}>
          <Routes>
            <Route path="/day/:date" element={<TodayPage />} />
          </Routes>
        </MemoryRouter>,
      )
      await screen.findByTestId('day-timeline')
      expect(scrollBy).toHaveBeenCalled()
      const afterLoad = scrollBy.mock.calls.length
      await user.click(within(screen.getByTestId('day-nav')).getByRole('button', { name: 'Today' }))
      expect(scrollBy.mock.calls.length).toBeGreaterThan(afterLoad)
    } finally {
      vi.useRealTimers()
    }
  })

  it('scrolls a Block deep link instead of the Now Line', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-06-01T12:00:00Z'))
    const originalScrollIntoView = HTMLElement.prototype.scrollIntoView
    const scrollIntoView = vi.fn()
    HTMLElement.prototype.scrollIntoView = scrollIntoView
    try {
      render(
        <MemoryRouter initialEntries={['/day/2026-06-01?block=10']}>
          <Routes>
            <Route path="/day/:date" element={<TodayPage />} />
          </Routes>
        </MemoryRouter>,
      )
      await screen.findByTestId('day-timeline')
      expect(screen.getByTestId('day-timeline').closest('[data-auto-scroll-to-now]')).toHaveAttribute('data-auto-scroll-to-now', 'false')
      await waitFor(() => expect(scrollIntoView).toHaveBeenCalled())
    } finally {
      HTMLElement.prototype.scrollIntoView = originalScrollIntoView
      vi.useRealTimers()
    }
  })
})
