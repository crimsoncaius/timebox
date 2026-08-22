import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { BattlePlanPage } from './BattlePlanPage'
import type { BattleTask, Project, TaskType } from '../../lib/api'

const project: Project = {
  id: 7,
  name: 'Atlas',
  description: 'Longer work',
  deadline_date: null,
  deadline_at: null,
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
  let failNextCreate: boolean

  beforeEach(() => {
    localStorage.clear()
    activeTasks = [task()]
    failNextCreate = false
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      const method = init?.method ?? 'GET'
      if (url.includes('/health')) return response({ status: 'ok', today: '2026-08-15', timezone: 'UTC' })
      if (url.endsWith('/projects') && method === 'GET') return response([project])
      if (url.endsWith('/task-types') && method === 'GET') return response([taskType])
      if (url.includes('/tasks?state=active')) {
        return response({ items: activeTasks, timezone: 'UTC', server_now_iso: '2026-08-15T12:00:00Z' })
      }
      if (url.includes('/tasks?state=archived') || url.includes('/tasks?state=trash')) {
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
          status: body.status ?? 'open',
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
            ? { ...row, subtasks: [...row.subtasks, { ...created, project_id: row.project_id, project: row.project }] }
            : row)
        } else {
          activeTasks = [...activeTasks, created]
        }
        return response(created, 201)
      }
      if (/\/tasks\/\d+$/.test(url) && method === 'PATCH') {
        const body = JSON.parse(String(init?.body)) as Partial<BattleTask>
        const id = Number(url.split('/').pop())
        let patched: BattleTask | undefined
        activeTasks = activeTasks.map((row) => {
          if (row.id === id) {
            patched = { ...row, ...body }
            return patched
          }
          const subtasks = row.subtasks.map((subtask) => {
            if (subtask.id !== id) return subtask
            patched = { ...subtask, ...body }
            return patched
          })
          return { ...row, subtasks }
        })
        return response(patched)
      }
      return response({ detail: 'not found' }, 404)
    }) as typeof fetch
  })

  afterEach(() => {
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
        body: expect.stringMatching(/"status":"blocked".*"urgency":"low"/),
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
      subtasks: [task({ id: 21, parent_id: 11, title: 'Check figures', status: 'in_progress', deadline_date: '2026-08-16', planned_dates: ['2026-08-16'], subtasks: [] })],
    })]
    render(<MemoryRouter initialEntries={['/battle-plan']}><BattlePlanPage /></MemoryRouter>)

    await user.click(await screen.findByRole('button', { name: '0 of 1 subtasks completed for Draft launch brief' }))
    const checklist = screen.getByRole('region', { name: 'Subtasks for Draft launch brief' })
    expect(within(checklist).getByText('In progress')).toBeInTheDocument()
    expect(screen.getByText('Today')).toBeInTheDocument()
    expect(within(checklist).getByText('Tomorrow')).toBeInTheDocument()
    expect(within(checklist).getByLabelText(`Planned Tomorrow · ${shortDate('2026-08-16')}`)).toBeInTheDocument()

    await user.click(screen.getByLabelText('Complete subtask Check figures'))
    await waitFor(() => expect(screen.getByRole('button', { name: '1 of 1 subtasks completed for Draft launch brief' })).toBeInTheDocument())
    expect(screen.queryByRole('dialog', { name: 'Task details' })).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('New subtask for Draft launch brief'), 'Send notes')
    await user.click(screen.getByLabelText('Add subtask to Draft launch brief'))
    expect(await screen.findByText('Send notes')).toBeInTheDocument()
  })
})
