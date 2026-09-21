import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import type { PlannedRecordingResult } from '../../lib/api'
import { RecordingPreview } from './RecordingPreview'
import { recordingTimeline } from './recordingTimeline'

beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function () { this.open = true }
  HTMLDialogElement.prototype.close = function () { this.open = false }
})

const preview: PlannedRecordingResult = {
  status: 'confirmation_required', start_at: '2026-09-13T10:00:00Z', end_at: '2026-09-13T11:00:00Z',
  fingerprint: 'observed', stale: false, undo_token: null, actual_block: null,
  replacement: { name: 'Chapter', note: 'Outline', task_id: null, task_type_id: 1 },
  conflicts: [{ id: 2, task_type_id: 1, task_type: { id: 1, name: 'Writing', created_at: '', updated_at: '' },
    task_id: null, task: null, planned_block_id: null, name: 'Draft', note: 'Keep my note',
    start_at: '2026-09-13T09:45:00Z', end_at: null, created_at: '', updated_at: '' }],
}

it('previews preserved time, lost metadata, and continuing tracking without applying on cancel', () => {
  const confirm = vi.fn(), cancel = vi.fn()
  render(<RecordingPreview preview={preview} timezone="UTC" onConfirm={confirm} onCancel={cancel} />)
  fireEvent.click(screen.getByText('See name and note changes'))
  expect(screen.getByText('Keep my note')).toBeVisible()
  expect(screen.getByText(/Tracking stays on/)).toBeInTheDocument()
  expect(screen.getByText(/^Keep .*9:45/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(cancel).toHaveBeenCalledOnce()
  expect(confirm).not.toHaveBeenCalled()
})

it('keeps cancellation and repeat confirmation disabled while saving', async () => {
  let finish!: () => void
  const confirm = vi.fn(() => new Promise<void>(resolve => { finish = resolve }))
  render(<RecordingPreview preview={preview} timezone="UTC" onConfirm={confirm} onCancel={vi.fn()} />)
  fireEvent.click(screen.getByRole('button', { name: 'Replace overlapping time' }))
  expect(screen.getByRole('button', { name: 'Recording…' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  finish()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Replace overlapping time' })).toBeEnabled())
})

it('shows an empty refreshed preview without claiming there is overlapping time', () => {
  render(<RecordingPreview preview={{ ...preview, stale: true, conflicts: [] }} timezone="UTC" onConfirm={vi.fn()} onCancel={vi.fn()} />)
  expect(screen.getByText('No Actual time overlaps this updated range.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Record Actual' })).toBeEnabled()
})

it('represents multiple conflicts, with outside edges kept and contained time removed', () => {
  const base = preview.conflicts[0]
  const model = recordingTimeline({ ...preview, conflicts: [
    { ...base, id: 1, name: 'Prefix', start_at: '2026-09-13T09:45:00Z', end_at: '2026-09-13T10:15:00Z' },
    { ...base, id: 2, name: 'Contained', start_at: '2026-09-13T10:20:00Z', end_at: '2026-09-13T10:40:00Z' },
    { ...base, id: 3, name: 'Suffix', start_at: '2026-09-13T10:50:00Z', end_at: '2026-09-13T11:15:00Z' },
  ] })
  expect(model.after.map(row => row.title)).toEqual(['Prefix · Writing', 'Chapter', 'Suffix · Writing'])
  expect(model.after.map(row => [new Date(row.start).toISOString(), new Date(row.end).toISOString()])).toEqual([
    ['2026-09-13T09:45:00.000Z', '2026-09-13T10:00:00.000Z'],
    ['2026-09-13T10:00:00.000Z', '2026-09-13T11:00:00.000Z'],
    ['2026-09-13T11:00:00.000Z', '2026-09-13T11:15:00.000Z'],
  ])
})

it('splits a spanning running record at the frozen subsecond endpoint across midnight', () => {
  const model = recordingTimeline({ ...preview, start_at: '2026-09-13T23:59:59.500Z', end_at: '2026-09-14T00:00:00.100Z' })
  expect(model.after).toHaveLength(3)
  expect(model.after[0].running).toBe(false)
  expect(model.after[2].running).toBe(true)
  expect(model.after[2].start).toBe(Date.parse('2026-09-14T00:00:00.100Z'))
  expect(model.after[1].end - model.after[1].start).toBe(600)
})

it('requires a new confirmation after a refreshed preview and shows errors inside the dialog', () => {
  const confirm = vi.fn().mockResolvedValue(undefined)
  render(<RecordingPreview preview={{ ...preview, stale: true }} timezone="UTC" onConfirm={confirm} onCancel={vi.fn()} error="Sync pending activity before recording." />)
  expect(screen.getByText(/Records changed/)).toBeInTheDocument()
  expect(screen.getByText('Sync pending activity before recording.')).toBeInTheDocument()
  expect(confirm).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Replace overlapping time' }))
  expect(confirm).toHaveBeenCalledOnce()
})


it('uses the linked task and meaningful type for an unnamed planned replacement', () => {
  render(<RecordingPreview preview={{ ...preview, conflicts: [], replacement: { ...preview.replacement, name: null } }}
    plannedBlock={{ name: null, task: { title: 'Prepare launch' }, task_type: { name: 'Writing' } }}
    timezone="UTC" onConfirm={vi.fn()} onCancel={vi.fn()} />)
  expect(screen.getByText('Prepare launch')).toBeInTheDocument()
  expect(screen.getByText('Writing')).toBeInTheDocument()
  expect(screen.queryByText('Unnamed activity')).not.toBeInTheDocument()
})
