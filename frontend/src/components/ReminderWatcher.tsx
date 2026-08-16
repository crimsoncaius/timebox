import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type DueReminder } from '../lib/api'

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
    <div className="fixed bottom-5 right-5 z-100 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2" aria-live="polite">
      {toasts.map((toast) => (
        <div
          key={toast.key}
          className="rounded-2xl bg-on-surface px-4 py-3 text-surface shadow-[0_16px_50px_rgba(0,0,0,0.22)] dark:bg-dark-on-surface dark:text-dark-background"
        >
          <div className="flex items-start gap-3">
            <button
              type="button"
              className="min-w-0 flex-1 text-left"
              onClick={() => navigate(`/battle-plan?task=${toast.id}`)}
            >
              <span className="block font-headline text-xs uppercase tracking-[0.16em] opacity-65">Reminder</span>
              <span className="mt-1 block truncate font-body text-sm">{toast.title}</span>
            </button>
            <button type="button" aria-label="Dismiss reminder" onClick={() => dismiss(toast.key)}>
              <span className="material-symbols-outlined text-[18px]" aria-hidden>close</span>
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
