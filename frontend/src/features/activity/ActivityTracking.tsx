import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import type { TaskType } from '../../lib/api'
import { ActivityRepository, getActivityRepository } from './activityRepository'

export function ActivityTracking({ taskTypes, onChanged, repository = getActivityRepository() }: {
  taskTypes: TaskType[]; onChanged: () => void; repository?: ActivityRepository
}) {
  const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const [switching, setSwitching] = useState(false)
  const [typeId, setTypeId] = useState('')
  const [name, setName] = useState('')
  const [now, setNow] = useState(() => repository.now())
  const changed = useRef(onChanged)
  useEffect(() => {
    if (!state.feedback) return
    const timer = window.setTimeout(repository.dismissFeedback, 6000)
    return () => clearTimeout(timer)
  }, [repository, state.feedback])
  useEffect(() => { changed.current = onChanged }, [onChanged])
  useEffect(() => { if (state.snapshot) changed.current() }, [state.snapshot?.cursor]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    void repository.refresh()
    const refresh = () => { if (document.visibilityState === 'visible') void repository.refresh() }
    const poll = window.setInterval(refresh, 5000)
    const clock = window.setInterval(() => setNow(repository.now()), 1000)
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)
    return () => { clearInterval(poll); clearInterval(clock); window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh) }
  }, [repository])
  const current = state.snapshot?.current
  const plan = repository.currentPlan()
  const disabled = state.busy || !state.snapshot
  const availableTypes = state.snapshot?.task_types ?? taskTypes
  const elapsed = current ? Math.max(0, Math.floor((now - Date.parse(current.start_at)) / 60000)) : 0
  return <div className="mb-3 text-sm text-on-surface-variant dark:text-dark-on-surface-variant" aria-label="Activity tracking">
    <div className="flex flex-wrap items-center justify-end gap-x-4 gap-y-1">
      {current ? <>
        <span className="max-w-64 truncate text-on-surface dark:text-dark-on-surface">{current.name || current.task_type.name}</span>
        <span aria-label="Elapsed time">{elapsed}m</span>
        <button className="py-2" disabled={disabled} onClick={() => setSwitching(true)}>Switch</button>
        <button className="py-2" disabled={disabled} onClick={() => void repository.command('stop')}>Stop</button>
      </> : <button className="py-2" disabled={disabled} onClick={() => void repository.command('start')}>Start tracking</button>}
      {state.busy ? <span role="status">Saving…</span> : null}
      <span role="status">{state.offline ? 'Offline' : state.pending ? 'Unsynced' : state.snapshot ? 'Synced' : 'Connection required'}{state.offline && state.pending ? ' · Unsynced' : ''}</span>
    </div>
    {current && plan && current.planned_block_id !== plan.id ? <p className="text-right">Planned now: {plan.name || availableTypes.find(t => t.id === plan.task_type_id)?.name} <button disabled={disabled} className="underline py-2" onClick={() => void repository.adoptPlan(plan)}>Switch to planned activity</button></p> : null}
    {current?.planned_block_id ? <p className="text-right">{state.snapshot!.records.filter(r => r.planned_block_id === current.planned_block_id).length} linked Actual Blocks · {Math.floor(state.snapshot!.records.filter(r => r.planned_block_id === current.planned_block_id).reduce((sum, r) => sum + Math.max(0, Date.parse(r.end_at ?? new Date(now).toISOString()) - Date.parse(r.start_at)), 0) / 60000)}m recorded</p> : null}
    {state.feedback ? <p role="status" className="text-right">{state.feedback}</p> : null}
    {state.error || state.pending ? <p role="alert" className="text-right">
      {state.error || 'Change not confirmed.'} <button disabled={state.busy} className="underline" onClick={() => void (state.pending ? repository.retry() : repository.refresh())}>Retry</button>
    </p> : null}
    {switching ? <form aria-label="Switch activity" className="ml-auto mt-2 max-w-md space-y-3 rounded-xl bg-surface-container-low p-4 dark:bg-dark-surface-container" onSubmit={async (event) => {
      event.preventDefault()
      if (await repository.command('switch', Number(typeId), name.trim())) { setSwitching(false); setTypeId(''); setName('') }
    }}>
      <label className="block">Task Type<select className="mt-1 block w-full rounded border p-2 dark:bg-dark-surface" required value={typeId} onChange={(e) => setTypeId(e.target.value)}>
        <option value="">Choose Task Type</option>{availableTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
      </select></label>
      <label className="block">Block Name (optional)<input className="mt-1 block w-full rounded border p-2 dark:bg-dark-surface" maxLength={500} value={name} onChange={(e) => setName(e.target.value)} /></label>
      <div className="flex justify-end gap-4"><button type="button" onClick={() => setSwitching(false)}>Cancel</button><button className="rounded-lg bg-[linear-gradient(135deg,#5d5e61_0%,#515255_100%)] px-4 py-2 text-on-primary disabled:opacity-40" disabled={disabled || !typeId}>Switch activity</button></div>
    </form> : null}
  </div>
}
