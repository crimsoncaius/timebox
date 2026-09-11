import type { ActivityRepository } from './activityRepository'

const key = `timebox.focus.v1:${import.meta.env.VITE_API_BASE_URL ?? '/api'}`
/** Device-local surface state. Rejected entry is never queued for later. */
export class FocusController {
  private listeners = new Set<() => void>()
  private generation = 0
  private legacyCancelled = false
  state = { active: false, planning: false, entering: false, wake: true, error: '', recovery: '' }
  private storage: Storage
  constructor(storage: Storage) {
    this.storage = storage
    try { this.state.recovery = storage.getItem(`${key}:legacy-recovery`) ?? '' } catch { /* Keep the source in place if storage cannot be read. */ }
    try { const saved = JSON.parse(storage.getItem(key) ?? '{}'); this.state = { ...this.state, active: saved.active === true, wake: saved.wake !== false } } catch { /* Optional preference storage does not gate tracking. */ }
  }
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  getSnapshot = () => this.state
  private update(patch: Partial<typeof this.state>) {
    let saved = true
    this.state = { ...this.state, ...patch }
    try { this.storage.setItem(key, JSON.stringify({ active: this.state.active, wake: this.state.wake })) } catch { saved = false; this.state = { ...this.state, error: 'Focus preference could not be saved on this device.' } }
    this.listeners.forEach(listener => listener())
    return saved
  }
  setPlanning = (planning: boolean) => {
    if (planning === this.state.planning) return
    if (planning) this.generation++
    this.update({ planning, ...(planning ? { active: false, entering: false } : {}) })
  }
  exit = () => { this.legacyCancelled = true; this.generation++; this.update({ active: false, entering: false }) }
  setWake = (wake: boolean) => this.update({ wake })
  reconcile = (repository: ActivityRepository) => {
    const snapshot = repository.state.snapshot
    if (repository.bootstrappedThisRun && snapshot) {
      const sourceKey = 'timebox.work-mode.v2'
      const archiveKey = `${key}:legacy-recovery`
      try {
        const raw = this.storage.getItem(archiveKey) ?? this.storage.getItem(sourceKey)
        if (raw && !this.storage.getItem(`${archiveKey}:complete`)) {
          // Source is retained verbatim; observation timestamps never become time records.
          this.storage.setItem(archiveKey, raw)
          let valid = false
          try {
            const saved = JSON.parse(raw)
            const entry = Date.parse(saved.entryAt), confirmed = Date.parse(saved.lastConfirmedAt), observed = Date.parse(saved.lastObservedAt)
            valid = !!snapshot.current && saved.activeActualId === snapshot.current.id &&
              Number.isFinite(entry) && entry <= confirmed && confirmed <= observed && observed <= Date.parse(snapshot.server_at) + 5000 &&
              this.storage.getItem('timebox.work-mode-exited-entry.v1') !== saved.entryAt
          } catch { /* Invalid source remains recoverable but cannot restore Focus. */ }
          const saved = this.update({ active: valid && !this.state.planning && !this.legacyCancelled, recovery: raw,
            error: 'Work Mode was upgraded. Old local state is retained on this device; use Day add/edit to correct any unsaved time.' })
          if (saved) this.storage.setItem(`${archiveKey}:complete`, '1')
        }
      } catch { this.update({ error: 'Could not preserve old Work Mode data. Keep this device data and retry setup.' }) }
    }
    if (snapshot && !snapshot.current && this.state.active) this.exit()
  }
  async enter(repository: ActivityRepository) {
    this.legacyCancelled = true
    if (this.state.planning || this.state.entering || !repository.state.snapshot) return false
    const token = ++this.generation
    this.update({ entering: true })
    const started = !!repository.state.snapshot.current || await repository.command('start')
    if (token !== this.generation || this.state.planning) return false
    this.update({ active: started && !!repository.state.snapshot?.current, entering: false })
    return this.state.active
  }
}
let shared: FocusController | undefined
export const getFocusController = () => shared ??= new FocusController(localStorage)
