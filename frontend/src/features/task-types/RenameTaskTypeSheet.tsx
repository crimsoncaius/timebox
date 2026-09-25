import { useEffect, useRef, useState } from 'react'
import { api, ApiHttpError, type TaskType, type TaskTypeMergePreview } from '../../lib/api'

export function RenameTaskTypeSheet({ type, types, busy, error, onClose, onSave, onMerged }: {
  type: TaskType; types: TaskType[]; busy: boolean; error: string | null
  onClose: () => void; onSave: (name: string) => void; onMerged?: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [draft, setDraft] = useState(type.name)
  const [preview, setPreview] = useState<TaskTypeMergePreview | null>(null)
  const [pending, setPending] = useState(false)
  const [mergeError, setMergeError] = useState<string | null>(null)
  const [details, setDetails] = useState(false)
  const path = draft.split('/').map(part => part.trim().toLowerCase()).join('/')
  const affected = types.filter(t => t.id === type.id || t.name.startsWith(type.name + '/'))
  const target = types.find(t => t.name === path && t.id !== type.id)
  const blocked = target && (target.name === 'unspecified' || target.name.startsWith(type.name + '/') || type.name.startsWith(target.name + '/'))
  const invalid = path.split('/').some(part => !part) || !!blocked
  const saving = busy || pending
  useEffect(() => { dialog.current?.showModal() }, [])
  const fetchPreview = async (targetId: number) => {
    const next = await api.previewTaskTypeMerge(type.id, targetId)
    setPreview(next); setDetails(next.changes.length > 1)
  }
  const review = async () => {
    if (!target || saving) return
    setPending(true); setMergeError(null)
    try { await fetchPreview(target.id) }
    catch (e) { setMergeError(e instanceof Error ? e.message : 'Unable to load merge. Check your connection and retry.') }
    finally { setPending(false) }
  }
  const confirm = async () => {
    if (!preview || saving) return
    setPending(true); setMergeError(null)
    try { await api.mergeTaskType(preview); onMerged?.() }
    catch (e) {
      setMergeError(e instanceof Error ? e.message : 'Merge failed. Check your connection and retry.')
      if (e instanceof ApiHttpError && e.status === 409) {
        try { await fetchPreview(preview.target_id) }
        catch { setPreview(null); setMergeError('The branches are no longer available. Close and reopen Task Types to refresh.') }
      }
    } finally { setPending(false) }
  }
  const button = 'rounded-xl px-4 py-3 text-sm disabled:opacity-50'
  return <dialog ref={dialog} aria-labelledby="rename-type-title"
    className="fixed inset-x-0 bottom-0 top-auto m-0 mx-auto max-h-[90dvh] w-full max-w-2xl overflow-y-auto rounded-t-3xl border-0 bg-surface-container-lowest p-0 text-on-surface shadow-2xl backdrop:bg-black/40"
    onCancel={event => { event.preventDefault(); if (!saving) onClose() }}>
    <div className="p-6">
      <h2 id="rename-type-title" className="font-headline text-2xl">{preview ? 'Merge task types' : 'Rename task type'}</h2>
      <p className="mt-2 mb-6 break-words text-sm text-on-surface-variant">{preview ? `${preview.source_name} → ${preview.target_name}` : type.name}</p>
      {preview ? <div className="space-y-5">
        <div className="rounded-xl bg-surface-container-low p-4"><p className="text-xs uppercase tracking-wider text-on-surface-variant">Category to keep</p><p className="mt-1 break-words text-xl font-medium">{preview.target_name}</p><p className="mt-2 text-sm">All work from {preview.source_name} joins this branch.</p></div>
        <section aria-label="Affected work"><h3 className="mb-3 font-medium">Affected work</h3><dl className="grid grid-cols-2 gap-3">{[['Tasks', preview.task_count], ['Planned Blocks', preview.planned_block_count], ['Actual Blocks', preview.actual_block_count], ['Recurring Task Series', preview.recurring_series_count]].map(([label, count]) => <div key={label} className="rounded-xl bg-surface-container-low p-3"><dd className="text-2xl">{count}</dd><dt className="text-sm text-on-surface-variant">{label}</dt></div>)}</dl><p className="mt-3 text-xs text-on-surface-variant">Includes {preview.completed_task_count} completed, {preview.archived_task_count} archived and {preview.trashed_task_count} trashed Tasks. These counts may overlap.</p></section>
        <section><button type="button" aria-expanded={details} onClick={() => setDetails(!details)} className="flex min-h-12 w-full items-center justify-between text-left"><span>Branch changes · {preview.changes.length}</span><span>{details ? 'Hide ↑' : 'View ↓'}</span></button>{details && <ul className="divide-y divide-outline-variant/20 rounded-xl bg-surface-container-low px-4">{preview.changes.map(change => <li key={change.source_id} className="py-3"><p className="break-words font-medium">{change.target_name}</p><p className="break-words text-sm text-on-surface-variant">{change.action === 'combine' ? 'Combine' : 'Move'} from {change.source_name}</p></li>)}</ul>}</section>
        <p className="text-sm text-on-surface-variant">History combines. Task and Block links stay intact; other classifications stay unchanged. Activity tracking continues.</p>
      </div> : <form id="rename-type-form" className="space-y-5" onSubmit={event => { event.preventDefault(); if (!saving && !invalid) { if (target) void review(); else onSave(path) } }}>
        <label className="block text-sm">Task type path<input autoFocus disabled={saving} value={draft} onChange={event => { setDraft(event.target.value); setMergeError(null) }} aria-invalid={!!(error || mergeError)} className="mt-2 w-full rounded-xl bg-surface-container-high px-4 py-3 text-on-surface outline-primary" /></label>
        {blocked ? <p role="alert" className="text-sm text-error">Choose a separate branch. A type cannot merge with its ancestors, descendants, or unspecified.</p> : target ? <div className="rounded-xl bg-surface-container-low p-4"><p className="font-medium">{target.name} already exists.</p><p className="mt-2 text-sm">Combine these categories. Review the changes before confirming.</p></div> : <><p className="text-sm text-on-surface-variant">Updates this type{affected.length > 1 ? ` and ${affected.length - 1} nested types` : ''} everywhere, including past work.</p><div className="rounded-xl bg-surface-container-low p-4"><p className="mb-3 text-xs uppercase tracking-widest">After saving</p>{invalid ? <p>Enter a path with no empty segments.</p> : affected.map(t => <p className="break-words text-sm" key={t.id}>{path + t.name.slice(type.name.length)}</p>)}</div></>}
      </form>}
    </div>
    <footer className="sticky bottom-0 border-t border-outline-variant/20 bg-surface-container-lowest p-6">
      {(mergeError || error) && <p role="alert" className="mb-3 text-sm text-error">{mergeError || error}</p>}
      {preview && <div className="mb-4"><p className="font-medium">Permanent merge. No undo.</p><p className="text-sm text-on-surface-variant">{preview.source_name} disappears; its work is preserved.</p></div>}
      <div className="flex justify-end gap-3 pb-[env(safe-area-inset-bottom)]"><button type="button" disabled={saving} onClick={() => preview ? (setPreview(null), setMergeError(null)) : onClose()} className={button}>{preview ? 'Back' : 'Cancel'}</button>{preview ? <button disabled={saving} onClick={() => void confirm()} className={`${button} bg-primary text-on-primary`}>{saving ? 'Merging…' : 'Confirm merge'}</button> : <button form="rename-type-form" type="submit" disabled={saving || invalid} className={`${button} bg-primary text-on-primary`}>{saving ? 'Loading…' : target ? 'Review merge' : 'Save'}</button>}</div>
    </footer>
  </dialog>
}
