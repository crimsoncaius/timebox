import { getFocusController } from './focusController'
import { ActivityTimeField } from './ActivityTimeField'
import { activityTimeValue, resolveActivityTime, type ActivityTimeValue } from './activityTime'
import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import type { TaskType } from '../../lib/api'
import { ActivityRepository, getActivityRepository } from './activityRepository'

export function ActivityTracking({ taskTypes, onChanged, repository = getActivityRepository(), focus = false }: {
  taskTypes: TaskType[]; onChanged: () => void; repository?: ActivityRepository; focus?: boolean
}) {
  const focusController = getFocusController()
  const focusState = useSyncExternalStore(focusController.subscribe, focusController.getSnapshot)
  const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const [switching, setSwitching] = useState(false)
  const [stopping, setStopping] = useState(false)
  const [targetId, setTargetId] = useState<number | null>(null)
  const [timing, setTiming] = useState<ActivityTimeValue | null>(null)
  const [timingError, setTimingError] = useState<string | null>(null)
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
    <div className={focus ? "mt-8 flex flex-col items-center gap-4 text-center" : "flex flex-wrap items-center justify-end gap-x-4 gap-y-1"}>
      {current ? <>
        <span className={focus ? "text-4xl font-semibold text-on-surface dark:text-dark-on-surface" : "max-w-64 truncate text-on-surface dark:text-dark-on-surface"}>{current.name || current.task_type.name}</span>
        <span className={focus ? "text-2xl" : undefined} aria-label="Elapsed time">{elapsed}m</span>
        <button className="py-2" disabled={disabled} onClick={() => { setTargetId(current.id); setTiming(null); setTimingError(null); setSwitching(true) }}>Switch</button>
        {!focus && <button className="py-2" disabled={disabled} onClick={() => { setTargetId(current.id); setTiming(null); setTimingError(null); setStopping(true) }}>Stop</button>}
      </> : <button className="py-2" disabled={disabled} onClick={() => void repository.command('start')}>Start tracking</button>}
      {!focus && <button disabled={disabled || focusState.planning || focusState.entering} onClick={() => void focusController.enter(repository)}>Focus</button>}
      {state.busy ? <span role="status">Saving…</span> : null}
      <span role="status">{state.offline ? 'Offline' : state.pending ? 'Unsynced' : state.snapshot ? 'Synced' : 'Connection required'}{state.offline && state.pending ? ' · Unsynced' : ''}</span>
    </div>
    {!focus && focusState.planning && <p className="text-right">Finish or cancel planning to enter Focus.</p>}
    {focus && current && !current.name && current.task_type.name === 'unspecified' && <UnknownActivity key={current.id} repository={repository} />}
    {current && plan && current.planned_block_id !== plan.id ? <p className="text-right">Planned now: {plan.name || availableTypes.find(t => t.id === plan.task_type_id)?.name} <button disabled={disabled} className="underline py-2" onClick={() => void repository.adoptPlan(plan)}>Switch to planned activity</button></p> : null}
    {current?.planned_block_id ? <p className="text-right">{state.snapshot!.records.filter(r => r.planned_block_id === current.planned_block_id).length} linked Actual Blocks · {Math.floor(state.snapshot!.records.filter(r => r.planned_block_id === current.planned_block_id).reduce((sum, r) => sum + Math.max(0, Date.parse(r.end_at ?? new Date(now).toISOString()) - Date.parse(r.start_at)), 0) / 60000)}m recorded</p> : null}
    {state.feedback ? <p role="status" className="text-right">{state.feedback}</p> : null}
    {state.error || state.pending ? <p role="alert" className="text-right">
      {state.error || 'Change not confirmed.'} <button disabled={state.busy} className="underline" onClick={() => void (state.pending ? repository.retry() : repository.refresh())}>Retry</button>
    </p> : null}
    {switching || stopping ? <form aria-label={stopping ? "Stop tracking" : "Switch activity"} className="ml-auto mt-2 max-w-md space-y-3 rounded-xl bg-surface-container-low p-4 dark:bg-dark-surface-container" onSubmit={async (event) => {
      event.preventDefault()
      try {
        const at = timing ? resolveActivityTime(timing, state.snapshot?.reporting_timezone ?? 'UTC') : undefined
        if (await repository.command(stopping ? 'stop' : 'switch', stopping ? undefined : Number(typeId), stopping ? undefined : name.trim(), false, undefined, { at, targetId: targetId! })) { setSwitching(false); setStopping(false); setTypeId(''); setName('') }
      } catch (error) { setTimingError(String(error)) }
    }}>
      {!stopping ? <><label className="block">Task Type<select className="mt-1 block w-full rounded border p-2 dark:bg-dark-surface" required value={typeId} onChange={(e) => setTypeId(e.target.value)}>
        <option value="">Choose Task Type</option>{availableTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
      </select></label>
      <label className="block">Block Name (optional)<input className="mt-1 block w-full rounded border p-2 dark:bg-dark-surface" maxLength={500} value={name} onChange={(e) => setName(e.target.value)} /></label></> : null}
      <p>When did this {stopping ? "stop" : "change"} happen?</p>
      <div className="flex gap-4"><button type="button" onClick={() => setTiming(null)}>Now</button><button type="button" onClick={() => setTiming(activityTimeValue(new Date(now - 15 * 60000).toISOString(), state.snapshot?.reporting_timezone ?? "UTC"))}>15 min ago</button><button type="button" onClick={() => setTiming(activityTimeValue(new Date(now).toISOString(), state.snapshot?.reporting_timezone ?? "UTC"))}>Choose time</button></div>
      {timing ? <ActivityTimeField label="Change time" value={timing} onChange={setTiming} timezone={state.snapshot?.reporting_timezone ?? "UTC"} /> : <p>Now</p>}
      <section aria-label="After this change"><h3>After this change</h3><p>{current?.name || current?.task_type.name} ends {timing?.local.replace("T", " ") ?? "now"}.</p><p>{stopping ? "Time after this is unrecorded." : `${name || availableTypes.find(t => t.id === Number(typeId))?.name || "Next activity"} starts at the same time and continues.`}</p></section>
      {timingError ? <p role="alert">{timingError}</p> : null}
      <div className="flex justify-end gap-4"><button type="button" onClick={() => { setSwitching(false); setStopping(false) }}>Cancel</button><button className="rounded-lg bg-[linear-gradient(135deg,#5d5e61_0%,#515255_100%)] px-4 py-2 text-on-primary disabled:opacity-40" disabled={disabled || (!stopping && !typeId)}>{stopping ? "Stop tracking" : "Switch activity"}</button></div>
    </form> : null}
  </div>
}

function UnknownActivity({ repository }: { repository: ActivityRepository }) {
  const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const current = state.snapshot!.current!
  const [type, setType] = useState(''), [name, setName] = useState('')
  const describe = (now: boolean) => repository.command(now ? 'switch' : 'describe', Number(type), name.trim(), false, undefined, { targetId: current.id })
  return <section aria-label="Unknown activity" className="my-8 rounded-xl bg-surface-container-low dark:bg-dark-surface-container p-6 space-y-4">
    <h2 className="text-xl font-semibold">What are you doing right now?</h2><p>Recording continues while you decide.</p>
    <label className="block">Task Type<select aria-label="Describe Task Type" value={type} onChange={e => setType(e.target.value)} className="block w-full p-2 dark:bg-dark-surface"><option value="">Choose Task Type</option>{state.snapshot?.task_types?.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
    <label className="block">Block Name (optional)<input aria-label="Describe Block Name" value={name} maxLength={500} onChange={e => setName(e.target.value)} className="block w-full p-2 dark:bg-dark-surface" /></label>
    <p>Apply from the original start ({new Date(current.start_at).toLocaleTimeString()}) describes all this activity. Start now keeps preceding unspecified time.</p>
    <div className="flex gap-4"><button disabled={!type} onClick={() => void describe(false)}>Apply from original start</button><button disabled={!type} onClick={() => void describe(true)}>Start now</button></div>
  </section>
}
