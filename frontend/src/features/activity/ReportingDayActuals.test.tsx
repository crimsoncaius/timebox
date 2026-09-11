import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import type { DayRead } from '../../lib/api'
import { ReportingDayActuals } from './ReportingDayActuals'

it('shows the API elapsed share and both repeated-hour offsets, opening the original identity', () => {
  const type = { id: 1, name: 'Reading', created_at: '', updated_at: '' }
  const day: DayRead = { id: 1, date: '2025-11-02', start_hour: 0, end_hour: 24, show_full_day: true, created_at: '', updated_at: '', time_blocks: [], planned_blocks: [], meta: { timezone: 'America/New_York', today: '2026-09-11', server_now_iso: '2026-09-11T10:00:00Z' }, actual_blocks: [{ date: '2025-11-02', start_minute: 110, end_minute: 70, duration_minutes: 20, actual_block: { id: 42, task_type_id: 1, task_type: type, task_id: null, task: null, planned_block_id: null, name: null, note: null, start_at: '2025-11-02T05:50:00Z', end_at: '2025-11-02T06:10:00Z', created_at: '', updated_at: '' } }] }
  const select = vi.fn()
  render(<ReportingDayActuals day={day} onSelect={select} />)
  const row = screen.getByRole('button', { name: /Reading/ })
  expect(row).toHaveTextContent('20m on this day')
  expect(row).toHaveTextContent('GMT-4')
  expect(row).toHaveTextContent('GMT-5')
  fireEvent.click(row)
  expect(select).toHaveBeenCalledWith(42)
})
