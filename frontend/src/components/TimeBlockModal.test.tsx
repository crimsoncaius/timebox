import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { DayRead, TaskType, TimeBlock } from '../lib/api'
import { TimeBlockModal } from './TimeBlockModal'

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
  meta: { timezone: 'UTC', today: '2026-06-01', server_now_iso: '2026-06-01T12:00:00Z' },
}

describe('TimeBlockModal', () => {
  it('shows read-only start and end as HH:MM', () => {
    render(
      <TimeBlockModal
        open
        block={makeBlock()}
        day={emptyDay}
        taskTypes={taskTypes}
        onClose={vi.fn()}
        onSave={vi.fn()}
        onDelete={vi.fn()}
        onCreateTaskTypePath={noopCreate}
      />,
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
    expect(noteField).toHaveClass('min-h-24')
  })

  it('submits only task_type_id and note, not time fields', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockResolvedValue(undefined)
    render(
      <TimeBlockModal
        open
        block={makeBlock({ note: 'old' })}
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
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(onSave).toHaveBeenCalledWith({ task_type_id: 2 })
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
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(onCreateTaskTypePath).toHaveBeenCalledWith('coding/personal')
    expect(onSave).toHaveBeenCalledWith({ task_type_id: 7 })
  })
})
