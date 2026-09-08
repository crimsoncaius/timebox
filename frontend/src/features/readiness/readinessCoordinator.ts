import { createContext, useContext, useState, useSyncExternalStore } from 'react'
import { api, type BattleTask } from '../../lib/api'

export type ReadinessState = {
  confirmed: boolean
  desired: boolean
  pending: boolean
  failedDesired: boolean | null
  error: string | null
}

type MutableReadinessState = ReadinessState & {
  running: boolean
  newestVersion: number | null
}

function initialState(task: Pick<BattleTask, 'ready_to_plan' | 'version'>): MutableReadinessState {
  const ready = Boolean(task.ready_to_plan)
  return {
    confirmed: ready,
    desired: ready,
    pending: false,
    failedDesired: null,
    error: null,
    running: false,
    newestVersion: task.version ?? null,
  }
}

function findTask(tasks: BattleTask[], taskId: number): BattleTask | null {
  for (const task of tasks) {
    if (task.id === taskId) return task
    const session = findTask(task.session_tasks ?? [], taskId)
    if (session) return session
  }
  return null
}

export class ReadinessCoordinator {
  private readonly entries = new Map<number, MutableReadinessState>()
  private readonly listeners = new Set<() => void>()
  private readonly persistenceLoopsByTask = new Map<number, Promise<void>>()
  private revision = 0

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  getRevision = () => this.revision

  private emit() {
    this.revision += 1
    this.listeners.forEach((listener) => listener())
  }

  observeTasks(tasks: BattleTask[]) {
    let changed = false
    const observe = (task: BattleTask) => {
      const ready = Boolean(task.ready_to_plan)
      const current = this.entries.get(task.id)
      if (!current) {
        this.entries.set(task.id, initialState(task))
        changed = true
      } else if (current.newestVersion == null || (task.version != null && task.version >= current.newestVersion)) {
        const wasConfirmed = current.confirmed
        const wasDesired = current.desired
        const wasPending = current.pending
        const hadFailedDesired = current.failedDesired
        current.newestVersion = task.version ?? current.newestVersion
        current.confirmed = ready
        if (!current.pending && !current.running) {
          current.desired = ready
          if (current.failedDesired === ready) {
            current.failedDesired = null
            current.error = null
          }
        }
        current.pending = current.running || current.desired !== current.confirmed
        changed = wasConfirmed !== current.confirmed
          || wasDesired !== current.desired
          || wasPending !== current.pending
          || hadFailedDesired !== current.failedDesired
          || changed
      }
      task.session_tasks?.forEach(observe)
    }
    tasks.forEach(observe)
    if (changed) this.emit()
  }

  stateFor(taskId: number): ReadinessState {
    const state = this.entries.get(taskId)
    return state
      ? {
          confirmed: state.confirmed,
          desired: state.desired,
          pending: state.pending,
          failedDesired: state.failedDesired,
          error: state.error,
        }
      : { confirmed: false, desired: false, pending: false, failedDesired: null, error: null }
  }

  projectTask(task: BattleTask): BattleTask {
    const state = this.entries.get(task.id)
    return {
      ...task,
      ready_to_plan: state?.desired ?? Boolean(task.ready_to_plan),
      session_tasks: task.session_tasks?.map((session) => this.projectTask(session)),
    }
  }

  projectTasks(tasks: BattleTask[]) {
    return tasks.map((task) => this.projectTask(task))
  }

  isSchedulable(taskId: number) {
    const state = this.entries.get(taskId)
    return state ? state.desired && !state.pending : false
  }

  async setReadyToPlan(task: Pick<BattleTask, 'id' | 'ready_to_plan'>, ready: boolean) {
    if (!this.entries.has(task.id)) {
      this.entries.set(task.id, initialState(task))
      this.emit()
    }
    const state = this.entries.get(task.id)!
    state.failedDesired = null
    state.error = null
    state.desired = ready
    state.pending = state.running || state.desired !== state.confirmed
    this.emit()

    const running = this.persistenceLoopsByTask.get(task.id)
    if (running) return running

    const persistenceLoop = this.persist(task.id)
      .finally(() => this.persistenceLoopsByTask.delete(task.id))
    this.persistenceLoopsByTask.set(task.id, persistenceLoop)
    return persistenceLoop
  }

  retry(task: Pick<BattleTask, 'id' | 'ready_to_plan' | 'version'>) {
    const failedDesired = this.entries.get(task.id)?.failedDesired
    if (failedDesired == null) return Promise.resolve()
    return this.setReadyToPlan(task, failedDesired)
  }

  private mergeConfirmed(state: MutableReadinessState, task: BattleTask) {
    if (state.newestVersion != null && (task.version == null || task.version < state.newestVersion)) return false
    state.confirmed = Boolean(task.ready_to_plan)
    state.newestVersion = task.version ?? state.newestVersion
    return true
  }

  private async reconcile(taskId: number) {
    try {
      const result = await api.listBattleTasks('active')
      return { available: true, task: findTask(result.items, taskId) }
    } catch {
      return { available: false, task: null }
    }
  }

  private async persist(taskId: number) {
    const state = this.entries.get(taskId)!
    state.running = true
    try {
      while (state.desired !== state.confirmed) {
        const target = state.desired
        try {
          const response = await api.patchBattleTask(taskId, { ready_to_plan: target })
          this.mergeConfirmed(state, response)
          state.pending = state.desired !== state.confirmed
          this.emit()
        } catch {
          const reconciliation = await this.reconcile(taskId)
          const isLatestIntent = state.desired === target
          if (reconciliation.task) this.mergeConfirmed(state, reconciliation.task)
          else if (reconciliation.available) state.confirmed = false

          if (!isLatestIntent) {
            state.pending = state.desired !== state.confirmed
            this.emit()
            continue
          }

          if (reconciliation.available && state.confirmed === target) {
            state.desired = target
            state.pending = false
            state.failedDesired = null
            state.error = null
            this.emit()
            continue
          }

          state.desired = state.confirmed
          state.pending = false
          state.failedDesired = target
          state.error = reconciliation.available
            ? 'Ready to Plan was not saved. Retry your latest choice.'
            : 'Ready to Plan could not be confirmed. Retry your latest choice.'
          this.emit()
          break
        }
      }
    } finally {
      state.running = false
      state.pending = state.desired !== state.confirmed
      this.emit()
    }
  }
}

export const ReadinessContext = createContext<ReadinessCoordinator | null>(null)

export function useReadinessCoordinator() {
  const shared = useContext(ReadinessContext)
  const [local] = useState(() => new ReadinessCoordinator())
  const coordinator = shared ?? local
  useSyncExternalStore(coordinator.subscribe, coordinator.getRevision, coordinator.getRevision)
  return coordinator
}
