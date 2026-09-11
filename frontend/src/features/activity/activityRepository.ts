import { ApiHttpError, fetchJson } from '../../lib/api/client'
import type { ActualBlock } from '../../lib/api'

export const activityDevelopmentEnabled = import.meta.env.DEV && import.meta.env.VITE_ACTIVITY_TRACKING_DEV === '1'

export interface ActivitySnapshot {
  protocol: 'activity-online-v1'
  cursor: number
  server_at: string
  reporting_timezone: string
  current: ActualBlock | null
  records: ActualBlock[]
  acknowledgement?: { operation_id: string; outcome: string } | null
}

interface Command {
  operation_id: string
  device_id: string
  sequence: number
  action_at: string
  calibration: { server_at: string; offset_ms: number }
  base_cursor: number
  effective: { mode: 'server_now' }
  target_id: number | null
  kind: 'start' | 'switch' | 'stop'
  task_type_id?: number
  name?: string
}

interface Journal {
  device: string
  sequence: number
  lastAction: number
  pending: Command | null
  rejected?: Command | null
  snapshot: ActivitySnapshot | null
}

const storageKey = `timebox.activity.online.v1:${import.meta.env.VITE_API_BASE_URL ?? '/api'}`
type Exclusive = <T>(work: () => Promise<T>) => Promise<T>
const browserExclusive: Exclusive = (work) => {
  if (!navigator.locks) return Promise.reject(new Error('Activity tracking requires browser storage locks. Open localhost or HTTPS.'))
  return navigator.locks.request(storageKey, work)
}

/** App-scoped confirmed projection and one durable, immutable online command. */
export class ActivityRepository {
  private listeners = new Set<() => void>()
  private journal: Journal
  private anchor: { server: number; monotonic: number; calibration: Command['calibration'] } | null = null
  state: { snapshot: ActivitySnapshot | null; pending: boolean; busy: boolean; error: string | null }

  private storage: Storage
  private exclusive: Exclusive
  constructor(storage: Storage, exclusive = browserExclusive) {
    this.storage = storage
    this.exclusive = exclusive
    this.journal = { device: crypto.randomUUID(), sequence: 0, lastAction: 0, pending: null, snapshot: null }
    let error: string | null = null
    try { this.journal = this.readJournal() } catch { error = 'Activity storage is unavailable. Recording has not started.' }
    this.state = { snapshot: this.journal.snapshot, pending: !!this.journal.pending, busy: false, error }
  }

  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  getSnapshot = () => this.state
  now = () => this.anchor ? this.anchor.server + performance.now() - this.anchor.monotonic : Date.now()
  private publish(error: string | null = null, busy = this.state.busy) {
    this.state = { snapshot: this.journal.snapshot, pending: !!this.journal.pending, error, busy }
    this.listeners.forEach((listener) => listener())
  }
  private readJournal(): Journal {
    const raw = this.storage.getItem(storageKey)
    return raw ? JSON.parse(raw) as Journal : this.journal
  }
  private save(journal: Journal) {
    this.storage.setItem(storageKey, JSON.stringify(journal))
    this.journal = journal
  }
  private accept(snapshot: ActivitySnapshot) {
    if (snapshot.protocol !== 'activity-online-v1') throw new Error('Incompatible activity server')
    const previous = this.journal.snapshot
    if (previous && (snapshot.cursor < previous.cursor || (snapshot.cursor === previous.cursor && Date.parse(snapshot.server_at) < Date.parse(previous.server_at)))) return false
    this.save({ ...this.journal, snapshot })
    return true
  }

  async refresh() {
    try {
      const snapshot = await fetchJson<ActivitySnapshot>('/activity')
      await this.exclusive(async () => {
        this.journal = this.readJournal()
        if (!this.accept(snapshot)) { this.publish(this.state.error); return }
        const server = Date.parse(snapshot.server_at)
        this.anchor = { server, monotonic: performance.now(), calibration: { server_at: snapshot.server_at, offset_ms: server - Date.now() } }
        this.publish(this.journal.pending ? 'Change not confirmed. Retry to check the saved result.' : null)
      })
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not refresh activity') }
  }

  async command(kind: Command['kind'], taskTypeId?: number, name?: string, retryOnly = false) {
    if (this.state.busy) return false
    this.publish(null, true)
    try {
      return await this.exclusive(async () => {
        this.journal = this.readJournal()
        if (retryOnly && !this.journal.pending) { this.publish(null, false); return false }
        if (!retryOnly && this.journal.pending) throw new Error('Retry the unconfirmed change before recording another activity')
        if (!this.journal.pending) {
          if (!this.anchor || !this.journal.snapshot) throw new Error('Refresh activity before recording')
          const action = Math.max(this.journal.lastAction, this.anchor.server + performance.now() - this.anchor.monotonic)
          const sequence = this.journal.sequence + 1
          const pending: Command = {
            operation_id: crypto.randomUUID(), device_id: this.journal.device, sequence,
            action_at: new Date(action).toISOString(), calibration: this.anchor.calibration,
            base_cursor: this.journal.snapshot.cursor, effective: { mode: 'server_now' },
            target_id: this.journal.snapshot.current?.id ?? null, kind,
            ...(taskTypeId == null ? {} : { task_type_id: taskTypeId }), ...(name ? { name } : {}),
          }
          // Persist before sending. A failed write must never look like recording.
          this.save({ ...this.journal, sequence, lastAction: action, pending })
        }
        const pending = this.journal.pending!
        let response: ActivitySnapshot
        try {
          response = await fetchJson<ActivitySnapshot>('/activity/commands', { method: 'POST', body: JSON.stringify(pending) })
        } catch (error) {
          // 422 is a definitive rejection with no transaction committed. Retain
          // the rejected intent for diagnosis, but do not trap the user in retry.
          if (error instanceof ApiHttpError && error.status === 422) this.save({ ...this.journal, rejected: pending, pending: null })
          throw error
        }
        if (response.acknowledgement?.operation_id !== pending.operation_id) throw new Error('Activity acknowledgement is missing')
        this.accept(response)
        this.save({ ...this.journal, pending: null })
        this.publish(response.acknowledgement.outcome === 'conflict' ? 'Activity changed on another device. Review the current activity before trying again.' : null, false)
        return response.acknowledgement.outcome === 'applied'
      })
    } catch (error) {
      this.publish(error instanceof Error ? error.message : 'Change not confirmed', false)
      return false
    }
  }
  retry = () => this.command(this.journal.pending?.kind ?? 'start', undefined, undefined, true)
}

let shared: ActivityRepository | undefined
export const getActivityRepository = () => shared ??= new ActivityRepository(window.localStorage)
