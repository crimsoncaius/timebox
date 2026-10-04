import { PlanNowEditor } from './PlanNowEditor'
import { HelperText, DiagnosticText } from '../../components/HelperText'
import { blockPrimaryIdentity, blockSecondaryIdentity, blockIdentityText } from '../../lib/blockIdentity'
import { formatDuration, formatRunningTime } from '../../lib/duration'
import { getFocusController } from './focusController'
import { ActivityTimeField } from './ActivityTimeField'
import { SwitchActivityTimeline } from './SwitchActivityTimeline'
import { ActivitySwitchDialog } from './ActivitySwitchDialog'
import { activityTimeValue, resolveActivityTime, type ActivityTimeValue } from './activityTime'
import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { api, type TaskType } from '../../lib/api'
import { ActivityRepository, getActivityRepository, matchesPlan } from './activityRepository'
import { ActivityTrackingStatus } from './ActivityTrackingStatus'
import { TaskTypePathCombobox } from '../../components/TaskTypePathCombobox'

export function ActivityTracking({ taskTypes, onChanged, repository = getActivityRepository(), focus = false, controlsVisible = true }: {
  taskTypes: TaskType[]; onChanged: () => void; repository?: ActivityRepository; focus?: boolean; controlsVisible?: boolean
}) {
  const focusController = getFocusController()
  const focusState = useSyncExternalStore(focusController.subscribe, focusController.getSnapshot)
  const state = useSyncExternalStore(repository.subscribe, repository.getSnapshot)
  const [editingCurrent, setEditingCurrent] = useState(false)
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
    : shortcutAt < Date.parse(current.start_at) && (stopping || !state.snapshot?.switch_history_ready)
      ? '15 min ago is before the Current Activity started. Choose Now or a later time.'
      : null
  const planNowAvailable = !!state.snapshot?.plan_now_revision
  const keepCurrent = editingCurrent && !!current && current.task_type_id === Number(typeId) && (current.name ?? '') === name.trim()
  const linkedPlan = state.snapshot?.plans?.find(p => p.id === current?.planned_block_id)
  const planSeconds = linkedPlan ? Math.floor((Date.parse(linkedPlan.end_at) - now) / 1000) : null
  const planClock = planSeconds == null ? '' : `${Math.floor(Math.abs(planSeconds) / 60)}:${String(Math.abs(planSeconds) % 60).padStart(2, '0')} ${planSeconds >= 0 ? 'left in plan' : 'over plan'}`
  const plan = repository.currentPlan()
  const disabled = state.busy || !state.snapshot
  const statusFlags = {
    offline: state.offline, pending: state.pending, busy: state.busy, hasSnapshot: Boolean(state.snapshot), error: state.error,
  }
  const availableTypes = state.snapshot?.task_types ?? taskTypes
  const planIdentity = plan ? { name: plan.name, task: plan.task_title ? { title: plan.task_title } : null, task_type: availableTypes.find(t => t.id === plan.task_type_id) } : null
  const nextIdentity = { name, task_type: availableTypes.find(t => t.id === Number(typeId)) }
  const zone = state.snapshot?.reporting_timezone ?? 'UTC'
  let selectedAt = now
  try { if (timing) selectedAt = Date.parse(resolveActivityTime(timing, zone)) } catch { /* The field keeps invalid local-time input for correction. */ }
  const runningSeconds = current ? Math.max(0, Math.floor((now - Date.parse(current.start_at)) / 1000)) : 0
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
      {!focus && planNowAvailable && <button className="py-2 underline" disabled={disabled} onClick={() => { setEditingCurrent(true); setTypeId(String(current.task_type_id)); setName(current.name ?? ''); setTargetId(current.id); setSwitching(true) }}>Edit current activity</button>}
      {blockSecondaryIdentity(current) && <span className="text-sm">{blockSecondaryIdentity(current)}</span>}
      <div className={focus ? "text-2xl" : undefined}><span aria-label="Running time">{formatRunningTime(runningSeconds, focus)}</span><span className="block text-xs">Running Time</span>{planSeconds !== null && <span className="block text-sm text-planned dark:text-planned-dark">{planClock}</span>}</div>
      <button className="py-2" disabled={disabled} onClick={() => { setEditingCurrent(false); setTypeId(''); setName(''); setTargetId(current.id); setTiming(null); setTimingError(null); setSwitching(true) }}>Switch</button>
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
      <button className="rounded-xl border px-5 py-3" onClick={() => { setEditingCurrent(false); setTypeId(''); setName(''); setTargetId(current.id); setTiming(null); setTimingError(null); setSwitching(true) }}>Switch activity</button></div>
      <HelperText className="mt-3">Recording continues while you decide.</HelperText>
    </section>}
    <div hidden={!controlsVisible && !focus}>
    {controlRow}
    {!focus && focusState.planning && <HelperText className="mt-1 text-right">Finish or cancel planning to enter Focus.</HelperText>}
    {!focus && focusState.error && <HelperText role="status">{focusState.error}</HelperText>}
    {!focus && focusState.recovery && <details><summary>Review old Work Mode data</summary><HelperText>These are saved device observations, not recorded time. Use Day add/edit for any correction.</HelperText><DiagnosticText>{focusState.recovery}</DiagnosticText></details>}
    {!focus && repository.recoveryData() && <details><summary>Review rejected changes</summary><HelperText>These changes were not replayed. Use Day add/edit to correct the saved timeline.</HelperText><DiagnosticText>{repository.recoveryData()}</DiagnosticText></details>}
    {current && plan && current.planned_block_id !== plan.id ? matchesPlan(current, plan)
      ? <p className="text-right">{blockIdentityText(planIdentity!)} is planned now <button disabled={disabled} className="underline py-2" onClick={() => void repository.countTowardPlan(plan)}>Count toward plan</button></p>
      : <p className="text-right">Planned now: {blockIdentityText(planIdentity!)} <button disabled={disabled} className="underline py-2" onClick={() => void repository.adoptPlan(plan)}>Switch to planned activity</button></p> : null}
    {current?.planned_block_id ? <HelperText className="text-right">{state.snapshot!.records.filter(r => r.planned_block_id === current.planned_block_id).length} linked Actual Blocks · {formatDuration(Math.floor(state.snapshot!.records.filter(r => r.planned_block_id === current.planned_block_id).reduce((sum, r) => sum + Math.max(0, Date.parse(r.end_at ?? new Date(now).toISOString()) - Date.parse(r.start_at)), 0) / 60000))} recorded</HelperText> : null}
    </div>
    {state.feedback ? <p role="status" className="text-right">{state.feedback}</p> : null}
    {planNowAvailable && (starting || switching) && !stopping && state.snapshot && <PlanNowEditor
      key={starting ? 'start' : editingCurrent ? 'adjust' : 'switch'} repository={repository} snapshot={state.snapshot} now={now}
      title={starting ? 'Start tracking' : editingCurrent ? 'Edit current activity' : 'Switch activity'}
      typeId={typeId ? Number(typeId) : null} name={name} taskId={keepCurrent ? current?.task_id ?? null : null}
      keepCurrent={keepCurrent} selectionFields={selectionFields}
      onClose={() => { setStarting(null); setSwitching(false); setEditingCurrent(false) }}
      onSaved={() => { const enterFocus = starting === 'focus'; setStarting(null); setSwitching(false); setEditingCurrent(false); setTypeId(''); setName(''); if (enterFocus && repository.state.snapshot?.current) void focusController.enter(repository) }}
    />}
    {starting && !current && !planNowAvailable ? <form aria-label="Start tracking" className="ml-auto mt-2 max-w-md space-y-3 rounded-xl bg-surface-container-low p-4 dark:bg-dark-surface-container" onSubmit={async (event) => {
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
    {(switching && !planNowAvailable) || stopping ? <ActivitySwitchDialog open={switching} onClose={() => setSwitching(false)}><form aria-label={stopping ? "Stop tracking" : "Switch activity"} className={switching ? "space-y-3" : "ml-auto mt-2 max-w-md space-y-3 rounded-xl bg-surface-container-low p-4 dark:bg-dark-surface-container"} onSubmit={async (event) => {
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
      {timing || switching ? <ActivityTimeField label="Change time" value={timing ?? activityTimeValue(new Date(now).toISOString(), zone)} onChange={setTiming} timezone={zone} /> : <p>Now</p>}
      {!stopping && state.snapshot?.switch_history_ready && current ? <SwitchActivityTimeline key={targetId} records={state.snapshot.records} plans={state.snapshot.plans ?? []} selected={selectedAt} now={now} zone={zone} nextActivity={blockIdentityText(nextIdentity)} currentStart={Date.parse(current.start_at)} enabled={!disabled && current.id === targetId} onSelect={at => { setTiming(at == null || at >= now ? null : activityTimeValue(new Date(at).toISOString(), zone)); setTimingError(null) }} /> :
        <section aria-label="After this change"><h3>After this change</h3><p>{current ? blockIdentityText(current) : "Unnamed activity"} ends {timing?.local.replace("T", " ") ?? "now"}.</p><p>{stopping ? "Time after this is unrecorded." : `${blockIdentityText(nextIdentity)} starts at the same time and continues.`}</p></section>}
      {timingError ? <p role="alert">{timingError}</p> : null}
      <div className="flex justify-end gap-4"><button type="button" onClick={() => { setSwitching(false); setStopping(false) }}>Cancel</button><button className="rounded-lg bg-[linear-gradient(135deg,#5d5e61_0%,#515255_100%)] px-4 py-2 text-on-primary disabled:opacity-40" disabled={disabled || (!stopping && !typeId)}>{stopping ? "Stop tracking" : "Switch activity"}</button></div>
    </form></ActivitySwitchDialog> : null}
  </div>
}

/** The choice shared by starting and switching: a required Task Type and an optional Block Name. */
function ActivitySelectionFields({ repository, taskTypes, typeId, onTypeId, name, onName }: {
  repository: ActivityRepository; taskTypes: TaskType[]; typeId: string; onTypeId: (id: string) => void; name: string; onName: (name: string) => void
}) {
  const [changing, setChanging] = useState(false)
  return <>
    {typeId && !changing ? <div><span className="mb-1 block">Task Type</span><div className="flex min-h-12 items-center justify-between gap-3 rounded-lg border border-outline-variant p-3 dark:border-dark-outline-variant"><span>{taskTypes.find(t => t.id === Number(typeId))?.name}</span><button type="button" className="min-h-11 px-2" onClick={() => setChanging(true)}>Change</button></div></div> : <TaskTypePathCombobox
        recommendationName={name}
      label="Task Type"
      taskTypes={taskTypes}
      valueTaskTypeId={typeId ? Number(typeId) : null}
      onSelectTaskTypeId={(id) => { onTypeId(id == null ? '' : String(id)); if (id != null) setChanging(false) }}
      onCreateTaskTypePath={async (name) => {
        const created = await api.createTaskType({ name })
        await repository.refresh()
        return created
      }}
    />}
    <label className="block">Block Name (optional)<input className="mt-1 block w-full rounded border p-2 dark:bg-dark-surface" maxLength={500} value={name} onChange={(e) => onName(e.target.value)} /></label>
  </>
}
