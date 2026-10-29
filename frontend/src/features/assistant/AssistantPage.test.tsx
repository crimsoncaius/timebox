import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { AssistantPage } from './AssistantPage'
import { fetchJson } from '../../lib/api/client'
vi.mock('../../components/Layout', () => ({ Layout: ({ children }: { children: React.ReactNode }) => <main>{children}</main> }))
vi.mock('../../lib/api/client', () => ({ fetchJson: vi.fn(), fetchVoid: vi.fn(), protocolHeaders: () => ({}) }))
vi.mock('../activity/activityRepository', () => ({ getActivityRepository: () => ({ refresh: vi.fn() }) }))
const record = { id: 7, name: 'Workout', task_type_id: 2, task_type: { name: 'Gym' }, start_at: '2026-10-23T10:00:37Z', end_at: null }
const row = { id: 1, revision: 'saved-revision', record, receipt: { question: 'Gym now', action: 'start', record }, reviewed: false }
const page = { items: [row], next_before: null, unchecked_count: 1, timezone: 'UTC', task_types: [{ id: 2, name: 'Gym' }] }
beforeEach(() => {
  vi.clearAllMocks()
  vi.unstubAllGlobals()
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  vi.mocked(fetchJson).mockImplementation(async path => String(path).includes('quick-failures') ? { items: [], next_before: null } : page)
})
it('sends conversation messages in confirmation mode and explains how to confirm tracking', async () => {
  const user = userEvent.setup()
  vi.mocked(fetchJson).mockResolvedValue({ conversation_id: 'conversation' })
  const request = vi.fn(async (_url: string, options: RequestInit) => {
    const body = JSON.parse(String(options.body))
    expect(body.tracking_mode).toBe('confirm')
    const frames = ['tracking_proposal', 'completed'].map((kind, index) =>
      `event: ${kind}\ndata: ${JSON.stringify({ run_id: body.run_id, sequence: index + 1 })}\n\n`).join('')
    return new Response(frames)
  })
  vi.stubGlobal('fetch', request)
  render(<MemoryRouter><AssistantPage /></MemoryRouter>)
  expect(screen.queryByText(/Track now, review later/)).not.toBeInTheDocument()
  await user.type(screen.getByLabelText('Message Assistant'), 'Gym now')
  await user.click(screen.getByRole('button', { name: 'Send →' }))
  expect(await screen.findByText(/Tracking changes need confirmation/)).toBeInTheDocument()
  expect(request).toHaveBeenCalledTimes(1)
})
it('marks only the inspected revision and exposes a stale batch without losing the selection', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><AssistantPage /></MemoryRouter>)
  await user.click(screen.getByRole('button', { name: 'Review' }))
  await user.click(await screen.findByRole('checkbox', { name: 'Select Gym now' }))
  expect(screen.queryByText('Correct anything that doesn’t look right.')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Help: Review activity' }))
  expect(screen.getByText('Correct anything that doesn’t look right.')).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Close help: Review activity' }))
  expect(screen.getByText('Check recorded activities and commands that need attention.')).toBeVisible()
  expect(screen.getByRole('checkbox', { name: 'Select Gym now' })).toBeChecked()
  vi.mocked(fetchJson).mockRejectedValueOnce(new Error('An activity changed. Refresh.'))
  await user.click(screen.getByRole('button', { name: 'Mark reviewed (1)' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('An activity changed')
  expect(fetchJson).toHaveBeenLastCalledWith('/assistant/reviews/acknowledge', { method: 'POST', body: JSON.stringify({ items: [{ id: 1, revision: 'saved-revision' }] }) })
  expect(screen.getByRole('checkbox', { name: 'Select Gym now' })).toBeChecked()
})
it('retries the exact correction after a lost response and preserves unedited timestamp precision', async () => {
  const user = userEvent.setup()
  render(<MemoryRouter><AssistantPage /></MemoryRouter>)
  await user.click(screen.getByRole('button', { name: 'Review' }))
  await user.click(await screen.findByRole('button', { name: 'Correct' }))
  await user.clear(screen.getByLabelText('Name'))
  await user.type(screen.getByLabelText('Name'), 'Weights')
  vi.mocked(fetchJson).mockRejectedValueOnce(new Error('Connection lost'))
  await user.click(screen.getByRole('button', { name: 'Save correction' }))
  await screen.findByText('Connection lost')
  const first = vi.mocked(fetchJson).mock.calls.at(-1)!
  expect(JSON.parse(String(first[1]?.body)).start_at).toBe(record.start_at)
  vi.mocked(fetchJson).mockRejectedValueOnce(new Error('Connection lost again'))
  await user.click(screen.getByRole('button', { name: 'Save correction' }))
  await waitFor(() => expect(fetchJson).toHaveBeenLastCalledWith(...first))
})
