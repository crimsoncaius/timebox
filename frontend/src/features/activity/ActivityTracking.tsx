import { blockPrimaryIdentity, blockSecondaryIdentity, blockIdentityText } from '../../lib/blockIdentity'
import { formatDuration } from '../../lib/duration'
import { getFocusController } from './focusController'
import { ActivityTimeField } from './ActivityTimeField'
import { activityTimeValue, resolveActivityTime, type ActivityTimeValue } from './activityTime'
import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { api, type TaskType } from '../../lib/api'
import { ActivityRepository, getActivityRepository } from './activityRepository'
import { ActivityTrackingStatus } from './ActivityTrackingStatus'
import { TaskTypePathCombobox } from '../../components/TaskTypePathCombobox'

export function ActivityTracking({ taskTypes, onChanged, repository = getActivityRepository(), focus = false, controlsVisible = true }: {
  taskTypes: TaskType[]; onChanged: () => void; repository?: ActivityRepository; focus?: boolean; controlsVisible?: boolean
}) {
  const focusController = getFocusController()
  const focusState = useSyncExternalStore(focusController.subscribe, focusController.getSnapshot)
  const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const [switching, setSwitching] = useState(false)
  const [stopping, setStopping] = useState(false)
  const [starting, setStarting] = useState<'track' | 'focus' | null>(null)
  const [startError, setStartError] = useState<string | null>(null)
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
  const shortcutAt = repository.now() - 15 * 60000
  const shortcutReason = !current || current.id !== targetId
    ? 'The Current Activity changed. Reopen Switch or Stop to choose a time.'
    : shortcutAt < Date.parse(current.start_at)
      ? '15 min ago is before the Current Activity started. Choose Now or a later time.'
      : null
  const plan = repository.currentPlan()
  const disabled = state.busy || !state.snapshot
  const statusFlags = {
    offline: state.offline, pending: state.pending, busy: state.busy, hasSnapshot: Boolean(state.snapshot), error: state.error,
  }
  const availableTypes = state.snapshot?.task_types ?? taskTypes
  const planIdentity = plan ? { name: plan.name, task: plan.task_title ? { title: plan.task_title } : null, task_type: availableTypes.find(t => t.id === plan.task_type_id) } : null
  const nextIdentity = { name, task_type: availableTypes.find(t => t.id === Number(typeId)) }
  const elapsed = current ? Math.max(0, Math.floor((now - Date.parse(current.start_at)) / 60000)) : 0
  // Without a covering Planned Block, starting waits for an explicit Task Type.
  const start = (then: 'track' | 'focus') => {
    if (plan) void (then === 'focus' ? focusController.enter(repository) : repository.command('start'))
    else { setTypeId(''); setName(''); setStartError(null); setStarting(then) }
  }
  const selectionFields = <ActivitySelectionFields repository={repository} taskTypes={availableTypes} typeId={typeId} onTypeId={setTypeId} name={name} onName={setName} />
  const retryStatus = () => { void (state.pending ? repository.retry() : repository.refresh()) }
  const statusMark = <ActivityTrackingStatus flags={statusFlags} retryDisabled={statusFlags.busy} onRetry={retryStatus} />
  const controls = <>
    {current ? <>
      <span className={focus ? "text-4xl font-semibold text-on-surface dark:text-dark-on-surface" : "max-w-64 truncate text-on-surface dark:text-dark-on-surface"}>{blockPrimaryIdentity(current)}</span>
      {blockSecondaryIdentity(current) && <span className="text-sm">{blockSecondaryIdentity(current)}</span>}
      <span className={focus ? "text-2xl" : undefined} aria-label="Elapsed time">{formatDuration(elapsed)}</span>
      <button className="py-2" disabled={disabled} onClick={() => { setTargetId(current.id); setTiming(null); setTimingError(null); setSwitching(true) }}>Switch</button>
      {!focus && <button className="py-2" disabled={disabled} onClick={() => { setTargetId(current.id); setTiming(null); setTimingError(null); setStopping(true) }}>Stop</button>}
    </> : <button className="py-2" disabled={disabled || !!starting} onClick={() => start('track')}>Start tracking</button>}
    {!focus && <button disabled={disabled || !!starting || focusState.planning || focusState.entering} onClick={() => current ? void focusController.enter(repository) : start('focus')}>Focus</button>}
  </>
  const controlRow = focus
    ? <div className="mt-8 flex flex-col items-center gap-4 text-center">{controls}{statusMark}</div>
    : <div className="flex flex-wrap items-center gap-x-4 gap-y-1">{statusMark}<div className="ml-auto flex flex-wrap items-center justify-end gap-x-4 gap-y-1">{controls}</div></div>
  return <div className={`${controlsVisible ? "mb-3 " : ""}text-sm text-on-surface-variant dark:text-dark-on-surface-variant`} aria-label="Activity tracking">
    {current && state.snapshot?.check_in?.question && <section aria-label="Inactivity check-in" className="my-4 rounded-2xl bg-surface-container-low p-6 dark:bg-dark-surface-container">
      <h2 className="text-lg">Still doing this?</h2><p className="my-3 text-2xl font-semibold">{blockPrimaryIdentity(current)}</p>{blockSecondaryIdentity(current) && <p>{blockSecondaryIdentity(current)}</p>}
      <div className="flex flex-wrap gap-3"><button className="rounded-xl bg-primary px-5 py-3 text-on-primary" onClick={() => void repository.checkIn({ action: 'confirm', question_id: state.snapshot!.check_in!.question!.id })}>Yes, still doing this</button>
      <button className="rounded-xl border px-5 py-3" onClick={() => { setTargetId(current.id); setTiming(null); setTimingError(null); setSwitching(true) }}>Switch activity</button></div>
      <p className="mt-3 text-sm">Recording continues while you decide.</p>
    </section>}
    <div hidden={!controlsVisible && !focus}>
    {controlRow}
    {!focus && focusState.planning && <p className="text-right">Finish or cancel planning to enter Focus.</p>}
    {!focus && focusState.error && <p role="status">{focusState.error}</p>}
    {!focus && focusState.recovery && <details><summary>Review old Work Mode data</summary><p>These are saved device observations, not recorded time. Use Day add/edit for any correction.</p><pre className="whitespace-pre-wrap break-all">{focusState.recovery}</pre></details>}
    {!focus && repository.recoveryData() && <details><summary>Review rejected changes</summary><p>These changes were not replayed. Use Day add/edit to correct the saved timeline.</p><pre className="whitespace-pre-wrap break-all">{repository.recoveryData()}</pre></details>}
    {current && plan && current.planned_block_id !== plan.id ? <p className="text-right">Planned now: {blockIdentityText(planIdentity!)} <button disabled={disabled} className="underline py-2" onClick={() => void repository.adoptPlan(plan)}>Switch to planned activity</button></p> : null}
    {current?.planned_block_id ? <p className="text-right">{state.snapshot!.records.filter(r => r.planned_block_id === current.planned_block_id).length} linked Actual Blocks · {formatDuration(Math.floor(state.snapshot!.records.filter(r => r.planned_block_id === current.planned_block_id).reduce((sum, r) => sum + Math.max(0, Date.parse(r.end_at ?? new Date(now).toISOString()) - Date.parse(r.start_at)), 0) / 60000))} recorded</p> : null}
    </div>
    {state.feedback ? <p role="status" className="text-right">{state.feedback}</p> : null}
    {starting && !current ? <form aria-label="Start tracking" className="ml-auto mt-2 max-w-md space-y-3 rounded-xl bg-surface-container-low p-4 dark:bg-dark-surface-container" onSubmit={async (event) => {
      event.preventDefault()
      const choice = { taskTypeId: Number(typeId), name: name.trim() || undefined }
      const started = starting === 'focus' ? await focusController.enter(repository, choice) : await repository.command('start', choice.taskTypeId, choice.name)
      if (started) { setStarting(null); setTypeId(''); setName('') }
      else setStartError('Could not start tracking.')
    }}>
      {selectionFields}
      {starting === 'focus' ? <p>Focus opens once tracking starts.</p> : null}
      {startError ? <p role="alert">{startError}</p> : null}
      <div className="flex justify-end gap-4"><button type="button" onClick={() => setStarting(null)}>Cancel</button><button className="rounded-lg bg-[linear-gradient(135deg,#5d5e61_0%,#515255_100%)] px-4 py-2 text-on-primary disabled:opacity-40" disabled={disabled || !typeId}>Start</button></div>
    </form> : null}
    {switching || stopping ? <form aria-label={stopping ? "Stop tracking" : "Switch activity"} className="ml-auto mt-2 max-w-md space-y-3 rounded-xl bg-surface-container-low p-4 dark:bg-dark-surface-container" onSubmit={async (event) => {
      event.preventDefault()
      try {
        const at = timing ? resolveActivityTime(timing, state.snapshot?.reporting_timezone ?? 'UTC') : undefined
        if (await repository.command(stopping ? 'stop' : 'switch', stopping ? undefined : Number(typeId), stopping ? undefined : name.trim(), false, undefined, { at, targetId: targetId! })) { setSwitching(false); setStopping(false); setTypeId(''); setName('') }
        else setTimingError(repository.state.error)
      } catch (error) { setTimingError(String(error)) }
    }}>
      {!stopping ? selectionFields : null}
      <p>When did this {stopping ? "stop" : "change"} happen?</p>
      <div className="flex gap-4"><button type="button" onClick={() => setTiming(null)}>Now</button><button type="button" disabled={Boolean(shortcutReason)} title={shortcutReason ?? undefined} className="disabled:opacity-40" onClick={() => setTiming(activityTimeValue(new Date(shortcutAt).toISOString(), state.snapshot?.reporting_timezone ?? "UTC"))}>15 min ago</button><button type="button" onClick={() => setTiming(activityTimeValue(new Date(now).toISOString(), state.snapshot?.reporting_timezone ?? "UTC"))}>Choose time</button></div>
      {shortcutReason ? <p>{shortcutReason}</p> : null}
      {timing ? <ActivityTimeField label="Change time" value={timing} onChange={setTiming} timezone={state.snapshot?.reporting_timezone ?? "UTC"} /> : <p>Now</p>}
      <section aria-label="After this change"><h3>After this change</h3><p>{current ? blockIdentityText(current) : "Unnamed activity"} ends {timing?.local.replace("T", " ") ?? "now"}.</p><p>{stopping ? "Time after this is unrecorded." : `${blockIdentityText(nextIdentity)} starts at the same time and continues.`}</p></section>
      {timingError ? <p role="alert">{timingError}</p> : null}
      <div className="flex justify-end gap-4"><button type="button" onClick={() => { setSwitching(false); setStopping(false) }}>Cancel</button><button className="rounded-lg bg-[linear-gradient(135deg,#5d5e61_0%,#515255_100%)] px-4 py-2 text-on-primary disabled:opacity-40" disabled={disabled || (!stopping && !typeId)}>{stopping ? "Stop tracking" : "Switch activity"}</button></div>
    </form> : null}
  </div>
}

/** The choice shared by starting and switching: a required Task Type and an optional Block Name. */
function ActivitySelectionFields({ repository, taskTypes, typeId, onTypeId, name, onName }: {
  repository: ActivityRepository; taskTypes: TaskType[]; typeId: string; onTypeId: (id: string) => void; name: string; onName: (name: string) => void
}) {
  return <>
    <TaskTypePathCombobox
        recommendationName={name}
      label="Task Type"
      taskTypes={taskTypes}
      valueTaskTypeId={typeId ? Number(typeId) : null}
      onSelectTaskTypeId={(id) => onTypeId(id == null ? '' : String(id))}
      onCreateTaskTypePath={async (name) => {
        const created = await api.createTaskType({ name })
        await repository.refresh()
        return created
      }}
    />
    <label className="block">Block Name (optional)<input className="mt-1 block w-full rounded border p-2 dark:bg-dark-surface" maxLength={500} value={name} onChange={(e) => onName(e.target.value)} /></label>
  </>
}
