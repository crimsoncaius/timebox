import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TodayPage } from './TodayPage'
import { ReadinessCoordinator } from '../readiness/readinessCoordinator'
import { ReadinessProvider } from '../readiness/ReadinessProvider'

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function DirectWorkRequest() {
  const navigate = useNavigate()
  return <button onClick={() => navigate('?workMode=start')}>Request work directly</button>
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

  beforeEach(() => {
    localStorage.clear()
    rejectNextTaskUndo = false
    standaloneActual = null
    readySaveGate = null
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/planned-blocks/10/record-actual-as-planned') && method === 'POST') {
        return Promise.resolve(jsonResponse({ actual_block: { id: 12 }, undo_token: 'record-undo' }, 201))
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
    expect(screen.getByRole('link', { name: 'Start Work Mode' })).toBeInTheDocument()
    fireEvent.change(name, { target: { value: 'Renamed block' } })
    expect(screen.getByRole('button', { name: 'Start Work Mode' })).toBeDisabled()
    fireEvent.blur(name)
    fireEvent.focus(note)
    fireEvent.change(note, { target: { value: 'New note that should not disappear' } })
    expect(patches).toEqual([{ name: 'Renamed block' }])
    expect(screen.getByRole('button', { name: 'Start Work Mode' })).toBeDisabled()
    await act(async () => { releaseName() })
    expect(note).toHaveValue('New note that should not disappear')
    expect(screen.getByRole('button', { name: 'Start Work Mode' })).toBeDisabled()
    fireEvent.blur(note)
    await waitFor(() => expect(savedBlock.note).toBe('New note that should not disappear'))
    // The hidden responsive editor must not write its previously saved Note back.
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 500)) })
    expect(patches).toEqual([{ name: 'Renamed block' }, { note: 'New note that should not disappear' }])
    expect(screen.getByRole('link', { name: 'Start Work Mode' })).toBeInTheDocument()
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

    expect(screen.getByText(/is selected\. Choose an open slot/)).toHaveTextContent('Write launch narrative')
    expect(taskButtons[0]).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Start Work Mode' })).toBeDisabled()
    expect(screen.getByText('Finish planning to start Work Mode.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Start Work Mode' }))
    expect(screen.queryByRole('dialog', { name: 'Start Work Mode' })).not.toBeInTheDocument()
    expect(taskButtons[0]).toHaveAttribute('aria-pressed', 'true')
    await user.click(taskButtons[0]!)
    expect(screen.getByRole('link', { name: 'Start Work Mode' })).toBeInTheDocument()
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
    expect(screen.queryByRole('button', { name: 'Start Work Mode' })).not.toBeInTheDocument()
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
    expect(screen.getByText(/is selected\. Choose an open slot/)).toHaveTextContent('Write launch narrative')
    if (clearSelectionFirst) {
      fireEvent.click(screen.getByRole('button', { name: 'Write launch narrative' }))
      expect(screen.getByRole('button', { name: 'Write launch narrative' })).toHaveAttribute('aria-pressed', 'false')
      expect(screen.queryByText(/is selected\. Choose an open slot/)).not.toBeInTheDocument()
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
      expect(screen.queryByText(/is selected\. Choose an open slot/)).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'New block' })).not.toBeInTheDocument()
      expect(screen.queryByText('Selected from Ready to Plan')).not.toBeInTheDocument()
    })

    await act(async () => {
      releaseSave()
      await removal
    })
  })

  it('consumes a direct Work Mode request during planning without clearing the selection or replaying it', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<><TodayPage /><DirectWorkRequest /></>} /></Routes></MemoryRouter>)
    const task = (await screen.findAllByRole('button', { name: /Write launch narrative/ }))[0]!
    await user.click(task)
    await user.click(screen.getByRole('button', { name: 'Request work directly' }))
    expect(task).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByRole('dialog', { name: 'Start Work Mode' })).not.toBeInTheDocument()
    await user.click(task)
    expect(screen.getByRole('link', { name: 'Start Work Mode' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Start Work Mode' })).not.toBeInTheDocument()
  })

  it('keeps a planned draft intact when the disabled Work Mode control is pressed', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    await screen.findByTestId('day-timeline')
    const lane = screen.getByTestId('day-timeline').querySelector('[data-day-lane="planned"]')!
    fireEvent.click(lane, { clientY: 190 })
    const create = await screen.findByRole('button', { name: 'Create block' })
    const work = screen.getByRole('button', { name: 'Start Work Mode' })
    expect(work).toBeDisabled()
    await user.click(work)
    expect(create).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.getByRole('link', { name: 'Start Work Mode' })).toBeInTheDocument()
  })

  it('finishes Plan something first before automatically entering Work Mode', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    await screen.findByTestId('day-timeline')
    await user.click(screen.getByRole('link', { name: 'Start Work Mode' }))
    await user.click(await screen.findByRole('button', { name: 'Plan something first' }))
    expect(screen.getByRole('button', { name: 'Start Work Mode' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Create block' }))
    expect(await screen.findByRole('dialog', { name: 'Work Mode' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create block' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Exit Work Mode' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Work Mode' })).not.toBeInTheDocument())
  })

  it.each(['cancel', 'failed save'])('does not start Work Mode after Plan something first: %s', async (outcome) => {
    const user = userEvent.setup()
    if (outcome === 'failed save') {
      const fallbackFetch = globalThis.fetch
      globalThis.fetch = vi.fn((input, init) => String(input).endsWith('/blocks') && init?.method === 'POST'
        ? Promise.resolve(jsonResponse({ detail: 'Save rejected' }, 409))
        : fallbackFetch(input, init))
    }
    render(<MemoryRouter initialEntries={['/day/2026-06-01']}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
    await screen.findByTestId('day-timeline')
    await user.click(screen.getByRole('link', { name: 'Start Work Mode' }))
    await user.click(await screen.findByRole('button', { name: 'Plan something first' }))
    if (outcome === 'cancel') {
      await user.keyboard('{Escape}')
      expect(screen.getByRole('link', { name: 'Start Work Mode' })).toBeInTheDocument()
    } else {
      await user.click(screen.getByRole('button', { name: 'Create block' }))
      await screen.findAllByText('Save rejected')
      expect(screen.getByRole('button', { name: 'Start Work Mode' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Create block' })).toBeInTheDocument()
    }
    expect(screen.queryByRole('dialog', { name: 'Work Mode' })).not.toBeInTheDocument()
  })

  it('keeps a newly created standalone Actual selected without a stale discard prompt', async () => {
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

    await user.type(within(rail).getByLabelText('Name'), 'Evening walk')
    await user.click(within(rail).getByRole('button', { name: 'Create block' }))

    const actualButton = await screen.findByRole('button', { name: 'Edit actual block' })
    await user.click(actualButton)

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(within(rail).getByLabelText('Name')).toHaveValue('Evening walk')
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
