import { ApiHttpError, fetchJson } from '../../lib/api/client'
import type { ActualBlock, TaskType } from '../../lib/api'

export const activityDevelopmentEnabled = (import.meta.env.DEV || import.meta.env.MODE === 'activity-review') && import.meta.env.VITE_ACTIVITY_TRACKING_DEV === '1'
export interface ActivityPlan {
  id: number; task_type_id: number; task_id: number | null; name: string | null; note: string | null; start_at: string; end_at: string
}
export interface ActivitySelection { task_id?: number | null; planned_block_id?: number | null; note?: string | null }
export interface ActivitySnapshot {
  protocol: 'activity-online-v1'; cursor: number; server_at: string; reporting_timezone: string
  current: ActualBlock | null; records: ActualBlock[]; task_types?: TaskType[]
  reporting_timezone_initialized?: boolean
  plans?: ActivityPlan[]
  offline_ready?: boolean
  operation_outcomes?: Record<string, { device_id: string; outcome: string }>
  coverage?: { start: string; end: string | null; record_id: number | null; order: [string, string, number, string] }[]
  acknowledgement?: { operation_id: string; outcome: string } | null
}
interface Command {
  operation_id: string; device_id: string; sequence: number; action_at: string
  calibration: { server_at: string; offset_ms: number }; base_cursor: number
  effective: { mode: 'server_now' | 'instant'; at?: string }; target_id: number | null
  selection_snapshot?: boolean; task_id?: number | null; planned_block_id?: number | null; note?: string | null
  predecessor_id?: string; kind: 'start' | 'switch' | 'stop'; task_type_id?: number; name?: string
}
interface Journal {
  device: string; sequence: number; lastAction: number; pending: Command | null
  outbox: Command[]; rejected?: Command[] | Command | null; snapshot: ActivitySnapshot | null
  calibration?: Command['calibration']
}
const storageKey = `timebox.activity.online.v1:${import.meta.env.VITE_API_BASE_URL ?? '/api'}`
type Exclusive = <T>(work: () => Promise<T>) => Promise<T>
const browserExclusive: Exclusive = (work) => {
  if (!navigator.locks) return Promise.reject(new Error('Activity tracking requires browser storage locks. Open localhost or HTTPS.'))
  return navigator.locks.request(storageKey, work)
}
export class ActivityRepository {
  private listeners = new Set<() => void>()
  private journal: Journal = { device: crypto.randomUUID(), sequence: 0, lastAction: 0, pending: null, outbox: [], snapshot: null }
  private anchor: { server: number; monotonic: number } | null = null
  private offline = false
  private storageError: string | null = null
  private feedback: string | null = null
  state: { snapshot: ActivitySnapshot | null; pending: boolean; busy: boolean; error: string | null; offline: boolean; feedback?: string | null }
  private storage: Storage
  private exclusive: Exclusive
  constructor(storage: Storage, exclusive = browserExclusive) {
    this.storage = storage
    this.exclusive = exclusive
    try { this.journal = this.readJournal() } catch { this.storageError = 'Activity storage is unavailable. Recording has not started.' }
    this.state = { snapshot: this.project(), pending: this.journal.outbox.length > 0, busy: false, error: this.storageError, offline: false }
  }
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  getSnapshot = () => this.state
  now = () => this.anchor ? this.anchor.server + performance.now() - this.anchor.monotonic : Date.now() + (this.journal.calibration?.offset_ms ?? 0)
  private project(): ActivitySnapshot | null {
    if (!this.journal.snapshot) return null
    const snapshot = { ...this.journal.snapshot, records: [...this.journal.snapshot.records] }
    if (snapshot.coverage?.length && this.journal.outbox.length) return this.projectRanges(snapshot)
    for (const command of this.journal.outbox) {
      // Old online receipts have no client effective instant; retain the confirmed view until replay.
      if (command.effective.mode !== 'instant') continue
      const at = command.effective.at!
      if (snapshot.current) snapshot.records = snapshot.records.map(row => row.id === snapshot.current!.id ? { ...row, end_at: at } : row)
      snapshot.current = null
      if (command.kind !== 'stop') {
        const type = snapshot.task_types?.find(t => t.id === command.task_type_id) ?? { id: command.task_type_id ?? 0, name: command.task_type_id ? 'Activity' : 'unspecified', created_at: at, updated_at: at }
        snapshot.current = { id: -command.sequence, task_type_id: type.id, task_type: type, task_id: command.task_id ?? null, task: null, name: command.name ?? null, note: command.note ?? null, planned_block_id: command.planned_block_id ?? null, start_at: at, end_at: null, created_at: at, updated_at: at }
        snapshot.records.push(snapshot.current)
      }
    }
    return snapshot
  }
  private projectRanges(snapshot: ActivitySnapshot): ActivitySnapshot {
    // Keep the API's microsecond precision when comparing canonical coverage.
    // Date alone truncates it and could turn different actions into a false tie.
    const infinity = 8640000000000000000n
    const instant = (value: string) => BigInt(Date.parse(value)) * 1000n + BigInt((value.match(/\.(\d+)/)?.[1] ?? '').padEnd(6, '0').slice(3, 6))
    const format = (value: bigint) => {
      const millis = value >= 0n ? value / 1000n : (value - 999n) / 1000n
      const remainder = value - millis * 1000n
      const date = new Date(Number(millis)).toISOString()
      return remainder === 0n ? date : date.replace('Z', `${String(remainder).padStart(3, '0')}Z`)
    }
    type Piece = { start: bigint; end: bigint; row: ActualBlock | null; order: [bigint, string, number, string] }
    const compare = (a: Piece['order'], b: Piece['order']) => {
      for (let i = 0; i < 4; i++) { if (a[i] < b[i]) return -1; if (a[i] > b[i]) return 1 }
      return 0
    }
    let pieces: Piece[] = snapshot.coverage!.map(p => ({ start: instant(p.start), end: p.end ? instant(p.end) : infinity,
      row: snapshot.records.find(r => r.id === p.record_id) ?? null,
      order: [p.order[0] ? instant(p.order[0]) : -infinity, p.order[1], p.order[2], p.order[3]] }))
    for (const command of this.journal.outbox) {
      if (command.effective.mode !== 'instant') continue
      const at = command.effective.at!
      const start = instant(at)
      const order: Piece['order'] = [instant(command.action_at), command.device_id, command.sequence, command.operation_id]
      const type = snapshot.task_types?.find(t => t.id === command.task_type_id) ?? { id: command.task_type_id ?? 0, name: 'unspecified', created_at: at, updated_at: at }
      const row: ActualBlock | null = command.kind === 'stop' ? null : { id: -command.sequence, task_type_id: type.id, task_type: type, task_id: command.task_id ?? null, task: null, name: command.name ?? null, note: command.note ?? null, planned_block_id: command.planned_block_id ?? null, start_at: at, end_at: null, created_at: at, updated_at: at }
      const boundaries = [...new Set([start, ...pieces.flatMap(p => [p.start, p.end])])].sort((a, b) => a < b ? -1 : a > b ? 1 : 0)
      if (boundaries.at(-1) !== infinity) boundaries.push(infinity)
      const next: Piece[] = []
      for (let i = 0; i < boundaries.length - 1; i++) {
        const a = boundaries[i], b = boundaries[i + 1]
        const previous = pieces.find(p => p.start <= a && p.end > a)
        const winner = a >= start && (!previous || compare(order, previous.order) > 0) ? { row, order } : previous
        if (winner) next.push({ start: a, end: b, row: winner.row, order: winner.order })
      }
      pieces = next
    }
    const merged: Piece[] = []
    for (const piece of pieces) {
      const previous = merged.at(-1)
      if (previous && previous.end === piece.start && previous.row?.id === piece.row?.id && compare(previous.order, piece.order) === 0) previous.end = piece.end
      else merged.push({ ...piece })
    }
    const records = merged.filter(p => p.row).map(p => ({ ...p.row!, start_at: format(p.start), end_at: p.end === infinity ? null : format(p.end) }))
    return { ...snapshot, records, current: records.find(r => r.end_at === null) ?? null }
  }
  private publish(error: string | null = null, busy = false) {
    this.state = { snapshot: this.project(), pending: this.journal.outbox.length > 0, error, busy, offline: this.offline, feedback: this.feedback }
    this.listeners.forEach(listener => listener())
  }
  private readJournal(): Journal {
    const raw = this.storage.getItem(storageKey)
    if (!raw) return { device: crypto.randomUUID(), sequence: 0, lastAction: 0, pending: null, outbox: [], snapshot: null }
    const value = JSON.parse(raw) as Journal
    return { ...value, outbox: value.outbox ?? (value.pending ? [value.pending] : []), pending: null }
  }
  private save(journal: Journal) {
    try { this.storage.setItem(storageKey, JSON.stringify(journal)) } catch { throw new Error('Activity storage failed. Change was not saved on this device.') }
    this.journal = journal
  }
  private newer(snapshot: ActivitySnapshot) {
    if (snapshot.protocol !== 'activity-online-v1') throw new Error('Incompatible activity server')
    const previous = this.journal.snapshot
    return !previous || snapshot.cursor > previous.cursor || (snapshot.cursor === previous.cursor && Date.parse(snapshot.server_at) >= Date.parse(previous.server_at))
  }
  dismissFeedback = () => { this.feedback = null; this.publish(this.state.error) }
  private noteReconciliation(snapshot: ActivitySnapshot) {
    if (!this.newer(snapshot)) return
    if (Object.entries(snapshot.operation_outcomes ?? {}).some(([id, result]) =>
      result.device_id === this.journal.device && result.outcome === 'superseded' &&
      this.journal.snapshot?.operation_outcomes?.[id]?.outcome !== 'superseded')) {
      this.feedback = 'A newer change on another device updated this time.'
    }
  }
  private async drain() {
    while (this.journal.outbox.length) {
      const command = this.journal.outbox[0]
      let response: ActivitySnapshot
      try { response = await fetchJson<ActivitySnapshot>('/activity/commands', { method: 'POST', body: JSON.stringify(command) }) }
      catch (error) {
        if (error instanceof ApiHttpError && error.status === 422) {
          this.save({ ...this.journal, rejected: this.journal.outbox, outbox: [] })
        } else this.offline = true
        throw error
      }
      if (response.acknowledgement?.operation_id !== command.operation_id) throw new Error('Activity acknowledgement is missing')
      const applied = ['applied', 'superseded'].includes(response.acknowledgement.outcome)
      this.noteReconciliation(response)
      // Receipt removal and canonical state advance are one durable write.
      this.save({ ...this.journal, snapshot: this.newer(response) ? response : this.journal.snapshot,
        outbox: applied ? this.journal.outbox.slice(1) : [], rejected: applied ? this.journal.rejected : this.journal.outbox })
      this.offline = false
      if (!applied) throw new Error('Activity changed on another device. Pending changes were retained for review.')
    }
  }
  async refresh() {
    try {
      await this.exclusive(async () => {
        if (this.storageError) throw new Error(this.storageError)
        this.journal = this.readJournal()
        // Replay before reading, avoiding a transient rollback of pending intent.
        await this.drain()
      })
      let snapshot: ActivitySnapshot
      try {
        snapshot = await fetchJson<ActivitySnapshot>('/activity')
        if (snapshot.reporting_timezone_initialized === false) snapshot = await fetchJson<ActivitySnapshot>('/activity/reporting-timezone/initialize', { method: 'POST', body: JSON.stringify({ timezone: Intl.DateTimeFormat().resolvedOptions().timeZone }) })
      } catch (error) { this.offline = true; throw error }
      await this.exclusive(async () => {
        this.journal = this.readJournal()
        this.offline = false
        if (this.newer(snapshot)) {
          this.noteReconciliation(snapshot)
          const server = Date.parse(snapshot.server_at)
          this.save({ ...this.journal, snapshot, calibration: { server_at: snapshot.server_at, offset_ms: server - Date.now() } })
          this.anchor = { server, monotonic: performance.now() }
        }
        this.publish()
      })
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not refresh activity') }
  }
  async setReportingTimezone(timezone: string) {
    try {
      await this.exclusive(async () => {
        this.journal = this.readJournal()
        const snapshot = await fetchJson<ActivitySnapshot>('/activity/reporting-timezone', { method: 'PUT', body: JSON.stringify({ timezone }) })
        if (this.newer(snapshot)) this.save({ ...this.journal, snapshot })
        this.publish()
      })
      return true
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not save time zone'); return false }
  }
  async command(kind: Command['kind'], taskTypeId?: number, name?: string, retryOnly = false, selection?: ActivitySelection) {
    if (retryOnly) { await this.refresh(); return !this.state.pending && !this.state.error }
    let saved = false
    const requestedAt = this.now()
    if (kind === 'start' && !selection && taskTypeId == null && !name) {
      const plan = this.currentPlan()
      if (plan) { taskTypeId = plan.task_type_id; name = plan.name ?? undefined; selection = { task_id: plan.task_id, planned_block_id: plan.id, note: plan.note } }
    }
    const observed = { snapshot: this.journal.snapshot, calibration: this.journal.calibration,
      current: this.project()?.current, predecessor: this.journal.outbox.at(-1) }
    try {
      await this.exclusive(async () => {
        if (this.storageError) throw new Error(this.storageError)
        this.journal = this.readJournal()
        if (!this.journal.snapshot?.offline_ready || !this.journal.calibration) throw new Error('Connect once to an updated server to initialize Activity Tracking before recording offline.')
        if (this.journal.outbox.some(command => command.effective.mode === 'server_now')) throw new Error('Reconnect to confirm the previous online change first.')
        const projected = this.project()!
        if ((kind === 'start') === !!projected.current) throw new Error('Activity changed. Review the current activity.')
        if (kind === 'switch' && !taskTypeId && !selection?.task_id) throw new Error('Task Type is required')
        const latest = projected.records.reduce((value, row) => Math.max(value, Date.parse(row.end_at ?? row.start_at)), 0)
        const action = Math.max(this.journal.lastAction + 1, latest + 1, requestedAt)
        const at = new Date(action).toISOString()
        const predecessor = this.journal.outbox.at(-1) ?? observed.predecessor
        const command: Command = { operation_id: crypto.randomUUID(), device_id: this.journal.device, sequence: this.journal.sequence + 1,
          action_at: at, calibration: observed.calibration ?? this.journal.calibration, base_cursor: observed.snapshot?.cursor ?? this.journal.snapshot.cursor,
          effective: { mode: 'instant', at }, target_id: predecessor ? null : observed.current?.id ?? null,
          ...(predecessor ? { predecessor_id: predecessor.operation_id } : {}), kind,
          selection_snapshot: true, ...selection,
          ...(taskTypeId == null ? {} : { task_type_id: taskTypeId }), ...(name ? { name } : {}) }
        this.save({ ...this.journal, sequence: command.sequence, lastAction: action, outbox: [...this.journal.outbox, command] })
        saved = true
        this.publish()
      })
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not save activity'); return false }
    if (saved) void this.refresh()
    return saved
  }
  currentPlan = () => this.state.snapshot?.plans?.find(p => Date.parse(p.start_at) <= this.now() && this.now() < Date.parse(p.end_at))
  async trackTask(task: { id: number; title: string; task_type_id: number | null; recurrence_kind?: string | null; status: string }) {
    if (task.recurrence_kind === 'quota_parent' || task.status === 'completed') return false
    if (!this.state.snapshot) await this.refresh()
    const type = task.task_type_id ?? this.state.snapshot?.task_types?.find(t => t.name === 'unspecified')?.id
    const result = await this.command(this.state.snapshot?.current ? 'switch' : 'start', type, task.title, false, { task_id: task.id })
    if (result) { this.feedback = `Tracking ${task.title}`; this.publish(this.state.error) }
    return result
  }
  adoptPlan = (plan: ActivityPlan) => this.command(this.state.snapshot?.current ? 'switch' : 'start', plan.task_type_id, plan.name ?? undefined, false, { task_id: plan.task_id, planned_block_id: plan.id, note: plan.note })
  retry = () => this.refresh()
}
let shared: ActivityRepository | undefined
export const getActivityRepository = () => shared ??= new ActivityRepository(window.localStorage)
