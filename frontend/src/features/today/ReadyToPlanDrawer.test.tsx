import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { BattleTask } from '../../lib/api'
import { ReadyToPlanDrawer } from './ReadyToPlanDrawer'

const tasks: BattleTask[] = Array.from({ length: 5 }, (_, index) => ({
  id: index + 1, title: `Task ${index + 1}`, parent_id: null,
  project_id: null, project: null, task_type_id: null, task_type: null,
  description: '', ready_to_plan: true, status: 'open', urgency: null,
  importance: null, deadline_date: null, deadline_at: null, reminder_at: null,
  reminder_delivered_at: null, position: index, archived_at: null, deleted_at: null,
  created_at: '', updated_at: '', overdue: false, subtasks: [],
}))

describe('ReadyToPlanDrawer search', () => {
  it.each(['mobile', 'desktop'] as const)('keeps a query clearable after the %s list shrinks', (dragInstance) => {
    const props = { selectedTaskId: null, busyTaskId: null, onSelect: vi.fn(), dragInstance }
    const { rerender } = render(<ReadyToPlanDrawer {...props} tasks={tasks} />)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'no match' } })

    rerender(<ReadyToPlanDrawer {...props} tasks={tasks.slice(0, 4)} />)
    expect(screen.getByText('No matching tasks.')).toBeInTheDocument()
    expect(screen.getByRole('searchbox')).toHaveValue('no match')

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '' } })
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
    for (const task of tasks.slice(0, 4)) {
      expect(screen.getByRole('button', { name: task.title })).toBeEnabled()
    }
    expect(screen.queryByText('No matching tasks.')).not.toBeInTheDocument()
  })
})
