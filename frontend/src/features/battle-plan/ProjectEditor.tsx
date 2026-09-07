import { useCallback, useRef, useState } from 'react'
import type { Project, ProjectWrite } from '../../lib/api'

export function ProjectEditor({
  project,
  taskCount,
  onSave,
  onDelete,
  onClose,
}: {
  project: Project | null
  taskCount: number
  onSave: (body: ProjectWrite) => Promise<void>
  onDelete: (() => Promise<void>) | null
  onClose: () => void
}) {
  const initialDraftRef = useRef({
    name: project?.name ?? '',
  })
  const initialDraft = initialDraftRef.current
  const [name, setName] = useState(initialDraft.name)
  const [busy, setBusy] = useState(false)
  const isDirty = name !== initialDraft.name

  const requestClose = useCallback(() => {
    if (isDirty && !window.confirm('Discard your unsaved changes?')) return
    onClose()
  }, [isDirty, onClose])

  const submit = async () => {
    if (!name.trim()) return
    setBusy(true)
    try {
      await onSave({
        name: name.trim(),
      })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-90 flex items-center justify-center bg-black/35 p-4 backdrop-blur-sm" onMouseDown={requestClose}>
      <section className="w-full max-w-lg rounded-3xl bg-surface-container-lowest p-6 shadow-2xl dark:bg-dark-surface-container-lowest" onMouseDown={(event) => event.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="font-headline text-2xl font-light tracking-tight">{project ? 'Edit project' : 'New project'}</h2>
          <button type="button" className="rounded-full p-2" aria-label="Close project editor" onClick={requestClose}>
            <span className="material-symbols-outlined" aria-hidden>close</span>
          </button>
        </div>
        <div className="mt-6 space-y-4">
          <label className="block">
            <span className="text-xs text-on-surface-variant">Name</span>
            <input autoFocus value={name} onChange={(event) => setName(event.target.value)} className="mt-1 w-full rounded-xl bg-surface-container-low px-3 py-2.5 outline-none dark:bg-dark-surface-container" />
          </label>

        </div>
        <div className="mt-7 flex items-center justify-between gap-3">
          {onDelete ? (
            <button
              type="button"
              className="text-sm text-error"
              onClick={async () => {
                if (!window.confirm(`Permanently delete ${project?.name} and ${taskCount} task${taskCount === 1 ? '' : 's'}? This cannot be undone.`)) return
                setBusy(true)
                try { await onDelete() } finally { setBusy(false) }
              }}
            >
              Delete permanently
            </button>
          ) : <span />}
          <button type="button" disabled={busy || !name.trim()} onClick={() => void submit()} className="rounded-xl bg-primary px-5 py-2.5 text-sm text-on-primary disabled:opacity-40">
            {busy ? 'Saving…' : 'Save project'}
          </button>
        </div>
      </section>
    </div>
  )
}
