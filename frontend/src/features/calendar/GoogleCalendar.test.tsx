import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { GoogleCalendar } from './GoogleCalendar'

const state = { configured: true, connected: true, account: 'me', selected: ['primary'], calendars: [{ id: 'primary', name: 'Primary' }], occurrences: [], timezone: 'UTC', last_success: null, error: null }
const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

it('requires a fresh preview after a concurrent plan change rejects confirmation', async () => {
  vi.spyOn(HTMLDialogElement.prototype, 'showModal').mockImplementation(function (this: HTMLDialogElement) { this.setAttribute('open', '') })
  vi.spyOn(HTMLDialogElement.prototype, 'close').mockImplementation(function (this: HTMLDialogElement) { this.removeAttribute('open') })
  const submitted: { fingerprint?: string }[] = []
  vi.stubGlobal('fetch', vi.fn(async (_url: unknown, init?: RequestInit) => {
    if (!init?.body) return response(state)
    const body = JSON.parse(String(init.body))
    submitted.push(body)
    return body.fingerprint
      ? response({ detail: 'The calendar or plan changed. Review a new preview before confirming.' }, 409)
      : response({ fingerprint: 'observed-plan', before: [], after: [], state })
  }))
  render(<GoogleCalendar />)
  fireEvent.click(await screen.findByRole('button', { name: 'Disconnect' }))
  expect(await screen.findByRole('dialog', { name: 'Review calendar changes' })).toBeVisible()
  expect(submitted).toHaveLength(1)
  expect(submitted[0].fingerprint).toBeUndefined()
  fireEvent.click(screen.getByRole('button', { name: 'Confirm changes' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('The calendar or plan changed')
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(submitted[1].fingerprint).toBe('observed-plan')
  fireEvent.click(screen.getByRole('button', { name: 'Disconnect' }))
  await screen.findByRole('dialog')
  expect(submitted[2].fingerprint).toBeUndefined()
})
