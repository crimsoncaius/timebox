import { ContextualHelp } from '../../components/ContextualHelp'
import { useSyncExternalStore } from 'react'
import {
  SettingsRow,
  SettingsStatus,
  SettingsToggle,
  settingsButtonClassName,
  settingsInputClassName,
} from '../../components/SettingsControls'
import { getActivityRepository } from './activityRepository'
import { getBrowserCheckIns } from './browserCheckIns'

const thresholdPresets = [15, 30, 45, 60, 90, 120, 180, 240, 360, 480]

function thresholdLabel(minutes: number) {
  if (minutes < 60) return `${minutes} minutes`
  const hours = minutes / 60
  if (Number.isInteger(hours)) return hours === 1 ? '1 hour' : `${hours} hours`
  return `${Math.floor(hours)} h ${minutes % 60} min`
}

function detectionStatus(capability: string, permission: string) {
  if (capability === 'unsupported') return 'Not supported'
  if (permission === 'granted') return 'Allowed'
  if (permission === 'denied') return 'Blocked'
  if (permission === 'unavailable') return 'Unavailable'
  return 'Not allowed'
}

function notificationStatus(notification: string) {
  if (notification === 'granted') return 'Allowed'
  if (notification === 'denied') return 'Blocked'
  if (notification === 'unavailable') return 'Not supported'
  return 'Not allowed'
}

const blockedHint = ' Change this in the site\'s browser settings.'

export function CheckInSettings() {
  const repository = getActivityRepository()
  const adapter = getBrowserCheckIns()
  const detection = useSyncExternalStore(adapter.subscribe, adapter.getSnapshot)
  useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const settings = repository.checkInPreferences()
  const options = thresholdPresets.includes(settings.thresholdMinutes)
    ? thresholdPresets
    : [...thresholdPresets, settings.thresholdMinutes].sort((a, b) => a - b)
  const canRequestDetection = detection.capability === 'supported' && detection.permission === 'prompt'
  return (
    <>
      <SettingsRow label="Inactivity check-ins">
        <ContextualHelp title="Inactivity check-ins"><p>Ask whether an activity is still going after a quiet stretch.</p><p>Only native device inactivity counts. Time away from Timebox never proves inactivity.</p><p>Hidden, frozen, discarded or closed pages may not detect inactivity; returning starts fresh observation.</p><p>Recording continues regardless of detection or notification settings.</p></ContextualHelp>
        <SettingsToggle
          label="Inactivity check-ins"
          checked={settings.enabled}
          onChange={enabled => void repository.setCheckInPreferences({ ...settings, enabled })}
        />
      </SettingsRow>
      <SettingsRow
        label="Check in after"
        htmlFor="settings-check-in-threshold"
        disabled={!settings.enabled}
      >
        <ContextualHelp title="Check in after">How long the device must be idle before asking.</ContextualHelp>
        <select
          id="settings-check-in-threshold"
          className={`${settingsInputClassName} pr-8`}
          value={settings.thresholdMinutes}
          disabled={!settings.enabled}
          onChange={e => void repository.setCheckInPreferences({ ...settings, thresholdMinutes: Number(e.target.value) })}
        >
          {options.map(minutes => <option key={minutes} value={minutes}>{thresholdLabel(minutes)}</option>)}
        </select>
      </SettingsRow>
      <SettingsRow
        label="Device detection"
        description={detection.permission === 'denied' ? blockedHint.trim() : undefined}
        disabled={!settings.enabled}
        footer={settings.enabled && detection.detail && (
          <p className="mt-2 text-sm text-on-surface-variant dark:text-dark-on-surface-variant">{detection.detail}</p>
        )}
      >
        {canRequestDetection ? (
          <button className={settingsButtonClassName} disabled={!settings.enabled} onClick={() => void adapter.requestDetection()}>
            Allow detection
          </button>
        ) : (
          <SettingsStatus>{detectionStatus(detection.capability, detection.permission)}</SettingsStatus>
        )}
      </SettingsRow>

    </>
  )
}

export function BrowserNotificationSettings() {
  const adapter = getBrowserCheckIns()
  const detection = useSyncExternalStore(adapter.subscribe, adapter.getSnapshot)
  return (
    <SettingsRow
      label="Browser notifications"
      description={detection.notification === 'denied' ? blockedHint.trim() : undefined}
    >
      <ContextualHelp title="Browser notifications"><p>Used for check-in questions and Battle Plan reminders.</p><p>Check-in notifications need a connection when the question is created. Missed notifications aren't replayed after reconnecting.</p></ContextualHelp>
      {detection.notification === 'default' ? (
        <button className={settingsButtonClassName} onClick={() => void adapter.requestNotifications()}>
          Allow notifications
        </button>
      ) : (
        <SettingsStatus>{notificationStatus(detection.notification)}</SettingsStatus>
      )}
    </SettingsRow>
  )
}
