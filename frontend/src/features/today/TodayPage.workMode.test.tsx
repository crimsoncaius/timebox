import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TodayPage } from './TodayPage'

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
}

const taskType = { id: 1, name: 'Deep work', created_at: '', updated_at: '' }
const task = {
  id: 77, title: 'Write launch narrative', description: 'Keep the argument concise.',
  task_type_id: 1, task_type: taskType, status: 'open', subtasks: [
    { id: 701, parent_task_id: 77, title: 'Check evidence', checked: false, effectively_resolved: false, position: 0, created_at: '', updated_at: '' },
  ], session_tasks: [],
}

function dayWithBlock(startMinute: number, endMinute: number) {
  return {
    id: 1, date: '2026-06-01', start_hour: 8, end_hour: 20, show_full_day: false,
    created_at: '', updated_at: '',
    meta: { timezone: 'UTC', today: '2026-06-01', server_now_iso: '2026-06-01T12:00:00Z' },
    time_blocks: [{
      id: 10, lane: 'planned', task_type_id: 1, task_type: taskType, task_id: 77,
      task: { id: 77, title: task.title, status: 'open', task_type_id: 1 }, note: 'Block note',
      start_minute: startMinute, end_minute: endMinute, created_at: '', updated_at: '',
    }],
    actual_blocks: [],
  }
}

describe('TodayPage present-tense Work Mode', () => {
  const originalFetch = globalThis.fetch
  let day = dayWithBlock(720, 780)
  let activeActual: Record<string, unknown> | null = null

  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date('2026-06-01T12:00:00Z'))
    day = dayWithBlock(720, 780)
    activeActual = null
    globalThis.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.includes('/days/2026-06-01')) return Promise.resolve(jsonResponse(day))
      if (url.includes('/task-types')) return Promise.resolve(jsonResponse([taskType]))
      if (url.includes('/tasks?state=active')) return Promise.resolve(jsonResponse({ items: [task], timezone: 'UTC', server_now_iso: day.meta.server_now_iso }))
      if (url.includes('/actual-blocks/active')) return Promise.resolve(jsonResponse(activeActual))
      if (url.includes('/actual-blocks/start') && method === 'POST') return Promise.resolve(jsonResponse({
        id: 40, task_type_id: 1, task_type: taskType, task_id: 77, task: day.time_blocks[0].task,
        note: 'Block note', planned_block_id: 10, start_at: '2026-06-01T12:00:00Z', end_at: null,
        created_at: '', updated_at: '',
      }, 201))
      if (url.includes('/actual-blocks/40') && method === 'PATCH') return Promise.resolve(jsonResponse({ id: 40, end_at: '2026-06-01T12:01:00Z' }))
      if (url.endsWith('/actual-blocks') && method === 'POST') return Promise.resolve(jsonResponse({ id: 41, end_at: '2026-06-01T12:01:00Z' }, 201))
      if (url.includes('/health')) return Promise.resolve(jsonResponse({ status: 'ok', today: '2026-06-01', timezone: 'UTC' }))
      return Promise.resolve(new Response('not found', { status: 404 }))
    }) as typeof fetch
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  function renderAt(path = '/day/2026-06-01?workMode=start') {
    return render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/day/:date" element={<TodayPage />} /></Routes></MemoryRouter>)
  }

  const renderStart = () => renderAt()

  it('enters on current planned work, confirms for one minute, then exits without completing the Task', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderStart()

    expect(await screen.findByRole('dialog', { name: 'Work Mode' })).toBeVisible()
    expect(screen.getByRole('heading', { name: task.title })).toBeVisible()
    expect(screen.getByText('Deep work')).toBeVisible()
    expect(screen.getByText(task.description)).toBeVisible()
    expect(screen.queryByText(/complete Task/i)).not.toBeInTheDocument()
    expect(vi.mocked(globalThis.fetch).mock.calls.some(([input]) => String(input).includes('/actual-blocks/start'))).toBe(false)

    act(() => { vi.advanceTimersByTime(60_000) })
    await waitFor(() => {
      const start = vi.mocked(globalThis.fetch).mock.calls.find(([input]) => String(input).includes('/actual-blocks/start'))
      expect(JSON.parse(String(start?.[1]?.body))).toMatchObject({ planned_block_id: 10, start_at: '2026-06-01T12:00:00.000Z' })
    })

    await user.click(screen.getByRole('button', { name: 'Exit Work Mode' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Work Mode' })).not.toBeInTheDocument())
    expect(vi.mocked(globalThis.fetch).mock.calls.some(([input]) => String(input).includes('/tasks/77/complete'))).toBe(false)
  })

  it('shows a block exactly ten minutes away as Up next without warning', async () => {
    day = dayWithBlock(730, 780)
    renderStart()

    expect(await screen.findByRole('dialog', { name: 'Work Mode' })).toBeVisible()
    expect(screen.getByText('Up next')).toBeVisible()
    expect(screen.queryByRole('dialog', { name: 'Start Work Mode' })).not.toBeInTheDocument()
  })

  it('warns when planned work is more than ten minutes away and still allows continuing', async () => {
    day = dayWithBlock(731, 780)
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderStart()

    const guard = await screen.findByRole('dialog', { name: 'Start Work Mode' })
    expect(guard).toHaveTextContent('no planned work for the immediate future')
    await user.click(screen.getByRole('button', { name: 'Continue' }))
    expect(await screen.findByRole('dialog', { name: 'Work Mode' })).toBeVisible()
    expect(screen.getByText('Up next')).toBeVisible()
  })

  it('exits before confirmation without creating an Actual Block', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderStart()
    await screen.findByRole('dialog', { name: 'Work Mode' })
    act(() => { vi.advanceTimersByTime(59_000) })
    await user.click(screen.getByRole('button', { name: 'Exit Work Mode' }))

    expect(vi.mocked(globalThis.fetch).mock.calls.some(([input]) => String(input).includes('/actual-blocks/start'))).toBe(false)
  })

  it('returns around an existing standalone Actual without starting a conflicting Actual', async () => {
    activeActual = {
      id: 55, task_type_id: 1, task_type: taskType, task_id: 77,
      task: { id: 77, title: 'Existing active work', status: 'open', task_type_id: 1 },
      note: 'Already underway', planned_block_id: null,
      start_at: '2026-06-01T11:45:00Z', end_at: null, created_at: '', updated_at: '',
    }
    renderStart()

    expect(await screen.findByRole('heading', { name: 'Existing active work' })).toBeVisible()
    act(() => { vi.advanceTimersByTime(61_000) })
    await waitFor(() => expect(screen.getByText('Actual recording live')).toBeVisible())
    expect(vi.mocked(globalThis.fetch).mock.calls.some(([input]) => String(input).includes('/actual-blocks/start'))).toBe(false)
  })

  it('reloads an active standalone Actual after a plain refresh', async () => {
    activeActual = {
      id: 55, task_type_id: 1, task_type: taskType, task_id: 77,
      task: { id: 77, title: 'Existing active work', status: 'open', task_type_id: 1 },
      note: 'Already underway', planned_block_id: null,
      start_at: '2026-06-01T11:45:00Z', end_at: null, created_at: '', updated_at: '',
    }
    const first = renderStart()
    await screen.findByRole('heading', { name: 'Existing active work' })
    first.unmount()

    renderAt('/day/2026-06-01')
    expect(await screen.findByRole('heading', { name: 'Existing active work' })).toBeVisible()
    expect(screen.getByText('Actual recording live')).toBeVisible()
  })

  it('records a confirmed block that ends while the Day page is unmounted', async () => {
    day = dayWithBlock(720, 721)
    const first = renderStart()
    await screen.findByRole('dialog', { name: 'Work Mode' })
    await screen.findByText('Confirming current work…')
    act(() => { vi.advanceTimersByTime(10_000) })
    first.unmount()

    act(() => { vi.advanceTimersByTime(60_000) })
    day = { ...day, meta: { ...day.meta, server_now_iso: '2026-06-01T12:01:10Z' } }
    renderStart()

    await waitFor(() => {
      const create = vi.mocked(globalThis.fetch).mock.calls.find(([input, init]) =>
        String(input).endsWith('/actual-blocks') && init?.method === 'POST')
      expect(JSON.parse(String(create?.[1]?.body))).toMatchObject({
        planned_block_id: 10,
        start_at: '2026-06-01T12:00:00.000Z',
        end_at: '2026-06-01T12:01:00.000Z',
      })
    })
  })

  it('ends at an exact boundary and begins fresh confirmation for an adjacent block', async () => {
    day = dayWithBlock(720, 722)
    day.time_blocks.push({ ...day.time_blocks[0], id: 11, start_minute: 722, end_minute: 724 })
    renderStart()
    await screen.findByText('Confirming current work…')

    act(() => { vi.advanceTimersByTime(60_000) })
    await waitFor(() => expect(
      vi.mocked(globalThis.fetch).mock.calls.some(([input]) => String(input).includes('/actual-blocks/start')),
    ).toBe(true))
    act(() => { vi.advanceTimersByTime(60_000) })

    await waitFor(() => {
      const end = vi.mocked(globalThis.fetch).mock.calls.find(([input, init]) =>
        String(input).includes('/actual-blocks/40') && init?.method === 'PATCH')
      expect(JSON.parse(String(end?.[1]?.body))).toEqual({ end_at: '2026-06-01T12:02:00.000Z' })
      expect(JSON.parse(String(localStorage.getItem('timebox.work-mode.v2'))).confirmingPlannedBlockId).toBe(11)
    })
  })

  it('stays open in the no-work state after the final block boundary', async () => {
    day = dayWithBlock(720, 722)
    renderStart()
    await screen.findByText('Confirming current work…')
    act(() => { vi.advanceTimersByTime(120_000) })

    expect(await screen.findByRole('heading', { name: 'No more planned work today' })).toBeVisible()
    expect(screen.getByRole('dialog', { name: 'Work Mode' })).toBeVisible()
  })

  it('does not record when a delayed check includes less than one uninterrupted minute before the boundary', async () => {
    day = dayWithBlock(720, 721)
    day = { ...day, meta: { ...day.meta, server_now_iso: '2026-06-01T12:01:10Z' } }
    localStorage.setItem('timebox.work-mode.v2', JSON.stringify({
      entryAt: '2026-06-01T12:00:50Z',
      lastConfirmedAt: '2026-06-01T12:00:50Z',
      lastObservedAt: '2026-06-01T12:01:10Z',
      confirmingPlannedBlockId: 10,
      confirmationStartedAt: '2026-06-01T12:00:50Z',
      activeActualId: null,
      activePlannedBlockId: null,
      activePlannedEndAt: null,
    }))
    renderAt('/day/2026-06-01')

    await waitFor(() => expect(
      JSON.parse(String(localStorage.getItem('timebox.work-mode.v2'))).confirmingPlannedBlockId,
    ).toBeNull())
    expect(vi.mocked(globalThis.fetch).mock.calls.some(([input, init]) =>
      init?.method === 'POST' && String(input).includes('/actual-blocks'))).toBe(false)
  })

  it('accepts long-absence recovery without duplicating the block whose active Actual reached its boundary', async () => {
    day = dayWithBlock(720, 750)
    day = { ...day, meta: { ...day.meta, server_now_iso: '2026-06-01T12:40:00Z' } }
    activeActual = {
      id: 40, task_type_id: 1, task_type: taskType, task_id: 77, task: day.time_blocks[0].task,
      note: 'Block note', planned_block_id: 10, start_at: '2026-06-01T12:00:00Z', end_at: null,
      created_at: '', updated_at: '',
    }
    localStorage.setItem('timebox.work-mode.v2', JSON.stringify({
      entryAt: '2026-06-01T12:00:00Z',
      lastConfirmedAt: '2026-06-01T12:10:00Z',
      lastObservedAt: '2026-06-01T12:20:00Z',
      confirmingPlannedBlockId: null,
      confirmationStartedAt: null,
      activeActualId: 40,
      activePlannedBlockId: 10,
      activePlannedEndAt: '2026-06-01T12:30:00Z',
    }))
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    renderAt('/day/2026-06-01')

    await user.click(await screen.findByRole('button', { name: 'Yes, I continued' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Restore Work Mode' })).not.toBeInTheDocument())
    const boundaryPatch = vi.mocked(globalThis.fetch).mock.calls.find(([input, init]) =>
      String(input).includes('/actual-blocks/40') && init?.method === 'PATCH')
    expect(JSON.parse(String(boundaryPatch?.[1]?.body))).toEqual({ end_at: '2026-06-01T12:30:00Z' })
    expect(vi.mocked(globalThis.fetch).mock.calls.some(([input, init]) =>
      String(input).endsWith('/actual-blocks') && init?.method === 'POST')).toBe(false)
  })
})
