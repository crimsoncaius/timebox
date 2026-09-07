import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TaskTypePathCombobox } from './TaskTypePathCombobox'

const taskTypes = [
  { id: 1, name: 'coding', created_at: '', updated_at: '' },
  { id: 2, name: 'coding/ai', created_at: '', updated_at: '' },
]

describe('TaskTypePathCombobox', () => {
  it('shows matching suggestions as the user types', async () => {
    const user = userEvent.setup()
    render(
      <TaskTypePathCombobox
        label="Task type"
        taskTypes={taskTypes}
        valueTaskTypeId={2}
        onSelectTaskTypeId={vi.fn()}
        onCreateTaskTypePath={vi.fn()}
      />,
    )

    await user.clear(screen.getByLabelText('Task type'))
    await user.type(screen.getByLabelText('Task type'), 'coding')
    expect(screen.getByRole('option', { name: /ai/i })).toBeInTheDocument()
  })

  it('creates a missing canonical path and selects the returned row', async () => {
    const user = userEvent.setup()
    const onSelectTaskTypeId = vi.fn()
    const onCreateTaskTypePath = vi.fn().mockResolvedValue({
      id: 5,
      name: 'coding/personal',
      created_at: '',
      updated_at: '',
    })

    render(
      <TaskTypePathCombobox
        label="Task type"
        taskTypes={taskTypes}
        valueTaskTypeId={2}
        onSelectTaskTypeId={onSelectTaskTypeId}
        onCreateTaskTypePath={onCreateTaskTypePath}
      />,
    )

    await user.clear(screen.getByLabelText('Task type'))
    await user.type(screen.getByLabelText('Task type'), 'Coding / Personal')
    await user.click(screen.getByRole('option', { name: /create "coding\/personal"/i }))

    expect(onCreateTaskTypePath).toHaveBeenCalledWith('coding/personal')
    expect(onSelectTaskTypeId).toHaveBeenCalledWith(5)
  })
})
