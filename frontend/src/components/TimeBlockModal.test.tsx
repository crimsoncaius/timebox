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
      expect(onCreateFromDraft).toHaveBeenCalledWith({ task_type_id: 2, note: null })
    })
  })

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
