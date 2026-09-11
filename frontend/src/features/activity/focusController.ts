import type { ActivityRepository } from './activityRepository'

const key = `timebox.focus.v1:${import.meta.env.VITE_API_BASE_URL ?? '/api'}`
/** Device-local surface state. Rejected entry is never queued for later. */
export class FocusController {
  private listeners = new Set<() => void>()
  private generation = 0
  state = { active: false, planning: false, entering: false, wake: true, error: '' }
  private storage: Storage
  constructor(storage: Storage) {
    this.storage = storage
    try { const saved = JSON.parse(storage.getItem(key) ?? '{}'); this.state = { ...this.state, active: saved.active === true, wake: saved.wake !== false } } catch { /* Optional preference storage does not gate tracking. */ }
  }
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  getSnapshot = () => this.state
  private update(patch: Partial<typeof this.state>) {
    this.state = { ...this.state, ...patch }
    try { this.storage.setItem(key, JSON.stringify({ active: this.state.active, wake: this.state.wake })) } catch { this.state = { ...this.state, error: 'Focus preference could not be saved on this device.' } }
    this.listeners.forEach(listener => listener())
  }
  setPlanning = (planning: boolean) => {
    if (planning === this.state.planning) return
    if (planning) this.generation++
    this.update({ planning, ...(planning ? { active: false, entering: false } : {}) })
  }
  exit = () => { this.generation++; this.update({ active: false, entering: false }) }
  setWake = (wake: boolean) => this.update({ wake })
  reconcile = (repository: ActivityRepository) => { if (repository.state.snapshot && !repository.state.snapshot.current && this.state.active) this.exit() }
  async enter(repository: ActivityRepository) {
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
