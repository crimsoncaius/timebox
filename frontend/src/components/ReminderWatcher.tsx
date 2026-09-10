import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type DueReminder } from '../lib/api'
import { TransientFeedback } from './TransientFeedback'

type Toast = DueReminder & { key: number }

export function ReminderWatcher() {
  const navigate = useNavigate()
  const [toasts, setToasts] = useState<Toast[]>([])
  const seen = useRef(new Set<number>())
  const nextKey = useRef(0)

  const dismiss = useCallback((key: number) => {
    setToasts((current) => current.filter((toast) => toast.key !== key))
  }, [])

  const deliver = useCallback(
    async (reminder: DueReminder) => {
      if (seen.current.has(reminder.id)) return
      seen.current.add(reminder.id)
      const key = nextKey.current++
      setToasts((current) => [...current, { ...reminder, key }])
      window.setTimeout(() => dismiss(key), 12_000)

      if ('Notification' in window && Notification.permission === 'granted') {
        const notification = new Notification('Battle Plan reminder', { body: reminder.title })
        notification.onclick = () => {
          window.focus()
          navigate(`/battle-plan?task=${reminder.id}`)
          notification.close()
        }
      }
      try {
        await api.acknowledgeReminder(reminder.id)
      } catch {
        // Keep the local guard for this app session; the API can retry next time the app opens.
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
    return () => {
      active = false
      window.clearInterval(timer)
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
