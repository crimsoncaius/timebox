import { useState, useSyncExternalStore } from 'react'
import { getActivityRepository } from './activityRepository'

export function CheckInSettings() {
  const repository = getActivityRepository()
  useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const settings = repository.checkInPreferences()
  const [minutes, setMinutes] = useState(String(settings.thresholdMinutes))
  return <section className="my-6 rounded-xl border border-outline-variant/20 p-4" aria-label="Inactivity settings">
    <h2 className="text-lg">Inactivity check-ins</h2>
    <label className="block my-3"><input type="checkbox" checked={settings.enabled} onChange={e => void repository.setCheckInPreferences({ ...settings, enabled: e.target.checked })} /> Enable on this device</label>
    <label>Inactivity minutes<input className="block my-2 rounded border p-2 dark:bg-dark-surface" type="number" min="15" max="480" value={minutes} onChange={e => setMinutes(e.target.value)} /></label>
    <button disabled={!Number.isInteger(Number(minutes)) || Number(minutes) < 15 || Number(minutes) > 480} onClick={() => void repository.setCheckInPreferences({ ...settings, thresholdMinutes: Number(minutes) })}>Save threshold</button>
    <p className="mt-3 text-sm">15 minutes–8 hours; default one hour. Device detection is not connected in this development slice. Recording remains available. Notification permission is separate.</p>
  </section>
}
