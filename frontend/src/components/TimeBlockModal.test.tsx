import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import type { DayRead, TaskType, TimeBlock } from '../lib/api'
import { TimeBlockModal } from './TimeBlockModal'
import { TimeBlockInspectorContent } from './TimeBlockInspectorContent'

const taskTypes: TaskType[] = [
  { id: 1, name: 'work', created_at: '', updated_at: '' },
  { id: 2, name: 'break', created_at: '', updated_at: '' },
]

async function noopCreate(path: string): Promise<TaskType> {
  return { id: 99, name: path, created_at: '', updated_at: '' }
}

function makeBlock(overrides: Partial<TimeBlock> = {}): TimeBlock {
  return {
    id: 10,
    lane: 'planned',
    task_type_id: 1,
    task_type: taskTypes[0]!,
    note: null,
    start_minute: 510,
    end_minute: 600,
    created_at: '',
    updated_at: '',
    ...overrides,
  }
}

const emptyDay: DayRead = {
  id: 1,
  date: '2026-06-01',
  start_hour: 8,
  end_hour: 20,
  show_full_day: false,
  created_at: '',
  updated_at: '',
  time_blocks: [],
  actual_blocks: [],
  meta: { timezone: 'UTC', today: '2026-06-01', server_now_iso: '2026-06-01T12:00:00Z' },
}

describe('TimeBlockModal', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows read-only start and end as HH:MM', () => {
    render(
      <MemoryRouter>
      <TimeBlockModal
        open
        block={makeBlock()}
        draft={null}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={vi.fn()}
        onDelete={vi.fn()}
        onCreateTaskTypePath={noopCreate}
      />
      </MemoryRouter>,
    )
    expect(screen.getByText('08:30')).toBeInTheDocument()
    expect(screen.getByText('10:00')).toBeInTheDocument()
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument()
  })

  it('keeps the note field tall enough to show its placeholder', () => {
    render(
      <TimeBlockModal
        open
        block={makeBlock()}
        draft={null}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={vi.fn()}
        onDelete={vi.fn()}
        onCreateTaskTypePath={noopCreate}
      />,
    )

    const noteField = screen.getByLabelText('Note')
    expect(noteField).toHaveAttribute('rows', '4')
    expect(noteField).toHaveClass('min-h-20')
  })

  it('auto-saves only task_type_id on task type change, not time fields', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockResolvedValue(undefined)
    render(
      <TimeBlockModal
        open
        block={makeBlock({ note: 'old' })}
        draft={null}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={onSave}
        onDelete={vi.fn()}
        onCreateTaskTypePath={noopCreate}
      />,
    )
    await user.click(screen.getByLabelText('Task type'))
    await user.clear(screen.getByLabelText('Task type'))
    await user.click(screen.getByRole('option', { name: /^break$/i }))
    await waitFor(() => {
      expect(onSave).toHaveBeenCalledWith({ task_type_id: 2 })
    })
  })

  it('creates a missing task type path from the modal and saves only task_type_id', async () => {
    const user = userEvent.setup()
    const onCreateTaskTypePath = vi.fn().mockResolvedValue({
      id: 7,
      name: 'coding/personal',
      created_at: '',
      updated_at: '',
    })
    const onSave = vi.fn().mockResolvedValue(undefined)
    render(
      <TimeBlockModal
        open
        block={makeBlock()}
        draft={null}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={onSave}
        onDelete={vi.fn()}
        onCreateTaskTypePath={onCreateTaskTypePath}
      />,
    )

    await user.clear(screen.getByLabelText('Task type'))
    await user.type(screen.getByLabelText('Task type'), 'coding/personal')
    await user.click(screen.getByRole('option', { name: /create "coding\/personal"/i }))

    expect(onCreateTaskTypePath).toHaveBeenCalledWith('coding/personal')
    await waitFor(() => {
      expect(onSave).toHaveBeenCalledWith({ task_type_id: 7 })
    })
  })

  it('does not let a second responsive inspector overwrite a task type selection', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockResolvedValue(undefined)
    const sharedProps = {
      draft: null,
      day: emptyDay,
      taskTypes,
      onClose: vi.fn(),
      onSave,
      onDelete: vi.fn().mockResolvedValue(undefined),
      onCreateTaskTypePath: noopCreate,
    }
    const { rerender } = render(
      <MemoryRouter>
        <TimeBlockInspectorContent variant="rail" block={makeBlock()} {...sharedProps} />
        <TimeBlockInspectorContent variant="sheet" block={makeBlock()} {...sharedProps} />
      </MemoryRouter>,
    )

    const firstTaskType = screen.getAllByRole('combobox')[0]!
    await user.click(firstTaskType)
    await user.clear(firstTaskType)
    await user.click(screen.getByRole('option', { name: /^break$/i }))
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ task_type_id: 2 }))

    const updated = makeBlock({ task_type_id: 2, task_type: taskTypes[1]! })
    rerender(
      <MemoryRouter>
        <TimeBlockInspectorContent variant="rail" block={updated} {...sharedProps} />
        <TimeBlockInspectorContent variant="sheet" block={updated} {...sharedProps} />
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getAllByRole('combobox')).toHaveLength(2))
    expect(onSave).toHaveBeenCalledTimes(1)
    expect(onSave).not.toHaveBeenCalledWith({ task_type_id: 1 })
  })

  it('draft mode creates the block when a task type is chosen (no Save button)', async () => {
    const user = userEvent.setup()
    const onCreateFromDraft = vi.fn().mockResolvedValue(undefined)
    const draft = { lane: 'planned' as const, start_minute: 480, end_minute: 510 }
    render(
      <TimeBlockModal
        open
        block={null}
        draft={draft}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={vi.fn()}
        onCreateFromDraft={onCreateFromDraft}
        onDelete={vi.fn()}
        onCreateTaskTypePath={noopCreate}
      />,
    )
    expect(screen.getByRole('heading', { name: 'New block' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()
    expect(onCreateFromDraft).not.toHaveBeenCalled()
    await user.click(screen.getByLabelText('Task type'))
    await user.click(screen.getByRole('option', { name: /^break$/i }))
    await waitFor(() => {
      expect(onCreateFromDraft).toHaveBeenCalledWith({ task_type_id: 2, name: null, note: null })
    })
  })

  it('creates a named taskless Planned Block without choosing a Task Type', async () => {
    const user = userEvent.setup()
    const onCreateFromDraft = vi.fn().mockResolvedValue(undefined)
    render(
      <TimeBlockModal
        open
        block={null}
        draft={{ lane: 'planned', start_minute: 480, end_minute: 510 }}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={vi.fn()}
        onCreateFromDraft={onCreateFromDraft}
        onDelete={vi.fn()}
        onCreateTaskTypePath={noopCreate}
      />,
    )

    const name = screen.getByLabelText('Name')
    const taskType = screen.getByLabelText('Task type')
    expect(name.compareDocumentPosition(taskType) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    await user.type(name, '  Dinner with Alex  ')
    await user.type(screen.getByLabelText('Note'), 'Bring invitation')
    await user.click(screen.getByRole('button', { name: 'Create block' }))

    expect(onCreateFromDraft).toHaveBeenCalledWith({
      name: 'Dinner with Alex',
      note: 'Bring invitation',
    })
  })

  it('creates a named standalone Actual Block without choosing a Task Type', async () => {
    const user = userEvent.setup()
    const onCreateFromDraft = vi.fn().mockResolvedValue(undefined)
    render(
      <TimeBlockModal
        open
        block={null}
        draft={{ lane: 'actual', start_minute: 480, end_minute: 510 }}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={vi.fn()}
        onCreateFromDraft={onCreateFromDraft}
        onDelete={vi.fn()}
        onCreateTaskTypePath={noopCreate}
      />,
    )

    const name = screen.getByLabelText('Name')
    expect(name.compareDocumentPosition(screen.getByLabelText('Task type')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    await user.type(name, '  Walk   home  ')
    await user.type(screen.getByLabelText('Note'), 'Took the river path')
    await user.click(screen.getByRole('button', { name: 'Create block' }))

    expect(onCreateFromDraft).toHaveBeenCalledWith({
      name: 'Walk   home',
      note: 'Took the river path',
    })
  })

  it('adds, reloads, and clears a standalone Actual Block Name separately from Note', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockResolvedValue(undefined)
    const actual = makeBlock({ lane: 'actual', task_id: null, name: null, note: 'Keep me' })
    const props = {
      open: true,
      draft: null,
      day: emptyDay,
      taskTypes,
      onClose: vi.fn(),
      onSave,
      onDelete: vi.fn(),
      onCreateTaskTypePath: noopCreate,
    }
    const view = render(<TimeBlockModal block={actual} {...props} />)

    await user.type(screen.getByLabelText('Name'), '  Evening walk  ')
    await user.tab()
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ name: 'Evening walk' }))
    expect(onSave).not.toHaveBeenCalledWith(expect.objectContaining({ note: expect.anything() }))

    view.rerender(<TimeBlockModal block={{ ...actual, name: 'Evening walk' }} {...props} />)
    expect(screen.getByLabelText('Name')).toHaveValue('Evening walk')
    expect(screen.getByLabelText('Note')).toHaveValue('Keep me')
    onSave.mockClear()
    await user.clear(screen.getByLabelText('Name'))
    await user.tab()
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ name: null }))
  })

  it.each(['planned', 'actual'] as const)(
    'edits and reloads a task-backed %s Block Name without changing its linked context',
    async (lane) => {
      const user = userEvent.setup()
      const onSave = vi.fn().mockResolvedValue(undefined)
      const linked = makeBlock({
        lane,
        task_id: 42,
        task: { id: 42, title: 'Prepare launch', status: 'in_progress', task_type_id: 1 },
        name: 'Outline session',
        note: 'Keep me',
        planned_block_id: lane === 'actual' ? 8 : null,
      })
      const props = {
        open: true,
        draft: null,
        day: emptyDay,
        taskTypes,
        onClose: vi.fn(),
        onSave,
        onDelete: vi.fn(),
        onCreateTaskTypePath: noopCreate,
      }
      const view = render(
        <MemoryRouter><TimeBlockModal block={linked} {...props} /></MemoryRouter>,
      )

      expect(screen.getByText('Prepare launch')).toBeVisible()
      await user.clear(screen.getByLabelText('Name'))
      await user.type(screen.getByLabelText('Name'), '  Review session  ')
      await user.tab()
      await waitFor(() => expect(onSave).toHaveBeenCalledWith({ name: 'Review session' }))
      expect(onSave).not.toHaveBeenCalledWith(expect.objectContaining({ task_type_id: expect.anything() }))

      view.rerender(
        <MemoryRouter><TimeBlockModal block={{ ...linked, name: 'Review session' }} {...props} /></MemoryRouter>,
      )
      expect(screen.getByLabelText('Name')).toHaveValue('Review session')
      expect(screen.getByLabelText('Note')).toHaveValue('Keep me')
      expect(screen.getByText('Prepare launch')).toBeVisible()
    },
  )

  it('draft mode hides Delete and Complete', () => {
    render(
      <TimeBlockModal
        open
        block={null}
        draft={{ lane: 'planned', start_minute: 480, end_minute: 510 }}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={vi.fn()}
        onCreateFromDraft={vi.fn()}
        onDelete={vi.fn()}
        onRecordActualAsPlanned={vi.fn()}
        onCreateTaskTypePath={noopCreate}
      />,
    )
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Complete' })).not.toBeInTheDocument()
  })

  it('offers definitive Actual actions without completion bookkeeping', async () => {
    const user = userEvent.setup()
    const onRecordActualAsPlanned = vi.fn().mockResolvedValue(undefined)
    const planned = makeBlock({
      task_id: 42,
      task: { id: 42, title: 'Linked task', status: 'in_progress', task_type_id: 1 },
    })
    render(
      <MemoryRouter>
      <TimeBlockModal
        open
        block={planned}
        draft={null}
        day={{ ...emptyDay, time_blocks: [planned] }}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={vi.fn()}
        onDelete={vi.fn()}
        onRecordActualAsPlanned={onRecordActualAsPlanned}
        onCreateTaskTypePath={noopCreate}
      />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('button', { name: 'Start Work Mode' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Record Actual as planned' }))
    expect(onRecordActualAsPlanned).toHaveBeenCalled()
  })

  it('preserves the time block when permanent deletion is cancelled', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const onDelete = vi.fn().mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(false)

    render(
      <TimeBlockModal
        open
        block={makeBlock()}
        draft={null}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={onClose}
        onSave={vi.fn()}
        onDelete={onDelete}
        onCreateTaskTypePath={noopCreate}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Delete' }))

    expect(window.confirm).toHaveBeenCalledWith(
      'Permanently delete this time block? This cannot be undone.',
    )
    expect(onDelete).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('permanently deletes and closes the time block after confirmation', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const onDelete = vi.fn().mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <TimeBlockModal
        open
        block={makeBlock()}
        draft={null}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={onClose}
        onSave={vi.fn()}
        onDelete={onDelete}
        onCreateTaskTypePath={noopCreate}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Delete' }))

    expect(window.confirm).toHaveBeenCalledWith(
      'Permanently delete this time block? This cannot be undone.',
    )
    await waitFor(() => expect(onDelete).toHaveBeenCalledTimes(1))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
