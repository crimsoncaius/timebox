import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type DueReminder } from '../lib/api'
import { TransientFeedback } from './TransientFeedback'

type Toast = DueReminder & { key: number }

export function ReminderWatcher() {
  const navigate = useNavigate()
  const [toasts, setToasts] = useState<Toast[]>([])
  const seen = useRef(new Set<string>())
  const inFlight = useRef(new Set<string>())
  const nextKey = useRef(0)

  const dismiss = useCallback((key: number) => {
    setToasts((current) => current.filter((toast) => toast.key !== key))
  }, [])

  const deliver = useCallback(
    async (reminder: DueReminder) => {
      const occurrence = `${reminder.id}:${reminder.reminder_at}`
      if (seen.current.has(occurrence) || inFlight.current.has(occurrence)) return
      const browserNotificationsAllowed = 'Notification' in window && Notification.permission === 'granted'
      if (document.visibilityState !== 'visible' && !browserNotificationsAllowed) return
      inFlight.current.add(occurrence)
      let token: string
      try {
        token = (await api.claimReminder(reminder.id, reminder.reminder_at)).token
      } catch {
        inFlight.current.delete(occurrence)
        return
      }
      try {
        if (document.visibilityState === 'visible') {
          const key = nextKey.current++
          setToasts((current) => [...current, { ...reminder, key }])
          window.setTimeout(() => dismiss(key), 12_000)
        } else if (browserNotificationsAllowed) {
          const notification = new Notification('Battle Plan reminder', { body: reminder.title })
          notification.onclick = () => {
            window.focus()
            navigate(`/battle-plan?task=${reminder.id}`)
            notification.close()
          }
        } else {
          await api.releaseReminder(reminder.id, reminder.reminder_at, token)
          return
        }
      } catch {
        try { await api.releaseReminder(reminder.id, reminder.reminder_at, token) } catch { /* Claim expires. */ }
        return
      } finally {
        inFlight.current.delete(occurrence)
      }
      seen.current.add(occurrence)
      try {
        await api.acknowledgeReminder(reminder.id, reminder.reminder_at, token)
      } catch {
        // Handoff already happened. Avoid another display in this tab session.
      }
    },
    [dismiss, navigate],
  )

  useEffect(() => {
    let active = true
    const poll = async () => {
      try {
        const reminders = await api.dueReminders()
        if (active) await Promise.all(reminders.map(deliver))
      } catch {
        // The feature remains quiet while the backend is unavailable.
      }
    }
    void poll()
    const timer = window.setInterval(() => void poll(), 60_000)
    const onVisible = () => { if (document.visibilityState === 'visible') void poll() }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      active = false
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [deliver])

  if (toasts.length === 0) return null
  return (
    <div className="feedback-reminders" aria-live="polite">
      {toasts.map((toast) => (
        <TransientFeedback
          key={toast.key}
          role="group"
          onDismiss={() => dismiss(toast.key)}
          dismissLabel="Dismiss reminder"
          title={
            <button
              type="button"
              className="feedback-reminder-link"
              onClick={() => navigate(`/battle-plan?task=${toast.id}`)}
            >
              <span className="feedback-reminder-label">Reminder</span>
              <span className="feedback-reminder-title">{toast.title}</span>
              <span className="feedback-reminder-open">Open task</span>
            </button>
          }
        />
      ))}
    </div>
  )
}
