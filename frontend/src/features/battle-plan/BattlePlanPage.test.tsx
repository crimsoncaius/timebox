import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { BattlePlanPage } from './BattlePlanPage'
import type { BattleTask, Project, Subtask, TaskType } from '../../lib/api'

const project: Project = {
  id: 7,
  name: 'Atlas',
  created_at: '2026-01-01T00:00:00Z',
  updated_at: '2026-01-01T00:00:00Z',
}

const taskType: TaskType = {
  id: 3,
  name: 'work/deep',
  created_at: '2026-01-01T00:00:00Z',
  updated_at: '2026-01-01T00:00:00Z',
  usage_count: 0,
  task_usage_count: 1,
}

function task(overrides: Partial<BattleTask> = {}): BattleTask {
  return {
    id: 11,
    parent_id: null,
    project_id: 7,
    project,
    task_type_id: 3,
    task_type: taskType,
    title: 'Draft launch brief',
    description: 'Gather the context',
    status: 'open',
    urgency: 'high',
    importance: 'medium',
    deadline_date: '2099-08-15',
    deadline_at: null,
    reminder_at: null,
    reminder_delivered_at: null,
    position: 0,
    archived_at: null,
    deleted_at: null,
    created_at: '2026-01-01T00:00:00Z',
    updated_at: '2026-01-01T00:00:00Z',
    overdue: false,
    subtasks: [],
    ...overrides,
  }
}

function response(data: unknown, status = 200) {
  return status === 204
    ? new Response(null, { status })
    : new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
}

function subtask(overrides: Partial<Subtask> = {}): Subtask {
  return { id: 21, parent_task_id: 11, title: 'Check figures', checked: false, effectively_resolved: false, position: 0, created_at: '', updated_at: '', ...overrides }
}

function shortDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${value}T12:00:00Z`))
}

function HistoryControls() {
  const navigate = useNavigate()
  return (
    <>
      <button type="button" onClick={() => void navigate(-1)}>History back</button>
      <button type="button" onClick={() => void navigate(1)}>History forward</button>
      <button type="button" onClick={() => void navigate('/battle-plan?task=12')}>Open reminder task</button>
    </>
  )
}

describe('BattlePlanPage', () => {
  const originalFetch = globalThis.fetch
  let activeTasks: BattleTask[]
  let trashTasks: BattleTask[]
  let failNextMove: boolean
  let failNextCreate: boolean
  let failNextRestore: boolean
  let failNextSubtaskCheck: boolean
  let restoreGate: Promise<void> | null
  let readyGate: Promise<void> | null

  it.each([null, 7])('moves a task from project %s and persists the assignment after remount', async (source) => {
    activeTasks = [task({ project_id: source, project: source ? project : null })]
    const user = userEvent.setup()
    const view = render(<MemoryRouter><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByLabelText('Actions for Draft launch brief'))
    await user.click(screen.getByRole('button', { name: 'Move to project' }))
    await user.selectOptions(screen.getByLabelText('Destination'), source ? '' : '7')
    await user.click(screen.getByRole('button', { name: 'Move' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(activeTasks[0].project_id).toBe(source ? null : 7)
    expect(globalThis.fetch).toHaveBeenCalledWith(expect.stringMatching(/\/tasks\/11$/), expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ project_id: source ? null : 7 }) }))
    view.unmount()
    render(<MemoryRouter><BattlePlanPage /></MemoryRouter>)
    const card = await screen.findByRole('article', { name: 'Move Draft launch brief' })
    expect(within(card).getByText(source ? 'Admin' : 'Atlas')).toBeInTheDocument()
  })

  it('cancels destination selection without saving', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByLabelText('Actions for Draft launch brief'))
    await user.click(screen.getByRole('button', { name: 'Move to project' }))
    await user.selectOptions(screen.getByLabelText('Destination'), '')
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(activeTasks[0].project_id).toBe(7)
    expect(vi.mocked(globalThis.fetch).mock.calls.some(([, init]) => init?.method === 'PATCH')).toBe(false)
  })

  it('keeps the saved assignment on failure and allows retry', async () => {
    failNextMove = true
    const user = userEvent.setup()
    render(<MemoryRouter><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByLabelText('Actions for Draft launch brief'))
    await user.click(screen.getByRole('button', { name: 'Move to project' }))
    await user.selectOptions(screen.getByLabelText('Destination'), '')
    await user.click(screen.getByRole('button', { name: 'Move' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Move failed')
    expect(activeTasks[0].project_id).toBe(7)
    expect(document.querySelector('[data-task-id="11"]')).toHaveTextContent('Atlas')
    await user.click(screen.getByRole('button', { name: 'Move' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(activeTasks[0].project_id).toBeNull()
  })

  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
    localStorage.clear()
    activeTasks = [task()]
    trashTasks = []
    failNextMove = false
    failNextCreate = false
    failNextRestore = false
    failNextSubtaskCheck = false
    restoreGate = null
    readyGate = null
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) return response({ status: 'ok', today: '2026-08-15', timezone: 'UTC' })
      if (url.endsWith('/projects') && method === 'GET') return response([project])
      if (url.endsWith('/task-types') && method === 'GET') return response([taskType])
      if (url.includes('/tasks?state=active')) {
        return response({ items: activeTasks, timezone: 'UTC', server_now_iso: '2026-08-15T12:00:00Z' })
      }
      if (url.includes('/tasks?state=trash')) {
        return response({ items: trashTasks, timezone: 'UTC', server_now_iso: '2026-08-15T12:00:00Z' })
      }
      if (url.includes('/tasks?state=archived')) {
        return response({ items: [], timezone: 'UTC', server_now_iso: '2026-08-15T12:00:00Z' })
      }
      if (url.endsWith('/tasks') && method === 'POST') {
        if (failNextCreate) {
          failNextCreate = false
          return response({ detail: 'Could not add task' }, 500)
        }
        const body = JSON.parse(String(init?.body)) as Partial<BattleTask> & { title: string }
        const created = task({
          id: 12,
          title: body.title,
          description: body.description ?? '',
          status: body.status === 'blocked' ? 'open' : body.status ?? 'open',
          is_blocked: body.is_blocked || body.status === 'blocked',
          parent_id: body.parent_id ?? null,
          project_id: body.project_id ?? null,
          project: body.project_id === project.id ? project : null,
          task_type_id: body.task_type_id ?? null,
          task_type: body.task_type_id === taskType.id ? taskType : null,
          urgency: body.urgency ?? null,
          importance: body.importance ?? null,
          deadline_date: body.deadline_date ?? null,
          deadline_at: body.deadline_at ?? null,
          reminder_at: body.reminder_at ?? null,
        })
        if (body.parent_id != null) {
          activeTasks = activeTasks.map((row) => row.id === body.parent_id
            ? { ...row, subtasks: [...row.subtasks, subtask({ id: created.id, parent_task_id: row.id, title: created.title, position: row.subtasks.length })] }
            : row)
        } else {
          activeTasks = [...activeTasks, created]
        }
        return response(created, 201)
      }
      if (/\/tasks\/\d+\/complete$/.test(url) && method === 'POST') {
        const id = Number(url.split('/').at(-2))
        let completed: BattleTask | undefined
        activeTasks = activeTasks.map((row) => {
          if (row.id === id) {
            completed = { ...row, status: 'completed', is_blocked: false }
            return completed
          }
          return row
        })
        return response({ task: completed, undo_token: `undo-${id}`, removed_planned_block_ids: [] })
      }
      if (/\/tasks\/\d+\/reopen$/.test(url) && method === 'POST') {
        const id = Number(url.split('/').at(-2))
        let reopened: BattleTask | undefined
        activeTasks = activeTasks.map((row) => {
          if (row.id === id) {
            reopened = { ...row, status: 'open' }
            return reopened
          }
          return row
        })
        return response(reopened)
      }
      if (/\/tasks\/\d+$/.test(url) && method === 'PATCH') {
        const body = JSON.parse(String(init?.body)) as Partial<BattleTask>
        if ('ready_to_plan' in body && readyGate) await readyGate
        if ('project_id' in body && failNextMove) { failNextMove = false; return response({ detail: 'Move failed' }, 500) }
        const id = Number(url.split('/').pop())
        let patched: BattleTask | undefined
        activeTasks = activeTasks.map((row) => {
          if (row.id === id) {
            patched = { ...row, ...body }
            if ('project_id' in body) patched.project = body.project_id === project.id ? project : null
            if (body.status === 'blocked') patched = { ...patched, status: 'open', is_blocked: true }
            return patched
          }
          return row
        })
        return response(patched)
      }
      if (/\/tasks\/\d+\/permanent$/.test(url) && method === 'DELETE') {
        const id = Number(url.split('/').at(-2))
        trashTasks = trashTasks.filter((row) => row.id !== id)
        return response(undefined, 204)
      }
      if (/\/tasks\/\d+$/.test(url) && method === 'DELETE') {
        const id = Number(url.split('/').pop())
        const trashed = activeTasks.find((row) => row.id === id)
        if (trashed) {
          activeTasks = activeTasks.filter((row) => row.id !== id)
          trashTasks = [...trashTasks, { ...trashed, deleted_at: '2026-08-15T12:00:00Z' }]
        } else {
          trashTasks = trashTasks.filter((row) => row.id !== id)
        }
        return response(trashed)
      }
      if (/\/tasks\/\d+\/restore$/.test(url) && method === 'POST') {
        if (restoreGate) await restoreGate
        if (failNextRestore) {
          failNextRestore = false
          return response({ detail: 'Restore is temporarily unavailable' }, 503)
        }
        const id = Number(url.split('/').at(-2))
        const restored = trashTasks.find((row) => row.id === id)
        trashTasks = trashTasks.filter((row) => row.id !== id)
        if (restored) activeTasks = [...activeTasks, { ...restored, deleted_at: null }]
        return response(undefined, 204)
      }
      const subtaskAction = /\/subtasks\/(\d+)\/(check|uncheck)$/.exec(url)
      if (subtaskAction && method === 'POST') {
        if (failNextSubtaskCheck) {
          failNextSubtaskCheck = false
          return response({ detail: 'Could not update subtask. Please retry.' }, 500)
        }
        const id = Number(subtaskAction[1])
        const checked = subtaskAction[2] === 'check'
        let updated: Subtask | undefined
        activeTasks = activeTasks.map((row) => ({ ...row, subtasks: row.subtasks.map((item) => item.id === id ? (updated = { ...item, checked, effectively_resolved: checked }) : item) }))
        return response(updated)
      }
      return response({ detail: 'not found' }, 404)
    }) as typeof fetch
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('renders the four columns and balanced task summary', async () => {
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    expect(await screen.findByText('Draft launch brief')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Open tasks' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'In progress tasks' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Blocked tasks' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Completed tasks' })).toBeInTheDocument()
    expect(screen.getAllByText('work/deep')).not.toHaveLength(0)
    expect(screen.getByText('U · high')).toBeInTheDocument()
  })

  it('shows a Ready to Plan card state before its save finishes', async () => {
    let release!: () => void
    readyGate = new Promise<void>((resolve) => { release = resolve })
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    const button = await screen.findByRole('button', { name: 'Add Draft launch brief to Ready to Plan' })

    await user.click(button)

    expect(screen.getByRole('button', { name: 'Remove Draft launch brief from Ready to Plan' })).toBeInTheDocument()
    release()
    await waitFor(() => expect(activeTasks[0].ready_to_plan).toBe(true))
  })

  it('keeps a saved blocked condition visible after reopening and reloading without changing underlying progress', async () => {
    activeTasks = [task({ status: 'in_progress', is_blocked: false })]
    const user = userEvent.setup()
    const view = render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    await user.selectOptions(await screen.findByLabelText('Status'), 'blocked')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const column = screen.getByRole('region', { name: 'Blocked tasks' })
    expect(within(column).getByRole('heading', { name: 'Draft launch brief' })).toBeInTheDocument()
    expect(activeTasks[0]).toMatchObject({ status: 'in_progress', is_blocked: true })
    expect(within(within(column).getByRole('heading', { name: 'Draft launch brief' }).closest('article')!).getByText('Blocked')).toBeInTheDocument()
    await user.click(within(column).getByRole('heading', { name: 'Draft launch brief' }))
    expect(screen.getByLabelText('Status')).toHaveValue('blocked')
    await user.type(screen.getByLabelText('Description'), ' — updated while blocked')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(activeTasks[0]).toMatchObject({ status: 'in_progress', is_blocked: true, description: 'Gather the context — updated while blocked' })
    view.unmount()
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    expect(await screen.findByLabelText('Status')).toHaveValue('blocked')
  })

  it.each(['open', 'in_progress'])('explicitly clears the blocked condition when the editor selects %s', async (status) => {
    activeTasks = [task({ is_blocked: true })]
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    expect(await screen.findByLabelText('Status')).toHaveValue('blocked')
    await user.selectOptions(screen.getByLabelText('Status'), status)
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(activeTasks[0]).toMatchObject({ status, is_blocked: false })
    expect(within(screen.getByRole('region', { name: 'Blocked tasks' })).queryByRole('heading', { name: 'Draft launch brief' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('heading', { name: 'Draft launch brief' }))
    expect(screen.getByLabelText('Status')).toHaveValue(status)
  })

  it('creates a blocked task using the independent condition returned by the API', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByLabelText('Add Blocked task'))
    await user.type(screen.getByLabelText('Task title'), 'Waiting for review')
    await user.click(screen.getByRole('button', { name: 'Add task' }))
    const column = screen.getByRole('region', { name: 'Blocked tasks' })
    expect(await within(column).findByRole('heading', { name: 'Waiting for review' })).toBeInTheDocument()
    expect(activeTasks.find((row) => row.title === 'Waiting for review')).toMatchObject({ status: 'open', is_blocked: true })
  })

  it('shows a failed subtask check inside the dialog and permits retry', async () => {
    activeTasks = [task({ subtasks: [subtask()] })]
    failNextSubtaskCheck = true
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    const dialog = await screen.findByRole('dialog', { name: 'Task details' })
    await user.click(within(dialog).getByRole('checkbox', { name: 'Check subtask Check figures' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Could not update subtask. Please retry.')
    expect(within(dialog).getByRole('checkbox', { name: 'Check subtask Check figures' })).not.toBeChecked()
    await user.click(within(dialog).getByRole('checkbox', { name: 'Check subtask Check figures' }))
    expect(await within(dialog).findByRole('checkbox', { name: 'Uncheck subtask Check figures' })).toBeChecked()
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('labels recurring Task Occurrences and Quota Trackers without marking one-off Tasks', async () => {
    activeTasks = [
      task({
        id: 11,
        title: 'Weekly planning occurrence',
        recurring_template_id: 7,
        recurring_template_title: 'Weekly planning',
        recurrence_kind: 'scheduled',
      }),
      task({
        id: 12,
        title: 'Exercise quota',
        recurring_template_id: 8,
        recurring_template_title: 'Exercise three times',
        recurrence_kind: 'quota_parent',
      }),
      task({ id: 13, title: 'One-off task' }),
      task({
        id: 14,
        title: 'Nested session task',
        parent_id: 12,
        recurring_template_id: 8,
        recurring_template_title: 'Exercise three times',
        recurrence_kind: 'quota_session',
      }),
    ]

    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)

    const occurrenceCard = (await screen.findByText('Weekly planning occurrence')).closest('article')
    const quotaCard = screen.getByText('Exercise quota').closest('article')
    const oneOffCard = screen.getByText('One-off task').closest('article')
    const sessionCard = screen.getByText('Nested session task').closest('article')
    expect(occurrenceCard).not.toBeNull()
    expect(quotaCard).not.toBeNull()
    expect(oneOffCard).not.toBeNull()
    expect(sessionCard).not.toBeNull()
    expect(within(occurrenceCard!).getByLabelText('Recurring Task Occurrence from Weekly planning')).toHaveTextContent('Recurring')
    expect(within(quotaCard!).getByLabelText('Quota Tracker from Recurring Task Series Exercise three times')).toHaveTextContent('Recurring')
    expect(within(oneOffCard!).queryByText('Recurring')).not.toBeInTheDocument()
    expect(within(sessionCard!).queryByText('Recurring')).not.toBeInTheDocument()
  })

  it('renders planned metadata before Due and keeps the planned row passive', async () => {
    const user = userEvent.setup()
    activeTasks = [task({ planned_dates: ['2026-08-14', '2026-08-15', '2026-08-17'] })]
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)

    const planned = await screen.findByLabelText(`Planned Today · ${shortDate('2026-08-15')} +2`)
    const due = screen.getByText(shortDate('2099-08-15'))
    expect(planned.compareDocumentPosition(due) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    await user.click(planned)
    expect(screen.getByRole('dialog', { name: 'Task details' })).toBeInTheDocument()
  })

  it('shows five planned dates in details, expands inline, and links every date to Day', async () => {
    const user = userEvent.setup()
    activeTasks = [task({
      planned_dates: [
        '2026-08-13', '2026-08-14', '2026-08-15', '2026-08-16',
        '2026-08-17', '2026-08-18', '2026-08-19',
      ],
    })]
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)

    const section = await screen.findByRole('region', { name: 'Planned Dates' })
    expect(within(section).getAllByRole('link')).toHaveLength(5)
    expect(within(section).getByRole('link', { name: `Today · ${shortDate('2026-08-15')}` })).toHaveAttribute('href', '/day/2026-08-15')
    expect(within(section).getByRole('button', { name: 'Show all (7)' })).toBeInTheDocument()

    await user.click(within(section).getByRole('button', { name: 'Show all (7)' }))
    expect(within(section).getAllByRole('link')).toHaveLength(7)
    expect(within(section).getByRole('button', { name: 'Show less' })).toBeInTheDocument()
  })

  it('toggles Ready to Plan without changing work status', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Add Draft launch brief to Ready to Plan' }))

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/tasks\/11$/),
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ ready_to_plan: true }),
      }),
    ))
    expect(await screen.findByRole('button', { name: 'Remove Draft launch brief from Ready to Plan' })).toBeInTheDocument()
    expect(activeTasks[0]?.status).toBe('open')
  })

  it('quick-adds an Admin task from All Tasks', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByLabelText('Add Open task'))
    await user.type(screen.getByLabelText('Task title'), 'Pay invoice{Enter}')
    expect(await screen.findByText('Pay invoice')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/tasks'),
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('"project_id":null'),
      }),
    )
  })

  it('keeps task edits local until Save and commits the full task draft', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByText('Draft launch brief'))
    expect(screen.getByRole('dialog', { name: 'Task details' })).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Status'), 'blocked')
    await user.click(within(screen.getByRole('radiogroup', { name: 'Urgency' })).getByRole('radio', { name: 'Low' }))
    expect(globalThis.fetch).not.toHaveBeenCalledWith(
      expect.stringMatching(/\/tasks\/11$/),
      expect.objectContaining({ method: 'PATCH' }),
    )
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/tasks\/11$/),
      expect.objectContaining({
        method: 'PATCH',
        body: expect.stringMatching(/"is_blocked":true.*"urgency":"low"/),
      }),
    ))
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Task details' })).not.toBeInTheDocument()
    })
  })

  it('guards unsaved edits and restores focus after the dialog closes', async () => {
    const user = userEvent.setup()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    const card = await screen.findByRole('button', { name: 'Move Draft launch brief' })
    card.focus()
    await user.keyboard('{Enter}')
    await user.type(screen.getByLabelText('Title'), ' updated')

    await user.click(screen.getByRole('button', { name: 'Close task details' }))
    expect(screen.getByRole('dialog', { name: 'Task details' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Close task details' }))

    expect(confirm).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('dialog', { name: 'Task details' })).not.toBeInTheDocument()
    expect(card).toHaveFocus()
  })

  it('opens task details from the full-card keyboard target', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)

    const card = await screen.findByRole('button', { name: 'Move Draft launch brief' })
    card.focus()
    await user.keyboard('{Enter}')

    expect(screen.getByRole('dialog', { name: 'Task details' })).toBeInTheDocument()
  })

  it('keeps task details synchronized with browser history', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/battle-plan']}>
        <BattlePlanPage />
        <HistoryControls />
      </MemoryRouter>,
    )

    await user.click(await screen.findByText('Draft launch brief'))
    expect(screen.getByRole('dialog', { name: 'Task details' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'History back' }))
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Task details' })).not.toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: 'History forward' }))
    expect(await screen.findByRole('dialog', { name: 'Task details' })).toBeInTheDocument()
  })

  it('keeps the selected collection synchronized with browser history', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/battle-plan']}>
        <BattlePlanPage />
        <HistoryControls />
      </MemoryRouter>,
    )

    await screen.findByText('Draft launch brief')
    await user.click(screen.getByRole('button', { name: 'Archive' }))
    expect(await screen.findByRole('heading', { name: 'Archive' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'History back' }))
    expect(await screen.findByRole('heading', { name: 'All Tasks' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'History forward' }))
    expect(await screen.findByRole('heading', { name: 'Archive' })).toBeInTheDocument()
  })

  it('opens the requested task when in-app navigation changes the query', async () => {
    const user = userEvent.setup()
    activeTasks = [
      task(),
      task({ id: 12, title: 'Follow up from reminder', position: 1 }),
    ]
    render(
      <MemoryRouter initialEntries={['/battle-plan?task=11']}>
        <BattlePlanPage />
        <HistoryControls />
      </MemoryRouter>,
    )

    expect(await screen.findByLabelText('Title')).toHaveValue('Draft launch brief')
    await user.click(screen.getByRole('button', { name: 'Open reminder task' }))
    await waitFor(() => {
      expect(screen.getByLabelText('Title')).toHaveValue('Follow up from reminder')
    })
  })

  it('names the trashed Battle Plan Task in one actionable polite notice', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))

    const notice = await screen.findByRole('status', { name: 'Trash undo' })
    expect(notice).toHaveTextContent('Draft launch brief moved to Trash')
    expect(within(notice).getByRole('button', { name: 'Undo' })).toBeEnabled()
    expect(within(notice).getByRole('button', { name: 'Dismiss' })).toBeEnabled()
    expect(screen.getAllByText(/moved to Trash/i)).toHaveLength(1)
  })

  it('consumes the web Undo opportunity after ten seconds and fades for 150 ms', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    const trash = await screen.findByRole('button', { name: 'Move to Trash' })
    vi.useFakeTimers()
    await act(async () => {
      fireEvent.click(trash)
      await Promise.resolve()
      await Promise.resolve()
    })
    const notice = screen.getByRole('status', { name: 'Trash undo' })

    await act(async () => { vi.advanceTimersByTime(10_000) })
    expect(within(notice).getByRole('button', { name: 'Undo' })).toBeDisabled()
    expect(notice).toHaveClass('opacity-0')

    await act(async () => { vi.advanceTimersByTime(150) })
    expect(screen.queryByRole('status', { name: 'Trash undo' })).not.toBeInTheDocument()
  })

  it('counts only visible, unhovered, unfocused web exposure', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    const trash = await screen.findByRole('button', { name: 'Move to Trash' })
    vi.useFakeTimers()
    await act(async () => {
      fireEvent.click(trash)
      await Promise.resolve()
      await Promise.resolve()
    })
    const notice = screen.getByRole('status', { name: 'Trash undo' })
    const undo = within(notice).getByRole('button', { name: 'Undo' })

    await act(async () => { vi.advanceTimersByTime(4_000) })
    fireEvent.mouseEnter(notice)
    await act(async () => { vi.advanceTimersByTime(20_000) })
    expect(undo).toBeEnabled()
    fireEvent.mouseLeave(notice)
    await act(async () => { vi.advanceTimersByTime(2_000) })

    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    fireEvent(document, new Event('visibilitychange'))
    await act(async () => { vi.advanceTimersByTime(20_000) })
    expect(undo).toBeEnabled()
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    fireEvent(document, new Event('visibilitychange'))
    await act(async () => { vi.advanceTimersByTime(2_000) })

    fireEvent.focus(undo)
    await act(async () => { vi.advanceTimersByTime(20_000) })
    expect(undo).toBeEnabled()
    fireEvent.blur(undo, { relatedTarget: document.body })
    await act(async () => { vi.advanceTimersByTime(2_000) })
    expect(undo).toBeDisabled()
  })

  it('removes an expired web notice immediately when reduced motion is requested', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    const trash = await screen.findByRole('button', { name: 'Move to Trash' })
    vi.useFakeTimers()
    await act(async () => {
      fireEvent.click(trash)
      await Promise.resolve()
      await Promise.resolve()
    })

    await act(async () => { vi.advanceTimersByTime(10_000) })

    expect(screen.queryByRole('status', { name: 'Trash undo' })).not.toBeInTheDocument()
  })

  it('starts exactly one restore and exposes progress until it succeeds', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let releaseRestore = () => {}
    restoreGate = new Promise<void>((resolve) => { releaseRestore = resolve })
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))
    const notice = await screen.findByRole('status', { name: 'Trash undo' })
    const undo = within(notice).getByRole('button', { name: 'Undo' })

    fireEvent.click(undo)
    fireEvent.click(undo)

    expect(within(notice).getByRole('button', { name: 'Restoring Draft launch brief' })).toBeDisabled()
    expect(within(notice).getByRole('button', { name: 'Dismiss' })).toBeDisabled()
    expect(vi.mocked(globalThis.fetch).mock.calls.filter(([input]) => String(input).includes('/tasks/11/restore'))).toHaveLength(1)

    releaseRestore()
    await waitFor(() => expect(screen.queryByRole('status', { name: 'Trash undo' })).not.toBeInTheDocument())
  })

  it('keeps a failed restore available for Retry or Dismiss', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))
    failNextRestore = true
    await user.click(within(screen.getByRole('status', { name: 'Trash undo' })).getByRole('button', { name: 'Undo' }))

    const failure = await screen.findByRole('alert', { name: 'Trash undo failed' })
    expect(failure).toHaveTextContent('Could not restore Draft launch brief')
    expect(failure).toHaveTextContent('Restore is temporarily unavailable')
    await user.click(within(failure).getByRole('button', { name: 'Retry' }))

    await waitFor(() => expect(screen.queryByLabelText('Trash undo')).not.toBeInTheDocument())
  })

  it('keeps a newer Trash notice when an older Undo finishes', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    activeTasks = [task(), task({ id: 12, title: 'Follow up from reminder', position: 1 })]
    let releaseRestore = () => {}
    restoreGate = new Promise<void>((resolve) => { releaseRestore = resolve })
    render(
      <MemoryRouter initialEntries={['/battle-plan?task=11']}>
        <BattlePlanPage />
        <HistoryControls />
      </MemoryRouter>,
    )
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))
    fireEvent.click(within(await screen.findByRole('status', { name: 'Trash undo' })).getByRole('button', { name: 'Undo' }))

    await user.click(screen.getByRole('button', { name: 'Open reminder task' }))
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))
    expect(await screen.findByRole('status', { name: 'Trash undo' })).toHaveTextContent('Follow up from reminder moved to Trash')

    releaseRestore()
    await act(async () => { await restoreGate })

    expect(screen.getByRole('status', { name: 'Trash undo' })).toHaveTextContent('Follow up from reminder moved to Trash')
  })

  it('invalidates a matching notice when the Task is restored from Trash', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))
    await user.click(screen.getByRole('button', { name: 'Trash' }))
    const trashedTask = await screen.findByText('Draft launch brief')

    await user.click(within(trashedTask.closest('article')!).getByRole('button', { name: 'Restore' }))

    expect(screen.queryByLabelText('Trash undo')).not.toBeInTheDocument()
  })

  it('invalidates a matching notice when the Task is permanently deleted', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))
    await user.click(screen.getByRole('button', { name: 'Trash' }))
    const trashedTask = await screen.findByText('Draft launch brief')

    await user.click(within(trashedTask.closest('article')!).getByRole('button', { name: 'Delete permanently' }))

    expect(screen.queryByLabelText('Trash undo')).not.toBeInTheDocument()
  })

  it('Dismiss removes the notice without restoring the Task', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))

    await user.click(within(await screen.findByRole('status', { name: 'Trash undo' })).getByRole('button', { name: 'Dismiss' }))

    expect(screen.queryByLabelText('Trash undo')).not.toBeInTheDocument()
    expect(vi.mocked(globalThis.fetch).mock.calls.some(([input]) => String(input).includes('/tasks/11/restore'))).toBe(false)
  })

  it('preserves the notice within Battle Plan and discards it after leaving', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(
      <MemoryRouter initialEntries={['/battle-plan?task=11']}>
        <HistoryControls />
        <Routes>
          <Route path="/battle-plan" element={<BattlePlanPage />} />
          <Route path="/day/:date" element={<p>Day page</p>} />
        </Routes>
      </MemoryRouter>,
    )
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))
    await user.click(screen.getByRole('button', { name: 'Archive' }))
    expect(screen.getByRole('status', { name: 'Trash undo' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Day' }))
    expect(await screen.findByText('Day page')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'History back' }))

    await screen.findByRole('heading', { name: 'Archive' })
    expect(screen.queryByLabelText('Trash undo')).not.toBeInTheDocument()
  })

  it('does not persist the notice across a page reload', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const page = render(<MemoryRouter initialEntries={['/battle-plan?task=11']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByRole('button', { name: 'Move to Trash' }))
    expect(await screen.findByLabelText('Trash undo')).toBeInTheDocument()

    page.unmount()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)

    await screen.findByRole('heading', { name: 'All Tasks' })
    expect(screen.queryByLabelText('Trash undo')).not.toBeInTheDocument()
  })

  it('keeps task status dragging enabled when tasks are sorted by deadline', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    await screen.findByText('Draft launch brief')

    await user.selectOptions(screen.getByLabelText('Sort tasks'), 'deadline')

    expect(screen.getByText('Draft launch brief').closest('article')).toHaveAttribute(
      'title',
      'Drag task to change its status',
    )
  })

  it('creates a richly configured task and renders its relative deadline', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByLabelText('Add Open task'))
    const composer = screen.getByRole('form', { name: 'New task' })
    await user.type(within(composer).getByLabelText('Task title'), 'Prepare review #Atlas !low ~medium')
    await user.type(within(composer).getByLabelText('Task description'), 'Bring the draft')
    expect(within(composer).getByTitle('Location: Atlas')).toBeInTheDocument()
    expect(within(composer).getByText('Low')).toBeInTheDocument()
    expect(within(composer).getByText('Medium')).toBeInTheDocument()
    await user.click(within(composer).getByRole('button', { name: 'Type' }))
    await user.click(within(composer).getByRole('menuitemradio', { name: 'work/deep' }))
    await user.click(within(composer).getByRole('button', { name: 'Urgency' }))
    await user.click(within(composer).getByRole('menuitemradio', { name: 'High' }))
    await user.click(within(composer).getByRole('button', { name: 'Due' }))
    await user.click(within(composer).getByRole('menuitemradio', { name: 'Today' }))
    await user.click(within(composer).getByRole('button', { name: 'Add task' }))

    expect(await screen.findByText('Prepare review')).toBeInTheDocument()
    expect(screen.getByText('Today')).toBeInTheDocument()
    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/tasks'),
      expect.objectContaining({
        method: 'POST',
        body: expect.stringMatching(/"project_id":7.*"task_type_id":3.*"urgency":"high".*"importance":"medium".*"deadline_date":"2026-08-15"/),
      }),
    )
  })

  it('keeps a failed task draft open and supports Escape cancellation', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)
    await user.click(await screen.findByLabelText('Add Open task'))
    const title = screen.getByLabelText('Task title')
    await user.type(title, 'Retry me')
    failNextCreate = true
    await user.click(screen.getByRole('button', { name: 'Add task' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not add task')
    expect(screen.getByLabelText('Task title')).toHaveValue('Retry me')
    await user.type(screen.getByLabelText('Task title'), '{Escape}')
    expect(screen.queryByRole('form', { name: 'New task' })).not.toBeInTheDocument()
  })

  it('expands, toggles, and adds subtasks without opening task details', async () => {
    const user = userEvent.setup()
    activeTasks = [task({
      deadline_date: '2026-08-15',
      subtasks: [subtask()],
    })]
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: '0 of 1 subtasks completed for Draft launch brief' }))
    screen.getByRole('region', { name: 'Subtasks for Draft launch brief' })
    expect(screen.getByText('Today')).toBeInTheDocument()

    await user.click(screen.getByLabelText('Check subtask Check figures'))
    await waitFor(() => expect(screen.getByRole('button', { name: '1 of 1 subtasks completed for Draft launch brief' })).toBeInTheDocument())
    expect(screen.queryByRole('dialog', { name: 'Task details' })).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('New subtask for Draft launch brief'), 'Send notes')
    await user.click(screen.getByLabelText('Add subtask to Draft launch brief'))
    expect(await screen.findByText('Send notes')).toBeInTheDocument()
  })
})
