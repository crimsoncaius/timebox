import { createContext, useContext, useState, useSyncExternalStore } from 'react'
import { api, type BattleTask } from '../../lib/api'

export type ReadinessState = {
  confirmed: boolean
  desired: boolean
  pending: boolean
  failure: { desired: boolean; message: string } | null
}

type MutableReadinessState = ReadinessState & {
  running: boolean
  newestVersion: number | null
  newestSnapshot: BattleTask | null
  removed: boolean
}

type ReadinessTask = Pick<BattleTask, 'id' | 'ready_to_plan' | 'version'>

function isCompleted(task: unknown) {
  return typeof task === 'object'
    && task !== null
    && 'status' in task
    && task.status === 'completed'
}

function initialState(task: ReadinessTask, snapshot: BattleTask | null): MutableReadinessState {
  const ready = isCompleted(snapshot ?? task) ? false : Boolean(task.ready_to_plan)
  return {
    confirmed: ready,
    desired: ready,
    pending: false,
    failure: null,
    running: false,
    newestVersion: task.version ?? null,
    newestSnapshot: snapshot,
    removed: false,
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
        this.entries.set(task.id, initialState(task, task))
        changed = true
      } else if (isCompleted(task) || current.newestVersion == null || (task.version != null && task.version >= current.newestVersion)) {
        const wasConfirmed = current.confirmed
        const wasDesired = current.desired
        const wasPending = current.pending
        const previousFailure = current.failure
        const wasRemoved = current.removed
        if (task.version != null && (current.newestVersion == null || task.version >= current.newestVersion)) {
          current.newestVersion = task.version
        }
        current.newestSnapshot = task
        current.removed = false
        if (isCompleted(task)) {
          current.confirmed = false
          current.desired = false
          current.pending = false
          current.failure = null
        } else {
          current.confirmed = ready
          if (!current.pending && !current.running) {
            current.desired = ready
            if (current.failure?.desired === ready) current.failure = null
          }
          current.pending = current.running || current.desired !== current.confirmed
        }
        changed = wasConfirmed !== current.confirmed
          || wasDesired !== current.desired
          || wasPending !== current.pending
          || previousFailure !== current.failure
          || wasRemoved !== current.removed
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
          failure: state.failure,
        }
      : { confirmed: false, desired: false, pending: false, failure: null }
  }

  projectTask(task: BattleTask): BattleTask {
    const state = this.entries.get(task.id)
    const taskIsStale = state?.newestVersion != null
      && (task.version == null || task.version < state.newestVersion)
    const source = taskIsStale && state.newestSnapshot ? state.newestSnapshot : task
    return {
      ...source,
      ready_to_plan: isCompleted(source) ? false : (state?.desired ?? Boolean(source.ready_to_plan)),
      session_tasks: source.session_tasks
        ?.filter((session) => !this.entries.get(session.id)?.removed)
        .map((session) => this.projectTask(session)),
    }
  }

  projectTasks(tasks: BattleTask[]) {
    return tasks
      .filter((task) => !this.entries.get(task.id)?.removed)
      .map((task) => this.projectTask(task))
  }

  isSchedulable(taskId: number) {
    const state = this.entries.get(taskId)
    return state ? state.desired && !state.pending && !state.removed && !isCompleted(state.newestSnapshot) : false
  }

  async setReadyToPlan(task: ReadinessTask, ready: boolean) {
    if (isCompleted(task)) return
    if (!this.entries.has(task.id)) {
      this.entries.set(task.id, initialState(task, null))
      this.emit()
    }
    const state = this.entries.get(task.id)!
    if (isCompleted(state.newestSnapshot)) return
    state.failure = null
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

  retry(task: ReadinessTask) {
    const state = this.entries.get(task.id)
    if (isCompleted(task) || isCompleted(state?.newestSnapshot)) return Promise.resolve()
    const failure = state?.failure
    if (!failure) return Promise.resolve()
    return this.setReadyToPlan(task, failure.desired)
  }

  private mergeConfirmed(state: MutableReadinessState, task: BattleTask) {
    if (isCompleted(state.newestSnapshot)) return false
    if (state.newestVersion != null && (task.version == null || task.version < state.newestVersion)) return false
    state.confirmed = Boolean(task.ready_to_plan)
    state.newestVersion = task.version ?? state.newestVersion
    return true
  }

  private mergeReadSnapshot(state: MutableReadinessState, task: BattleTask) {
    if (isCompleted(task)) {
      if (task.version != null && (state.newestVersion == null || task.version >= state.newestVersion)) {
        state.newestVersion = task.version
      }
      state.newestSnapshot = task
      state.confirmed = false
      state.desired = false
      state.pending = false
      state.failure = null
      state.removed = false
      return true
    }
    if (!this.mergeConfirmed(state, task)) return false
    state.newestSnapshot = task
    state.removed = false
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
          if (reconciliation.task) this.mergeReadSnapshot(state, reconciliation.task)
          else if (reconciliation.available) {
            state.confirmed = false
            state.removed = true
          }

          if (isCompleted(state.newestSnapshot)) {
            state.pending = false
            state.failure = null
            this.emit()
            break
          }

          if (!isLatestIntent) {
            state.pending = state.desired !== state.confirmed
            this.emit()
            continue
          }

          if (reconciliation.available && state.confirmed === target) {
            state.desired = target
            state.pending = false
            state.failure = null
            this.emit()
            continue
          }

          state.desired = state.confirmed
          state.pending = false
          state.failure = {
            desired: target,
            message: reconciliation.available
              ? 'Ready to Plan was not saved. Retry your latest choice.'
              : 'Ready to Plan could not be confirmed. Retry your latest choice.',
          }
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
