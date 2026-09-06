export const WORK_MODE_STORAGE_KEY = 'timebox.work-mode.v2'
export const WORK_MODE_CHANGED_EVENT = 'timebox-work-mode-changed'
const WORK_MODE_EXITED_ENTRY_KEY = 'timebox.work-mode-exited-entry.v1'

export interface StoredWorkMode {
  entryAt: string
  lastConfirmedAt: string
  lastObservedAt: string
  confirmingPlannedBlockId: number | null
  confirmationStartedAt: string | null
  activeActualId: number | null
  activePlannedBlockId: number | null
  activePlannedEndAt: string | null
}

export function readStoredWorkMode(): StoredWorkMode | null {
  try {
    const raw = localStorage.getItem(WORK_MODE_STORAGE_KEY)
    if (!raw) return null
    const value = JSON.parse(raw) as Partial<StoredWorkMode>
    if (!value.entryAt || !value.lastConfirmedAt || !value.lastObservedAt) return null
    if (localStorage.getItem(WORK_MODE_EXITED_ENTRY_KEY) === value.entryAt) {
      localStorage.removeItem(WORK_MODE_STORAGE_KEY)
      return null
    }
    return {
      entryAt: value.entryAt,
      lastConfirmedAt: value.lastConfirmedAt,
      lastObservedAt: value.lastObservedAt,
      confirmingPlannedBlockId: value.confirmingPlannedBlockId ?? null,
      confirmationStartedAt: value.confirmationStartedAt ?? null,
      activeActualId: value.activeActualId ?? null,
      activePlannedBlockId: value.activePlannedBlockId ?? null,
      activePlannedEndAt: value.activePlannedEndAt ?? null,
    }
  } catch {
    return null
  }
}

export function writeStoredWorkMode(value: StoredWorkMode | null, exitedEntryAt?: string) {
  const exited = localStorage.getItem(WORK_MODE_EXITED_ENTRY_KEY)
  if (value) {
    if (exited !== value.entryAt) localStorage.setItem(WORK_MODE_STORAGE_KEY, JSON.stringify(value))
  } else {
    localStorage.removeItem(WORK_MODE_STORAGE_KEY)
    if (exitedEntryAt) localStorage.setItem(WORK_MODE_EXITED_ENTRY_KEY, exitedEntryAt)
  }
  window.dispatchEvent(new Event(WORK_MODE_CHANGED_EVENT))
}

export function beginStoredWorkMode(entryAt: string): StoredWorkMode {
  const value: StoredWorkMode = {
    entryAt,
    lastConfirmedAt: entryAt,
    lastObservedAt: entryAt,
    confirmingPlannedBlockId: null,
    confirmationStartedAt: null,
    activeActualId: null,
    activePlannedBlockId: null,
    activePlannedEndAt: null,
  }
  writeStoredWorkMode(value)
  return value
}
