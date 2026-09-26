import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { BattleTask, TaskType } from '../../lib/api'
import { TaskTypeFilterMenu } from './TaskTypeFilterMenu'

const type = (id: number, name: string, usage = 0): TaskType => ({ id, name, created_at: '', updated_at: '', usage_count: usage })
const types = [type(1, 'coding', 3), type(2, 'coding/ai', 2), type(3, 'writing', 1), type(9, 'unspecified', 9)]
const task = (id: number, taskType: TaskType | null) => ({ id, task_type_id: taskType?.id ?? null, task_type: taskType }) as BattleTask

function Harness({ initial = [] as string[] }) {
  const [selected, setSelected] = useState(initial)
  return (
    <>
      <TaskTypeFilterMenu taskTypes={types} tasks={[task(1, types[0]), task(2, types[1]), task(3, null)]} selected={selected} onChange={setSelected} />
      <output data-testid="selected">{selected.join(',')}</output>
    </>
  )
}

describe('TaskTypeFilterMenu', () => {
  it('shows a chosen parent as a chip and its sub-types as included, with branch counts', async () => {
    render(<Harness initial={['1']} />)
    await userEvent.click(screen.getByText('Task types · 1'))

    expect(screen.getByRole('button', { name: 'Remove coding and 1 sub-types' })).toBeInTheDocument()
    const ai = screen.getByRole('checkbox', { name: 'coding/ai' })
    expect(ai).toBeChecked()
    expect(ai).toBeDisabled()
    expect(screen.getByText('Included via coding')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'coding' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'coding' }).closest('label')).toHaveTextContent('2')
    expect(screen.queryByText('unspecified')).not.toBeInTheDocument()
  })

  it('searches existing types, keeps Unset, and never offers Create', async () => {
    render(<Harness />)
    await userEvent.click(screen.getByText('Task types'))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Unset' }))
    expect(screen.getByTestId('selected')).toHaveTextContent('unset')

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search task types' }), 'writ')
    expect(screen.queryByRole('checkbox', { name: /coding/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'writing' }))
    expect(screen.getByTestId('selected')).toHaveTextContent('unset,3')

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search task types' }), 'zz')
    expect(screen.getByText('No type matches that path.')).toBeInTheDocument()
    expect(screen.queryByText(/Create/)).not.toBeInTheDocument()
  })

  it('absorbs chosen sub-types when their parent is chosen', async () => {
    render(<Harness initial={['2']} />)
    await userEvent.click(screen.getByText('Task types · 1'))
    await userEvent.click(screen.getByRole('checkbox', { name: 'coding' }))
    expect(screen.getByTestId('selected')).toHaveTextContent(/^1$/)
  })
})
