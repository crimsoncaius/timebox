import { useEffect, useState, useSyncExternalStore } from 'react'
import { ActivityRepository, getActivityRepository } from './activityRepository'

export function ReportingTimezoneSettings({ repository = getActivityRepository() }: { repository?: ActivityRepository }) {
  const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  useEffect(() => { void repository.refresh() }, [repository])
  const stored = state.snapshot?.reporting_timezone ?? ''
  return <ReportingTimezoneForm key={stored} stored={stored} error={state.error} repository={repository} />
}

/** Keyed on the stored zone: a zone saved here or on another device reseeds the draft by remounting. */
function ReportingTimezoneForm({ stored, error, repository }: {
  stored: string
  error: string | null
  repository: ActivityRepository
}) {
  const [zone, setZone] = useState(stored)
  const [saving, setSaving] = useState(false)
  return <form className="mb-6 rounded-xl border border-outline-variant/20 p-4" onSubmit={async event => {
    event.preventDefault(); setSaving(true); await repository.setReportingTimezone(zone.trim()); setSaving(false)
  }}>
    <label className="block">Reporting Time Zone<input aria-label="Reporting Time Zone" className="block mt-2 rounded border p-2 dark:bg-dark-surface" value={zone} onChange={event => setZone(event.target.value)} placeholder="Asia/Singapore" /></label>
    <p className="my-2 text-sm">Shared by all devices. Changing this recalculates daily shares; recorded times and elapsed duration stay the same. Travel does not change it.</p>
    <button disabled={saving || !zone || zone === stored}>Save time zone</button>
    {error && <p role="alert">{error}</p>}
  </form>
}
