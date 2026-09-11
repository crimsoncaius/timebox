import { useState, useSyncExternalStore } from 'react'
import { getActivityRepository } from './activityRepository'
import { getBrowserCheckIns } from './browserCheckIns'

export function CheckInSettings() {
  const repository = getActivityRepository()
  const adapter = getBrowserCheckIns()
  const detection = useSyncExternalStore(adapter.subscribe, adapter.getSnapshot)
  useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const settings = repository.checkInPreferences()
  const [minutes, setMinutes] = useState(String(settings.thresholdMinutes))
  return <section className="my-6 rounded-xl border border-outline-variant/20 p-4" aria-label="Inactivity settings">
    <h2 className="text-lg">Inactivity check-ins</h2>
    <label className="block my-3"><input type="checkbox" checked={settings.enabled} onChange={e => void repository.setCheckInPreferences({ ...settings, enabled: e.target.checked })} /> Enable on this device</label>
    <label>Inactivity minutes<input className="block my-2 rounded border p-2 dark:bg-dark-surface" type="number" min="15" max="480" value={minutes} onChange={e => setMinutes(e.target.value)} /></label>
    <button disabled={!Number.isInteger(Number(minutes)) || Number(minutes) < 15 || Number(minutes) > 480} onClick={() => void repository.setCheckInPreferences({ ...settings, thresholdMinutes: Number(minutes) })}>Save threshold</button>
    <p className="mt-3 text-sm">15 minutes–8 hours; default one hour. Recording remains available with any permission choice.</p>
    <p className="mt-3">Device detection: {detection.capability === 'unsupported' ? 'unsupported in this browser' : detection.permission === 'denied' ? 'denied or revoked; change browser site settings to allow it' : detection.permission}.</p>
    {detection.capability === 'supported' && detection.permission !== 'granted' && <button className="my-2 underline" onClick={() => void adapter.requestDetection()}>Allow device detection</button>}
    <p className="text-sm">{settings.enabled ? detection.detail : 'Detection is off on this device.'} Only native device inactivity is used. Hidden, frozen, discarded or closed pages may not detect inactivity; returning starts fresh observation. Time away from Timebox never proves inactivity.</p>
    <p className="mt-3">Notifications: {detection.notification}. This permission is separate from device detection.</p>
    {detection.notification !== 'unavailable' && detection.notification !== 'granted' && <button className="my-2 underline" onClick={() => void adapter.requestNotifications()}>Allow check-in notifications</button>}
    <p className="text-sm">Optional notifications require a connection when the question is created. Missed notifications are not replayed on reconnect.</p>
  </section>
}
