import { ApiHttpError, fetchJson } from '../../lib/api/client'
import type { ActualBlock, TaskType } from '../../lib/api'

export const activityDevelopmentEnabled = (import.meta.env.DEV || import.meta.env.MODE === 'activity-review') && import.meta.env.VITE_ACTIVITY_TRACKING_DEV === '1'
export interface ActivityPlan {
  id: number; task_type_id: number; task_id: number | null; name: string | null; note: string | null; start_at: string; end_at: string
}
export interface ActivitySelection { task_id?: number | null; planned_block_id?: number | null; note?: string | null }
export interface CheckInState {
  enabled: boolean; threshold_minutes: number; generation: string; rearm: number; armed_at: string | null; active_at?: string | null
  question: { id: string; created_at: string; candidate_device?: string; candidate_operation_id?: string; delivery: { device_id: string; operation_id: string; at: string; dismissed: boolean } | null } | null
}
export interface CheckInEvent {
  action: 'candidate' | 'observe' | 'confirm' | 'delivery' | 'notification_dismiss'
  generation?: string; rearm?: number; question_id?: string; enabled?: boolean; threshold_minutes?: number
  capability?: 'supported' | 'unsupported' | 'approximate'; permission?: 'granted' | 'denied' | 'prompt' | 'unavailable'
  observed?: 'active' | 'idle' | 'locked' | 'unknown'; coverage_start?: string; coverage_end?: string
}
export interface ActivitySnapshot {
  check_in?: CheckInState
  protocol: 'activity-online-v1'; cursor: number; server_at: string; reporting_timezone: string
  current: ActualBlock | null; records: ActualBlock[]; provenance?: Record<string, string>; task_types?: TaskType[]
  reporting_timezone_initialized?: boolean
  plans?: ActivityPlan[]
  offline_ready?: boolean
  operation_outcomes?: Record<string, { device_id: string; outcome: string }>
  coverage?: { start: string; end: string | null; record_id: number | null; order: [string, string, number, string] }[]
  acknowledgement?: { operation_id: string; outcome: string } | null
}
export type ActivityCorrection = Partial<Pick<ActualBlock, 'start_at' | 'task_type_id' | 'task_id' | 'name' | 'note'>> & { end_at?: string }
interface Command {
  operation_id: string; device_id: string; sequence: number; action_at: string
  calibration: { server_at: string; offset_ms: number }; base_cursor: number
  effective: { mode: 'server_now' | 'instant' | 'range'; at?: string; end?: string }; target_id: number | null
  selection_snapshot?: boolean; task_id?: number | null; planned_block_id?: number | null; note?: string | null
  predecessor_id?: string; kind: 'start' | 'switch' | 'stop' | 'describe' | 'add' | 'edit' | 'delete' | 'check_in'; check_in?: CheckInEvent; target_source?: string; target_start_at?: string; task_type_id?: number; name?: string | null
}
export interface CheckInPreferences { enabled: boolean; thresholdMinutes: number }
interface Journal {
  notificationAttempts?: string[]
  checkInPreferences?: CheckInPreferences
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
  bootstrappedThisRun = false
  private startListeners = new Set<() => void>()
  subscribeTrackingStart = (listener: () => void) => { this.startListeners.add(listener); return () => { this.startListeners.delete(listener) } }
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
  recoveryData = () => this.journal.rejected ? JSON.stringify(this.journal.rejected, null, 2) : ''
  private retainRejected(commands: Command[]) {
    const previous = this.journal.rejected
    return [...(Array.isArray(previous) ? previous : previous ? [previous] : []), ...commands]
  }
  now = () => this.anchor ? this.anchor.server + performance.now() - this.anchor.monotonic : Date.now() + (this.journal.calibration?.offset_ms ?? 0)
  private project(): ActivitySnapshot | null {
    if (!this.journal.snapshot) return null
    const snapshot = { ...this.journal.snapshot, records: [...this.journal.snapshot.records] }
    for (const command of this.journal.outbox) {
      if (!snapshot.check_in) continue
      const event = command.check_in
      if (event?.action === 'observe' && event.observed === 'active' && event.generation === snapshot.check_in.generation && ['supported', 'approximate'].includes(event.capability ?? '') && event.permission === 'granted' && event.coverage_start && event.coverage_end && Date.parse(event.coverage_start) <= Date.parse(event.coverage_end) && Date.parse(event.coverage_end) <= this.now() + 5000) snapshot.check_in = { ...snapshot.check_in, active_at: new Date(Math.max(Date.parse(event.coverage_end), snapshot.check_in.active_at ? Date.parse(snapshot.check_in.active_at) : 0)).toISOString() }
      const prompt = snapshot.check_in
      if (event?.action === 'candidate' && event.generation === prompt.generation && event.rearm === prompt.rearm && !prompt.question && event.enabled !== false && ['supported', 'approximate'].includes(event.capability ?? '') && event.permission === 'granted' && ['idle', 'locked'].includes(event.observed ?? '') && event.coverage_start && event.coverage_end && Date.parse(event.coverage_end) <= this.now() + 5000 && prompt.armed_at) {
        const start = Math.max(Date.parse(event.coverage_start), Date.parse(prompt.armed_at), prompt.active_at ? Date.parse(prompt.active_at) : 0)
        if (Date.parse(event.coverage_end) - start >= (event.threshold_minutes ?? 60) * 60000) snapshot.check_in = { ...prompt, question: { id: `${prompt.generation}:${prompt.rearm}`, created_at: command.action_at, candidate_device: command.device_id, candidate_operation_id: command.operation_id, delivery: null } }
      }
      if (event?.action === 'confirm' && event.question_id === snapshot.check_in.question?.id) snapshot.check_in = { ...snapshot.check_in, question: null, rearm: snapshot.check_in.rearm + 1, armed_at: command.action_at }
      if (['start', 'switch', 'stop'].includes(command.kind)) snapshot.check_in = { ...snapshot.check_in, question: null, generation: `pending:${command.operation_id}`, rearm: 0, armed_at: command.action_at }
    }
    if (this.journal.outbox.length) return this.projectRanges(snapshot)
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
    let pieces: Piece[] = (snapshot.coverage?.length ? snapshot.coverage : snapshot.records.map(r => ({ start: r.start_at, end: r.end_at, record_id: r.id, order: ['', '', 0, 'baseline'] as [string, string, number, string] }))).map(p => ({ start: instant(p.start), end: p.end ? instant(p.end) : infinity,
      row: snapshot.records.find(r => r.id === p.record_id) ?? null,
      order: [p.order[0] ? instant(p.order[0]) : -infinity, p.order[1], p.order[2], p.order[3]] }))
    for (const command of this.journal.outbox) {
      if (command.kind === 'check_in' || command.effective.mode === 'server_now') continue
      const at = command.effective.at!
      const start = instant(at)
      const end = command.effective.end ? instant(command.effective.end) : infinity
      const target = pieces.find(p => p.row && (command.target_source ? (snapshot.provenance?.[p.row.id] ?? `baseline:${p.row.id}`) === command.target_source && p.start === instant(command.target_start_at!) : p.row.id === command.target_id))?.row ?? pieces.find(p => p.row?.id === command.target_id)?.row
      const historical = command.effective.mode === 'range'
      const order: Piece['order'] = [instant(command.action_at), command.device_id, command.sequence, command.operation_id]
      const type = snapshot.task_types?.find(t => t.id === command.task_type_id) ?? { id: command.task_type_id ?? 0, name: 'unspecified', created_at: at, updated_at: at }
      const row: ActualBlock | null = ['stop', 'delete'].includes(command.kind) ? null : { ...target, id: ['edit', 'describe'].includes(command.kind) ? target?.id ?? command.target_id! : -command.sequence, task_type_id: type.id, task_type: type, task_id: command.task_id ?? null, task: historical ? target?.task ?? null : null, name: command.name ?? null, note: command.note ?? null, planned_block_id: ['edit', 'describe'].includes(command.kind) && target?.task_type_id === command.task_type_id && target?.task_id === command.task_id ? target?.planned_block_id ?? null : command.planned_block_id ?? null, start_at: at, end_at: historical ? command.effective.end! : null, created_at: target?.created_at ?? at, updated_at: command.action_at }
      if (row) snapshot.provenance = { ...snapshot.provenance, [row.id]: ['edit', 'describe'].includes(command.kind) ? command.target_source! : command.operation_id }
      const oldStart = target ? instant(target.start_at) : start
      const oldEnd = target?.end_at ? instant(target.end_at) : end
      const boundaries = [...new Set([start, end, ...pieces.flatMap(p => [p.start, p.end])])].sort((a, b) => a < b ? -1 : a > b ? 1 : 0)
      if (boundaries.at(-1) !== infinity) boundaries.push(infinity)
      const next: Piece[] = []
      for (let i = 0; i < boundaries.length - 1; i++) {
        const a = boundaries[i], b = boundaries[i + 1]
        const previous = pieces.find(p => p.start <= a && p.end > a)
        const inRange = a >= start && a < end
        const removed = command.kind === 'edit' && a >= oldStart && a < oldEnd
        const winner = (inRange || removed) && (!previous || compare(order, previous.order) > 0) ? { row: inRange ? row : null, order } : previous
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
    const records = merged.filter(p => p.row).map(p => ({ ...p.row!, start_at: instant(p.row!.start_at) === p.start ? p.row!.start_at : format(p.start), end_at: p.end === infinity ? null : p.row!.end_at && instant(p.row!.end_at) === p.end ? p.row!.end_at : format(p.end) }))
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
            this.save({ ...this.journal, rejected: this.retainRejected(this.journal.outbox), outbox: [] })
        } else this.offline = true
        throw error
      }
      if (response.acknowledgement?.operation_id !== command.operation_id) throw new Error('Activity acknowledgement is missing')
      const applied = ['applied', 'superseded'].includes(response.acknowledgement.outcome)
      this.noteReconciliation(response)
      // Receipt removal and canonical state advance are one durable write.
      this.save({ ...this.journal, snapshot: this.newer(response) ? response : this.journal.snapshot,
        outbox: applied ? this.journal.outbox.slice(1) : [], rejected: applied ? this.journal.rejected : this.retainRejected(this.journal.outbox) })
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
        this.bootstrappedThisRun = true
        this.publish()
      })
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not refresh activity') }
  }
  checkInPreferences = (): CheckInPreferences => this.journal.checkInPreferences ?? { enabled: true, thresholdMinutes: 60 }
  async setCheckInPreferences(preferences: CheckInPreferences) {
    try {
      await this.exclusive(async () => {
        this.journal = this.readJournal()
        if (!Number.isInteger(preferences.thresholdMinutes) || preferences.thresholdMinutes < 15 || preferences.thresholdMinutes > 480) throw new Error('Choose 15 to 480 minutes.')
        this.save({ ...this.journal, checkInPreferences: preferences }); this.publish()
      }); return true
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not save settings'); return false }
  }
  async checkIn(event: CheckInEvent): Promise<string | false> {
    let saved: string | false = false
    const observed = this.state.snapshot?.check_in
    if (event.action === 'candidate') event = { ...event, enabled: this.checkInPreferences().enabled, threshold_minutes: this.checkInPreferences().thresholdMinutes }
    try {
      await this.exclusive(async () => {
        if (this.storageError) throw new Error(this.storageError)
        this.journal = this.readJournal()
        if (!this.journal.calibration || !observed) throw new Error('Connect to initialize check-ins.')
        const sequence = this.journal.sequence + 1
        const at = Math.max(this.now(), this.journal.lastAction + 1)
        const command: Command = { operation_id: crypto.randomUUID(), device_id: this.journal.device, sequence,
          action_at: new Date(at).toISOString(), calibration: this.journal.calibration, base_cursor: this.journal.snapshot!.cursor,
          target_id: this.state.snapshot?.current?.id ?? null, effective: { mode: 'instant', at: new Date(at).toISOString() }, kind: 'check_in',
          check_in: { generation: observed.generation, rearm: observed.rearm, ...event } }
        this.save({ ...this.journal, sequence, lastAction: at, outbox: [...this.journal.outbox, command] })
        saved = command.operation_id; this.publish(); await this.drain(); this.publish()
      })
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not save check-in') }
    return saved
  }
  async claimCheckInNotification(candidateOperation: string) {
    const question = this.state.snapshot?.check_in?.question
    if (!question || this.state.offline || this.state.pending || question.candidate_operation_id !== candidateOperation || question.candidate_device !== this.journal.device) return false
    let eligible = false
    await this.exclusive(async () => {
      this.journal = this.readJournal()
      if (this.journal.notificationAttempts?.includes(question.id)) return
      this.save({ ...this.journal, notificationAttempts: [...(this.journal.notificationAttempts ?? []), question.id] })
      eligible = true
    })
    if (!eligible) return false
    const operation = await this.checkIn({ action: 'delivery', question_id: question.id })
    const current = this.state.snapshot?.check_in?.question
    return !this.state.offline && !this.state.pending && current?.id === question.id && current.delivery?.operation_id === operation ? current : false
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
  async command(kind: 'start' | 'switch' | 'stop' | 'describe', taskTypeId?: number, name?: string, retryOnly = false, selection?: ActivitySelection, timing?: { at?: string; targetId: number }) {
    if (retryOnly) { await this.refresh(); return !this.state.pending && !this.state.error }
    if (kind === 'start') this.startListeners.forEach(listener => listener())
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
        if (['switch', 'describe'].includes(kind) && !taskTypeId && !selection?.task_id) throw new Error('Task Type is required')
        if (kind === 'describe' && (projected.current?.name || projected.current?.task_type.name !== 'unspecified')) throw new Error('Only an unknown Current Activity can be described')
        const latest = projected.records.reduce((value, row) => Math.max(value, Date.parse(row.end_at ?? row.start_at)), 0)
        const action = Math.max(this.journal.lastAction + 1, latest + 1, requestedAt)
        const at = new Date(action).toISOString()
        if (timing && (timing.targetId !== projected.current?.id || (timing.at != null && (Date.parse(timing.at) < Date.parse(projected.current.start_at) || Date.parse(timing.at) > requestedAt)))) throw new Error('Choose a time after the current activity started and no later than now. Review the current activity if it changed.')
        const predecessor = this.journal.outbox.findLast(c => ['start', 'switch', 'stop'].includes(c.kind)) ?? (observed.predecessor && ['start', 'switch', 'stop'].includes(observed.predecessor.kind) ? observed.predecessor : undefined)
        const command: Command = { operation_id: crypto.randomUUID(), device_id: this.journal.device, sequence: this.journal.sequence + 1,
          action_at: at, calibration: observed.calibration ?? this.journal.calibration, base_cursor: observed.snapshot?.cursor ?? this.journal.snapshot.cursor,
          effective: { mode: 'instant', at: kind === 'describe' ? projected.current!.start_at : timing?.at ?? at }, target_id: predecessor ? null : observed.current?.id ?? null,
          ...(predecessor ? { predecessor_id: predecessor.operation_id } : {}), kind,
          selection_snapshot: true, ...selection,
          ...(kind === 'describe' ? { task_id: projected.current!.task_id, planned_block_id: projected.current!.planned_block_id, note: projected.current!.note, target_id: projected.current!.id, target_source: projected.provenance?.[projected.current!.id] ?? this.journal.outbox.find(c => -c.sequence === projected.current!.id)?.operation_id ?? `baseline:${projected.current!.id}`, target_start_at: projected.current!.start_at } : {}),
          ...(taskTypeId == null ? {} : { task_type_id: taskTypeId }), ...(name ? { name } : {}) }
        this.save({ ...this.journal, sequence: command.sequence, lastAction: action, outbox: [...this.journal.outbox, command] })
        saved = true
        this.publish()
      })
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not save activity'); return false }
    if (saved) void this.refresh()
    return saved
  }
  async correct(kind: 'add' | 'edit' | 'delete', targetId: number | null, patch: ActivityCorrection = {}) {
    try {
      await this.exclusive(async () => {
        this.journal = this.readJournal()
        const snapshot = this.project()
        if (this.storageError) throw new Error(this.storageError)
        if (!snapshot?.offline_ready || !this.journal.calibration) throw new Error('Connect once to initialize Activity Tracking.')
        const target = snapshot.records.find(r => r.id === targetId)
        if (kind !== 'add' && (!target || !target.end_at)) throw new Error('Select an ended Actual Block. Use Switch or Stop for the Current Activity.')
        const data = { ...target, ...patch }
        const start = kind === 'delete' ? target!.start_at : data.start_at
        const end = kind === 'delete' ? target!.end_at : data.end_at
        if (!start || !end || !(Date.parse(start) < Date.parse(end)) || Date.parse(end) > this.now()) throw new Error('Choose a positive time range ending no later than now.')
        if (kind !== 'delete' && snapshot.records.some(r => r.id !== targetId && Date.parse(start) < Date.parse(r.end_at ?? '9999-01-01') && Date.parse(r.start_at) < Date.parse(end))) throw new Error('Activity overlaps recorded time. Adjust the other record first.')
        const action = Math.max(this.now(), this.journal.lastAction + 1)
        const localOrigin = this.journal.outbox.find(c => -c.sequence === targetId)
        const priorEdit = this.journal.outbox.findLast(c => c.kind === 'edit' && c.target_id === targetId)
        const command: Command = { operation_id: crypto.randomUUID(), device_id: this.journal.device, sequence: this.journal.sequence + 1,
          action_at: new Date(action).toISOString(), calibration: this.journal.calibration, base_cursor: this.journal.snapshot!.cursor,
          kind, effective: { mode: 'range', at: start, end }, target_id: targetId,
          ...(target ? { target_source: priorEdit?.target_source ?? localOrigin?.operation_id ?? snapshot.provenance?.[target.id] ?? `baseline:${target.id}`, target_start_at: target.start_at } : {}),
          task_type_id: data.task_type_id, task_id: data.task_id, name: data.name, note: data.note }
        this.save({ ...this.journal, sequence: command.sequence, lastAction: action, outbox: [...this.journal.outbox, command] })
        this.publish()
      })
      void this.refresh()
      return true
    } catch (error) { this.publish(error instanceof Error ? error.message : 'Could not save correction'); return false }
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
