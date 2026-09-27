import { useEffect, useState, useSyncExternalStore } from 'react'
import { SettingsRow, settingsButtonClassName, settingsInputClassName } from '../../components/SettingsControls'
import { ActivityRepository, getActivityRepository } from './activityRepository'

const timeZoneSuggestions = (() => {
  try {
    return ['UTC', ...Intl.supportedValuesOf('timeZone')]
  } catch {
    return ['UTC']
  }
})()

function isKnownTimeZone(zone: string) {
  try {
    new Intl.DateTimeFormat('en', { timeZone: zone })
    return true
  } catch {
    return false
  }
}

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
  const [blurred, setBlurred] = useState(false)
  const trimmed = zone.trim()
  const unknown = trimmed !== '' && !isKnownTimeZone(trimmed)
  const showUnknown = unknown && blurred
  return (
    <SettingsRow
      label="Reporting Time Zone"
      htmlFor="settings-reporting-zone"
      description="Daily totals are grouped by this zone. Travel doesn't change it, and recorded times and durations stay the same."
      footer={(showUnknown || error) && (
        <p role="alert" className="mt-2 text-sm text-error">
          {showUnknown ? 'Choose a time zone from the list, such as Asia/Singapore.' : error}
        </p>
      )}
    >
      <form
        className="flex w-full min-w-0 items-center gap-2 sm:w-auto"
        onSubmit={async event => {
          event.preventDefault()
          setSaving(true)
          try {
            if (await repository.setReportingTimezone(trimmed)) {
              setDraft(current => current === draft ? null : current)
            }
          } finally {
            setSaving(false)
          }
        }}
      >
        <input
          id="settings-reporting-zone"
          aria-label="Reporting Time Zone"
          className={`${settingsInputClassName} flex-1 sm:w-56 sm:flex-none`}
          list="settings-reporting-zone-options"
          autoComplete="off"
          spellCheck={false}
          value={zone}
          aria-invalid={showUnknown}
          onBlur={() => setBlurred(true)}
          onChange={event => {
            setBlurred(false)
            setDraft(!saving && event.target.value === stored ? null : event.target.value)
          }}
          placeholder="Asia/Singapore"
        />
        <datalist id="settings-reporting-zone-options">
          {timeZoneSuggestions.map(option => <option key={option} value={option} />)}
        </datalist>
        <button className={settingsButtonClassName} disabled={saving || !trimmed || unknown || zone === stored}>
          {saving ? 'Saving…' : 'Save time zone'}
        </button>
      </form>
    </SettingsRow>
  )
}
