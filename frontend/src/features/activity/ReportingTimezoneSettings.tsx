import { useEffect, useState, useSyncExternalStore } from 'react'
import { ActivityRepository, getActivityRepository } from './activityRepository'

export function ReportingTimezoneSettings({ repository = getActivityRepository() }: { repository?: ActivityRepository }) {
  const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const [zone, setZone] = useState('')
  const [saving, setSaving] = useState(false)
  useEffect(() => { void repository.refresh() }, [repository])
  useEffect(() => { setZone(state.snapshot?.reporting_timezone ?? '') }, [state.snapshot?.reporting_timezone])
  return <form className="mb-6 rounded-xl border border-outline-variant/20 p-4" onSubmit={async event => {
    event.preventDefault(); setSaving(true); await repository.setReportingTimezone(zone.trim()); setSaving(false)
  }}>
    <label className="block">Reporting Time Zone<input aria-label="Reporting Time Zone" className="block mt-2 rounded border p-2 dark:bg-dark-surface" value={zone} onChange={event => setZone(event.target.value)} placeholder="Asia/Singapore" /></label>
    <p className="my-2 text-sm">Shared by all devices. Changing this recalculates daily shares; recorded times and elapsed duration stay the same. Travel does not change it.</p>
    <button disabled={saving || !zone || zone === state.snapshot?.reporting_timezone}>Save time zone</button>
    {state.error && <p role="alert">{state.error}</p>}
  </form>
}
