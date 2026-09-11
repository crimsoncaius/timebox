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
  const disabled = state.busy || state.pending || !state.snapshot
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
    </div>
    {state.error || state.pending ? <p role="alert" className="text-right">
      {state.error || 'Change not confirmed.'} <button disabled={state.busy} className="underline" onClick={() => void (state.pending ? repository.retry() : repository.refresh())}>Retry</button>
    </p> : null}
    {switching ? <form aria-label="Switch activity" className="ml-auto mt-2 max-w-md space-y-3 rounded-xl bg-surface-container-low p-4 dark:bg-dark-surface-container" onSubmit={async (event) => {
      event.preventDefault()
      if (await repository.command('switch', Number(typeId), name.trim())) { setSwitching(false); setTypeId(''); setName('') }
    }}>
      <label className="block">Task Type<select className="mt-1 block w-full rounded border p-2 dark:bg-dark-surface" required value={typeId} onChange={(e) => setTypeId(e.target.value)}>
        <option value="">Choose Task Type</option>{taskTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
      </select></label>
      <label className="block">Block Name (optional)<input className="mt-1 block w-full rounded border p-2 dark:bg-dark-surface" maxLength={500} value={name} onChange={(e) => setName(e.target.value)} /></label>
      <div className="flex justify-end gap-4"><button type="button" onClick={() => setSwitching(false)}>Cancel</button><button className="rounded-full bg-primary px-4 py-2 text-on-primary disabled:opacity-40" disabled={disabled || !typeId}>Switch activity</button></div>
    </form> : null}
  </div>
}
