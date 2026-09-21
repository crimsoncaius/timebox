import { useEffect, useState, useSyncExternalStore } from 'react'
import { ActivityRepository, getActivityRepository } from './activityRepository'

export function ReportingTimezoneSettings({ repository = getActivityRepository() }: { repository?: ActivityRepository }) {
  const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  useEffect(() => { void repository.refresh() }, [repository])
  const stored = state.snapshot?.reporting_timezone ?? ''
  return <ReportingTimezoneForm stored={stored} error={state.error} repository={repository} />
}

/** Follow shared updates until editing begins; refreshes must never replace a draft. */
function ReportingTimezoneForm({ stored, error, repository }: {
  stored: string
  error: string | null
  repository: ActivityRepository
}) {
  const [draft, setDraft] = useState<string | null>(null)
  const zone = draft ?? stored
  const [saving, setSaving] = useState(false)
  return <form className="mb-6 rounded-xl border border-outline-variant/20 p-4" onSubmit={async event => {
    event.preventDefault()
    setSaving(true)
    try {
      if (await repository.setReportingTimezone(zone.trim())) {
        setDraft(current => current === draft ? null : current)
      }
    } finally {
      setSaving(false)
    }
  }}>
    <label className="block">Reporting Time Zone<input aria-label="Reporting Time Zone" className="block mt-2 rounded border p-2 dark:bg-dark-surface" value={zone} onChange={event => setDraft(!saving && event.target.value === stored ? null : event.target.value)} placeholder="Asia/Singapore" /></label>
    <p className="my-2 text-sm">Shared by all devices. Changing this recalculates daily shares; recorded times and elapsed duration stay the same. Travel does not change it.</p>
    <button disabled={saving || !zone || zone === stored}>Save time zone</button>
    {error && <p role="alert">{error}</p>}
  </form>
}
