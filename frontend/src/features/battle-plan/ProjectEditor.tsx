import { useState } from 'react'
import { isoToZonedLocal, zonedLocalToIso } from '../../lib/battlePlan'
import type { Project, ProjectWrite } from '../../lib/api'

export function ProjectEditor({
  project,
  timezone,
  taskCount,
  onSave,
  onDelete,
  onClose,
}: {
  project: Project | null
  timezone: string
  taskCount: number
  onSave: (body: ProjectWrite) => Promise<void>
  onDelete: (() => Promise<void>) | null
  onClose: () => void
}) {
  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [deadlineMode, setDeadlineMode] = useState<'none' | 'date' | 'datetime'>(
    project?.deadline_at ? 'datetime' : project?.deadline_date ? 'date' : 'none',
  )
  const [deadlineDate, setDeadlineDate] = useState(project?.deadline_date ?? '')
  const [deadlineAt, setDeadlineAt] = useState(isoToZonedLocal(project?.deadline_at ?? null, timezone))
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    if (!name.trim()) return
    setBusy(true)
    try {
      await onSave({
        name: name.trim(),
        description,
        deadline_date: deadlineMode === 'date' && deadlineDate ? deadlineDate : null,
        deadline_at: deadlineMode === 'datetime' && deadlineAt ? zonedLocalToIso(deadlineAt, timezone) : null,
      })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-90 flex items-center justify-center bg-black/35 p-4 backdrop-blur-sm" onMouseDown={onClose}>
      <section className="w-full max-w-lg rounded-3xl bg-surface-container-lowest p-6 shadow-2xl dark:bg-dark-surface-container-lowest" onMouseDown={(event) => event.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="font-headline text-2xl font-light tracking-tight">{project ? 'Edit project' : 'New project'}</h2>
          <button type="button" className="rounded-full p-2" aria-label="Close project editor" onClick={onClose}>
            <span className="material-symbols-outlined" aria-hidden>close</span>
          </button>
        </div>
        <div className="mt-6 space-y-4">
          <label className="block">
            <span className="text-xs text-on-surface-variant">Name</span>
            <input autoFocus value={name} onChange={(event) => setName(event.target.value)} className="mt-1 w-full rounded-xl bg-surface-container-low px-3 py-2.5 outline-none dark:bg-dark-surface-container" />
          </label>
          <label className="block">
            <span className="text-xs text-on-surface-variant">Description</span>
            <textarea rows={4} value={description} onChange={(event) => setDescription(event.target.value)} className="mt-1 w-full resize-y rounded-xl bg-surface-container-low px-3 py-2.5 outline-none dark:bg-dark-surface-container" />
          </label>
          <label className="block">
            <span className="text-xs text-on-surface-variant">Deadline</span>
            <select value={deadlineMode} onChange={(event) => setDeadlineMode(event.target.value as typeof deadlineMode)} className="mt-1 w-full rounded-xl bg-surface-container-low px-3 py-2.5 dark:bg-dark-surface-container">
              <option value="none">No deadline</option>
              <option value="date">Date only</option>
              <option value="datetime">Date and time</option>
            </select>
          </label>
          {deadlineMode === 'date' ? <input aria-label="Project deadline date" type="date" value={deadlineDate} onChange={(event) => setDeadlineDate(event.target.value)} className="w-full rounded-xl bg-surface-container-low px-3 py-2.5 dark:bg-dark-surface-container" /> : null}
          {deadlineMode === 'datetime' ? <input aria-label="Project deadline date and time" type="datetime-local" value={deadlineAt} onChange={(event) => setDeadlineAt(event.target.value)} className="w-full rounded-xl bg-surface-container-low px-3 py-2.5 dark:bg-dark-surface-container" /> : null}
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
