import { useState, type ReactNode } from 'react'
import { ActivitySwitchDialog } from './ActivitySwitchDialog'
import type { ActivityRepository, ActivitySnapshot } from './activityRepository'
import { blockIdentityText } from '../../lib/blockIdentity'
import { ActivityTimeField } from './ActivityTimeField'
import { SwitchActivityTimeline } from './SwitchActivityTimeline'
import { activityTimeValue, resolveActivityTime, type ActivityTimeValue } from './activityTime'

/** The same confirmed duration and plan preview for Start, Switch and Adjust. */
export function PlanNowEditor({ repository, snapshot, now, title, typeId, name, taskId, keepCurrent, selectionFields, onClose, onSaved }: {
  repository: ActivityRepository; snapshot: ActivitySnapshot; now: number; title: string
  typeId: number | null; name: string; taskId: number | null; keepCurrent: boolean
  selectionFields: ReactNode; onClose: () => void; onSaved: () => void
}) {
  const [duration, setDuration] = useState('')
  const [custom, setCustom] = useState('45')
  const [revision, setRevision] = useState(snapshot.plan_now_revision!)
  const [targetId] = useState(snapshot.current?.id ?? null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [confirmation, setConfirmation] = useState<number[] | null>(null)
  const [timing, setTiming] = useState<ActivityTimeValue | null>(null)
  const minutes = duration === '' ? null : Number(duration === 'custom' ? custom : duration)
  const valid = minutes === null || (Number.isInteger(minutes) && minutes >= 1 && minutes <= 90)
  const online = !repository.state.offline && !repository.state.pending
  const changed = (snapshot.current?.id ?? null) !== targetId
  const zone = snapshot.reporting_timezone
  let selectedAt = now
  let timingValid = true
  try { if (timing) selectedAt = Date.parse(resolveActivityTime(timing, zone)) } catch { timingValid = false }
  timingValid = timingValid && selectedAt <= now && (!!snapshot.switch_history_ready || !snapshot.current || selectedAt >= Date.parse(snapshot.current.start_at))
  const end = now + (minutes ?? 0) * 60000
  const time = (at: number) => new Intl.DateTimeFormat(undefined, { timeZone: snapshot.reporting_timezone, hour: '2-digit', minute: '2-digit' }).format(at)
  const label = blockIdentityText({ name: name.trim(), task: keepCurrent ? snapshot.current?.task : null, task_type: snapshot.task_types?.find(t => t.id === typeId) })
  const matching = snapshot.plans?.find(p => p.task_type_id === typeId && p.task_id === taskId && (p.name ?? '') === name.trim() && Date.parse(p.start_at) <= now && (Date.parse(p.end_at) > now || (keepCurrent && p.id === snapshot.current?.planned_block_id)))
  const displaced = (snapshot.plans ?? []).filter(p => p.id !== matching?.id && Date.parse(p.start_at) < end && Date.parse(p.end_at) > now)
  const later = displaced.some(p => Date.parse(p.start_at) > now)
  const identity = (id: number) => {
    const p = snapshot.plans?.find(p => p.id === id)
    return p ? blockIdentityText({ name: p.name, task: p.task_title ? { title: p.task_title } : null, task_type: snapshot.task_types?.find(t => t.id === p.task_type_id) }) : 'Changed plan'
  }
  async function save(ids: number[]) {
    if (busy || !typeId || !valid) return
    setBusy(true); setError('')
    try {
      const saved = minutes !== null
        ? await repository.planNow({ operation_id: crypto.randomUUID(), revision, current_id: targetId,
          effective_at: new Date(repository.now()).toISOString(), minutes, task_type_id: typeId, task_id: taskId,
          name: name.trim() || null, replace_plan_ids: ids })
        : keepCurrent || await repository.command(targetId == null ? 'start' : 'switch', typeId, name.trim(), false,
          { task_id: taskId }, targetId == null ? undefined : { targetId, at: timing ? resolveActivityTime(timing, zone) : undefined })
      if (saved) onSaved()
      else { setError(repository.state.error || 'Could not save. Review the preview and try again.'); setRevision(repository.state.snapshot?.plan_now_revision ?? revision) }
    } catch (error) { setError(String(error)) } finally { setBusy(false) }
  }
  return <ActivitySwitchDialog open onClose={() => { if (!busy) onClose() }} title={title}>
    <form className="space-y-4" onSubmit={event => {
      event.preventDefault()
      const ids = displaced.map(p => p.id)
      if (minutes !== null && later) setConfirmation(ids)
      else void save(ids)
    }}>
      <fieldset disabled={busy} className="space-y-4">{selectionFields}</fieldset>
      {minutes === null && targetId != null && !keepCurrent && <section className="space-y-3">
        <p>When did this change happen?</p>
        <div className="flex gap-4"><button type="button" onClick={() => setTiming(null)}>Now</button><button type="button" disabled={!snapshot.switch_history_ready && now - 900000 < Date.parse(snapshot.current!.start_at)} onClick={() => setTiming(activityTimeValue(new Date(now - 900000).toISOString(), zone))}>15 min ago</button><button type="button" onClick={() => setTiming(activityTimeValue(new Date(now).toISOString(), zone))}>Choose time</button></div>
        <ActivityTimeField label="Change time" value={timing ?? activityTimeValue(new Date(now).toISOString(), zone)} onChange={setTiming} timezone={zone} />
        {snapshot.switch_history_ready && snapshot.current && <SwitchActivityTimeline records={snapshot.records} plans={snapshot.plans ?? []} selected={selectedAt} now={now} zone={zone} nextActivity={label} currentStart={Date.parse(snapshot.current.start_at)} enabled={!busy && !changed} onSelect={at => setTiming(at == null || at >= now ? null : activityTimeValue(new Date(at).toISOString(), zone))} />}
        {!timingValid && <p role="alert">Choose a valid time no later than now.</p>}
      </section>}
      <fieldset disabled={busy} className="space-y-2">
        <legend className="mb-2 font-medium">How long from now?</legend>
        <div className="flex flex-wrap gap-2">{['', '15', '30', '60', '90', 'custom'].map(value =>
          <button key={value} type="button" aria-pressed={duration === value} onClick={() => setDuration(value)}
            className={`min-h-11 rounded-lg border px-3 py-2 ${duration === value ? 'bg-primary-container text-on-primary-container dark:bg-dark-surface-container-high dark:text-dark-on-surface' : 'border-outline-variant dark:border-dark-outline-variant'}`}>
            {value === '' ? keepCurrent ? 'Keep plan' : 'Open-ended' : value === 'custom' ? 'Custom' : `${value} min`}
          </button>)}</div>
        {duration === 'custom' && <div className="flex items-end gap-4"><label>Duration<div className="mt-1 flex items-center gap-2"><input type="number" min="1" max="90" step="1" inputMode="numeric" value={custom} onChange={e => setCustom(e.target.value)} className="w-24 rounded-lg border border-outline-variant bg-transparent p-3" aria-invalid={!valid} /><span>min</span></div></label><p className="pb-3 text-sm">{valid ? 'Choose' : 'Enter'} 1–90 minutes</p></div>}
        {!online && <p role="status">Connect and sync to change your plan. Open-ended tracking is still available.</p>}
      </fieldset>
      {minutes !== null && valid && <section aria-label="After this change" className="space-y-2 rounded-xl bg-surface-container p-4 dark:bg-dark-surface-container-high">
        <h3 className="text-lg">{label} until {time(end)}</h3>
        <p>{matching ? 'Your plan now ends here. Earlier planned time stays as it is.' : `${minutes} minutes added to your plan from confirmation.`}</p>
        {displaced.map(p => <p key={p.id}>{identity(p.id)} {Date.parse(p.end_at) > end ? `resumes at ${time(end)}, until ${time(Date.parse(p.end_at))}.` : `is replaced until ${time(Date.parse(p.end_at))}.`}</p>)}
        {keepCurrent && <p>Running Time does not restart.</p>}
        <p>Tracking continues when planned time ends.</p>
      </section>}
      {(changed || error) && <p role="alert">{changed ? 'The Current Activity changed. Close this editor and review it.' : error}</p>}
      <div className="flex justify-end gap-3 border-t border-outline-variant pt-4 dark:border-dark-outline-variant">
        <button type="button" disabled={busy} onClick={onClose} className="min-h-11 px-4">Cancel</button>
        <button disabled={busy || changed || !typeId || !valid || (minutes === null && !keepCurrent && !timingValid) || (minutes !== null && !online)} className="min-h-11 rounded-full bg-primary px-6 py-3 text-on-primary disabled:opacity-40">
          {busy ? 'Saving…' : targetId == null ? 'Start' : keepCurrent ? minutes === null ? 'Done' : 'Save plan' : 'Switch activity'}
        </button>
      </div>
    </form>
    {confirmation && <ActivitySwitchDialog open title="Replace this planned time?" onClose={() => setConfirmation(null)}>
      <p>{label} will replace overlapping time in {confirmation.map(identity).join(', ')}. Time outside this interval stays in place.</p>
      <div className="mt-5 flex justify-end gap-3"><button className="min-h-11 px-4" onClick={() => setConfirmation(null)}>Back</button><button className="min-h-11 rounded-full bg-primary px-5 text-on-primary" onClick={() => { const ids = confirmation; setConfirmation(null); void save(ids) }}>Replace this time</button></div>
    </ActivitySwitchDialog>}
  </ActivitySwitchDialog>
}
