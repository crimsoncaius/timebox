import { describe, expect, it } from 'vitest'
import { ApiHttpError, type ActualBlock, type DayRead } from '../../lib/api'
import { WorkModeExecution, type WorkModeEnvironment, type WorkModeStore, type WorkModeTransport } from './workModeExecution'
import type { StoredWorkMode } from './workModeState'

describe('WorkModeExecution', () => {
  it('preserves the session and Actual Block when exiting fails', async () => {
    class FailedExitTransport extends MemoryTransport {
      override async endActual(): Promise<ActualBlock> { throw new Error('Exit failed') }
    }
    const transport = new FailedExitTransport()
    const store = new MemoryStore()
    const execution = new WorkModeExecution(transport, store, new ManualEnvironment())
    const now = '2026-06-01T12:00:00Z'
    execution.setContext(day, () => now)
    execution.attachActive(day, now, actual(10, now))
    expect(await execution.exit(now)).toBe(false)
    expect(execution.state.session).not.toBeNull()
    expect(execution.state.actual?.id).toBe(40)
    expect(store.value?.activeActualId).toBe(40)
    execution.dispose()
  })

  it('rejects every entry path during planning without starting work or a warning', async () => {
    const transport = new MemoryTransport()
    const store = new MemoryStore()
    const environment = new ManualEnvironment()
    const execution = new WorkModeExecution(transport, store, environment)
    const now = '2026-06-01T12:00:00Z'
    execution.setContext(day, () => now)
    execution.setPlanningActive(true)
    expect(await execution.open(day, now)).toBe(false)
    expect(execution.begin(now)).toBeNull()
    execution.attachActive(day, now, actual(10, now))
    execution.show()
    expect(execution.state.session).toBeNull()
    expect(execution.state.visible).toBe(false)
    expect(execution.state.entryGuard).toBe(false)
    expect(store.value).toBeNull()
    expect(environment.active).toBe(false)
    execution.setPlanningActive(false)
    expect(await execution.open(day, now)).toBe(true)
    expect(execution.state.session).not.toBeNull()
    execution.dispose()
  })

  it('does not replay an in-flight activation after planning starts and finishes', async () => {
    const execution = new WorkModeExecution(new MemoryTransport(), new MemoryStore(), new ManualEnvironment())
    const now = '2026-06-01T12:00:00Z'
    execution.setContext(day, () => now)
    const opening = execution.open(day, now)
    execution.setPlanningActive(true)
    execution.setPlanningActive(false)
    expect(await opening).toBe(false)
    expect(execution.state.session).toBeNull()
  })

  it('begins, confirms for a minute, coordinates Actual time, and exits durably', async () => {
    const transport = new MemoryTransport()
    const store = new MemoryStore()
    const environment = new ManualEnvironment()
    let now = '2026-06-01T12:00:00Z'
    const execution = new WorkModeExecution(transport, store, environment)
    execution.setContext(day, () => now)

    execution.begin(now)
    expect(transport.started).toHaveLength(0)
    now = '2026-06-01T12:01:00Z'
    await environment.tick()

    expect(transport.started).toEqual([{ plannedBlockId: 10, startAt: '2026-06-01T12:00:00Z' }])
    expect(execution.state.actual?.id).toBe(40)
    expect(store.value?.activeActualId).toBe(40)

    now = '2026-06-01T12:05:00Z'
    expect(await execution.exit(now)).toBe(true)
    expect(transport.ended).toEqual([{ id: 40, endAt: '2026-06-01T12:05:00Z' }])
    expect(execution.state.session).toBeNull()
    expect(store.value).toBeNull()
  })

  it('prompts after a long absence then backfills completed planned intervals', async () => {
    const store = new MemoryStore({
      entryAt: '2026-06-01T12:00:00Z', lastConfirmedAt: '2026-06-01T12:00:00Z',
      lastObservedAt: '2026-06-01T12:01:00Z', confirmingPlannedBlockId: null,
      confirmationStartedAt: null, activeActualId: null, activePlannedBlockId: null, activePlannedEndAt: null,
    })
    const transport = new MemoryTransport()
    const environment = new ManualEnvironment()
    const execution = new WorkModeExecution(transport, store, environment)
    execution.setContext(day, () => '2026-06-01T12:20:00Z')

    execution.restoreIfAbsent('2026-06-01T12:20:00Z')
    expect(execution.state.restorePrompt).toBe(true)
    expect(await execution.continueAfterAbsence(day, '2026-06-01T12:20:00Z')).toBe(true)

    expect(transport.created).toEqual([{
      plannedBlockId: 10,
      startAt: '2026-06-01T12:00:00.000Z',
      endAt: '2026-06-01T12:10:00.000Z',
    }])
    expect(execution.state.restorePrompt).toBe(false)
  })

  it('keeps the session and reports transport failures', async () => {
    const transport = new MemoryTransport()
    transport.failStart = true
    const environment = new ManualEnvironment()
    let now = '2026-06-01T12:00:00Z'
    const execution = new WorkModeExecution(transport, new MemoryStore(), environment)
    execution.setContext(day, () => now)
    execution.begin(now)

    now = '2026-06-01T12:01:00Z'
    await environment.tick()

    expect(execution.state.session).not.toBeNull()
    expect(execution.state.actual).toBeNull()
    expect(execution.state.error).toBe('start failed')
  })

  it('owns one ticker and stops it on exit and disposal', async () => {
    const environment = new ManualEnvironment()
    const execution = new WorkModeExecution(new MemoryTransport(), new MemoryStore(), environment)
    execution.setContext(day, () => '2026-06-01T12:00:00Z')

    execution.begin('2026-06-01T12:00:00Z')
    expect(environment.active).toBe(true)
    await execution.exit('2026-06-01T12:00:30Z')
    expect(environment.active).toBe(false)

    execution.begin('2026-06-01T12:01:00Z')
    expect(environment.active).toBe(true)
    execution.dispose()
    expect(environment.active).toBe(false)
  })

  it('recovers when a persisted Actual Block was already removed', async () => {
    const store = new MemoryStore({
      entryAt: '2026-06-01T12:00:00Z', lastConfirmedAt: '2026-06-01T12:09:00Z',
      lastObservedAt: '2026-06-01T12:09:00Z', confirmingPlannedBlockId: null,
      confirmationStartedAt: null, activeActualId: 40, activePlannedBlockId: 10,
      activePlannedEndAt: '2026-06-01T12:10:00Z',
    })
    const transport = new MemoryTransport()
    transport.missingActualIds.add(40)
    const environment = new ManualEnvironment()
    let now = '2026-06-01T12:09:00Z'
    const execution = new WorkModeExecution(transport, store, environment)
    execution.setContext(day, () => now)
    await Promise.resolve()

    now = '2026-06-01T12:10:00Z'
    await environment.tick()

    expect(execution.state.error).toBeNull()
    expect(execution.state.actual).toBeNull()
    expect(execution.state.session).toMatchObject({
      activeActualId: null,
      activePlannedBlockId: null,
      activePlannedEndAt: null,
    })
    expect(await execution.exit('2026-06-01T12:11:00Z')).toBe(true)
    expect(store.value).toBeNull()
  })

  it('repairs a stale active marker while hydrating a restored session', async () => {
    const store = new MemoryStore({
      entryAt: '2026-06-01T12:00:00Z', lastConfirmedAt: '2026-06-01T12:09:00Z',
      lastObservedAt: '2026-06-01T12:09:00Z', confirmingPlannedBlockId: null,
      confirmationStartedAt: null, activeActualId: 40, activePlannedBlockId: 10,
      activePlannedEndAt: '2026-06-01T12:10:00Z',
    })
    const execution = new WorkModeExecution(new MemoryTransport(), store, new ManualEnvironment())

    await execution.hydrateActive()

    expect(execution.state.visible).toBe(true)
    expect(execution.state.error).toBeNull()
    expect(store.value).toMatchObject({
      activeActualId: null,
      activePlannedBlockId: null,
      activePlannedEndAt: null,
    })
  })

  it('does not restore a session after exit while reconciliation is in flight', async () => {
    const store = new MemoryStore({
      entryAt: '2026-06-01T12:00:00Z', lastConfirmedAt: '2026-06-01T12:09:00Z',
      lastObservedAt: '2026-06-01T12:09:00Z', confirmingPlannedBlockId: null,
      confirmationStartedAt: null, activeActualId: 40, activePlannedBlockId: 10,
      activePlannedEndAt: '2026-06-01T12:10:00Z',
    })
    const transport = new DeferredMissingTransport()
    const execution = new WorkModeExecution(transport, store, new ManualEnvironment())
    execution.setContext(day, () => '2026-06-01T12:10:00Z')

    await execution.hydrateActive()
    expect(await execution.exit('2026-06-01T12:11:00Z')).toBe(true)
    transport.releaseAsMissing()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(execution.state.session).toBeNull()
    expect(execution.state.visible).toBe(false)
    expect(store.value).toBeNull()
  })

  it('does not let an older instance overwrite a cleared shared store', async () => {
    const store = new MemoryStore({
      entryAt: '2026-06-01T12:00:00Z', lastConfirmedAt: '2026-06-01T12:09:00Z',
      lastObservedAt: '2026-06-01T12:09:00Z', confirmingPlannedBlockId: null,
      confirmationStartedAt: null, activeActualId: 40, activePlannedBlockId: 10,
      activePlannedEndAt: '2026-06-01T12:10:00Z',
    })
    const transport = new DeferredMissingTransport()
    const execution = new WorkModeExecution(transport, store, new ManualEnvironment())
    execution.setContext(day, () => '2026-06-01T12:10:00Z')

    store.save(null, '2026-06-01T12:00:00Z')
    transport.releaseAsMissing()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(store.value).toBeNull()
  })
})

class ManualEnvironment implements WorkModeEnvironment {
  private task: (() => void | Promise<void>) | null = null
  get active() { return this.task != null }
  scheduleEvery(_milliseconds: number, task: () => void | Promise<void>) {
    this.task = task
    return () => { this.task = null }
  }
  async tick() { await this.task?.() }
}

class MemoryStore implements WorkModeStore {
  value: StoredWorkMode | null
  retiredEntryAt: string | null = null
  constructor(value: StoredWorkMode | null = null) { this.value = value }
  load() { return this.value?.entryAt === this.retiredEntryAt ? null : this.value }
  save(value: StoredWorkMode | null, exitedEntryAt?: string) {
    if (exitedEntryAt) this.retiredEntryAt = exitedEntryAt
    if (!value || value.entryAt !== this.retiredEntryAt) this.value = value
  }
}

class MemoryTransport implements WorkModeTransport {
  active: ActualBlock | null = null
  started: Array<{ plannedBlockId: number; startAt: string }> = []
  created: Array<{ plannedBlockId: number; startAt: string; endAt: string }> = []
  ended: Array<{ id: number; endAt: string }> = []
  missingActualIds = new Set<number>()
  failStart = false
  async getActiveActual() { return this.active }
  async startActual(plannedBlockId: number, startAt: string) {
    if (this.failStart) throw new Error('start failed')
    this.started.push({ plannedBlockId, startAt })
    this.active = actual(plannedBlockId, startAt)
    return this.active
  }
  async createActual(plannedBlockId: number, startAt: string, endAt: string) {
    this.created.push({ plannedBlockId, startAt, endAt })
    return { ...actual(plannedBlockId, startAt), end_at: endAt }
  }
  async endActual(id: number, endAt: string): Promise<ActualBlock> {
    this.ended.push({ id, endAt })
    if (this.missingActualIds.has(id)) throw new ApiHttpError(404, 'Actual Block not found')
    const result = { ...(this.active ?? actual(10, endAt)), end_at: endAt }
    this.active = null
    return result
  }
}

class DeferredMissingTransport extends MemoryTransport {
  private rejectEnd: ((error: ApiHttpError) => void) | null = null

  override async endActual(id: number, endAt: string): Promise<ActualBlock> {
    this.ended.push({ id, endAt })
    return new Promise<ActualBlock>((_resolve, reject) => {
      this.rejectEnd = reject
    })
  }

  releaseAsMissing() {
    this.rejectEnd?.(new ApiHttpError(404, 'Actual Block not found'))
  }
}

const taskType = { id: 1, name: 'focus', created_at: '', updated_at: '' }
const day = {
  id: 1, date: '2026-06-01', start_hour: 8, end_hour: 20, show_full_day: false,
  created_at: '', updated_at: '', actual_blocks: [],
  meta: { timezone: 'UTC', today: '2026-06-01', server_now_iso: '2026-06-01T12:00:00Z' },
  time_blocks: [{
    id: 10, lane: 'planned', task_type_id: 1, task_type: taskType, task_id: null, task: null,
    note: null, start_minute: 12 * 60, end_minute: 12 * 60 + 10, created_at: '', updated_at: '',
  }],
} satisfies DayRead

function actual(plannedBlockId: number, startAt: string): ActualBlock {
  return {
    id: 40, task_type_id: 1, task_type: taskType, task_id: null, task: null, note: null,
    planned_block_id: plannedBlockId, start_at: startAt, end_at: null, created_at: '', updated_at: '',
  }
}
