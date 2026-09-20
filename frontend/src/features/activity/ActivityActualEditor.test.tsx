import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ActualBlock, DayRead, TaskType } from '../../lib/api'
import { ActivityActualEditor } from './ActivityActualEditor'

const taskTypes: TaskType[] = [
  { id: 1, name: 'alpha', created_at: '', updated_at: '' },
  { id: 3, name: 'unspecified', created_at: '', updated_at: '' },
]

const day: DayRead = {
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

function makeActual(overrides: Partial<ActualBlock> = {}): ActualBlock {
  return {
    id: 10,
    lane: 'actual',
    task_type_id: 1,
    task_type: taskTypes[0]!,
    task_id: null,
    name: null,
    note: null,
    start_at: '2026-06-01T08:00:00Z',
    end_at: '2026-06-01T08:30:00Z',
    created_at: '',
    updated_at: '',
    ...overrides,
  } as ActualBlock
}

const noopCreate = vi.fn(async () => taskTypes[0]!)

const props = {
  day,
  taskTypes,
  onClose: vi.fn(),
  onDelete: vi.fn(async () => {}),
  onCreateTaskTypePath: noopCreate,
}

describe('ActivityActualEditor', () => {
  it('creates a named standalone Actual Block from a draft without choosing a Task Type', async () => {
    const user = userEvent.setup()
    const onCreate = vi.fn(async () => {})
    render(
      <ActivityActualEditor
        draft={{ lane: 'actual', start_minute: 480, end_minute: 510 }}
        onSave={vi.fn(async () => {})}
        onCreate={onCreate}
        {...props}
      />,
    )

    await user.type(screen.getByLabelText('Block Name (optional)'), '  Walk   home  ')
    await user.type(screen.getByLabelText('Note'), 'Took the river path')
    await user.click(screen.getByRole('button', { name: 'Create block' }))

    await waitFor(() => expect(onCreate).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Walk   home', note: 'Took the river path' }),
    ))
  })

  it('saves a Block Name and keeps the existing Note', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn(async () => {})
    const actual = makeActual({ note: 'Keep me' })
    render(
      <ActivityActualEditor
        actual={actual}
        draft={null}
        onSave={onSave}
        {...props}
      />,
    )

    expect(screen.getByLabelText('Note')).toHaveValue('Keep me')
    await user.type(screen.getByLabelText('Block Name (optional)'), '  Evening walk  ')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Evening walk', note: 'Keep me' }),
    ))
  })

  it('clears a Block Name back to null', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn(async () => {})
    render(
      <ActivityActualEditor
        actual={makeActual({ name: 'Evening walk' })}
        draft={null}
        onSave={onSave}
        {...props}
      />,
    )

    await user.clear(screen.getByLabelText('Block Name (optional)'))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ name: null }),
    ))
  })

  it('shows the linked Task without letting the edit touch Task Completion', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn(async () => {})
    render(
      <ActivityActualEditor
        actual={makeActual({
          task_id: 42,
          task: { id: 42, title: 'Prepare launch', status: 'in_progress', task_type_id: 1 },
          name: 'Outline session',
          note: 'Keep me',
        } as Partial<ActualBlock>)}
        draft={null}
        onSave={onSave}
        {...props}
      />,
    )

    expect(screen.getByText(/Prepare launch/)).toBeVisible()
    await user.clear(screen.getByLabelText('Block Name (optional)'))
    await user.type(screen.getByLabelText('Block Name (optional)'), 'Review session')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Review session', note: 'Keep me' }),
    ))
    expect(screen.getByText(/Prepare launch/)).toBeVisible()
  })

  it('renders without a draft when the selected Actual is already gone', () => {
    render(
      <ActivityActualEditor draft={null} onSave={vi.fn(async () => {})} {...props} />,
    )
    expect(screen.getByRole('form', { name: 'Add Actual Block' })).toBeInTheDocument()
  })
})
