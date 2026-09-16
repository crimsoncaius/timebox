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
    expect(screen.getByText(/Adds under existing/)).toBeInTheDocument()
    await user.click(screen.getByRole('option', { name: /create "coding\/personal"/i }))

    expect(onCreateTaskTypePath).toHaveBeenCalledWith('coding/personal')
    expect(onSelectTaskTypeId).toHaveBeenCalledWith(5)
  })

  it('offers Unset last when allowed and the query is empty', async () => {
    const user = userEvent.setup()
    const onSelectTaskTypeId = vi.fn()
    render(
      <TaskTypePathCombobox
        label="Task type"
        allowUnset
        taskTypes={[
          ...taskTypes,
          { id: 9, name: 'unspecified', created_at: '', updated_at: '' },
        ]}
        valueTaskTypeId={2}
        onSelectTaskTypeId={onSelectTaskTypeId}
        onCreateTaskTypePath={vi.fn()}
      />,
    )

    await user.clear(screen.getByLabelText('Task type'))
    const options = screen.getAllByRole('option').map((option) => option.textContent)
    expect(options).not.toContain('unspecified')
    expect(options.at(-1)).toBe('Unset')
    await user.click(screen.getByRole('option', { name: 'Unset' }))
    expect(onSelectTaskTypeId).toHaveBeenCalledWith(null)
  })

  it('matches a stored name regardless of case and does not offer create', async () => {
    const user = userEvent.setup()
    render(
      <TaskTypePathCombobox
        label="Task type"
        taskTypes={[{ id: 2, name: 'Reading', created_at: '', updated_at: '' }]}
        valueTaskTypeId={null}
        onSelectTaskTypeId={vi.fn()}
        onCreateTaskTypePath={vi.fn()}
      />,
    )

    await user.type(screen.getByLabelText('Task type'), 'reading')
    expect(screen.getByRole('option', { name: 'Reading' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /create/i })).not.toBeInTheDocument()
  })
})
