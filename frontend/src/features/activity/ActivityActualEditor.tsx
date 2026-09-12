import { useEffect, useState } from 'react'
import type { ActualBlock, BlockDraftPlacement, DayRead, TaskType } from '../../lib/api'
import { addDaysIso } from '../../lib/time'
import { ActivityTimeField } from './ActivityTimeField'
import { activityTimeValue, resolveActivityTime } from './activityTime'
import type { ActivityCorrection } from './activityRepository'

/** The Actual form inside the existing Day inspector rail / sheet. */
export function ActivityActualEditor({ actual, draft, day, taskTypes, onSave, onCreate, onDelete, onClose, onDirtyChange }: {
  actual?: ActualBlock; draft: BlockDraftPlacement | null; day: DayRead; taskTypes: TaskType[];
  onSave: (patch: ActivityCorrection) => Promise<void>;
  onCreate?: (patch: ActivityCorrection & { name: string | null; note: string | null }) => Promise<void>;
  onDelete: () => Promise<void>; onClose: () => void; onDirtyChange?: (dirty: boolean) => void;
}) {
  const zone = day.meta.timezone
  const draftLocal = (minute: number) => `${addDaysIso(day.date, Math.floor(minute / 1440))}T${String(Math.floor(minute % 1440 / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
  const [start, setStart] = useState(() => actual ? activityTimeValue(actual.start_at, zone) : { local: draftLocal(draft!.start_minute) })
  const [end, setEnd] = useState(() => actual?.end_at ? activityTimeValue(actual.end_at, zone) : { local: draftLocal(draft?.end_minute ?? 0) })
  const [name, setName] = useState(actual?.name ?? '')
  const [note, setNote] = useState(actual?.note ?? '')
  const [type, setType] = useState(actual?.task_type_id ?? draft?.task_type_id ?? taskTypes.find(t => t.name === 'unspecified')?.id ?? 0)
  const [dirty, setDirty] = useState(false), [saving, setSaving] = useState(false), [error, setError] = useState<string | null>(null)
  useEffect(() => onDirtyChange?.(dirty), [dirty, onDirtyChange])
  const running = actual && !actual.end_at
  return <form aria-label={actual ? 'Edit Actual Block' : 'Add Actual Block'} className="space-y-4 rounded-2xl bg-surface-container-low p-5 dark:bg-dark-surface-container" onChange={() => setDirty(true)} onSubmit={async e => {
    e.preventDefault(); setSaving(true); setError(null)
    try {
      const patch = { start_at: resolveActivityTime(start, zone), end_at: resolveActivityTime(end, zone), task_type_id: type || undefined, name: name.trim() || null, note: note.trim() || null }
      if (actual) await onSave(patch); else await onCreate?.(patch)
      setDirty(false); onDirtyChange?.(false)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not save correction') } finally { setSaving(false) }
  }}>
    <h2>{actual ? 'Actual Block' : 'New Actual Block'}</h2>
    <p className="text-xs">Reporting Time Zone: {zone}</p>
    {running ? <p>Use Switch or Stop above the Day timeline to correct the Current Activity.</p> : <>
      <ActivityTimeField label="Start" value={start} onChange={setStart} timezone={zone} />
      <ActivityTimeField label="End" value={end} onChange={setEnd} timezone={zone} />
      <label className="block">Task Type<select className="block w-full rounded border p-2 dark:bg-dark-surface" value={type} onChange={e => setType(Number(e.target.value))}>{taskTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
      <label className="block">Block Name (optional)<input className="block w-full rounded border p-2 dark:bg-dark-surface" maxLength={500} value={name} onChange={e => setName(e.target.value)} /></label>
      <label className="block">Note<textarea className="block w-full rounded border p-2 dark:bg-dark-surface" value={note} onChange={e => setNote(e.target.value)} /></label>
      {actual?.task ? <p>Task: {actual.task.title} · Task Completion stays independent.</p> : null}
      {error ? <p role="alert">{error}</p> : null}
      <button disabled={saving}>{actual ? 'Save changes' : 'Create block'}</button>
      {actual ? <button type="button" disabled={saving} className="ml-4 text-error" onClick={async () => {
        if (!window.confirm('Delete this Actual Block and leave its time unrecorded?')) return
        setSaving(true)
        try { await onDelete(); setDirty(false); onDirtyChange?.(false) } catch (cause) { setError(String(cause)) } finally { setSaving(false) }
      }}>Delete</button> : null}
    </>}
    <button type="button" className="ml-4" onClick={onClose}>Cancel</button>
  </form>
}
