import { useEffect, useState, useSyncExternalStore, type ReactNode } from 'react'
import { useReadinessCoordinator } from '../readiness/readinessCoordinator'
import { api, type BattleTask } from '../../lib/api'
import { ActivityTracking } from './ActivityTracking'
import { getActivityRepository } from './activityRepository'
import { getFocusController } from './focusController'
import { observeFocusWake } from './focusWake'
import { UndoNotice } from '../../components/UndoNotice'
import { useUndoNotice } from '../../components/useUndoNotice'

export function FocusWakeSettings() {
  const controller = getFocusController()
  const state = useSyncExternalStore(controller.subscribe, controller.getSnapshot)
  return <label className="block my-4"><input type="checkbox" checked={state.wake} onChange={e => controller.setWake(e.target.checked)} /> Keep display awake while Focus is visible<p className="text-sm">On this device. Browser and battery policy apply; manual locking still works.</p></label>
}
export function FocusHost({ children }: { children: ReactNode }) {
  const controller = getFocusController(), repository = getActivityRepository()
  const state = useSyncExternalStore(controller.subscribe, controller.getSnapshot)
  const activity = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const visible = state.active && !state.planning && !!activity.snapshot?.current
  const [wakeMessage, setWakeMessage] = useState('')
  useEffect(() => { window.dispatchEvent(new Event(visible ? 'timebox:focus-open' : 'timebox:focus-close')) }, [visible])
  useEffect(() => controller.reconcile(repository), [controller, repository, activity.snapshot])
  useEffect(() => {
    if (!visible) return
    const stop = observeFocusWake(state.wake, setWakeMessage)
    return () => { stop(); setWakeMessage('') }
  }, [visible, state.wake])
  return <><div hidden={visible} inert={visible}>{children}</div>{visible && <main aria-label="Focus" className="min-h-screen bg-surface dark:bg-dark-background text-on-surface dark:text-dark-on-surface p-6">
    <div className="fixed right-6 top-6 z-50 rounded-xl bg-surface dark:bg-dark-background"><button className="p-3" onClick={controller.exit}>Exit Focus</button></div>
    <div className="mx-auto max-w-xl pt-16"><p className="text-center uppercase tracking-widest">Focus</p><ActivityTracking taskTypes={activity.snapshot?.task_types ?? []} onChanged={() => {}} focus />
    <FocusTask key={activity.snapshot?.current?.task_id ?? 'none'} taskId={activity.snapshot?.current?.task_id ?? null} />
    {wakeMessage && <p className="text-sm text-center mt-8">{wakeMessage}</p>}{state.error && <p role="status">{state.error}</p>}</div>
  </main>}</>
}
function FocusTask({ taskId }: { taskId: number | null }) {
  const readiness = useReadinessCoordinator()
  const [task, setTask] = useState<BattleTask | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const { notice, offer, dismiss } = useUndoNotice()
  useEffect(() => { let current = true; setTask(null); if (taskId != null) void api.listBattleTasks('active').then(r => r.items.flatMap(t => [t, ...(t.session_tasks ?? [])]).find(t => t.id === taskId) ?? null).then(t => { if (current) setTask(t) }).catch(() => {}); return () => { current = false } }, [taskId])
  if (!task || task.id !== taskId) return null
  return <section className="mt-6"><h2>{task.title}</h2>{task.description && <p>{task.description}</p>}{error && <p role="alert">{error}</p>}
    {task.subtasks.map(subtask => <label className="block" key={subtask.id}><input type="checkbox" checked={subtask.checked} disabled={busy || task.status === 'completed'} onChange={async () => { setBusy(true); try { const next = await (subtask.checked ? api.uncheckSubtask(subtask.id) : api.checkSubtask(subtask.id)); setTask(t => t?.id === task.id ? { ...t, subtasks: t.subtasks.map(s => s.id === next.id ? next : s) } : t) } catch { setError('Could not update Subtask.') } finally { setBusy(false) } }} />{subtask.title}</label>)}
    {task.status !== 'completed' && <button disabled={busy} onClick={async () => { setBusy(true); try { const next = await api.completeBattleTask(task.id); setTask(t => t?.id === task.id ? next.task : t); readiness.observeTasks([next.task]); offer({ title: next.task.title, label: `${next.task.title} completed`, ariaLabel: 'Task completion undo', kind: 'completion', targetId: next.task.id, detail: `${next.removed_planned_block_ids.length} future Planned ${next.removed_planned_block_ids.length === 1 ? 'Block' : 'Blocks'} removed.`, undo: async () => { const restored = await api.undoBattleTaskCompletion(next.task.id, next.undo_token); setTask(t => t?.id === restored.id ? restored : t); readiness.observeTasks([restored]); window.dispatchEvent(new Event('timebox:focus-task-changed')) } }); window.dispatchEvent(new Event('timebox:focus-task-changed')) } catch { setError('Could not complete Task. Try again.') } finally { setBusy(false) } }}>Complete Task</button>}
    {notice && <UndoNotice key={notice.id} notice={notice} onDismiss={dismiss} onFailure={setError} />}
  </section>
}
