import { useState } from 'react'

export type DayViewPreferences = { calendar: boolean; tracking: boolean }
export const DAY_VIEW_STORAGE_KEY = 'timebox.dayView'
export const DEFAULT_DAY_VIEW: DayViewPreferences = { calendar: true, tracking: false }

export function readDayViewPreferences(): DayViewPreferences {
  try {
    const value = JSON.parse(localStorage.getItem(DAY_VIEW_STORAGE_KEY) ?? '{}')
    return Object.fromEntries(Object.entries(DEFAULT_DAY_VIEW).map(([key, fallback]) =>
      [key, typeof value?.[key] === 'boolean' ? value[key] : fallback],
    )) as DayViewPreferences
  } catch { return { ...DEFAULT_DAY_VIEW } }
}

export function useDayViewPreferences() {
  const [preferences, setPreferences] = useState(readDayViewPreferences)
  const [storageError, setStorageError] = useState<string | null>(null)
  function change(section: keyof DayViewPreferences, visible: boolean) {
    const next = { ...preferences, [section]: visible }
    setPreferences(next)
    try {
      localStorage.setItem(DAY_VIEW_STORAGE_KEY, JSON.stringify(next))
      setStorageError(null)
    } catch { setStorageError('This browser could not save your view preferences. Changes apply for this visit.') }
  }
  return { preferences, change, storageError }
}
