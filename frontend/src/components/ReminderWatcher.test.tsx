import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ReminderWatcher } from './ReminderWatcher'

const reminderApi = vi.hoisted(() => ({
  dueReminders: vi.fn(),
  claimReminder: vi.fn(),
  acknowledgeReminder: vi.fn(),
  releaseReminder: vi.fn(),
}))

vi.mock('../lib/api', () => ({ api: reminderApi }))

const reminder = {
  id: 42,
  title: 'Write release notes',
  deadline_date: null,
  deadline_at: null,
  reminder_at: '2099-01-01T12:00:00Z',
}

describe('Task Reminder web delivery', () => {
  let visibility = 'visible'
  const originalVisibility = Object.getOwnPropertyDescriptor(document, 'visibilityState')

  beforeEach(() => {
    visibility = 'visible'
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility })
    reminderApi.dueReminders.mockResolvedValue([reminder])
    reminderApi.claimReminder.mockResolvedValue({ token: 'claim' })
    reminderApi.acknowledgeReminder.mockResolvedValue(undefined)
    reminderApi.releaseReminder.mockResolvedValue(undefined)
  })

  afterEach(() => {
    if (originalVisibility) Object.defineProperty(document, 'visibilityState', originalVisibility)
    else Reflect.deleteProperty(document, 'visibilityState')
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('uses only the in-app presentation while the tab is visible', async () => {
    const browserNotification = vi.fn()
    class MockNotification {
      static permission = 'granted'
      constructor(title: string) { browserNotification(title) }
      close() {}
    }
    vi.stubGlobal('Notification', MockNotification)

    render(<MemoryRouter><ReminderWatcher /></MemoryRouter>)

    expect(await screen.findByText('Write release notes')).toBeInTheDocument()
    await waitFor(() => expect(reminderApi.acknowledgeReminder).toHaveBeenCalledWith(42, reminder.reminder_at, 'claim'))
    expect(browserNotification).not.toHaveBeenCalled()
  })

  it('waits for a hidden tab without browser notifications to become visible', async () => {
    visibility = 'hidden'
    const browserNotification = vi.fn()
    Object.assign(browserNotification, { permission: 'denied' })
    vi.stubGlobal('Notification', browserNotification)
    render(<MemoryRouter><ReminderWatcher /></MemoryRouter>)

    await waitFor(() => expect(reminderApi.dueReminders).toHaveBeenCalled())
    expect(reminderApi.claimReminder).not.toHaveBeenCalled()
    visibility = 'visible'
    fireEvent(document, new Event('visibilitychange'))

    expect(await screen.findByText('Write release notes')).toBeInTheDocument()
    expect(reminderApi.claimReminder).toHaveBeenCalledWith(42, reminder.reminder_at)
  })

  it('uses a browser notification for a hidden tab when permitted', async () => {
    visibility = 'hidden'
    const browserNotification = vi.fn()
    class MockNotification {
      static permission = 'granted'
      constructor(title: string) { browserNotification(title) }
      close() {}
    }
    vi.stubGlobal('Notification', MockNotification)
    render(<MemoryRouter><ReminderWatcher /></MemoryRouter>)

    await waitFor(() => expect(reminderApi.acknowledgeReminder).toHaveBeenCalledWith(42, reminder.reminder_at, 'claim'))
    expect(browserNotification).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('Write release notes')).not.toBeInTheDocument()
  })
})
