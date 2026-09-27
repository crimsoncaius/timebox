import { useCallback, useEffect, useRef, useState } from 'react'
import { BrowserNotificationSettings, CheckInSettings } from '../activity/CheckInSettings'
import { FocusWakeSettings } from '../activity/FocusMode'
import { ReportingTimezoneSettings } from '../activity/ReportingTimezoneSettings'
import { Layout } from '../../components/Layout'
import {
  SettingsRow,
  SettingsSection,
  SettingsToggle,
  settingsButtonClassName,
  settingsInputClassName,
} from '../../components/SettingsControls'
import { api, type SettingsRead } from '../../lib/api'
import { errorMessage } from '../../lib/errors'

function saveStatusClass(saveState: 'idle' | 'saving' | 'saved' | 'error') {
  if (saveState === 'error') return 'text-error'
  if (saveState === 'saving' || saveState === 'saved') return 'text-tertiary'
  return 'text-on-surface-variant'
}

type SettingsPatch = Partial<Pick<SettingsRead, 'start_hour' | 'end_hour' | 'show_full_day'>>
type SettingsField = keyof SettingsPatch
type AcceptedSetting = { requestId: number; value: SettingsRead[SettingsField] }

const settingsFields: SettingsField[] = ['start_hour', 'end_hour', 'show_full_day']

function hasSetting(patch: SettingsPatch, field: SettingsField) {
  return Object.prototype.hasOwnProperty.call(patch, field)
}

export function SettingsPage() {
  const [settings, setSettings] = useState<SettingsRead | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [hourErrors, setHourErrors] = useState<Partial<Record<'start_hour' | 'end_hour', string>>>({})
  const nextPatchRequestId = useRef(0)
  const activePatchRequests = useRef(0)
  const pendingPatches = useRef(new Map<number, SettingsPatch>())
  const acceptedSettings = useRef<Partial<Record<SettingsField, AcceptedSetting>>>({})
  const latestAcceptedMetadata = useRef({ requestId: 0, updatedAt: '' })
  const saveError = useRef<string | null>(null)

  const reconcileSettings = useCallback((current: SettingsRead | null) => {
    if (!current) return current
    const merged = { ...current, updated_at: latestAcceptedMetadata.current.updatedAt || current.updated_at }
    for (const field of settingsFields) {
      const accepted = acceptedSettings.current[field]
      let winningRequestId = accepted?.requestId ?? 0
      let winningValue = accepted?.value
      for (const [requestId, patch] of pendingPatches.current) {
        if (requestId > winningRequestId && hasSetting(patch, field)) {
          winningRequestId = requestId
          winningValue = patch[field]
        }
      }
      if (winningValue !== undefined) Object.assign(merged, { [field]: winningValue })
    }
    return merged
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const s = await api.getSettings()
      for (const field of settingsFields) {
        acceptedSettings.current[field] = { requestId: 0, value: s[field] }
      }
      latestAcceptedMetadata.current = { requestId: 0, updatedAt: s.updated_at }
      setSettings(s)
    } catch (e) {
      setError(errorMessage(e, 'Failed to load settings'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const patchSettings = async (body: SettingsPatch) => {
    const requestId = ++nextPatchRequestId.current
    pendingPatches.current.set(requestId, body)
    activePatchRequests.current += 1
    saveError.current = null
    setSaveState('saving')
    setError(null)
    setSettings(reconcileSettings)
    try {
      const next = await api.patchSettings(body)
      for (const field of settingsFields) {
        if (!hasSetting(body, field)) continue
        const accepted = acceptedSettings.current[field]
        if (!accepted || requestId > accepted.requestId) {
          acceptedSettings.current[field] = { requestId, value: next[field] }
        }
      }
      if (requestId > latestAcceptedMetadata.current.requestId) {
        latestAcceptedMetadata.current = { requestId, updatedAt: next.updated_at }
      }
    } catch (e) {
      const isRelevant = settingsFields.some((field) => {
        if (!hasSetting(body, field)) return false
        const acceptedRequestId = acceptedSettings.current[field]?.requestId ?? 0
        const laterPending = [...pendingPatches.current].some(([pendingId, patch]) => (
          pendingId > requestId && hasSetting(patch, field)
        ))
        return requestId > acceptedRequestId && !laterPending
      })
      if (isRelevant) {
        const message = errorMessage(e, 'Failed to save settings')
        saveError.current = message
        setError(message)
      }
    } finally {
      pendingPatches.current.delete(requestId)
      activePatchRequests.current -= 1
      setSettings(reconcileSettings)
      setSaveState(activePatchRequests.current > 0 ? 'saving' : saveError.current ? 'error' : 'saved')
    }
  }

  const saveHour = (field: 'start_hour' | 'end_hour', raw: string) => {
    if (!settings) return
    const value = Number(raw)
    const isStart = field === 'start_hour'
    const label = isStart ? 'Start hour' : 'End hour'
    const min = isStart ? 0 : 1
    const max = isStart ? 23 : 24
    let message: string | undefined
    if (raw.trim() === '') message = `${label} is required.`
    else if (!Number.isInteger(value) || value < min || value > max) {
      message = `${label} must be a whole hour from ${min} to ${max}.`
    } else if (isStart ? value >= settings.end_hour : value <= settings.start_hour) {
      message = 'Start hour must be before end hour.'
    }
    setHourErrors((current) => ({ ...current, [field]: message }))
    if (!message) void patchSettings({ [field]: value })
  }

  return (
    <Layout>
      <section className="mb-10 flex max-w-3xl flex-col gap-4 sm:mb-12 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="mb-2 font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
            Settings
          </h1>
          <p className="max-w-xl font-body text-lg font-light leading-relaxed text-on-surface-variant">
            Shared settings apply on every device. The rest stay on this one.
          </p>
        </div>
        <div
          className="inline-flex shrink-0 items-center gap-2 self-start rounded-full border border-outline-variant/15 bg-surface-container-low/80 px-3 py-1.5 text-xs font-medium dark:border-dark-outline-variant dark:bg-dark-surface-container/50"
          aria-live="polite"
        >
          <span
            className={[
              'h-1.5 w-1.5 shrink-0 rounded-full',
              saveState === 'saving' && 'animate-pulse bg-tertiary',
              saveState === 'saved' && 'bg-tertiary',
              saveState === 'error' && 'bg-error',
              saveState === 'idle' && 'bg-outline-variant/60',
            ]
              .filter(Boolean)
              .join(' ')}
            aria-hidden
          />
          <span className={['font-label tracking-tight', saveStatusClass(saveState)].join(' ')}>
            {saveState === 'saving' && 'Saving…'}
            {saveState === 'saved' && 'Saved'}
            {saveState === 'error' && 'Save failed'}
            {saveState === 'idle' && 'Up to date'}
          </span>
        </div>
      </section>

      {error && settings && (
        <div className="mb-6 max-w-3xl rounded-xl border border-error-container bg-error-container/20 px-4 py-3 text-sm text-on-error-container">
          {error}
        </div>
      )}

      <div className="max-w-3xl space-y-6">
        <SettingsSection
          id="settings-day"
          title="Day & time"
          scope="shared"
          description="How the timeline is framed and how daily totals are grouped."
        >
          {settings ? (
            <>
              <SettingsRow
                label="Start hour"
                htmlFor="settings-start-hour"
                description="First hour shown on the timeline (0–23)."
                disabled={settings.show_full_day}
                footer={hourErrors.start_hour && (
                  <p id="settings-start-hour-error" role="alert" className="mt-2 text-sm text-error">
                    {hourErrors.start_hour}
                  </p>
                )}
              >
                <input
                  id="settings-start-hour"
                  type="number"
                  min={0}
                  max={23}
                  className={`${settingsInputClassName} text-right`}
                  defaultValue={settings.start_hour}
                  key={`start-${settings.updated_at}`}
                  required
                  disabled={settings.show_full_day}
                  aria-invalid={!!hourErrors.start_hour}
                  aria-describedby={hourErrors.start_hour ? 'settings-start-hour-error' : undefined}
                  onBlur={(e) => saveHour('start_hour', e.target.value)}
                />
              </SettingsRow>
              <SettingsRow
                label="End hour"
                htmlFor="settings-end-hour"
                description="Exclusive end (1–24). 8–20 shows 8:00 through 19:59."
                disabled={settings.show_full_day}
                footer={hourErrors.end_hour && (
                  <p id="settings-end-hour-error" role="alert" className="mt-2 text-sm text-error">
                    {hourErrors.end_hour}
                  </p>
                )}
              >
                <input
                  id="settings-end-hour"
                  type="number"
                  min={1}
                  max={24}
                  className={`${settingsInputClassName} text-right`}
                  defaultValue={settings.end_hour}
                  key={`end-${settings.updated_at}`}
                  required
                  disabled={settings.show_full_day}
                  aria-invalid={!!hourErrors.end_hour}
                  aria-describedby={hourErrors.end_hour ? 'settings-end-hour-error' : undefined}
                  onBlur={(e) => saveHour('end_hour', e.target.value)}
                />
              </SettingsRow>
              <SettingsRow label="Show full 24 hours" description="Ignore the start and end hours and show the whole day.">
                <SettingsToggle
                  label="Show full 24 hours"
                  checked={settings.show_full_day}
                  onChange={(checked) => void patchSettings({ show_full_day: checked })}
                />
              </SettingsRow>
            </>
          ) : (
            <SettingsRow
              label="Day window"
              description={loading ? 'Loading…' : (error ?? 'Could not load the day window.')}
            >
              {!loading && (
                <button className={settingsButtonClassName} onClick={() => void load()}>
                  Try again
                </button>
              )}
            </SettingsRow>
          )}
          <ReportingTimezoneSettings />
        </SettingsSection>

        <SettingsSection id="settings-focus" title="Focus & check-ins" scope="device">
          <FocusWakeSettings />
          <CheckInSettings />
        </SettingsSection>

        <SettingsSection id="settings-notifications" title="Notifications" scope="device">
          <BrowserNotificationSettings />
        </SettingsSection>
      </div>
    </Layout>
  )
}
