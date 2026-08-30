import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { WorkMode } from './WorkMode'
import type { ActualBlock, BattleTask } from '../../lib/api'

const actual: ActualBlock = {
  id: 40, task_type_id: 2, task_type: { id: 2, name: 'deep', created_at: '', updated_at: '' },
  task_id: 7, task: { id: 7, title: 'Prepare launch', status: 'open', task_type_id: 2 },
  note: null, planned_block_id: 12, start_at: '2026-08-30T02:12:00Z', end_at: null,
  created_at: '', updated_at: '',
}

const task: BattleTask = {
  id: 7, parent_id: null, project_id: null, project: null, task_type_id: 2,
  task_type: actual.task_type, title: 'Prepare launch', description: '', ready_to_plan: false,
  status: 'open', urgency: null, importance: null, deadline_date: null, deadline_at: null,
  reminder_at: null, reminder_delivered_at: null, position: 0, archived_at: null,
  deleted_at: null, created_at: '', updated_at: '', overdue: false,
  subtasks: [{ id: 8, parent_task_id: 7, title: 'Proofread', checked: false, effectively_resolved: false, position: 0, created_at: '', updated_at: '' }],
}

describe('WorkMode', () => {
  it('derives Task capabilities, checks Subtasks, and exposes both finish outcomes', async () => {
    const user = userEvent.setup()
    const onSetSubtask = vi.fn().mockResolvedValue(undefined)
    const onFinish = vi.fn().mockResolvedValue(undefined)
    const onFinishAndComplete = vi.fn().mockResolvedValue(undefined)
    render(<WorkMode actual={actual} task={task} timezone="UTC" busy={false} error={null} onSave={vi.fn()} onSetSubtask={onSetSubtask} onFinish={onFinish} onFinishAndComplete={onFinishAndComplete} onClose={vi.fn()} />)

    expect(screen.getByRole('dialog', { name: 'Work Mode' })).toHaveClass('fixed', 'inset-0')
    await user.click(screen.getByRole('checkbox', { name: 'Proofread' }))
    expect(onSetSubtask).toHaveBeenCalledWith(8, true)
    await user.click(screen.getByRole('button', { name: 'Finish session' }))
    await user.click(screen.getByRole('button', { name: 'Finish session + complete Task' }))
    expect(onFinish).toHaveBeenCalled()
    expect(onFinishAndComplete).toHaveBeenCalled()
  })

  it('does not expose Task or series actions for a non-task item', () => {
    render(<WorkMode actual={{ ...actual, task_id: null, task: null }} task={null} timezone="UTC" busy={false} error={null} onSave={vi.fn()} onSetSubtask={vi.fn()} onFinish={vi.fn()} onFinishAndComplete={vi.fn()} onClose={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Finish session' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Finish session + complete Task' })).not.toBeInTheDocument()
    expect(screen.queryByText(/recurr/i)).not.toBeInTheDocument()
  })

  it('treats an occurrence as an ordinary Task without series actions', () => {
    const occurrenceTask: BattleTask = {
      ...task,
      recurring_template_id: 91,
      recurring_template_title: 'Weekly launch ritual',
      occurrence_key: '2026-08-30',
      recurrence_kind: 'scheduled',
      occurrence: { id: 92, recurring_task_series_id: 91, occurrence_key: '2026-08-30' },
    }
    render(<WorkMode actual={actual} task={occurrenceTask} timezone="UTC" busy={false} error={null} onSave={vi.fn()} onSetSubtask={vi.fn()} onFinish={vi.fn()} onFinishAndComplete={vi.fn()} onClose={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Finish session' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Finish session + complete Task' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /series|recurr|template/i })).not.toBeInTheDocument()
  })

  it('edits a cross-midnight Actual interval at minute precision', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn().mockResolvedValue(undefined)
    render(<WorkMode actual={{ ...actual, start_at: '2026-08-30T23:30:00Z', end_at: '2026-08-31T00:20:00Z' }} task={task} timezone="UTC" busy={false} error={null} onSave={onSave} onSetSubtask={vi.fn()} onFinish={vi.fn()} onFinishAndComplete={vi.fn()} onClose={vi.fn()} />)
    expect(screen.getByLabelText('Actual start')).toHaveValue('2026-08-30T23:30')
    expect(screen.getByLabelText('Actual end')).toHaveValue('2026-08-31T00:20')
    await user.click(screen.getByRole('button', { name: 'Save Actual time' }))
    expect(onSave).toHaveBeenCalledWith({ startLocal: '2026-08-30T23:30', endLocal: '2026-08-31T00:20' })
  })

  it('requires an end time when editing an ended Actual interval', async () => {
    const user = userEvent.setup()
    render(<WorkMode actual={{ ...actual, end_at: '2026-08-30T03:00:00Z' }} task={task} timezone="UTC" busy={false} error={null} onSave={vi.fn()} onSetSubtask={vi.fn()} onFinish={vi.fn()} onFinishAndComplete={vi.fn()} onClose={vi.fn()} />)

    await user.clear(screen.getByLabelText('Actual end'))
    expect(screen.getByRole('button', { name: 'Save Actual time' })).toBeDisabled()
  })
})
