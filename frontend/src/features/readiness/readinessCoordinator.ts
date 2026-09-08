import { createContext, useContext, useState, useSyncExternalStore } from 'react'
import { api, type BattleTask } from '../../lib/api'

export type ReadinessState = {
  confirmed: boolean
  desired: boolean
  pending: boolean
}

type MutableReadinessState = ReadinessState & {
  running: boolean
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
        this.entries.set(task.id, { confirmed: ready, desired: ready, pending: false, running: false })
        changed = true
      } else if (!current.pending && !current.running && (current.confirmed !== ready || current.desired !== ready)) {
        current.confirmed = ready
        current.desired = ready
        changed = true
      }
      task.session_tasks?.forEach(observe)
    }
    tasks.forEach(observe)
    if (changed) this.emit()
  }

  stateFor(taskId: number): ReadinessState {
    const state = this.entries.get(taskId)
    return state
      ? { confirmed: state.confirmed, desired: state.desired, pending: state.pending }
      : { confirmed: false, desired: false, pending: false }
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
      const current = Boolean(task.ready_to_plan)
      this.entries.set(task.id, { confirmed: current, desired: current, pending: false, running: false })
      this.emit()
    }
    const state = this.entries.get(task.id)!
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

  private async persist(taskId: number) {
    const state = this.entries.get(taskId)!
    state.running = true
    try {
      while (state.desired !== state.confirmed) {
        const target = state.desired
        try {
          await api.patchBattleTask(taskId, { ready_to_plan: target })
          state.confirmed = target
          state.pending = state.desired !== state.confirmed
          this.emit()
        } catch (cause) {
          state.desired = state.confirmed
          state.pending = false
          this.emit()
          throw cause
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
