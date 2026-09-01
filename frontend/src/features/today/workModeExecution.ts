import type { ActualBlock, DayRead, TimeBlock } from '../../lib/api'
import { api } from '../../lib/api'
import { zonedLocalDateTimeToIso } from '../../lib/time'
import { readStoredWorkMode, writeStoredWorkMode, type StoredWorkMode } from './workModeState'

export interface WorkModeExecutionState {
  session: StoredWorkMode | null
  actual: ActualBlock | null
  visible: boolean
  busy: boolean
  error: string | null
  entryGuard: boolean
  restorePrompt: boolean
}

export interface WorkModeTransport {
  getActiveActual(): Promise<ActualBlock | null>
  startActual(plannedBlockId: number, startAt: string): Promise<ActualBlock>
  createActual(plannedBlockId: number, startAt: string, endAt: string): Promise<ActualBlock>
  endActual(actualBlockId: number, endAt: string): Promise<ActualBlock>
}

export interface WorkModeStore {
  load(): StoredWorkMode | null
  save(value: StoredWorkMode | null): void
}

export interface WorkModeEnvironment {
  scheduleEvery(milliseconds: number, task: () => void | Promise<void>): () => void
}

export const browserWorkModeEnvironment: WorkModeEnvironment = {
  scheduleEvery(milliseconds, task) {
    const id = window.setInterval(task, milliseconds)
    return () => window.clearInterval(id)
  },
}

export const browserWorkModeStore: WorkModeStore = {
  load: readStoredWorkMode,
  save: writeStoredWorkMode,
}

export const apiWorkModeTransport: WorkModeTransport = {
  getActiveActual: api.getActiveActualBlock,
  startActual: (plannedBlockId, startAt) => api.startActualBlock({ planned_block_id: plannedBlockId, start_at: startAt }),
  createActual: (plannedBlockId, startAt, endAt) => api.createActualBlock({ planned_block_id: plannedBlockId, start_at: startAt, end_at: endAt }),
  endActual: (actualBlockId, endAt) => api.patchActualBlock(actualBlockId, { end_at: endAt }),
}

/**
 * Client-local Work Mode module. Its interface is state plus lifecycle intents; callers
 * never coordinate timers, persistence, recovery, or Actual Blocks themselves.
 */
export class WorkModeExecution {
  private transport: WorkModeTransport
  private store: WorkModeStore
  private value: WorkModeExecutionState
  private transitioning = false
  private listeners = new Set<() => void>()
  private environment: WorkModeEnvironment
  private day: DayRead | null = null
  private clock: () => string = () => new Date().toISOString()
  private stopTicker: (() => void) | null = null

  constructor(
    transport: WorkModeTransport,
    store: WorkModeStore,
    environment: WorkModeEnvironment = browserWorkModeEnvironment,
  ) {
    this.transport = transport
    this.store = store
    this.environment = environment
    const session = store.load()
    this.value = {
      session,
      actual: null,
      visible: session != null,
      busy: false,
      error: null,
      entryGuard: false,
      restorePrompt: false,
    }
  }

  get state() { return this.value }
  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => { this.listeners.delete(listener) }
  }

  setContext(day: DayRead, clock: () => string) {
    this.day = day
    this.clock = clock
    if (this.value.session && !this.value.restorePrompt) {
      const now = clock()
      if (Date.parse(now) - Date.parse(this.value.session.lastObservedAt) > 10 * 60_000) {
        this.patch({ restorePrompt: true, visible: true })
        this.stopTicking()
      } else {
        void this.reconcile(day, now)
        this.ensureTicker()
      }
    }
  }

  dispose() {
    this.stopTicking()
    this.listeners.clear()
  }

  begin(entryAt: string) {
    let session: StoredWorkMode = {
      entryAt,
      lastConfirmedAt: entryAt,
      lastObservedAt: entryAt,
      confirmingPlannedBlockId: null,
      confirmationStartedAt: null,
      activeActualId: null,
      activePlannedBlockId: null,
      activePlannedEndAt: null,
    }
    if (this.day) {
      const { current } = workSelection(this.day, entryAt)
      if (current) {
        const start = blockInstant(this.day, current.start_minute)
        session = {
          ...session,
          confirmingPlannedBlockId: current.id,
          confirmationStartedAt: Date.parse(start) > Date.parse(entryAt) ? start : entryAt,
        }
      }
    }
    this.persist(session)
    this.patch({ visible: true, entryGuard: false, restorePrompt: false, error: null })
    this.ensureTicker()
    return session
  }

  show() { this.patch({ visible: true }) }
  hide() { this.patch({ visible: false }) }
  setEntryGuard(value: boolean) { this.patch({ entryGuard: value }) }

  restoreIfAbsent(now: string) {
    const session = this.store.load()
    if (!session) return
    this.persist(session)
    if (Date.parse(now) - Date.parse(session.lastObservedAt) > 10 * 60_000) {
      this.patch({ restorePrompt: true, visible: true })
      this.stopTicking()
    } else {
      this.ensureTicker()
    }
  }

  async open(day: DayRead, now: string) {
    const active = await this.transport.getActiveActual().catch(() => null)
    if (active) {
      this.attachActive(day, now, active)
      return
    }
    if (this.value.session) {
      this.show()
      return
    }
    const { current, next, nowMinute } = workSelection(day, now)
    if (current || (next && next.start_minute - nowMinute <= 10)) this.begin(now)
    else this.patch({ entryGuard: true })
  }

  attachActive(day: DayRead, now: string, actual: ActualBlock) {
    const session = this.value.session ?? this.begin(actual.start_at)
    const linked = actual.planned_block_id == null ? null : planned(day).find((block) => block.id === actual.planned_block_id)
    this.patch({ actual, visible: true, entryGuard: false, restorePrompt: false })
    this.persist({
      ...session,
      activeActualId: actual.id,
      activePlannedBlockId: actual.planned_block_id,
      activePlannedEndAt: linked ? blockInstant(day, linked.end_minute) : session.activePlannedEndAt,
      lastObservedAt: now,
      lastConfirmedAt: now,
    })
    this.ensureTicker()
  }

  async hydrateActive() {
    const session = this.value.session
    if (!session?.activeActualId || this.value.actual?.id === session.activeActualId) return
    const active = await this.transport.getActiveActual().catch(() => null)
    if (active?.id === session.activeActualId) this.patch({ actual: active })
  }

  private async reconcile(day: DayRead, now: string) {
    const original = this.value.session
    if (!original || this.value.restorePrompt || this.transitioning) return
    if (Date.parse(now) - Date.parse(original.lastObservedAt) > 10 * 60_000) {
      this.patch({ restorePrompt: true })
      this.stopTicking()
      return
    }
    this.transitioning = true
    try {
      let next = original
      const nowMs = Date.parse(now)
      if (next.activeActualId != null && next.activePlannedEndAt && nowMs >= Date.parse(next.activePlannedEndAt)) {
        await this.transport.endActual(next.activeActualId, next.activePlannedEndAt)
        this.patch({ actual: null })
        next = { ...next, activeActualId: null, activePlannedBlockId: null, activePlannedEndAt: null,
          confirmingPlannedBlockId: null, confirmationStartedAt: null, lastConfirmedAt: next.activePlannedEndAt }
      }

      if (next.activeActualId == null && next.confirmingPlannedBlockId != null && next.confirmationStartedAt) {
        const confirmed = planned(day).find((block) => block.id === next.confirmingPlannedBlockId)
        if (confirmed) {
          const blockStart = blockInstant(day, confirmed.start_minute)
          const blockEnd = blockInstant(day, confirmed.end_minute)
          const actualStart = Date.parse(blockStart) > Date.parse(next.entryAt) ? blockStart : next.entryAt
          const duration = Math.min(nowMs, Date.parse(blockEnd)) - Date.parse(next.confirmationStartedAt)
          if (duration < 60_000 && nowMs >= Date.parse(blockEnd)) {
            next = { ...next, confirmingPlannedBlockId: null, confirmationStartedAt: null }
          } else if (duration >= 60_000 && nowMs >= Date.parse(blockEnd)) {
            await this.transport.createActual(confirmed.id, actualStart, blockEnd)
            next = { ...next, confirmingPlannedBlockId: null, confirmationStartedAt: null, lastConfirmedAt: blockEnd }
          } else if (duration >= 60_000) {
            const actual = await this.transport.startActual(confirmed.id, actualStart)
            this.patch({ actual })
            next = { ...next, activeActualId: actual.id, activePlannedBlockId: confirmed.id,
              activePlannedEndAt: blockEnd, confirmingPlannedBlockId: null, confirmationStartedAt: null, lastConfirmedAt: now }
          }
        } else next = { ...next, confirmingPlannedBlockId: null, confirmationStartedAt: null }
      }

      const { current } = workSelection(day, now)
      if (next.activeActualId != null) {
        if (next.lastConfirmedAt !== now) next = { ...next, lastConfirmedAt: now }
      } else if (!current) {
        if (next.confirmingPlannedBlockId != null) next = { ...next, confirmingPlannedBlockId: null, confirmationStartedAt: null }
      } else if (next.activePlannedBlockId !== current.id) {
        const start = blockInstant(day, current.start_minute)
        if (next.confirmingPlannedBlockId !== current.id || !next.confirmationStartedAt) {
          next = { ...next, confirmingPlannedBlockId: current.id,
            confirmationStartedAt: Date.parse(start) > Date.parse(next.entryAt) ? start : next.entryAt }
        } else if (nowMs - Date.parse(next.confirmationStartedAt) >= 60_000) {
          const actualStart = Date.parse(start) > Date.parse(next.entryAt) ? start : next.entryAt
          const actual = await this.transport.startActual(current.id, actualStart)
          this.patch({ actual })
          next = { ...next, activeActualId: actual.id, activePlannedBlockId: current.id,
            activePlannedEndAt: blockInstant(day, current.end_minute), confirmingPlannedBlockId: null,
            confirmationStartedAt: null, lastConfirmedAt: now }
        }
      }
      if (nowMs - Date.parse(next.lastObservedAt) >= 30_000) next = { ...next, lastObservedAt: now }
      if (next !== original) this.persist(next)
    } catch (cause) {
      this.patch({ error: message(cause, 'Work Mode could not update Actual time') })
      const active = await this.transport.getActiveActual().catch(() => null)
      if (active) {
        this.patch({ actual: active })
        this.persist({ ...original, activeActualId: active.id, activePlannedBlockId: active.planned_block_id, lastObservedAt: now })
      }
    } finally {
      this.transitioning = false
      const latest = this.clock()
      const currentDay = this.day
      if (
        currentDay &&
        this.value.session &&
        !this.value.restorePrompt &&
        Date.parse(latest) > Date.parse(now)
      ) {
        void this.reconcile(currentDay, latest)
      }
    }
  }

  async exit(now: string) {
    const session = this.value.session
    if (!session) return false
    this.patch({ busy: true, error: null })
    this.stopTicking()
    try {
      const activeId = this.value.actual?.id ?? session.activeActualId
      if (activeId != null) await this.transport.endActual(activeId, now)
      this.patch({ actual: null, visible: false })
      this.persist(null)
      return true
    } catch (cause) {
      this.patch({ error: message(cause, 'Failed to exit Work Mode') })
      this.ensureTicker()
      return false
    } finally { this.patch({ busy: false }) }
  }

  async continueAfterAbsence(day: DayRead, now: string) {
    const original = this.value.session
    if (!original) return false
    this.patch({ busy: true, error: null })
    try {
      let next = original
      let ended: number | null = null
      if (next.activeActualId != null && next.activePlannedEndAt && Date.parse(next.activePlannedEndAt) <= Date.parse(now)) {
        ended = next.activePlannedBlockId
        await this.transport.endActual(next.activeActualId, next.activePlannedEndAt)
        next = { ...next, activeActualId: null, activePlannedBlockId: null, activePlannedEndAt: null }
        this.patch({ actual: null })
      }
      for (const block of planned(day)) {
        if (block.id === next.activePlannedBlockId || block.id === ended) continue
        const blockStart = blockInstant(day, block.start_minute)
        const blockEnd = blockInstant(day, block.end_minute)
        const startMs = Math.max(Date.parse(blockStart), Date.parse(original.lastConfirmedAt), Date.parse(original.entryAt))
        const endMs = Math.min(Date.parse(blockEnd), Date.parse(now))
        if (endMs <= startMs) continue
        if (Date.parse(blockEnd) <= Date.parse(now)) {
          await this.transport.createActual(block.id, new Date(startMs).toISOString(), new Date(endMs).toISOString())
        } else {
          const actual = await this.transport.startActual(block.id, new Date(startMs).toISOString())
          this.patch({ actual })
          next = { ...next, activeActualId: actual.id, activePlannedBlockId: block.id, activePlannedEndAt: blockEnd }
        }
      }
      this.persist({ ...next, lastObservedAt: now, lastConfirmedAt: now,
        confirmingPlannedBlockId: null, confirmationStartedAt: null })
      this.patch({ restorePrompt: false })
      this.ensureTicker()
      return true
    } catch (cause) {
      this.patch({ error: message(cause, 'Failed to backfill Work Mode') })
      return false
    } finally { this.patch({ busy: false }) }
  }

  async declineAfterAbsence() {
    const session = this.value.session
    if (!session) return
    this.patch({ busy: true, error: null })
    this.stopTicking()
    try {
      const activeId = this.value.actual?.id ?? session.activeActualId
      if (activeId != null) await this.transport.endActual(activeId, session.lastConfirmedAt)
      this.patch({ actual: null, restorePrompt: false, visible: false })
      this.persist(null)
    } catch (cause) { this.patch({ error: message(cause, 'Failed to restore Work Mode') }) }
    finally { this.patch({ busy: false }) }
  }

  private persist(session: StoredWorkMode | null) {
    this.store.save(session)
    this.patch({ session })
  }
  private ensureTicker() {
    if (this.stopTicker || !this.day || !this.value.session || this.value.restorePrompt) return
    this.stopTicker = this.environment.scheduleEvery(1_000, () => {
      const day = this.day
      if (day) return this.reconcile(day, this.clock())
    })
  }
  private stopTicking() {
    this.stopTicker?.()
    this.stopTicker = null
  }
  private patch(patch: Partial<WorkModeExecutionState>) {
    this.value = { ...this.value, ...patch }
    this.listeners.forEach((listener) => listener())
  }
}

function planned(day: DayRead) {
  return day.time_blocks.filter((block) => block.lane === 'planned').sort((a, b) => a.start_minute - b.start_minute)
}

function workSelection(day: DayRead, now: string): { current: TimeBlock | null; next: TimeBlock | null; nowMinute: number } {
  const nowMinute = minuteInTimeZone(now, day.meta.timezone)
  const blocks = planned(day)
  return {
    current: blocks.find((block) => block.start_minute <= nowMinute && nowMinute < block.end_minute) ?? null,
    next: blocks.find((block) => block.start_minute > nowMinute) ?? null,
    nowMinute,
  }
}

export function minuteInTimeZone(instant: string, timezone: string): number {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(instant))
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return Number(values.hour) * 60 + Number(values.minute)
}

export function blockInstant(day: DayRead, minute: number) {
  const hour = String(Math.floor(minute / 60)).padStart(2, '0')
  const mins = String(minute % 60).padStart(2, '0')
  return zonedLocalDateTimeToIso(`${day.date}T${hour}:${mins}`, day.meta.timezone)
}

function message(cause: unknown, fallback: string) { return cause instanceof Error ? cause.message : fallback }
