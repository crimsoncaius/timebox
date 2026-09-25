import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { RenameTaskTypeSheet } from './RenameTaskTypeSheet'
import { api, ApiHttpError, type TaskTypeMergePreview } from '../../lib/api'

const types = [1, 2, 3].map((id) => ({ id, name: ['travel', 'transportation', 'travel/train'][id - 1], created_at: '', updated_at: '', usage_count: 0 }))
const preview: TaskTypeMergePreview = {
  source_id: 1, source_name: 'travel', target_id: 2, target_name: 'transportation', preview_token: 'first',
  changes: [{ source_id: 1, source_name: 'travel', target_name: 'transportation', action: 'combine' }, { source_id: 3, source_name: 'travel/train', target_name: 'transportation/train', action: 'move' }],
  task_count: 8, completed_task_count: 2, archived_task_count: 1, trashed_task_count: 1, planned_block_count: 12, actual_block_count: 34, recurring_series_count: 2,
}
beforeEach(() => { HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') } })
afterEach(() => vi.restoreAllMocks())

it('previews collisions without renaming, expands descendants, and requires a second confirmation after a stale preview', async () => {
  const onSave = vi.fn(), onMerged = vi.fn()
  vi.spyOn(api, 'previewTaskTypeMerge').mockResolvedValueOnce(preview).mockResolvedValueOnce({ ...preview, preview_token: 'refreshed' })
  vi.spyOn(api, 'mergeTaskType').mockRejectedValueOnce(new ApiHttpError(409, 'Branches changed')).mockResolvedValue(preview)
  render(<RenameTaskTypeSheet type={types[0]} types={types} busy={false} error={null} onClose={() => {}} onSave={onSave} onMerged={onMerged} />)
  fireEvent.change(screen.getByLabelText('Task type path'), { target: { value: 'transportation' } })
  fireEvent.click(screen.getByRole('button', { name: 'Review merge' }))
  await screen.findByRole('button', { name: 'Confirm merge' })
  expect(onSave).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: /Branch changes/ })).toHaveAttribute('aria-expanded', 'true')
  fireEvent.click(screen.getByRole('button', { name: 'Confirm merge' }))
  await waitFor(() => expect(api.previewTaskTypeMerge).toHaveBeenCalledTimes(2))
  expect(onMerged).not.toHaveBeenCalled()
  expect(api.mergeTaskType).toHaveBeenCalledTimes(1)
  await waitFor(() => expect(screen.getByRole('button', { name: 'Confirm merge' })).toBeEnabled())
  fireEvent.click(screen.getByRole('button', { name: 'Confirm merge' }))
  await waitFor(() => expect(onMerged).toHaveBeenCalledOnce())
  expect(api.mergeTaskType).toHaveBeenLastCalledWith(expect.objectContaining({ preview_token: 'refreshed' }))
})

it('keeps a failed preview recoverable and prevents overlapping branches', async () => {
  vi.spyOn(api, 'previewTaskTypeMerge').mockRejectedValue(new Error('Offline'))
  render(<RenameTaskTypeSheet type={types[0]} types={types} busy={false} error={null} onClose={() => {}} onSave={() => {}} />)
  fireEvent.change(screen.getByLabelText('Task type path'), { target: { value: 'travel/train' } })
  expect(screen.getByRole('button', { name: 'Review merge' })).toBeDisabled()
  fireEvent.change(screen.getByLabelText('Task type path'), { target: { value: 'transportation' } })
  fireEvent.click(screen.getByRole('button', { name: 'Review merge' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Offline')
  expect(screen.getByLabelText('Task type path')).toHaveValue('transportation')
  expect(screen.queryByRole('button', { name: 'Confirm merge' })).not.toBeInTheDocument()
})
