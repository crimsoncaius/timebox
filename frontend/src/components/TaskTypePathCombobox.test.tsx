import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TaskTypePathCombobox } from './TaskTypePathCombobox'

const taskTypes = [
  { id: 1, name: 'coding', created_at: '', updated_at: '' },
  { id: 2, name: 'coding/ai', created_at: '', updated_at: '' },
]

describe('TaskTypePathCombobox', () => {
  it('commits an active matching option without submitting its form', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    const onSubmit = vi.fn((event) => event.preventDefault())
    render(<form onSubmit={onSubmit}><TaskTypePathCombobox label="Task type"
      taskTypes={[{ id: 3, name: 'exercise', created_at: '', updated_at: '' }]}
      valueTaskTypeId={null} onSelectTaskTypeId={onSelect} onCreateTaskTypePath={vi.fn()} />
      <button type="submit">Save</button></form>)
    const input = screen.getByRole('combobox')
    await user.type(input, 'exer')
    await user.keyboard('{ArrowDown}{Enter}')
    expect(onSelect).toHaveBeenCalledWith(3)
    expect(onSubmit).not.toHaveBeenCalled()
    expect(input).toHaveFocus()
  })

  it('announces active and saved options, navigates both directions and resets on editing', async () => {
    const user = userEvent.setup()
    render(<TaskTypePathCombobox label="Task type" taskTypes={taskTypes}
      valueTaskTypeId={2} onSelectTaskTypeId={vi.fn()} onCreateTaskTypePath={vi.fn()} />)
    const input = screen.getByRole('combobox')
    await user.clear(input)
    const list = screen.getByRole('listbox', { name: 'Task type options' })
    const options = screen.getAllByRole('option')
    expect(input).toHaveAttribute('aria-controls', list.id)
    expect(options[0]).toHaveAttribute('aria-selected', 'true')
    expect(options[1]).toHaveAttribute('aria-selected', 'false')
    await user.keyboard('{ArrowDown}')
    expect(input).toHaveAttribute('aria-activedescendant', options[0].id)
    await user.keyboard('{ArrowDown}')
    expect(input).toHaveAttribute('aria-activedescendant', options[1].id)
    await user.keyboard('{ArrowUp}')
    expect(input).toHaveAttribute('aria-activedescendant', options[0].id)
    await user.type(input, 'coding/ai')
    expect(input).not.toHaveAttribute('aria-activedescendant')
    await user.keyboard('{Escape}')
    expect(input).toHaveAttribute('aria-expanded', 'false')
    expect(input).not.toHaveAttribute('aria-controls')
    await user.keyboard('{ArrowUp}')
    expect(input).toHaveAttribute('aria-expanded', 'true')
    expect(input).toHaveAttribute('aria-activedescendant', screen.getAllByRole('option').at(-1)!.id)
  })

  it.each([null, 2])('commits Unset from its ranked position (%s)', async (valueTaskTypeId) => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<TaskTypePathCombobox label="Task type" allowUnset taskTypes={taskTypes}
      valueTaskTypeId={valueTaskTypeId} onSelectTaskTypeId={onSelect} onCreateTaskTypePath={vi.fn()} />)
    await user.clear(screen.getByRole('combobox'))
    await user.keyboard(valueTaskTypeId == null ? '{ArrowDown}{Enter}' : '{ArrowUp}{Enter}')
    expect(onSelect).toHaveBeenCalledWith(null)
  })

  it('commits Create only once while pending and reports failure', async () => {
    const user = userEvent.setup()
    let reject!: (error: Error) => void
    const onCreate = vi.fn(() => new Promise<never>((_, no) => { reject = no }))
    render(<TaskTypePathCombobox label="Task type" taskTypes={taskTypes}
      valueTaskTypeId={null} onSelectTaskTypeId={vi.fn()} onCreateTaskTypePath={onCreate} />)
    await user.type(screen.getByRole('combobox'), 'new/path')
    await user.keyboard('{ArrowDown}{Enter}{Enter}')
    expect(onCreate).toHaveBeenCalledExactlyOnceWith('new/path')
    expect(screen.getByRole('option')).toHaveAttribute('aria-disabled', 'true')
    await act(async () => reject(new Error('Failed')))
    expect(screen.getByRole('alert')).toHaveTextContent('Could not create Task Type')
  })

  it('allows Tab to leave without committing and handles an empty list', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<><TaskTypePathCombobox label="Task type" taskTypes={[]}
      valueTaskTypeId={null} onSelectTaskTypeId={onSelect} onCreateTaskTypePath={vi.fn()} />
      <button>Outside</button></>)
    await user.click(screen.getByRole('combobox'))
    await user.keyboard('{ArrowDown}{ArrowUp}')
    expect(screen.getByRole('combobox')).not.toHaveAttribute('aria-activedescendant')
    await user.tab()
    expect(screen.getByRole('button', { name: 'Outside' })).toHaveFocus()
    expect(onSelect).not.toHaveBeenCalled()
  })

  it.each(['mouse', 'keyboard'])('preserves an internal option commit via %s', async (method) => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<TaskTypePathCombobox label="Task type" taskTypes={taskTypes}
      valueTaskTypeId={2} onSelectTaskTypeId={onSelect} onCreateTaskTypePath={vi.fn()} />)
    await user.clear(screen.getByRole('combobox'))
    await user.type(screen.getByRole('combobox'), 'coding')
    const option = screen.getByRole('option', { name: 'coding' })
    if (method === 'mouse') await user.click(option)
    else {
      await user.tab()
      expect(option).toHaveFocus()
      await user.keyboard('{Enter}')
    }
    expect(onSelect).toHaveBeenCalledWith(1)
    expect(screen.getByRole('combobox')).toHaveValue('coding')
  })

  it.each([true, false])('restores saved text while creation is pending and handles success=%s', async (succeeds) => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    const created = { id: 5, name: 'exercise', created_at: '', updated_at: '' }
    let resolve!: (value: typeof created) => void
    let reject!: (reason: Error) => void
    const pending = new Promise<typeof created>((yes, no) => { resolve = yes; reject = no })
    render(<><TaskTypePathCombobox label="Task type" taskTypes={taskTypes}
      valueTaskTypeId={2} onSelectTaskTypeId={onSelect} onCreateTaskTypePath={() => pending} />
      <button>Outside</button></>)
    const input = screen.getByRole('combobox')
    await user.clear(input)
    await user.type(input, 'exercise')
    await user.click(screen.getByRole('option', { name: 'Create "exercise"' }))
    await user.click(screen.getByText('Outside'))
    expect(input).toHaveValue('coding/ai')
    expect(onSelect).not.toHaveBeenCalled()
    await act(async () => {
      if (succeeds) resolve(created)
      else reject(new Error('Network unavailable'))
    })
    expect(input).toHaveValue(succeeds ? 'exercise' : 'coding/ai')
    if (succeeds) expect(onSelect).toHaveBeenCalledWith(5)
    else {
      expect(onSelect).not.toHaveBeenCalled()
      expect(screen.getByRole('alert')).toHaveTextContent('Could not create Task Type')
    }
  })
  it.each([2, null, 9])('restores the committed presentation on outside blur (%s)', async (valueTaskTypeId) => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    const onCreate = vi.fn()
    render(<><TaskTypePathCombobox label="Task type" allowUnset
      taskTypes={[...taskTypes, { id: 9, name: 'unspecified', created_at: '', updated_at: '' }]}
      valueTaskTypeId={valueTaskTypeId} onSelectTaskTypeId={onSelect} onCreateTaskTypePath={onCreate} />
      <button>Outside</button></>)
    const input = screen.getByRole('combobox')
    await user.clear(input)
    await user.type(input, 'exer')
    await user.click(screen.getByText('Outside'))
    expect(input).toHaveValue(valueTaskTypeId === 2 ? 'coding/ai' : '')
    expect(input).toHaveAttribute('placeholder', 'Unset')
    expect(onSelect).not.toHaveBeenCalled()
    expect(onCreate).not.toHaveBeenCalled()
  })
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
