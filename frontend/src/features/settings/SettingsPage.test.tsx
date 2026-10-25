import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SettingsPage } from './SettingsPage'

function response(data: unknown) {
  return new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } })
}

describe('SettingsPage', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => { globalThis.fetch = originalFetch; vi.restoreAllMocks() })

  it('groups settings by where they apply, with no day window or week boundary', async () => {
    globalThis.fetch = vi.fn(async () => response({})) as typeof fetch

    render(<MemoryRouter><SettingsPage /></MemoryRouter>)
    const day = await screen.findByRole('region', { name: 'Day & time' })
    expect(within(day).getByText('All devices')).toBeInTheDocument()
    expect(within(day).getByRole('combobox', { name: 'Reporting Time Zone' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Start hour')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('End hour')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Week starts on')).not.toBeInTheDocument()
    const focus = screen.getByRole('region', { name: 'Focus & check-ins' })
    expect(within(focus).getByText('This device')).toBeInTheDocument()
    expect(within(focus).getByRole('checkbox', { name: 'Keep display awake in Focus' })).toBeInTheDocument()
    expect(within(focus).getByLabelText('Check in after')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Notifications' })).toBeInTheDocument()
  })
})
