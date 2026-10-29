import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { fetchJson } from '../../lib/api/client'
import { QuickFailures } from './QuickFailures'

vi.mock('../../lib/api/client', () => ({ fetchJson: vi.fn() }))
const failure = { id: 'failure', command: 'Lunch ten minutes ago', occurred_at: '2026-10-29T10:00:00Z',
  reason: 'Cannot reach Assistant.', status: 'failed', dismissed: false, recorded_at: null, review_id: null,
  history: [{ at: '2026-10-29T10:01:00Z', command: 'Lunch ten minutes ago', reason: 'First attempt failed.' }] }
beforeEach(() => { vi.resetAllMocks(); vi.mocked(fetchJson).mockResolvedValue({ items: [failure], next_before: null }) })

it('shows original words and failure, offers origin-only recovery and retains attempt details', async () => {
  render(<MemoryRouter><QuickFailures history={false} timezone="UTC" /></MemoryRouter>)
  expect(await screen.findByText('“Lunch ten minutes ago”')).toBeVisible()
  expect(screen.getByText('Cannot reach Assistant.')).toBeVisible()
  expect(screen.getByText(/Retry on the originating phone/)).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Retry command' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open Day' })).toHaveAttribute('href', '/day/2026-10-29')
  await userEvent.click(screen.getByText('Attempt history'))
  expect(screen.getByText('First attempt failed.')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
  await waitFor(() => expect(screen.queryByText('“Lunch ten minutes ago”')).not.toBeInTheDocument())
  expect(fetchJson).toHaveBeenLastCalledWith('/assistant/quick-failures/failure/dismiss', { method: 'POST', body: '{}' })
})

it('shows no-transcript capture failures without inventing words', async () => {
  vi.mocked(fetchJson).mockResolvedValue({ items: [{ ...failure, command: null, history: [] }], next_before: null })
  render(<MemoryRouter><QuickFailures history={false} timezone="UTC" /></MemoryRouter>)
  expect(await screen.findByText('No transcript captured')).toBeVisible()
  expect(screen.getByText('Speak again on the originating phone.')).toBeVisible()
})

it('explains uncertainty and links resolved attempts to the recorded day', async () => {
  vi.mocked(fetchJson).mockResolvedValue({ items: [{ ...failure, status: 'uncertain' }], next_before: null })
  const rendered = render(<MemoryRouter><QuickFailures history={false} timezone="UTC" /></MemoryRouter>)
  expect(await screen.findByText(/Waiting to check recording/)).toBeVisible()
  vi.mocked(fetchJson).mockResolvedValue({ items: [{ ...failure, status: 'recorded', recorded_at: '2026-10-28T22:00:00Z' }], next_before: null })
  rendered.rerender(<MemoryRouter><QuickFailures history={true} timezone="UTC" /></MemoryRouter>)
  expect(await screen.findByRole('link', { name: 'View recorded activity' })).toHaveAttribute('href', '/day/2026-10-28')
  expect(screen.queryByRole('button', { name: 'Dismiss' })).not.toBeInTheDocument()
})
