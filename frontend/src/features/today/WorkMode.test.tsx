import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { BattleTask, TimeBlock } from '../../lib/api'
import { WorkMode } from './WorkMode'

const block: TimeBlock = {
  id: 12, lane: 'planned', task_type_id: 2,
  task_type: { id: 2, name: 'Deep work', created_at: '', updated_at: '' },
  task_id: 7, task: { id: 7, title: 'Prepare launch', status: 'open', task_type_id: 2 },
  note: 'Plan note', start_minute: 600, end_minute: 660, created_at: '', updated_at: '',
}

const task: BattleTask = {
  id: 7, parent_id: null, project_id: null, project: null, task_type_id: 2,
  task_type: block.task_type, title: 'Prepare launch', description: 'Use the final evidence.', ready_to_plan: false,
  status: 'open', urgency: null, importance: null, deadline_date: null, deadline_at: null,
  reminder_at: null, reminder_delivered_at: null, position: 0, archived_at: null,
  deleted_at: null, created_at: '', updated_at: '', overdue: false,
  subtasks: [{ id: 8, parent_task_id: 7, title: 'Proofread', checked: false, effectively_resolved: false, position: 0, created_at: '', updated_at: '' }],
}

const baseProps = {
  next: null, nowMinute: 620, confirming: false, recording: true, busy: false, error: null,
  onSetSubtask: vi.fn().mockResolvedValue(undefined), onExit: vi.fn().mockResolvedValue(undefined),
  onLeave: vi.fn(),
}

describe('WorkMode', () => {
  it('presents Task execution context, interactive Subtasks, and one exit action', async () => {
    const user = userEvent.setup()
    const onSetSubtask = vi.fn().mockResolvedValue(undefined)
    const onExit = vi.fn().mockResolvedValue(undefined)
    render(<WorkMode {...baseProps} current={block} task={task} onSetSubtask={onSetSubtask} onExit={onExit} />)

    expect(screen.getByRole('heading', { name: 'Prepare launch' })).toBeVisible()
    expect(screen.getByText('Deep work')).toBeVisible()
    expect(screen.getByText('Use the final evidence.')).toBeVisible()
    await user.click(screen.getByRole('checkbox', { name: 'Proofread' }))
    expect(onSetSubtask).toHaveBeenCalledWith(8, true)
    await user.click(screen.getByRole('button', { name: 'Exit Work Mode' }))
    expect(onExit).toHaveBeenCalled()
    expect(screen.queryByText(/complete Task|skip this block|save actual/i)).not.toBeInTheDocument()
  })

  it('presents a taskless current block through Task Type and note', () => {
    render(<WorkMode {...baseProps} current={{ ...block, task_id: null, task: null, note: 'Read in the garden' }} task={null} />)
    expect(screen.getByRole('heading', { name: 'Read in the garden' })).toBeVisible()
    expect(screen.getByText('Deep work')).toBeVisible()
  })

  it('presents Up next with time and countdown', () => {
    render(<WorkMode {...baseProps} current={null} next={{ ...block, start_minute: 630 }} task={null} recording={false} nowMinute={620} />)
    expect(screen.getByText('Up next')).toBeVisible()
    expect(screen.getByText(/in 10 minutes/)).toBeVisible()
  })

  it('keeps Work Mode open in today’s empty state', () => {
    render(<WorkMode {...baseProps} current={null} next={null} task={null} recording={false} />)
    expect(screen.getByRole('heading', { name: 'No more planned work today' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Exit Work Mode' })).toBeVisible()
  })
})
