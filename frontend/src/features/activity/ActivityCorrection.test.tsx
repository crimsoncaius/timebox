import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { ActivityRepository } from './activityRepository'
import { TimeBlockInspectorContent } from '../../components/TimeBlockInspectorContent'
import { activityDay } from './activityDay'
import { ActivityTracking } from './ActivityTracking'
import { useState, useSyncExternalStore } from 'react'
import type { ActivitySnapshot } from './activityRepository'

vi.mock('./activityRepository', async original => ({ ...await original<object>(), activityDevelopmentEnabled: true }))
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); localStorage.clear() })
const type = { id: 1, name: 'Writing', created_at: '', updated_at: '' }
const row = { id: 42, task_type_id: 1, task_type: type, task_id: null, task: null, name: 'Across midnight', note: null, planned_block_id: null, start_at: '2025-11-01T23:30:12Z', end_at: '2025-11-02T01:30:34Z', created_at: '', updated_at: '' }
const initial: ActivitySnapshot = { protocol: 'activity-online-v1', offline_ready: true, cursor: 1, server_at: '2026-09-11T12:15:00Z', reporting_timezone: 'UTC', current: null, records: [row], task_types: [type] }

it.each(['rail', 'sheet'] as const)('edits the whole prior-day record offline through the %s inspector and restores the changed Day', async variant => {
  let online = true
  vi.stubGlobal('fetch', vi.fn(async () => { if (!online) throw new Error('Offline'); return new Response(JSON.stringify(initial)) }))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh(); online = false
  function DayEditor() {
    const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
    const [open, setOpen] = useState(true)
    const day = activityDay('2025-11-02', state.snapshot!, null, repository.now())
    const actual = day.actual_blocks[0]
    return <><p>{actual?.actual_block.name} · {actual?.duration_minutes}m on this day</p>{open && actual ? <TimeBlockInspectorContent block={{ ...actual.actual_block, lane: 'actual', start_minute: actual.start_minute, end_minute: actual.end_minute }} draft={null} day={day} taskTypes={[type]} variant={variant} onClose={() => setOpen(false)} onSave={async patch => { if (!await repository.correct('edit', 42, patch)) throw new Error(repository.state.error!) }} onDelete={async () => { await repository.correct('delete', 42) }} onCreateTaskTypePath={async () => type} /> : null}</>
  }
  const view = render(<DayEditor />)
  expect(screen.getByLabelText('Start')).toHaveValue('2025-11-01T23:30')
  expect(screen.getByLabelText('End')).toHaveValue('2025-11-02T01:30')
  fireEvent.change(screen.getByLabelText('End'), { target: { value: '2025-11-02T02:00' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  await screen.findByText('Across midnight · 120m on this day')
  expect(repository.state.snapshot?.records[0].start_at).toBe(row.start_at)
  view.unmount()
  const restored = new ActivityRepository(localStorage, work => work())
  expect(activityDay('2025-11-02', restored.state.snapshot!, null, restored.now()).actual_blocks[0].duration_minutes).toBe(120)
})

it('rejects spring gaps and asks which occurrence of an autumn time before editing', async () => {
  const day = activityDay('2025-11-02', { ...initial, reporting_timezone: 'America/New_York', records: [{ ...row, start_at: '2025-11-02T05:50:00Z', end_at: '2025-11-02T06:10:00Z' }] }, null, Date.parse(initial.server_at))
  const save = vi.fn(async () => {})
  render(<TimeBlockInspectorContent block={{ ...day.actual_blocks[0].actual_block, lane: 'actual', start_minute: 110, end_minute: 70 }} draft={null} day={day} taskTypes={[type]} variant="sheet" onClose={() => {}} onSave={save} onDelete={async () => {}} onCreateTaskTypePath={async () => type} />)
  expect(screen.getByLabelText('Start occurrence')).toHaveValue('earlier')
  expect(screen.getByLabelText('End occurrence')).toHaveValue('later')
  fireEvent.change(screen.getByLabelText('Start'), { target: { value: '2025-03-09T02:30' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(screen.getAllByRole('alert').length).toBeGreaterThan(0))
  expect(save).not.toHaveBeenCalled()
  fireEvent.change(screen.getByLabelText('Start'), { target: { value: '2025-11-02T01:40' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  await screen.findByText(/occurs twice; choose/)
  fireEvent.change(screen.getByLabelText('Start occurrence'), { target: { value: 'earlier' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(save).toHaveBeenCalledWith(expect.objectContaining({ start_at: '2025-11-02T05:40:00.000Z', end_at: '2025-11-02T06:10:00Z' })))
})

it('previews a late stop, Cancel is inert, and a remote switch cannot retarget the open correction', async () => {
  let saved = { ...initial, current: { ...row, start_at: '2026-09-11T10:00:00Z', end_at: null }, records: [{ ...row, start_at: '2026-09-11T10:00:00Z', end_at: null }] }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(saved))))
  const repository = new ActivityRepository(localStorage, work => work())
  await repository.refresh()
  render(<ActivityTracking taskTypes={[type]} repository={repository} onChanged={() => {}} />)
  fireEvent.click(screen.getByRole('button', { name: 'Stop' }))
  fireEvent.click(screen.getByRole('button', { name: '15 min ago' }))
  expect(screen.getByLabelText('After this change')).toHaveTextContent('12:00')
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(repository.state.pending).toBe(false)
  fireEvent.click(screen.getByRole('button', { name: 'Stop' }))
  const remote = { ...saved.current, id: 99, name: 'Remote' }
  saved = { ...saved, cursor: 2, current: remote, records: [remote] }
  await act(() => repository.refresh())
  fireEvent.click(screen.getByRole('button', { name: 'Stop tracking' }))
  await screen.findByText(/Review the current activity if it changed/)
  expect(repository.state.pending).toBe(false)
  expect(repository.state.snapshot?.current?.id).toBe(99)
})
