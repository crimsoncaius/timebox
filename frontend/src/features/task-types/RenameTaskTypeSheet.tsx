import { useEffect, useRef, useState } from 'react'
import type { TaskType } from '../../lib/api'

export function RenameTaskTypeSheet({ type, types, busy, error, onClose, onSave }: {
  type: TaskType; types: TaskType[]; busy: boolean; error: string | null
  onClose: () => void; onSave: (name: string) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [draft, setDraft] = useState(type.name)
  const path = draft.split('/').map(part => part.trim().toLowerCase()).join('/')
  const affected = types.filter(t => t.id === type.id || t.name.startsWith(type.name + '/'))
  const invalid = path.split('/').some(part => !part)
  useEffect(() => { dialog.current?.showModal() }, [])
  return <dialog ref={dialog} aria-labelledby="rename-type-title"
    className="fixed inset-x-0 bottom-0 top-auto m-0 mx-auto max-h-[90dvh] w-full max-w-2xl overflow-y-auto rounded-t-3xl border-0 bg-surface-container-lowest p-6 text-on-surface shadow-2xl backdrop:bg-black/40"
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>
    <h2 id="rename-type-title" className="font-headline text-2xl font-light">Rename task type</h2>
    <p className="mt-2 mb-6 break-all text-sm text-on-surface-variant">{type.name}</p>
    <form className="space-y-5" onSubmit={event => { event.preventDefault(); if (!busy && !invalid) onSave(path) }}>
      <label className="block text-sm">Task type path<input autoFocus disabled={busy} value={draft}
        onChange={event => setDraft(event.target.value)} aria-describedby="rename-type-help" aria-invalid={!!error}
        className="mt-2 w-full rounded-xl bg-surface-container-high px-4 py-3 text-on-surface outline-primary" /></label>
      <p id="rename-type-help" className="text-sm text-on-surface-variant">Updates this type{affected.length > 1 ? ` and ${affected.length - 1} nested types` : ''} everywhere, including past work.</p>
      <div className="rounded-xl bg-surface-container-low p-4"><p className="mb-3 text-xs uppercase tracking-widest text-on-surface-variant">After saving</p>
        {invalid ? <p className="text-sm text-on-surface-variant">Enter a path with no empty segments.</p> : <ul className="max-h-48 space-y-2 overflow-y-auto text-sm">{affected.map(t => <li className="break-all" key={t.id}>{path + t.name.slice(type.name.length)}</li>)}</ul>}
      </div>
      {error && <p role="alert" className="text-sm text-error">{error}</p>}
      <div className="flex justify-end gap-3 pb-[env(safe-area-inset-bottom)]"><button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-outline-variant/30 px-4 py-2 disabled:opacity-50">Cancel</button><button type="submit" disabled={busy || invalid} className="rounded-lg bg-primary px-5 py-2 text-on-primary disabled:opacity-50">{busy ? 'Saving…' : 'Save'}</button></div>
    </form>
  </dialog>
}
